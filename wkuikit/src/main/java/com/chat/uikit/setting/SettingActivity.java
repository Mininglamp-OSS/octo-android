package com.chat.uikit.setting;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatBgItemMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.AppExecutors;
import com.chat.base.utils.DataCleanManager;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.databinding.ActSettingLayoutBinding;
import com.chat.uikit.message.BackupRestoreMessageActivity;
import com.chat.uikit.user.service.UserModel;
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
        renderRealnameStatus();
        EndpointManager.getInstance().invoke("set_chat_bg_view", new ChatBgItemMenu(this, wkVBinding.chatBgLayout, "", WKChannelType.PERSONAL));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // YUJ-361 (#227)：从 Custom Tabs 回来后 VerifyLandingActivity 会刷新 profile，
        // 这里 onResume 把最新状态重新渲染到行上。
        renderRealnameStatus();
    }

    /**
     * YUJ-361 (#227)：实名认证行的状态渲染。
     * - 未认证：显示「去认证」，点击发起 verify-token + Custom Tabs。
     * - 已认证：显示「已认证 · YYYY-MM」，隐藏箭头，不可点。
     */
    private void renderRealnameStatus() {
        UserInfoEntity me = WKConfig.getInstance().getUserInfo();
        if (me.realname_verified) {
            wkVBinding.realnameStatusTv.setText(getString(
                    R.string.realname_verified_at_fmt, formatVerifiedAt(me.realname_verified_at)));
            wkVBinding.realnameArrowIv.setVisibility(View.INVISIBLE);
            wkVBinding.realnameLayout.setClickable(false);
            wkVBinding.realnameLayout.setOnClickListener(null);
        } else {
            wkVBinding.realnameStatusTv.setText(R.string.realname_goto_verify);
            wkVBinding.realnameArrowIv.setVisibility(View.VISIBLE);
            wkVBinding.realnameLayout.setClickable(true);
            SingleClickUtil.onSingleClick(wkVBinding.realnameLayout, v -> startVerifyFlow());
        }
    }

    private static String formatVerifiedAt(String iso) {
        // 后端回 ISO-8601（如 2026-05-05T12:34:56Z）。只截年-月即可；
        // 空值或格式不符时回落到空串，避免 NPE。
        if (TextUtils.isEmpty(iso)) return "";
        if (iso.length() >= 7) return iso.substring(0, 7);
        return iso;
    }

    private void startVerifyFlow() {
        UserModel.getInstance().createVerifyToken((code, msg, resp) -> {
            // Activity 生命周期守卫：回调返回时 Activity 可能已 destroyed。
            if (isFinishing() || isDestroyed()) return;
            if (code != HttpResponseCode.success || resp == null
                    || TextUtils.isEmpty(resp.verify_url)) {
                showToast(getString(R.string.realname_verify_token_fail));
                return;
            }
            // 安全：verify_url 来自服务端响应，防止后端被篡改后返回恶意 scheme
            // （intent:// / file:// / javascript: 等）在 launchUrl 内部被 startActivity() 触发劫持。
            Uri verifyUri = Uri.parse(resp.verify_url);
            String scheme = verifyUri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                showToast(getString(R.string.realname_verify_token_fail));
                return;
            }
            try {
                CustomTabsIntent intent = new CustomTabsIntent.Builder()
                        .setToolbarColor(ContextCompat.getColor(this, R.color.colorAccent))
                        .setShowTitle(true)
                        .build();
                intent.launchUrl(this, verifyUri);
            } catch (Exception e) {
                // 没装任何可以处理 http(s) intent 的包（极少见，CI 模拟器可能遇到）。
                showToast(getString(R.string.realname_verify_no_browser));
            }
        });
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
                // 与 Web/iOS 一致：直接执行本地退出，不调用 user/quit
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
        SingleClickUtil.onSingleClick(wkVBinding.destroyAccountLayout, view1 -> startActivity(new Intent(this, DestroyAccountActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.aboutLayout, view1 -> startActivity(new Intent(this, WKAboutActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.fontSizeLayout, view1 -> startActivity(new Intent(this, WKSetFontSizeActivity.class)));
        WKCommonModel.getInstance().getAppNewVersion(false, version -> {
            String v = WKDeviceUtils.getInstance().getVersionName(this);
            if (version != null && !TextUtils.isEmpty(version.url) && WKDeviceUtils.getInstance().isNewerVersion(version.version, v)) {
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
        // YUJ-283 P-11: AppExecutors.io() 替代 new Thread()（磁盘统计属 I/O）
        AppExecutors.io().execute(() -> {
            try {
                str = DataCleanManager.getTotalCacheSize(SettingActivity.this);
                if (str.equalsIgnoreCase("0.0Byte")) {
                    str = "0.00M";
                }
                AndroidUtilities.runOnUIThread(() -> wkVBinding.imageCacheTv.setText(str));
            } catch (Exception e) {
                WKLogUtils.e("获取图片缓存大小错误");
            }
        });

    }

}
