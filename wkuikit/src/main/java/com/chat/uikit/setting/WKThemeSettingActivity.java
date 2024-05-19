package com.chat.uikit.setting;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.ui.Theme;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActDarkSettingLayoutBinding;

/**
 * 2020-12-02 11:57
 * 深色模式
 */
public class WKThemeSettingActivity extends WKBaseActivity<ActDarkSettingLayoutBinding> {

    private int type = 0;

    @Override
    protected ActDarkSettingLayoutBinding getViewBinding() {
        return ActDarkSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.dark_night);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();
        showDialog(String.format(getString(R.string.dark_save_tips), getString(R.string.app_name)), index -> {
            if (index == 1) {
                saveType();
            }
        });
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        return getString(R.string.sure);
    }

    @Override
    protected void initView() {
        String sp = Theme.getTheme();
        if (sp.equals(Theme.DARK_MODE)) {
            wkVBinding.followSystemSwitch.setChecked(false);
            wkVBinding.nightIv.setVisibility(View.VISIBLE);
            wkVBinding.bottomView.setVisibility(View.VISIBLE);
            wkVBinding.normalIv.setVisibility(View.INVISIBLE);
        } else if (sp.equals(Theme.LIGHT_MODE)) {
            wkVBinding.followSystemSwitch.setChecked(false);
            wkVBinding.nightIv.setVisibility(View.INVISIBLE);
            wkVBinding.normalIv.setVisibility(View.VISIBLE);
            wkVBinding.bottomView.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.followSystemSwitch.setChecked(true);
            wkVBinding.nightIv.setVisibility(View.INVISIBLE);
            wkVBinding.normalIv.setVisibility(View.VISIBLE);
            wkVBinding.bottomView.setVisibility(View.GONE);
        }

    }

    @Override
    protected void initListener() {
        wkVBinding.followSystemSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                wkVBinding.bottomView.setVisibility(View.GONE);
                type = 0;
            } else {
                type = 1;
                wkVBinding.bottomView.setVisibility(View.VISIBLE);
            }
        });
        wkVBinding.darkLayout.setOnClickListener(v -> {
            type = 2;
            wkVBinding.nightIv.setVisibility(View.VISIBLE);
            wkVBinding.normalIv.setVisibility(View.INVISIBLE);
        });
        wkVBinding.normalLayout.setOnClickListener(v -> {
            type = 1;
            wkVBinding.nightIv.setVisibility(View.INVISIBLE);
            wkVBinding.normalIv.setVisibility(View.VISIBLE);
        });
    }

    private void saveType() {
        String s = Theme.DEFAULT_MODE;
        if (type == 0) {
            s = Theme.DEFAULT_MODE;
        } else if (type == 1) {
            s = Theme.LIGHT_MODE;
        } else if (type == 2){
            s = Theme.DARK_MODE;
        }
        Theme.setTheme(s);
        finish();
    }

    @Override
    protected void resetTheme(boolean isDark) {
        super.resetTheme(isDark);
        wkVBinding.contentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
        wkVBinding.topLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.layoutColor));
        wkVBinding.normalLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.layoutColor));
        wkVBinding.darkLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.layoutColor));
        wkVBinding.normalTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        wkVBinding.darkTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        wkVBinding.systemTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        wkVBinding.followSystemSwitch.invalidate();
    }
}
