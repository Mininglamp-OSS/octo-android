package com.chat.base.base;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chat.base.R;
import com.chat.base.act.WKWebViewActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.RadialProgressView;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.StringUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.utils.systembar.WKStatusBarUtils;
import com.chat.base.views.CommonAnim;
import com.chat.base.views.swipeback.SwipeBackActivity;
import com.chat.base.views.swipeback.SwipeBackLayout;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.impl.LoadingPopupView;


/**
 * 2019-11-08 11:32
 * 基础类
 */
public abstract class WKBaseActivity<WKVBinding extends ViewBinding> extends SwipeBackActivity {
    private RadialProgressView titleRightLoadingIv;
    private View titleRightLayout;
    private Button rightBtn;
    protected LoadingPopupView loadingPopup;
    protected View view;
    protected WKVBinding wkVBinding;
    public SwipeBackLayout mSwipeBackLayout;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //禁止横屏
        // setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //这个特性是安卓5.0以后才支持的所以需要对系统版本号做判断
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
//            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//            );
//            getWindow().setStatusBarColor(Color.TRANSPARENT);
//        }
        boolean na = WKStatusBarUtils.isNavigationBarExist(this);
//        if (na) {
//            getWindow().getDecorView().findViewById(android.R.id.content).setPadding(0, 0, 0, WKStatusBarUtils.getNavigationBarHeight(this));
//        }
        WKMultiLanguageUtil.getInstance().setConfiguration();
//        adaptTheme(false);
        wkVBinding = getViewBinding();
        setContentView(wkVBinding.getRoot());
        initSwipeBackFinish();
        initPresenter();
        loadingPopup = new XPopup.Builder(this)
                .asLoading(getString(R.string.loading));
        toggleStatusBarMode();
//        setStatusBarColor();
        initData(savedInstanceState);
        initView();
        initListener();
        initTitleBar();
        initData();
        ActManagerUtils.getInstance().addActivity(this);
    }

    /**
     * 初始化滑动返回。在 super.onCreate(savedInstanceState) 之前调用该方法
     */
    private void initSwipeBackFinish() {
        mSwipeBackLayout = getSwipeBackLayout();
        mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);
        mSwipeBackLayout.setEnableGesture(supportSlideBack());
    }

    protected void resetTheme(boolean isDark) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        Object addSecurityModule = EndpointManager.getInstance().invoke("add_security_module", null);
        if (addSecurityModule instanceof Boolean) {
            boolean disable_screenshot;
            String uid = WKConfig.getInstance().getUid();
            if (!TextUtils.isEmpty(uid)) {
                disable_screenshot = WKSharedPreferencesUtil.getInstance().getBoolean(uid + "_disable_screenshot");
            } else {
                disable_screenshot = WKSharedPreferencesUtil.getInstance().getBoolean("disable_screenshot");
            }
            if (disable_screenshot)
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    protected void initData(Bundle savedInstanceState) {
    }

    /**
     * 切换状态栏模式
     */
    protected void toggleStatusBarMode() {
        Window window = getWindow();
        if (window == null) return;
        WKStatusBarUtils.transparentStatusBar(window);
        if (!Theme.getDarkModeStatus(this))
            WKStatusBarUtils.setDarkMode(window);
        else WKStatusBarUtils.setLightMode(window);
    }

//    protected void setStatusBarColor(int color) {
//        Window window = getWindow();
//        if (window == null || color == -1) return;
//        WKStatusBarUtils.transparentStatusBar(window);
//        WKStatusBarUtils.setStatusBarColor(window, ContextCompat.getColor(this, color), 0);
//        if (!Theme.getDarkModeStatus(this))
//            WKStatusBarUtils.setDarkMode(window);
//        else WKStatusBarUtils.setLightMode(window);
//    }

    //是否能侧滑返回
    protected boolean supportSlideBack() {
        return true;
    }

    protected abstract WKVBinding getViewBinding();

    //设置显示的标题
    protected void setTitle(TextView titleTv) {
    }


    protected void setSubtitle(TextView subtitleTv) {
        subtitleTv.setVisibility(View.GONE);
    }

    //初始化present对象
    protected void initPresenter() {
    }


    //初始化view
    protected void initView() {
    }

    //初始化事件
    protected void initListener() {
    }

    //设置返回键图标
    protected int getBackResourceID(ImageView backIv) {
        return -1;
    }

    //设置标题栏颜色
    protected int getTitleBg(View titleView) {
        return -1;
    }

    //标题右上角文字
    protected String getRightTvText(TextView textView) {
        return "";
    }

    protected String getRightBtnText(Button titleRightBtn) {
        return "";
    }

    protected boolean hideStatusBar() {
        return false;
    }

    //是否显示标题左上角返回布局
    protected boolean isHiddenBackLayout() {
        return false;
    }

    //标题右上角图标id。默认-1是不显示
    protected int getRightIvResourceId(ImageView imageView) {
        return -1;
    }

    //标题左边第一个图片id
    protected int getRightIvLeftResourceId(ImageView imageView) {
        return -1;
    }

    //是否显示标题栏底部view
    protected boolean isShowTitleBottomView() {
        return false;
    }

    //标题左上角返回事件(1:表示标题栏返回触发2：物理按键返回)
    protected void backListener(int type) {
        finish();
    }

    protected void rightButtonClick() {

    }

    //标题右上角点击事件
    protected void rightLayoutClick() {
    }

    //标题右边第一个图标事件
    protected void rightLeftLayoutClick() {

    }

    //显示一个弹框
    protected void showDialog(String content, WKDialogUtils.IClickListener iClickListener) {
        WKDialogUtils.getInstance().showDialog(this, "", content, true, "", "", 0, 0, iClickListener);
    }

    protected void showSingleBtnDialog(String content) {
        WKDialogUtils.getInstance().showSingleBtnDialog(this, "", content, "", null);
    }

    protected void showToast(String content) {
        WKToastUtils.getInstance().showToastFail(content);
    }

    protected void showToast(int contentId) {
        showToast(getString(contentId));
    }

    //初始化数据
    protected void initData() {
    }


    protected void showWebView(String url) {
        Intent intent = new Intent(this, WKWebViewActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }


    protected boolean checkEditInputIsEmpty(@NonNull EditText editText, int tipStrId) {
        boolean isTips = StringUtils.isEditTextsEmpty(editText);
        if (isTips) showToast(getString(tipStrId));
        return isTips;
    }

    protected void hideTitleRightView() {
        if (titleRightLayout != null) {
            CommonAnim.getInstance().showOrHide(titleRightLayout, false, true, true);
        }
    }

    protected void showTitleRightView() {
        if (titleRightLayout != null) {
            CommonAnim.getInstance().showOrHide(titleRightLayout, true, true, true);
        }
    }

    protected void setRightViewEnabled(boolean isEnabled) {
        if (titleRightLayout != null) {
            titleRightLayout.setEnabled(isEnabled);
        }
    }

    //显示标题栏loading
    protected void showTitleRightLoading() {
        if (titleRightLoadingIv != null)
            titleRightLoadingIv.setVisibility(View.VISIBLE);
        if (titleRightLayout != null) titleRightLayout.setVisibility(View.GONE);
    }

    //隐藏标题栏loading
    protected void hideTitleRightLoading() {
        if (titleRightLoadingIv != null)
            titleRightLoadingIv.setVisibility(View.GONE);
        if (titleRightLayout != null) {
            titleRightLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void showOrHideRightBtn(boolean isShow) {
        if (rightBtn != null) {
            rightBtn.setVisibility(isShow ? View.VISIBLE : View.GONE);
        }
    }


    /**
     * 初始化默认适配器（垂直列表）
     *
     * @param recyclerView 列表
     * @param adapter      适配器
     */
    protected void initAdapter(RecyclerView recyclerView, BaseQuickAdapter<?, ?> adapter) {
        if (recyclerView == null || adapter == null) return;
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter.setAnimationFirstOnly(true);
        recyclerView.setAdapter(adapter);
    }

    //初始化标题栏
    private void initTitleBar() {
        View titleBar = findViewById(R.id.titleBarLayout);

        if (titleBar == null) return;
        View statusBarView = findViewById(R.id.statusBarView);
        if (getTitleBg(titleBar) != -1) {
            int color = getTitleBg(titleBar);
            titleBar.setBackgroundColor(ContextCompat.getColor(this, color));
            statusBarView.setBackgroundColor(ContextCompat.getColor(this, color));
        }
        boolean hideStatusBar = hideStatusBar();
        statusBarView.setVisibility(hideStatusBar ? View.GONE : View.VISIBLE);
        ImageView backIv = findViewById(R.id.backIv);
        backIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.titleBarIcon), PorterDuff.Mode.MULTIPLY));

        Theme.setPressedBackground(backIv);
        if (getBackResourceID(backIv) != -1) {
            backIv.setImageResource(getBackResourceID(backIv));
        }
        titleRightLoadingIv = findViewById(R.id.titleRightLoadingIv);
        titleRightLoadingIv.setSize(35);
        backIv.setOnClickListener(v -> backListener(1));
        if (isHiddenBackLayout())
            backIv.setVisibility(View.GONE);
        View titleBottomLinView = findViewById(R.id.titleBottomLinView);
        if (isShowTitleBottomView())
            titleBottomLinView.setVisibility(View.VISIBLE);
        else titleBottomLinView.setVisibility(View.GONE);
        //设置标题
        TextView titleCenterTv = findViewById(R.id.titleCenterTv);
        setTitle(titleCenterTv);
        //子标题
        TextView subtitleTv = findViewById(R.id.subtitleTv);
        setSubtitle(subtitleTv);
        ImageView titleRightIvLeft = findViewById(R.id.titleRightIvLeft);
        titleRightIvLeft.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY));
        if (getRightIvLeftResourceId(titleRightIvLeft) != -1) {
            titleRightIvLeft.setImageResource(getRightIvLeftResourceId(titleRightIvLeft));
            titleRightIvLeft.setVisibility(View.VISIBLE);
        }

        titleRightLayout = findViewById(R.id.titleRightLayout);
        ImageView rightIv = findViewById(R.id.titleRightIv);
        rightIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY));

        if (getRightIvResourceId(rightIv) != -1) {
            rightIv.setImageResource(getRightIvResourceId(rightIv));
            rightIv.setVisibility(View.VISIBLE);
            titleRightLayout.setVisibility(View.VISIBLE);
        }

        TextView rightTv = findViewById(R.id.titleRightTv);
        rightTv.setTextColor(Theme.colorAccount);
        if (!TextUtils.isEmpty(getRightTvText(rightTv))) {
            rightTv.setText(getRightTvText(rightTv));
            titleRightLayout.setVisibility(View.VISIBLE);
            rightTv.setVisibility(View.VISIBLE);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            rightIv.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));
            rightTv.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));
            titleRightIvLeft.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));
        }
        rightBtn = findViewById(R.id.titleRightBtn);
        if (!TextUtils.isEmpty(getRightBtnText(rightBtn))) {
            rightBtn.setText(getRightBtnText(rightBtn));
            rightBtn.setVisibility(View.VISIBLE);
        }
        SingleClickUtil.onSingleClick(rightBtn, view1 -> rightButtonClick());
        SingleClickUtil.onSingleClick(rightIv, view1 -> rightLayoutClick());
        SingleClickUtil.onSingleClick(rightTv, view1 -> rightLayoutClick());
        SingleClickUtil.onSingleClick(titleRightIvLeft, view1 -> rightLeftLayoutClick());
//        rightBtn.setOnClickListener(v -> rightButtonClick());
//        rightIv.setOnClickListener(v -> rightLayoutClick());
//        rightTv.setOnClickListener(v -> rightLayoutClick());
//        titleRightIvLeft.setOnClickListener(v -> rightLeftLayoutClick());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActManagerUtils.getInstance().removeActivity(this);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(WKMultiLanguageUtil.getInstance().attachBaseContext(newBase));
    }
}
