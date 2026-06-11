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

package com.chat.uikit.fragment;

import android.content.Intent;
import android.text.TextUtils;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.PersonalInfoMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.ApiUrlDialog;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.WKUIKitApplication;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.FragMyLayoutBinding;
import com.chat.uikit.user.MyInfoActivity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-11-12 14:58
 * 我的
 */
public class MyFragment extends WKBaseFragment<FragMyLayoutBinding> {
    private PersonalItemAdapter adapter;
    private boolean isAppConfigLoaded = false;
    private final android.os.Handler longPressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable longPressRunnable;

    @Override
    protected FragMyLayoutBinding getViewBinding() {
        return FragMyLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        wkVBinding.recyclerView.setNestedScrollingEnabled(false);
        adapter = new PersonalItemAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, adapter);
        List<PersonalInfoMenu> endpoints = EndpointManager.getInstance().invokes(EndpointCategory.personalCenter, null);
        for (int i = 0; i < endpoints.size(); i++) {
            if (!TextUtils.isEmpty(endpoints.get(i).sid)
                    && endpoints.get(i).sid.equals("invite_code")
                    && WKConfig.getInstance().getAppConfig().register_invite_on == 0) {
                endpoints.remove(i);
                break;
            }
        }
        adapter.setList(endpoints);
    }

    @Override
    protected void initPresenter() {
        wkVBinding.avatarView.setSize(48);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
    }

    @Override
    protected void initListener() {
        adapter.setOnItemClickListener((adapter1, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, view1 -> {
            PersonalInfoMenu menu = (PersonalInfoMenu) adapter1.getItem(position);
            if (menu != null && menu.iPersonalInfoMenuClick != null) {
                menu.iPersonalInfoMenuClick.onClick();
            }
        }));
        SingleClickUtil.onSingleClick(wkVBinding.cardLayout, view -> gotoMyInfo());
        wkVBinding.copyShortNoIv.setOnClickListener(v -> {
            com.chat.base.entity.UserInfoEntity me = WKConfig.getInstance().getUserInfo();
            if (!TextUtils.isEmpty(me.short_no)) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("short_no", me.short_no));
                com.chat.base.utils.WKToastUtils.getInstance().showToast(getString(R.string.copied));
            }
        });
        // 隐藏入口：长按头像 3 秒修改 API 地址
        longPressRunnable = () -> {
            if (getActivity() == null) return;
            ApiUrlDialog dialog = new ApiUrlDialog(getActivity());
            dialog.setOnConfirmListener(url -> {
                WKUIKitApplication.getInstance().exitLogin(0);
                Intent intent = requireActivity().getPackageManager().getLaunchIntentForPackage(requireActivity().getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
                Runtime.getRuntime().exit(0);
            });
            dialog.show();
        };
        wkVBinding.avatarView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    longPressHandler.postDelayed(longPressRunnable, 1500);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    break;
            }
            return false;
        });
    }

    void gotoMyInfo() {
        startActivity(new Intent(getActivity(), MyInfoActivity.class));
    }

    @Override
    public void onPause() {
        super.onPause();
        longPressHandler.removeCallbacks(longPressRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        longPressHandler.removeCallbacks(longPressRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        //  (#227)：displayName 合并 —— 实名态下用 realname，否则用 nickname
        com.chat.base.entity.UserInfoEntity me = WKConfig.getInstance().getUserInfo();
        wkVBinding.nameTv.setText(me.getDisplayName());
        int verifiedVis = me.realname_verified ? android.view.View.VISIBLE : android.view.View.GONE;
        wkVBinding.realnameVerifiedIv.setVisibility(verifiedVis);
        wkVBinding.realnameVerifiedTag.setVisibility(verifiedVis);
        wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);

        String shortNo = me.short_no;
        if (!TextUtils.isEmpty(shortNo)) {
            wkVBinding.shortNoTv.setVisibility(android.view.View.VISIBLE);
            wkVBinding.copyShortNoIv.setVisibility(android.view.View.VISIBLE);
            wkVBinding.shortNoTv.setText(getString(R.string.short_no_format, getString(R.string.app_name), shortNo));
        }

        String versionName = WKDeviceUtils.getInstance().getVersionName(requireContext());
        String buildCode;
        try {
            buildCode = String.valueOf(requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionCode);
        } catch (Exception e) {
            buildCode = "";
        }
        wkVBinding.statusTv.setText(getString(R.string.online_status_text) + " · Android v" + versionName + "(" + buildCode + ")");

        if (null != adapter) {
            try {
                WKCommonModel.getInstance().getAppNewVersion(false, version -> {
                    if (!isAdded() || adapter == null) return;
                    int index = -1;
                    for (int i = 0; i < adapter.getData().size(); i++) {
                        if (getString(R.string.currency).equals(adapter.getData().get(i).text)) {
                            index = i;
                            break;
                        }
                    }
                    if (index != -1) {
                        String v = WKDeviceUtils.getInstance().getVersionName(requireContext());
                        if (version != null && !TextUtils.isEmpty(version.url) && WKDeviceUtils.getInstance().isNewerVersion(version.version, v)) {
                            if (!adapter.getData().get(index).isNewVersionIv) {
                                adapter.getData().get(index).setIsNewVersionIv(true);
                                adapter.notifyItemChanged(index);
                            }
                        } else if (adapter.getData().get(index).isNewVersionIv) {
                            adapter.getData().get(index).setIsNewVersionIv(false);
                            adapter.notifyItemChanged(index);
                        }
                    }
                });
            } catch (Exception e) {
                WKLogUtils.w("检查新版本错误");
            }
        }
        if (!isAppConfigLoaded) {
            WKCommonModel.getInstance().getAppConfig((code, msg, wkappConfig) -> {
                if (!isAdded() || adapter == null || WKReader.isEmpty(adapter.getData())) {
                    return;
                }
                if (code == HttpResponseCode.success) {
                    isAppConfigLoaded = true;
                    if (wkappConfig.register_invite_on == 0) {
                        for (int i = 0; i < adapter.getData().size(); i++) {
                            if (!TextUtils.isEmpty(adapter.getData().get(i).sid) && adapter.getData().get(i).sid.equals("invite_code")) {
                                adapter.removeAt(i);
                                break;
                            }
                        }
                    }
                }
            });
        }
    }
}
