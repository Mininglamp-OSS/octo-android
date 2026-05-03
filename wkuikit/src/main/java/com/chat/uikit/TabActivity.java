package com.chat.uikit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.adapter.WKFragmentStateAdapter;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.MailListDot;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.CounterView;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.base.utils.rxpermissions.RxPermissions;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActTabMainBinding;
import com.chat.uikit.fragment.ChatFragment;
import com.chat.uikit.fragment.ContactsFragment;
import com.chat.uikit.fragment.MyFragment;
import com.chat.uikit.user.service.UserModel;

import org.telegram.ui.Components.RLottieImageView;

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
    RLottieImageView chatIV, contactsIV, meIV;
    private TextView chatTV, contactsTV, meTV;
    private long lastClickChatTabTime = 0L;
    private final boolean isShowTabText = true;
    // YUJ-287: 记录当前选中 tab，避免 playAnimation 重复 setImageResource / tint。
    // 初始 -1 保证首帧 playAnimation(0) 必定执行一次着色。
    private int currentTabIndex = -1;

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
        chatIV = new RLottieImageView(this);
        contactsIV = new RLottieImageView(this);
        meIV = new RLottieImageView(this);
        // YUJ-287: drawable 只在 ViewHolder 初始化时 setImageResource 一次；
        // 后续切 tab 只通过 tintTab 改 ColorFilter，避免 RLottieImageView
        // 每次 setImageResource 都重新解析 drawable + invalidate。
        chatIV.setImageResource(R.drawable.ic_tab_message);
        contactsIV.setImageResource(R.drawable.ic_tab_contacts);
        meIV.setImageResource(R.drawable.ic_tab_me);
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
        fragments.add(new ContactsFragment());
        fragments.add(new MyFragment());

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
        playAnimation(0);

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
            // 通知权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                String desc = String.format(getString(R.string.notification_permissions_desc), getString(R.string.app_name));
                RxPermissions rxPermissions = new RxPermissions(this);
                rxPermissions.request(Manifest.permission.POST_NOTIFICATIONS).subscribe(aBoolean -> {
                    if (!aBoolean) {
                        WKDialogUtils.getInstance().showDialog(this, getString(com.chat.base.R.string.authorization_request), desc, true, getString(R.string.cancel), getString(R.string.to_set), 0, Theme.colorAccount, index -> {
                            if (index == 1) {
                                EndpointManager.getInstance().invoke("show_open_notification_dialog", this);
                            }
                        });
                    }
                });
            } else {
                boolean isEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
                if (!isEnabled) {
                    EndpointManager.getInstance().invoke("show_open_notification_dialog", this);
                }
            }
        });
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
            // YUJ-287: 只在真正需要切页时调 setCurrentItem；
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
        boolean showDot = false;
        int totalCount = 0;
        int newFriendCount = WKSharedPreferencesUtil.getInstance().getInt(WKConfig.getInstance().getUid() + "_new_friend_count");
        totalCount = totalCount + newFriendCount;
        List<MailListDot> list = EndpointManager.getInstance().invokes(EndpointCategory.wkGetMailListRedDot, null);
        if (WKReader.isNotEmpty(list)) {
            for (MailListDot MailListDot : list) {
                if (MailListDot != null) {
                    totalCount += MailListDot.numCount;
                    if (!showDot) showDot = MailListDot.showDot;
                }
            }
        }
        setContactCount(totalCount, showDot);
    }

    @Override
    public Resources getResources() {
        float fontScale = WKConstants.getFontScale();
        Resources res = super.getResources();
        Configuration config = res.getConfiguration();
        config.fontScale = fontScale; //1 设置正常字体大小的倍数
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
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

    private void tintTab(RLottieImageView iv, boolean selected) {
        int color = ContextCompat.getColor(this, selected ? R.color.tab_text_selected : R.color.tab_text_normal);
        iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private void playAnimation(int index) {
        // YUJ-287: i_chat 顶部双击滚动逻辑依赖 lastClickChatTabTime 在首次进入聊天 tab 时置 0；
        // 即便 tab 未变也需要走这一行，因此放在 early-return 之前。
        if (index == 0) {
            lastClickChatTabTime = 0;
        }

        // YUJ-287: tab 未变时跳过重复 tint / setImageResource / setTextColor。
        // ViewPager2 onPageSelected 与 BottomNavigationView OnItemSelectedListener
        // 有时会对同一次切换双重回调，这里保证每次真正的切换只着色一次。
        if (currentTabIndex == index) {
            return;
        }
        currentTabIndex = index;

        // drawable 已在 initView 阶段 setImageResource 一次，这里只更新 ColorFilter。
        tintTab(chatIV, index == 0);
        tintTab(contactsIV, index == 1);
        tintTab(meIV, index == 2);

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
