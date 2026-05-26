package com.chat.base.act;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class WKPdfViewActivity extends AppCompatActivity {

    private LinearLayout container;
    private ScrollView scrollView;
    private TextView loadingTv;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scrollView = new ScrollView(this);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(container);
        setContentView(scrollView);

        loadingTv = new TextView(this);
        loadingTv.setText("加载中...");
        loadingTv.setGravity(Gravity.CENTER);
        loadingTv.setPadding(0, 200, 0, 0);
        loadingTv.setTextSize(15);
        loadingTv.setTextColor(0xFF999999);
        container.addView(loadingTv);

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 4.0f));
                container.setScaleX(scaleFactor);
                container.setScaleY(scaleFactor);
                container.setPivotX(container.getWidth() / 2f);
                container.setPivotY(detector.getFocusY() + scrollView.getScrollY());
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
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
                });
            } catch (Exception e) {
                String errMsg = e.getMessage();
                runOnUiThread(() -> {
                    loadingTv.setText("加载失败: " + errMsg);
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
