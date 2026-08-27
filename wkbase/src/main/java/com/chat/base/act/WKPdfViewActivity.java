package com.chat.base.act;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chat.base.R;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class WKPdfViewActivity extends AppCompatActivity {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 4.0f;
    private static final int INVALID_POINTER = -1;

    private LinearLayout container;
    private ScrollView scrollView;
    private TextView loadingTv;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.0f;
    private int touchSlop;
    // 横向拖动的跟踪状态；竖向照旧交给 ScrollView
    private int panPointerId = INVALID_POINTER;
    private float lastPanX;
    private float lastPanY;
    private boolean panning;
    private int pendingScrollY;
    private boolean scrollCorrectionScheduled;
    // 复用的 getLocationInWindow 出参，避免每次缩放事件都分配
    private final int[] viewportOffset = new int[2];
    // layout 完成、绘制之前用新的高度补正 scrollY，见 applyScale
    private final ViewTreeObserver.OnPreDrawListener scrollCorrection =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    scrollView.getViewTreeObserver().removeOnPreDrawListener(this);
                    scrollCorrectionScheduled = false;
                    scrollView.scrollTo(0, pendingScrollY);
                    return true;
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scrollView = new ScrollView(this);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        // 缩放锚点固定在左上角：内容只向右、向下溢出，右侧靠 translationX、底部靠
        // 撑高测量高度变成可达区域。用中心锚点的话两侧同时溢出，可平移范围算不干净。
        container.setPivotX(0f);
        container.setPivotY(0f);
        scrollView.addView(container, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        loadingTv = new TextView(this);
        loadingTv.setText(getString(R.string.pdf_loading));
        loadingTv.setGravity(Gravity.CENTER);
        loadingTv.setPadding(0, 200, 0, 0);
        loadingTv.setTextSize(15);
        loadingTv.setTextColor(0xFF999999);
        container.addView(loadingTv);

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            // 每帧最多回调一次：ACTION_MOVE 的多个采样点会累积进同一个 MotionEvent 的
            // history（见 MotionEvent 类文档 "Batching" 一节），触摸采样率高于刷新率时
            // 增加的是 getHistorySize()、不是分发次数，而 ScaleGestureDetector 只读当前
            // 坐标、不遍历 historical 点。所以两次 applyScale 之间必定隔着一次 traversal。
            // 不过这只是背景，不构成正确性依赖 —— updateVerticalScrollRange 读的量与
            // layout 时机无关，见 measureContentHeight()。
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float previousScale = scaleFactor;
                scaleFactor = clamp(scaleFactor * detector.getScaleFactor(), MIN_SCALE, MAX_SCALE);
                if (scaleFactor != previousScale) {
                    applyScale(previousScale, detector.getFocusX(), detector.getFocusY());
                }
                return true;
            }
        });

        String url = getIntent().getStringExtra("url");
        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }
        downloadAndRender(url);
    }

    /**
     * 以手势焦点为锚点应用新的缩放级别。
     * <p>
     * 传入的 focus 是 window 坐标（Activity.dispatchTouchEvent 的事件坐标系），而锚点公式
     * 要的是相对 ScrollView 视口的坐标。主题是 NoActionBar 且未开 edge-to-edge，视口顶边
     * 在状态栏之下，两者差一个状态栏高度；不减掉的话每次缩放都会引入
     * {@code offset * (ratio - 1)} 的锚点漂移并逐次累积。
     */
    private void applyScale(float previousScale, float windowFocusX, float windowFocusY) {
        scrollView.getLocationInWindow(viewportOffset);
        float focusX = windowFocusX - viewportOffset[0];
        float focusY = windowFocusY - viewportOffset[1];

        float ratio = scaleFactor / previousScale;
        float offsetX = anchoredOffset(focusX, -container.getTranslationX(), ratio);
        float offsetY = anchoredOffset(focusY, scrollView.getScrollY(), ratio);

        container.setScaleX(scaleFactor);
        container.setScaleY(scaleFactor);
        updateVerticalScrollRange();
        container.setTranslationX(-clampOffsetX(offsetX));

        pendingScrollY = Math.max(0, Math.round(offsetY));
        // setPadding 触发的 requestLayout 是异步的，此刻 scrollTo 仍会被旧高度钳制
        // （ScrollView.scrollTo 按 child.getHeight() 夹取），所以先尽力滚一次保证跟手，
        // 再在 layout 完成、绘制之前按新高度补正一次。
        scrollView.scrollTo(0, pendingScrollY);
        if (!scrollCorrectionScheduled) {
            scrollCorrectionScheduled = true;
            scrollView.getViewTreeObserver().addOnPreDrawListener(scrollCorrection);
        }
    }

    /**
     * 用底部内边距把 container 的测量高度撑到 H * scale，让 ScrollView 的滚动范围跟上缩放。
     * <p>
     * 只能用 padding，两条约束都验证过：
     * <ul>
     *   <li>ScrollView 的滚动范围只认子 View 的测量高度 —— {@code getScrollRange()} 与
     *       {@code scrollTo()} 都按 {@code child.getHeight()} 夹取，<b>不计 margin</b>
     *       （计 margin 的是 androidx 的 NestedScrollView，两者别混）；</li>
     *   <li>{@code ScrollView.measureChildWithMargins()} 强制以 UNSPECIFIED 测量子 View，
     *       <b>无视 LayoutParams.height</b>，所以直接设高度同样没用。</li>
     * </ul>
     * 而 LinearLayout 自身测量时一定把 padding 计进 mTotalLength，不受父级 spec 影响。
     * <p>
     * setScaleY 只改绘制、不改测量尺寸，配合 pivotY = 0 内容绘制在 0..H*scale；撑高后
     * 滚到底时内容底部正好落在视口底部，多出来的空白在视口之外。
     */
    private void updateVerticalScrollRange() {
        int contentHeight = measureContentHeight();
        if (contentHeight <= 0) {
            return;
        }
        int padding = scaleCompensationPadding(contentHeight, scaleFactor);
        if (container.getPaddingBottom() != padding) {
            // setPadding 会触发 requestLayout，捏合期间每帧重测一次 container。协议类 PDF
            // 页数少、ImageView 的 measure 是常数级，成本可忽略；若长文档上出现掉帧，
            // 可改成手势结束时才应用最终缩放级别。
            container.setPadding(0, 0, 0, padding);
        }
    }

    /**
     * 未缩放的内容高度：末个子 View 的底边加它的下外边距。
     * <p>
     * 刻意不用 {@code getHeight() - getPaddingBottom()} —— 那两个量的更新时机不同
     * （setPadding 同步生效，getHeight 要等下一次 traversal），一旦在同一次 layout 间隔里
     * 读到「旧高度 + 新 padding」就会算出偏小的内容高度，补偿 padding 随之偏小，
     * 放大后底部又滚不到。竖向 LinearLayout 的 paddingBottom 不参与子 View 定位
     * （参与的是 paddingTop，这里恒为 0），所以子 View 的 getBottom() 与补偿 padding
     * 正交，无论 layout 有没有跟上都是同一个值。
     * <p>
     * 正因为读的量与 layout 时机无关，也就不需要 isLayoutRequested() 延迟重算或
     * OnLayoutChangeListener 维护一个 contentHeight 字段：前者会被 setPadding 自身触发的
     * layout 再次唤醒、得额外防循环，后者要引入可变状态和刷新时机。
     * <p>
     * paddingTop 这里恒为 0；即便将来不是，公式依然闭合 —— getBottom() 已经含 paddingTop
     * 偏移，而缩放基准正是「container 顶边到内容底边」的距离。
     */
    private int measureContentHeight() {
        int count = container.getChildCount();
        if (count == 0) {
            return 0;
        }
        View last = container.getChildAt(count - 1);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) last.getLayoutParams();
        return last.getBottom() + lp.bottomMargin;
    }

    /** 缩放 scale 倍后 container 应有的测量高度，ScrollView 靠它算滚动范围。 */
    @VisibleForTesting
    static int scaledContentHeight(int contentHeight, float scale) {
        return Math.max(contentHeight, Math.round(contentHeight * scale));
    }

    /** 把测量高度从 contentHeight 撑到缩放后高度所需的底部内边距。 */
    @VisibleForTesting
    static int scaleCompensationPadding(int contentHeight, float scale) {
        return scaledContentHeight(contentHeight, scale) - contentHeight;
    }

    /**
     * 以 focus 为锚点缩放后的新偏移量。
     * <p>
     * 缩放锚点在左上角时，内容坐标 c 的屏幕位置是 {@code c * scale - offset}
     * （横向 offset 即 -translationX，竖向即 scrollY）。要让 focus 处的内容在缩放前后
     * 停在同一位置，解出 {@code offset1 = (focus + offset0) * ratio - focus}，
     * 其中 {@code ratio = scale1 / scale0}。
     */
    @VisibleForTesting
    static float anchoredOffset(float focus, float previousOffset, float ratio) {
        return (focus + previousOffset) * ratio - focus;
    }

    /** 放大后内容宽度为 width * scale，可横向平移的范围是 [0, width * (scale - 1)]。 */
    private float clampOffsetX(float offsetX) {
        return clamp(offsetX, 0f, container.getWidth() * (scaleFactor - 1f));
    }

    @VisibleForTesting
    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        trackHorizontalPan(ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 外层只有竖向的 ScrollView，放大后横向溢出的部分没有任何载体能滑到。
     * 这里单独跟踪横向位移转成 translationX；事件照常往下传，竖向仍由 ScrollView 消费。
     */
    private void trackHorizontalPan(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                panPointerId = ev.getPointerId(0);
                lastPanX = ev.getX();
                lastPanY = ev.getY();
                panning = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 第二根手指落下到 ScaleGestureDetector 越过 span slop 之间有一小段空窗，
                // 此时两轴会被不同手指带着走。重置基准，把这段交给缩放。
                int index = ev.findPointerIndex(panPointerId);
                if (index >= 0) {
                    lastPanX = ev.getX(index);
                    lastPanY = ev.getY(index);
                }
                panning = false;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (panPointerId == INVALID_POINTER) break;
                int index = ev.findPointerIndex(panPointerId);
                if (index < 0) break;
                float x = ev.getX(index);
                float y = ev.getY(index);
                if (scaleDetector.isInProgress()) {
                    // 缩放中不额外平移，但基准要跟手，否则手势结束那一下会跳
                    lastPanX = x;
                    lastPanY = y;
                    panning = false;
                    break;
                }
                float dx = x - lastPanX;
                if (!panning) {
                    // 轴向锁定：只有横向占优才进入平移。否则一次长距离竖向滚动里累积的
                    // 横向抖动迟早超过 slop，会把页面拖偏（基准在未锁定前不刷新，dx 从
                    // ACTION_DOWN 起累计）。
                    if (!shouldStartHorizontalPan(dx, y - lastPanY, touchSlop)) break;
                    panning = true;
                    lastPanX = x;
                    lastPanY = y;
                    break;
                }
                lastPanX = x;
                lastPanY = y;
                container.setTranslationX(-clampOffsetX(-container.getTranslationX() - dx));
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // 抬起的正好是被跟踪的那根手指时换一根，避免基准突跳
                int index = ev.getActionIndex();
                if (ev.getPointerId(index) == panPointerId) {
                    int next = index == 0 ? 1 : 0;
                    panPointerId = ev.getPointerId(next);
                    lastPanX = ev.getX(next);
                    lastPanY = ev.getY(next);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                panPointerId = INVALID_POINTER;
                panning = false;
                break;
            default:
                break;
        }
    }

    /** 越过 slop 且横向位移占优时才开始横向平移，避免竖向滚动被判成平移。 */
    @VisibleForTesting
    static boolean shouldStartHorizontalPan(float dx, float dy, int touchSlop) {
        return Math.abs(dx) >= touchSlop && Math.abs(dx) > Math.abs(dy);
    }

    private void downloadAndRender(String pdfUrl) {
        Executors.newSingleThreadExecutor().execute(() -> {
            File tempFile = null;
            try {
                tempFile = File.createTempFile("pdf_view_", ".pdf", getCacheDir());
                HttpURLConnection conn = (HttpURLConnection) new URL(pdfUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                }
                conn.disconnect();

                ParcelFileDescriptor fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(fd);
                int pageCount = renderer.getPageCount();
                int screenWidth = getResources().getDisplayMetrics().widthPixels;

                Bitmap[] bitmaps = new Bitmap[pageCount];
                for (int i = 0; i < pageCount; i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    float scale = (float) screenWidth / page.getWidth();
                    int bmpWidth = screenWidth;
                    int bmpHeight = (int) (page.getHeight() * scale);
                    Bitmap bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(0xFFFFFFFF);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    bitmaps[i] = bmp;
                }
                renderer.close();
                fd.close();

                runOnUiThread(() -> {
                    container.removeAllViews();
                    for (Bitmap bmp : bitmaps) {
                        ImageView iv = new ImageView(this);
                        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        iv.setAdjustViewBounds(true);
                        iv.setImageBitmap(bmp);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.bottomMargin = 8;
                        container.addView(iv, lp);
                    }
                    // 内容高度从 loading 文案变成了实际页面，按当前缩放级别重算滚动范围。
                    // 这个 runnable 跑在 layout 之后，不是之前：上面的 addView 已触发
                    // requestLayout()，而 ViewRootImpl.scheduleTraversals() 会先往主线程
                    // 队列装 sync barrier、再把 traversal 作为异步消息排入；View.post 发的
                    // 是同步消息，被 barrier 挡到 traversal 完成之后。该方法的注释把这条
                    // 列为 "load-bearing for public API correctness"，并以
                    // setText → post → getWidth 举例，与此处 addView → post → 读子 View
                    // 位置同构。所以不需要换成 OnPreDrawListener。
                    container.post(this::updateVerticalScrollRange);
                });
            } catch (Exception e) {
                String errMsg = e.getMessage();
                runOnUiThread(() -> {
                    loadingTv.setText(getString(R.string.pdf_load_failed, errMsg));
                    loadingTv.setTextColor(0xFFE53935);
                });
            } finally {
                if (tempFile != null) tempFile.delete();
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
