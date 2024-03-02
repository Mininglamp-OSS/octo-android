package com.chat.uikit.setting;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatBgItemMenu;
import com.chat.base.ui.Theme;
import com.chat.base.utils.DataCleanManager;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.databinding.ActSettingLayoutBinding;
import com.chat.uikit.message.BackupRestoreMessageActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2020-03-22 21:11
 * 设置页面
 */
public class SettingActivity extends WKBaseActivity<ActSettingLayoutBinding> {
    private String str;

    @Override
    protected ActSettingLayoutBinding getViewBinding() {
        return ActSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.setting);
    }

    @Override
    protected void initPresenter() {
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
    }

    @Override
    protected void initView() {
        getCacheSize();
        EndpointManager.getInstance().invoke("set_chat_bg_view", new ChatBgItemMenu(this, wkVBinding.chatBgLayout, "", WKChannelType.PERSONAL));
        View keepAliveView = (View) EndpointManager.getInstance().invoke("show_keep_alive_item", this);
        if (keepAliveView != null) {
            wkVBinding.keepAliveLayout.addView(keepAliveView);
        }
    }

    @Override
    protected void initListener() {
        String wk_theme_pref = Theme.getTheme();
        if (wk_theme_pref.equals(Theme.DARK_MODE)) {
            wkVBinding.darkStatusTv.setText(R.string.enabled);
        } else {
            wkVBinding.darkStatusTv.setText(R.string.disabled);
        }
        wkVBinding.loginOutTv.setOnClickListener(v -> WKDialogUtils.getInstance().showDialog(this, getString(R.string.login_out), getString(R.string.login_out_dialog), true, "", getString(R.string.login_out), 0, 0, index -> {
            if (index == 1) {
                WKUIKitApplication.getInstance().exitLogin(0);
            }
        }));
        SingleClickUtil.onSingleClick(wkVBinding.languageLayout, view1 -> startActivity(new Intent(this, WKLanguageActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.darkLayout, view1 -> startActivity(new Intent(this, WKThemeSettingActivity.class)));
        wkVBinding.clearImgCacheLayout.setOnClickListener(v -> showDialog(getString(R.string.clear_img_cache_tips), index -> {
            if (index == 1) {
                DataCleanManager.clearAllCache(SettingActivity.this);
                str = "0.00M";
                wkVBinding.imageCacheTv.setText(str);
            }
        }));
        wkVBinding.clearChatMsgLayout.setOnClickListener(v -> showDialog(getString(R.string.clear_all_msg_tips), index -> {
            if (index == 1) {
                WKIM.getInstance().getConversationManager().clearAll();
                WKIM.getInstance().getMsgManager().clearAll();
            }
        }));
        SingleClickUtil.onSingleClick(wkVBinding.moduleLayout, view1 -> startActivity(new Intent(this, AppModulesActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.aboutLayout, view1 -> startActivity(new Intent(this, WKAboutActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.fontSizeLayout, view1 -> startActivity(new Intent(this, WKSetFontSizeActivity.class)));
        WKCommonModel.getInstance().getAppNewVersion(false, version -> {
            if (version != null && !TextUtils.isEmpty(version.download_url)) {
                wkVBinding.newVersionIv.setVisibility(View.VISIBLE);
            } else {
                wkVBinding.newVersionIv.setVisibility(View.GONE);
            }
        });

        SingleClickUtil.onSingleClick(wkVBinding.msgBackupLayout, view1 -> {
            Intent intent = new Intent(this, BackupRestoreMessageActivity.class);
            intent.putExtra("handle_type", 1);
            startActivity(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.msgRecoveryLayout, view1 -> {
            Intent intent = new Intent(this, BackupRestoreMessageActivity.class);
            intent.putExtra("handle_type", 2);
            startActivity(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.thirdShareLayout, view1 -> {
            Intent intent = new Intent(this, WKWebViewActivity.class);
            intent.putExtra("url", WKApiConfig.baseWebUrl + "sdkinfo.html");
            startActivity(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.errorLogLayout, view1 -> startActivity(new Intent(this, ErrorLogsActivity.class)));

    }


    //获取缓存大小
    private void getCacheSize() {
        new Thread(() -> {
            try {
                str = DataCleanManager.getTotalCacheSize(SettingActivity.this);
                if (str.equalsIgnoreCase("0.0Byte")) {
                    str = "0.00M";
                }
                mHandler.sendEmptyMessage(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }


    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                wkVBinding.imageCacheTv.setText(str);
            }
        }
    };
}
