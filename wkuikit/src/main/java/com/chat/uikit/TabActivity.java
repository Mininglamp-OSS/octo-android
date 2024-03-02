package com.chat.uikit;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
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
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActTabMainBinding;
import com.chat.uikit.fragment.ChatFragment;
import com.chat.uikit.fragment.ContactsFragment;
import com.chat.uikit.fragment.MyFragment;
import com.tbruyelle.rxpermissions3.RxPermissions;

import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;


/**
 * 2019-11-12 13:57
 * tab导航栏
 */
public class TabActivity extends WKBaseActivity<ActTabMainBinding> {
    CounterView msgCounterView;
    CounterView contactsCounterView;
//    CounterView workplaceCounterView;
    View contactsSpotView;
    RLottieImageView chatIV, contactsIV, workplaceIV, meIV;
    private long lastClickChatTabTime = 0L;

    @Override
    protected ActTabMainBinding getViewBinding() {
        return ActTabMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {

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
//        wkVBinding.vp.setUserInputEnabled(false);

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

        chatIV = new RLottieImageView(this);
        contactsIV = new RLottieImageView(this);
//        workplaceIV = new RLottieImageView(this);
        meIV = new RLottieImageView(this);

        List<Fragment> fragments = new ArrayList<>(3);
        fragments.add(new ChatFragment());
        fragments.add(new ContactsFragment());
//        Fragment workplaceFra = (Fragment) EndpointManager.getInstance().invoke("get_workplace_fragment", null);
//        fragments.add(workplaceFra);
        fragments.add(new MyFragment());

        wkVBinding.vp.setAdapter(new WKFragmentStateAdapter(this, fragments));
        WKCommonModel.getInstance().getAppNewVersion(false, version -> {
            if (version != null && !TextUtils.isEmpty(version.download_url)) {
                WKDialogUtils.getInstance().showNewVersionDialog(TabActivity.this, version);
            }
        });
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        WKCommonModel.getInstance().getAppConfig();
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_chat).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_my).setVisible(false);
//        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_workplace).setVisible(false);
        wkVBinding.bottomNavigation.getOrCreateBadge(R.id.i_chat).setVisible(false);
        FrameLayout view = wkVBinding.bottomNavigation.findViewById(R.id.i_chat);
        msgCounterView = new CounterView(this);
        msgCounterView.setColors(R.color.white, R.color.reminderColor);
        view.addView(chatIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        view.addView(msgCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));

        FrameLayout contactsView = wkVBinding.bottomNavigation.findViewById(R.id.i_contacts);
        contactsCounterView = new CounterView(this);
        contactsCounterView.setColors(R.color.white, R.color.reminderColor);
        contactsView.addView(contactsIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        contactsView.addView(contactsCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));
        contactsSpotView = new View(this);
        contactsSpotView.setBackgroundResource(R.drawable.msg_bg);
        contactsView.addView(contactsSpotView, LayoutHelper.createFrame(10, 10, Gravity.CENTER_HORIZONTAL, 10, 10, 0, 0));


//        FrameLayout workplaceView = wkVBinding.bottomNavigation.findViewById(R.id.i_workplace);
//        workplaceCounterView = new CounterView(this);
//        workplaceCounterView.setColors(R.color.white, R.color.reminderColor);
//        workplaceView.addView(workplaceIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
//        workplaceView.addView(workplaceCounterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 5, 0, 15));


        FrameLayout meView = wkVBinding.bottomNavigation.findViewById(R.id.i_my);
        meView.addView(meIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        contactsSpotView.setVisibility(View.GONE);
        contactsCounterView.setVisibility(View.GONE);
//        workplaceCounterView.setVisibility(View.GONE);
        msgCounterView.setVisibility(View.GONE);
        playAnimation(0);


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
                }
//                else if (position == 2) {
//                    playAnimation(2);
//                    wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_workplace);
//                }
                else {
                    playAnimation(3);
                    wkVBinding.bottomNavigation.setSelectedItemId(R.id.i_my);
                }
            }
        });
        wkVBinding.bottomNavigation.setItemIconTintList(null);
        wkVBinding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.i_chat) {
                long nowTime = WKTimeUtils.getInstance().getCurrentMills();
                if (wkVBinding.vp.getCurrentItem() == 0){
                    if (nowTime - lastClickChatTabTime <= 300) {
                        EndpointManager.getInstance().invoke("scroll_to_unread_channel", null);
                    }
                    lastClickChatTabTime = nowTime;
                    return true;
                }
                wkVBinding.vp.setCurrentItem(0);
                playAnimation(0);
            } else if (item.getItemId() == R.id.i_contacts) {
                wkVBinding.vp.setCurrentItem(1);
                playAnimation(1);
            }
//            else if (item.getItemId() == R.id.i_workplace) {
//                wkVBinding.vp.setCurrentItem(2);
//                playAnimation(2);
//            }
            else {
                wkVBinding.vp.setCurrentItem(3);
                playAnimation(3);
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
        if (list != null && list.size() > 0) {
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
            moveTaskToBack(true);
            return true;
        } else
            return super.onKeyDown(keyCode, event);
    }

    private void playAnimation(int index) {
        if (index == 0) {
            lastClickChatTabTime = 0;
            meIV.setImageResource(R.mipmap.ic_mine_n);
            contactsIV.setImageResource(R.mipmap.ic_contacts_n);
            chatIV.setImageResource(R.mipmap.ic_chat_s);
//            workplaceIV.setImageResource(R.mipmap.ic_contacts_n);
        } else if (index == 1) {
            meIV.setImageResource(R.mipmap.ic_mine_n);
            chatIV.setImageResource(R.mipmap.ic_chat_n);
            contactsIV.setImageResource(R.mipmap.ic_contacts_s);
//            workplaceIV.setImageResource(R.mipmap.ic_contacts_n);
        } else if (index == 2) {
            meIV.setImageResource(R.mipmap.ic_mine_n);
            chatIV.setImageResource(R.mipmap.ic_chat_n);
            contactsIV.setImageResource(R.mipmap.ic_contacts_n);
//            workplaceIV.setImageResource(R.mipmap.ic_contacts_s);
        } else {
            chatIV.setImageResource(R.mipmap.ic_chat_n);
            contactsIV.setImageResource(R.mipmap.ic_contacts_n);
            meIV.setImageResource(R.mipmap.ic_mine_s);
//            workplaceIV.setImageResource(R.mipmap.ic_contacts_n);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WKMultiLanguageUtil.getInstance().setConfiguration();
        Theme.applyTheme();
    }

    @Override
    public void finish() {
        super.finish();
        EndpointManager.getInstance().remove("tab_activity");
    }
}
