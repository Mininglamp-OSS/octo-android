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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chat.base.R;

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
    private final Runnable applyPendingScroll = new Runnable() {
        @Override
        public void run() {
            scrollView.scrollTo(0, pendingScrollY);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scrollView = new ScrollView(this);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        // 缩放锚点固定在左上角：内容只向右、向下溢出，右侧靠 translationX、
        // 底部靠 bottomMargin 变成可达区域。用中心锚点的话两侧同时溢出，
        // 可平移范围和 ScrollView 的滚动范围都算不干净。
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
     * <p>
     * 锚点在左上角时，内容坐标 c 的屏幕位置是 {@code c * scale - offset}（横向 offset 即
     * -translationX，竖向即 scrollY）。要让焦点前后停在同一处内容上，解出新的偏移：
     * {@code offset1 = (focus + offset0) * scale1 / scale0 - focus}。
     */
    private void applyScale(float previousScale, float focusX, float focusY) {
        float offsetX = -container.getTranslationX();
        float offsetY = scrollView.getScrollY();
        float ratio = scaleFactor / previousScale;

        container.setScaleX(scaleFactor);
        container.setScaleY(scaleFactor);
        updateVerticalScrollRange();
        container.setTranslationX(-clampOffsetX((focusX + offsetX) * ratio - focusX));

        pendingScrollY = Math.max(0, Math.round((focusY + offsetY) * ratio - focusY));
        // 先立即滚一次保证跟手（会被旧的滚动范围钳制），再等 bottomMargin 触发的
        // layout 生效后补正一次，否则放大时竖向锚点会往回漂。
        scrollView.scrollTo(0, pendingScrollY);
        scrollView.removeCallbacks(applyPendingScroll);
        scrollView.post(applyPendingScroll);
    }

    /**
     * setScaleY 只改绘制、不改 layout 尺寸，ScrollView 仍按未缩放的高度算滚动范围。
     * 把放大多出来的高度补进 bottomMargin，底部才滑得到（ScrollView 的滚动范围含子 View margin）。
     */
    private void updateVerticalScrollRange() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) container.getLayoutParams();
        int extraHeight = Math.round(container.getHeight() * (scaleFactor - 1f));
        if (lp.bottomMargin != extraHeight) {
            lp.bottomMargin = extraHeight;
            container.setLayoutParams(lp);
        }
    }

    /** 放大后内容宽度为 width * scale，可横向平移的范围是 [0, width * (scale - 1)]。 */
    private float clampOffsetX(float offsetX) {
        return clamp(offsetX, 0f, container.getWidth() * (scaleFactor - 1f));
    }

    private static float clamp(float value, float min, float max) {
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
