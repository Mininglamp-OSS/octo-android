package com.chat.base.act;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
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
    private boolean panning;
    private int pendingScrollY;
    private boolean scrollCorrectionScheduled;
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
     */
    private void applyScale(float previousScale, float focusX, float focusY) {
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
        int contentHeight = container.getHeight() - container.getPaddingBottom();
        if (contentHeight <= 0) {
            return;
        }
        int padding = scaleCompensationPadding(contentHeight, scaleFactor);
        if (container.getPaddingBottom() != padding) {
            container.setPadding(0, 0, 0, padding);
        }
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
                panning = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                if (panPointerId == INVALID_POINTER) break;
                int index = ev.findPointerIndex(panPointerId);
                if (index < 0) break;
                float x = ev.getX(index);
                if (scaleDetector.isInProgress()) {
                    // 缩放中不额外平移，但基准要跟手，否则手势结束那一下会跳
                    lastPanX = x;
                    panning = false;
                    break;
                }
                float dx = x - lastPanX;
                if (!panning) {
                    if (Math.abs(dx) < touchSlop) break;
                    panning = true;
                    lastPanX = x;
                    break;
                }
                lastPanX = x;
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
                    // 内容高度从 loading 文案变成了实际页面，按当前缩放级别重算滚动范围
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
