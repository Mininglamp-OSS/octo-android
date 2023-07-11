package com.chat.scan;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.zxing.Result;
import com.king.zxing.CameraScan;
import com.king.zxing.DefaultCameraScan;
import com.king.zxing.config.CameraConfig;
import com.king.zxing.util.CodeUtils;
import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.systembar.WKStatusBarUtils;
import com.chat.scan.databinding.ActScanLayoutBinding;

import java.util.List;


/**
 * 2020-04-19 17:21
 * 扫描页面
 */
public class WKScanActivity extends WKBaseActivity<ActScanLayoutBinding> implements CameraScan.OnScanResultCallback {
    private CameraScan mCameraScan;

    @Override
    protected ActScanLayoutBinding getViewBinding() {
        return ActScanLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.wk_scan_module_scan);
    }

    @Override
    protected boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void initPresenter() {

        String desc = String.format(getString(R.string.camera_permissions_desc), getString(R.string.app_name));
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {

            }

            @Override
            public void clickResult(boolean isCancel) {
                finish();
            }
        }, this, desc, Manifest.permission.CAMERA);

    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.album);
    }

    @Override
    protected void initView() {
        mCameraScan = new DefaultCameraScan(this, wkVBinding.previewView);
        mCameraScan.setOnScanResultCallback(this);
        mCameraScan.startCamera();
        mCameraScan.setPlayBeep(true);
        mCameraScan.setVibrate(true);
        mCameraScan.setNeedAutoZoom(true);
        CameraConfig config = new CameraConfig();
        mCameraScan.setCameraConfig(
                config
        );
    }


    @Override
    protected void initListener() {
        wkVBinding.rightIV.setOnClickListener(view1 -> chooseIMG());
        WKStatusBarUtils.setDarkMode(getWindow());
        WKStatusBarUtils.setStatusBarColor(getWindow(), ContextCompat.getColor(this, R.color.black), 1);
        wkVBinding.backIv.setOnClickListener(view1 -> finish());
        wkVBinding.ivFlash.setOnClickListener(v -> toggleTorchState());
    }

    protected void toggleTorchState() {
        if (mCameraScan != null) {
            boolean isTorch = mCameraScan.isTorchEnabled();
            mCameraScan.enableTorch(!isTorch);
            wkVBinding.ivFlash.setSelected(!isTorch);
        }
    }

    private void asyncThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    @Override
    public boolean onScanResultCallback(Result result) {
        if (result != null && !TextUtils.isEmpty(result.getText())) {
            handleResult(result.getText());
        }
        return false;
    }

    private void handleResult(String result) {
        ScanUtils.getInstance().handleScanResult(WKScanActivity.this, result, new ScanUtils.IHandleScanResult() {
            @Override
            public void showOtherContent(String content) {
                Intent intent = new Intent(WKScanActivity.this, WKScanOtherResultActivity.class);
                intent.putExtra("result", content);
                startActivity(intent);
                finish();
            }

            @Override
            public void showWebView(String url) {
                Intent intent = new Intent(WKScanActivity.this, WKWebViewActivity.class);
                intent.putExtra("url", url);
                startActivity(intent);
                finish();
            }

            @Override
            public void dismissView() {
                finish();
            }
        });
    }

    @Override
    public void onScanResultFailure() {

    }

    private void chooseIMG() {
        GlideUtils.getInstance().chooseIMG(this, 1, false, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (paths.size() > 0) {
                    Bitmap bitmap = BitmapFactory.decodeFile(paths.get(0).path);
                    asyncThread(() -> {
                        final String result = CodeUtils.parseCode(bitmap);
                        runOnUiThread(() -> {
                            if (TextUtils.isEmpty(result)) {
                                showToast(R.string.wk_scan_module_scan_no_result);
                                return;
                            }
                            handleResult(result);
                        });
                    });
                }
            }

            @Override
            public void onCancel() {

            }
        });
    }
}
