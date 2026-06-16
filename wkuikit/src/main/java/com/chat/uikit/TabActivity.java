/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import android.os.Bundle;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.adapter.WKFragmentStateAdapter;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.CounterView;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.base.utils.rxpermissions.RxPermissions;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActTabMainBinding;
import com.chat.uikit.fragment.ChatFragment;
import com.chat.uikit.fragment.MyFragment;
import com.chat.uikit.summary.context.ContextFragment;
import com.chat.uikit.user.service.UserModel;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.List;


/**
 * 2019-11-12 13:57
 * tab导航栏
 */
public class TabActivity extends WKBaseActivity<ActTabMainBinding> {
    CounterView msgCounterView;
    CounterView contactsCounterView;
    //    CounterView workplaceCounterView;
    View contactsSpotView;
    AppCompatImageView chatIV, contactsIV, meIV;
    private TextView chatTV, contactsTV, meTV;
    private long lastClickChatTabTime = 0L;
    private final boolean isShowTabText = true;
    // : 记录当前选中 tab，避免 playAnimation 重复 setImageResource / tint。
    // 初始 -1 保证首帧 playAnimation(0) 必定执行一次着色。
    private int currentTabIndex = -1;
    private static final String KEY_TAB_INDEX = "current_tab_index";
    private int pendingRestoreTab = -1;

    @Override
    protected void initData(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            pendingRestoreTab = savedInstanceState.getInt(KEY_TAB_INDEX, -1);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (wkVBinding != null && wkVBinding.vp != null) {
            outState.putInt(KEY_TAB_INDEX, wkVBinding.vp.getCurrentItem());
        }
    }

    @Override
    protected ActTabMainBinding getViewBinding() {
        return ActTabMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        ActManagerUtils.getInstance().clearAllActivity();
    }

    @Override
    public boolean supportSlideBack() {
        return false;
    }

    @SuppressLint("CheckResult")
    @Override
    protected void initView() {
        // ===== 关键路径：先设置 ViewPager + Tab，让第一帧尽快渲染 =====
        chatIV = new AppCompatImageView(this);
        contactsIV = new AppCompatImageView(this);
        meIV = new AppCompatImageView(this);
        chatIV.setImageResource(R.drawable.ic_tab_message_normal);
        contactsIV.setImageResource(R.drawable.ic_tab_context_normal);
        meIV.setImageResource(R.drawable.ic_tab_me_normal);
        chatTV = new TextView(this);
        contactsTV = new TextView(this);
        meTV = new TextView(this);
        Typeface face = Typeface.createFromAsset(getResources().getAssets(),
                "fonts/mw_bold.ttf");
        chatTV.setTypeface(face);
        contactsTV.setTypeface(face);
        meTV.setTypeface(face);
        chatTV.setText(R.string.tab_text_chat);
        chatTV.setTextColor(ContextCompat.getColor(this, R.color.tab_text_normal));
        chatTV.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        contactsTV.setText(R.string.tab_text_contacts);
        contactsTV.setTextColor(ContextCompat.getColor(this, R.color.tab_text_normal));
        contactsTV.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        meTV.setText(R.string.tab_text_me);
        meTV.setTextColor(ContextCompat.getColor(this, R.color.tab_text_normal));
        meTV.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        List<Fragment> fragments = new ArrayList<>(3);
        fragments.add(new ChatFragment());
        // tab[1] 由"联系人"换成"上下文" — 联系人入口已并入"我的"页, 上下文 tab 承载
        // 智能总结及未来基于 IM 上下文的 AI 能力 (1:1 对齐 iOS OctoContextEntryVC)。
        fragments.add(new ContextFragment());
        fragments.add(new MyFragment());

        wkVBinding.vp.setSaveEnabled(false);
        wkVBinding.vp.setAdapter(new WKFragmentStateAdapter(this, fragments));
        wkVBinding.vp.setUserInputEnabled(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_chat).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_my).setVisible(false);
//        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_workplace).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_chat).setVisible(false);
        FrameLayout view = wkVBinding.bottomNavigation.findViewById(R.id.i_chat);
        msgCounterView = new CounterView(this);
        msgCounterView.setColors(R.color.white, R.color.reminderColor);
        if (isShowTabText) {
            view.addView(chatIV, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 7, 0, 0));
            view.addView(msgCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 5, 0, 15));
            view.addView(chatTV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 6));
        } else {
            view.addView(chatIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            view.addView(msgCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));
        }
        FrameLayout contactsView = wkVBinding.bottomNavigation.findViewById(R.id.i_contacts);
        contactsCounterView = new CounterView(this);
        contactsCounterView.setColors(R.color.white, R.color.reminderColor);
        if (isShowTabText) {
            contactsView.addView(contactsIV, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 7, 0, 0));
            contactsView.addView(contactsCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 5, 0, 15));
            contactsView.addView(contactsTV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 6));
        } else {
            contactsView.addView(contactsIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            contactsView.addView(contactsCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));
        }
        contactsSpotView = new View(this);
        contactsSpotView.setBackgroundResource(R.drawable.msg_bg);
        contactsView.addView(contactsSpotView, LayoutHelper.createFrame(10, 10, Gravity.CENTER_HORIZONTAL, 10, 10, 0, 0));


