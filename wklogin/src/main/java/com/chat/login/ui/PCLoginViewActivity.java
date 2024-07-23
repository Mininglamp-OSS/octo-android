package com.chat.login.ui;

import static android.view.View.VISIBLE;

import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKToastUtils;
import com.chat.login.R;
import com.chat.login.databinding.PcLoginViewLayoutBinding;
import com.chat.login.service.LoginModel;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 4/14/21 4:57 PM
 * pc登录
 */
public class PCLoginViewActivity extends WKBaseActivity<PcLoginViewLayoutBinding> {
    @Override
    protected PcLoginViewLayoutBinding getViewBinding() {
        overridePendingTransition(R.anim.bottom_in, R.anim.bottom_silent);
        return PcLoginViewLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        Theme.setColorFilter(this, wkVBinding.closeIv, R.color.popupTextColor);
        wkVBinding.closeIv.setOnClickListener(v -> finish());
        wkVBinding.exitBtn.setTextColor(Theme.colorAccount);
        wkVBinding.exitBtn.setText(String.format(getString(R.string.exit_pc_login), getString(R.string.app_name)));
        wkVBinding.pcLoginTv.setText(String.format(getString(R.string.pc_login), getString(R.string.app_name)));
        wkVBinding.phoneMuteBtn.setOnClickListener(v -> {
            int muteForApp = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_mute_of_app");
            LoginModel.getInstance().updateUserSetting("mute_of_app", muteForApp == 1 ? 0 : 1, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    WKSharedPreferencesUtil.getInstance().putInt(WKConfig.getInstance().getUid() + "_mute_of_app", muteForApp == 1 ? 0 : 1);
                    updateMuteStatus(muteForApp == 1 ? 0 : 1);
                } else WKToastUtils.getInstance().showToastNormal(msg);
            });

        });

        int muteForApp = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_mute_of_app");
        updateMuteStatus(muteForApp);
        findViewById(R.id.exitBtn).setOnClickListener(v -> LoginModel.getInstance().quitPc((code, msg) -> {
            if (code == HttpResponseCode.success) {
                finish();
            } else WKToastUtils.getInstance().showToastNormal(msg);
        }));
        findViewById(R.id.fileLayout).setOnClickListener(v -> {
            finish();
            EndpointManager.getInstance().invoke(EndpointSID.chatView, new ChatViewMenu(PCLoginViewActivity.this, WKSystemAccount.system_file_helper, WKChannelType.PERSONAL, 0, false));
        });
        wkVBinding.lockLayout.setOnClickListener(v -> {
            //锁定
            wkVBinding.lockLayout.setBackground(Theme.getBackground(Theme.colorAccount,55));
            wkVBinding.lockIv.setImageResource(R.mipmap.icon_lock_white);
            wkVBinding.topLockIv.setVisibility(VISIBLE);
        });
    }

    @Override
    protected void initListener() {

    }


    private void updateMuteStatus(int muteForApp) {
        if (muteForApp == 1) {
            wkVBinding.noticeTv.setText(R.string.phone_notice_close);
            wkVBinding.phoneMuteBtn.setBackground(Theme.getBackground(Theme.colorAccount, 55, 55, 55));
            wkVBinding.pcLoginIV.setImageResource(R.mipmap.device_status_pc_online_silence);
            wkVBinding.muteIv.setImageResource(R.mipmap.icon_mute_white);
        } else {
            wkVBinding.noticeTv.setText(R.string.phone_notice_open);
            wkVBinding.phoneMuteBtn.setBackgroundResource(R.drawable.pc_login_btn_bg);
            wkVBinding.pcLoginIV.setImageResource(R.mipmap.device_status_pc_online_normal);
            wkVBinding.muteIv.setImageResource(R.mipmap.icon_mute);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.bottom_silent, R.anim.bottom_out);
    }
}