//        FrameLayout workplaceView = wkVBinding.bottomNavigation.findViewById(R.id.i_workplace);
//        workplaceCounterView = new CounterView(this);
//        workplaceCounterView.setColors(R.color.white, R.color.reminderColor);
//        workplaceView.addView(workplaceIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
//        workplaceView.addView(workplaceCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));


        FrameLayout meView = wkVBinding.bottomNavigation.findViewById(R.id.i_my);
        if (isShowTabText) {
            meView.addView(meIV, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 7, 0, 0));
            meView.addView(meTV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 6));
        } else {
            meView.addView(meIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
        contactsSpotView.setVisibility(View.GONE);
        contactsCounterView.setVisibility(View.GONE);
//        workplaceCounterView.setVisibility(View.GONE);
        msgCounterView.setVisibility(View.GONE);
        int restoreTab = pendingRestoreTab > 0 ? pendingRestoreTab : 0;
        pendingRestoreTab = -1;
        if (restoreTab != 0) {
            wkVBinding.vp.setCurrentItem(restoreTab, false);
            if (restoreTab == 1) {
                wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_contacts);
            } else if (restoreTab == 2) {
                wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_my);
            }
        }
        playAnimation(restoreTab);

        // 非关键工作延迟到第一帧渲染后执行，不阻塞启动
        wkVBinding.getRoot().post(() -> {
            UserModel.getInstance().device();
            WKCommonModel.getInstance().getAppNewVersion(false, version -> {
                String v = WKDeviceUtils.getInstance().getVersionName(TabActivity.this);
                if (version != null && !TextUtils.isEmpty(version.url) && WKDeviceUtils.getInstance().isNewerVersion(version.version, v)) {
                    WKDialogUtils.getInstance().showNewVersionDialog(TabActivity.this, version);
                }
            });
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
            WKCommonModel.getInstance().getAppConfig(null);
            // 通知权限引导：带冷却的「软请求」。
            // 背景：部分华为/HarmonyOS 设备 areNotificationsEnabled() 在用户已开通知时仍返回 false，
            // 老逻辑会每次冷启动弹框。改成 7 天冷却 + 已授权时清零，避免循环骚扰，自愈用户后续关闭场景。
            checkNotificationPermission();
        });
    }

    private static final String KEY_NOTIFY_PROMPT_LAST_TS = "notify_prompt_last_ts";
    private static final long NOTIFY_PROMPT_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000;

    private void checkNotificationPermission() {
        boolean isEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (isEnabled) {
            // 已授权：清零冷却时间戳，便于用户后续若关闭通知能在下次冷启动重新提示一次。
            WKSharedPreferencesUtil.getInstance().putSP(KEY_NOTIFY_PROMPT_LAST_TS, "0");
            return;
        }
        long lastTs = parsePromptLastTs();
        long now = System.currentTimeMillis();
        if (lastTs > 0 && now - lastTs < NOTIFY_PROMPT_COOLDOWN_MS) {
            return;
        }
        WKSharedPreferencesUtil.getInstance().putSP(KEY_NOTIFY_PROMPT_LAST_TS, String.valueOf(now));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String desc = String.format(getString(R.string.notification_permissions_desc), getString(R.string.app_name));
            RxPermissions rxPermissions = new RxPermissions(this);
            rxPermissions.request(Manifest.permission.POST_NOTIFICATIONS).subscribe(aBoolean -> {
                if (aBoolean) {
                    WKSharedPreferencesUtil.getInstance().putSP(KEY_NOTIFY_PROMPT_LAST_TS, "0");
                    return;
                }
                WKDialogUtils.getInstance().showDialog(this, getString(com.chat.base.R.string.authorization_request), desc, true, getString(R.string.cancel), getString(R.string.to_set), 0, Theme.colorAccount, index -> {
                    if (index == 1) {
                        EndpointManager.getInstance().invoke("show_open_notification_dialog", this);
                    }
                });
            });
        } else {
            EndpointManager.getInstance().invoke("show_open_notification_dialog", this);
        }
    }

    private long parsePromptLastTs() {
        String raw = WKSharedPreferencesUtil.getInstance().getSP(KEY_NOTIFY_PROMPT_LAST_TS, "0");
        if (TextUtils.isEmpty(raw)) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.vp.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 0) {
                    playAnimation(0);
                    wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_chat);
                } else if (position == 1) {
                    playAnimation(1);
                    wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_contacts);
                } else {
                    playAnimation(2);
                    wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_my);
                }
            }
        });
        wkVBinding.bottomNavigation.setItemIconTintList(null);
        wkVBinding.bottomNavigation.setOnItemSelectedListener(item -> {
            // : 只在真正需要切页时调 setCurrentItem；
            // 切页后 ViewPager2 的 onPageSelected 会回调 playAnimation，
            // 这里不再重复调用，避免一次 tab 点击触发两次 playAnimation。
            if (item.getItemId() == R.id.i_chat) {
                long nowTime = WKTimeUtils.getInstance().getCurrentMills();
                if (wkVBinding.vp.getCurrentItem() == 0) {
                    if (nowTime - lastClickChatTabTime <= 300) {
                        EndpointManager.getInstance().invoke("scroll_to_unread_channel", null);
                    }
                    lastClickChatTabTime = nowTime;
                    return true;
                }
                wkVBinding.vp.setCurrentItem(0);
            } else if (item.getItemId() == R.id.i_contacts) {
                if (wkVBinding.vp.getCurrentItem() != 1) {
                    wkVBinding.vp.setCurrentItem(1);
                }
            } else {
                if (wkVBinding.vp.getCurrentItem() != 2) {
                    wkVBinding.vp.setCurrentItem(2);
                }
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("tab_activity", EndpointCategory.wkRefreshMailList, object -> {
            getAllRedDot();
            return null;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getAllRedDot();
        boolean sync_friend = WKSharedPreferencesUtil.getInstance().getBoolean("sync_friend");
        if (sync_friend) {
            FriendModel.getInstance().syncFriends((code, msg) -> {
                if (code != HttpResponseCode.success && !TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
                if (code == HttpResponseCode.success) {
                    WKSharedPreferencesUtil.getInstance().putBoolean("sync_friend", false);
                }
            });
        }
    }

    public void setMsgCount(int number) {
        WKUIKitApplication.getInstance().totalMsgCount = number;
        if (number > 0) {
            msgCounterView.setCount(number, true);
            msgCounterView.setVisibility(View.VISIBLE);
        } else {
            msgCounterView.setCount(0, true);
            msgCounterView.setVisibility(View.GONE);
        }
    }

    public void setContactCount(int number, boolean showDot) {
        if (number > 0 || showDot) {
            if (number > 0) {
                contactsCounterView.setCount(number, true);
                contactsCounterView.setVisibility(View.VISIBLE);
                contactsSpotView.setVisibility(View.GONE);
            } else {
                contactsCounterView.setVisibility(View.GONE);
                contactsSpotView.setVisibility(View.VISIBLE);
                contactsCounterView.setCount(0, true);
            }
        } else {
            contactsCounterView.setVisibility(View.GONE);
            contactsSpotView.setVisibility(View.GONE);
        }
    }

    private void getAllRedDot() {
        // tab[1] 已从"联系人"换为"上下文",好友邀请 / mailList 红点不再绑这个 tab。
        // counter / spot view 保留是为不动 tab framelayout 几何, 但永远 0。
        // 联系人红点的归位 (我的 tab 内显示) 不在本次 PR 范围。
        setContactCount(0, false);
    }

    /**
     * P-05: fontScale 一次性在 attachBaseContext 里通过 createConfigurationContext
     * 生效，避免每次 getResources() 都走一遍已 deprecated 的 updateConfiguration() 热路径。
     * getResources() 在 View inflate、theme 查询、getString()、ContextCompat.getColor() 等
     * 场景下被高频访问，原实现每次都会读 SP + 改 Configuration + updateConfiguration，
     * 在 Android U/14 还会触发 StrictMode 告警。
     *
     * 父类 WKBaseActivity.attachBaseContext 只做 locale 包装，这里在其基础上再叠一层
     * fontScale 的 Configuration，仍保留原有的语言切换行为；fontScale 变更走
     * WKSetFontSizeActivity → "main_show_home_view" 重启流程，重启后 attachBaseContext 再
     * 执行一次，不需要在运行时重复更新。
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        Context localeCtx = WKMultiLanguageUtil.getInstance().attachBaseContext(newBase);
        float fontScale = WKConstants.getFontScale();
        if (fontScale > 0f) {
            Configuration overrideConfig = new Configuration(localeCtx.getResources().getConfiguration());
            if (Math.abs(overrideConfig.fontScale - fontScale) > 0.0001f) {
                overrideConfig.fontScale = fontScale;
                localeCtx = localeCtx.createConfigurationContext(overrideConfig);
            }
        }
        super.attachBaseContext(localeCtx);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
            return true;
        } else
            return super.onKeyDown(keyCode, event);
    }

    private void showExitDialog() {
        WKDialogUtils.getInstance().showDialog(
                this,
                getString(R.string.exit_app_title),
                getString(R.string.exit_app_msg),
                true,
                getString(com.chat.base.R.string.cancel),
                getString(com.chat.base.R.string.sure),
                0,
                Theme.colorAccount,
                index -> {
                    if (index == 1) {
                        finishAffinity();
                    }
                }
        );
    }

    private void playAnimation(int index) {
        if (index == 0) {
            lastClickChatTabTime = 0;
        }

        if (currentTabIndex == index) {
            return;
        }
        currentTabIndex = index;

        chatIV.setImageResource(index == 0 ? R.drawable.ic_tab_message_selected : R.drawable.ic_tab_message_normal);
        contactsIV.setImageResource(index == 1 ? R.drawable.ic_tab_context_selected : R.drawable.ic_tab_context_normal);
        meIV.setImageResource(index == 2 ? R.drawable.ic_tab_me_selected : R.drawable.ic_tab_me_normal);

        boolean isDark = Theme.isDark();
        if (isDark) {
            chatIV.setColorFilter(index == 0 ? 0xFFFFFFFF : 0x8CFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
            contactsIV.setColorFilter(index == 1 ? 0xFFFFFFFF : 0x8CFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
            meIV.setColorFilter(index == 2 ? 0xFFFFFFFF : 0x8CFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            chatIV.clearColorFilter();
            contactsIV.clearColorFilter();
            meIV.clearColorFilter();
        }

        if (isShowTabText) {
            int selectedColor = ContextCompat.getColor(this, R.color.tab_text_selected);
            int normalColor = ContextCompat.getColor(this, R.color.tab_text_normal);
            chatTV.setTextColor(index == 0 ? selectedColor : normalColor);
            contactsTV.setTextColor(index == 1 ? selectedColor : normalColor);
            meTV.setTextColor(index == 2 ? selectedColor : normalColor);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WKMultiLanguageUtil.getInstance().setConfiguration();
        Theme.applyTheme();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EndpointManager.getInstance().remove("tab_activity");
    }
}
