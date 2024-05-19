package com.chat.base.common;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.AppModule;
import com.chat.base.entity.AppVersion;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.entity.WKChannelState;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.DispatchQueuePool;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 4/21/21 6:23 PM
 */
public class WKCommonModel extends WKBaseModel {
    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);

    private WKCommonModel() {
    }

    private static class CommonModelBinder {
        final static WKCommonModel model = new WKCommonModel();
    }

    public static WKCommonModel getInstance() {
        return CommonModelBinder.model;
    }

    public void getAppNewVersion(boolean isShowToast, final IAppNewVersion iAppNewVersion) {
        String v = WKDeviceUtils.getInstance().getVersionName(WKBaseApplication.getInstance().getContext());
        request(createService(WKCommonService.class).getAppNewVersion(v), new IRequestResultListener<AppVersion>() {
            @Override
            public void onSuccess(AppVersion result) {
                if ((result == null || TextUtils.isEmpty(result.download_url)) && isShowToast) {
                    WKToastUtils.getInstance().showToastNormal(WKBaseApplication.getInstance().getContext().getString(R.string.is_new_version));
                } else {
                    iAppNewVersion.onNewVersion(result);
                }
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    public interface IAppNewVersion {
        void onNewVersion(AppVersion version);
    }

    public interface IAppConfig {
        void onResult(int code, String msg, WKAPPConfig wkappConfig);
    }

    public void getAppConfig(IAppConfig iAppConfig) {
        request(createService(WKCommonService.class).getAppConfig(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(WKAPPConfig result) {
                WKConfig.getInstance().saveAppConfig(result);
                if (iAppConfig != null) {
                    iAppConfig.onResult(HttpResponseCode.success, "", result);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iAppConfig != null) {
                    iAppConfig.onResult(code, msg, null);
                }
            }
        });
    }

    public void getChannelState(String channelID, byte channelType, final IChannelState iChannelState) {
        request(createService(WKCommonService.class).getChannelState(channelID, channelType), new IRequestResultListener<WKChannelState>() {
            @Override
            public void onSuccess(WKChannelState result) {
                iChannelState.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                iChannelState.onResult(null);
            }
        });
    }

    public interface IChannelState {
        void onResult(WKChannelState channelState);
    }


    public void getChannel(String channelID, byte channelType, IGetChannel iGetChannel) {
        dispatchQueuePool.execute(() -> request(createService(WKCommonService.class).getChannel(channelID, channelType), new IRequestResultListener<ChannelInfoEntity>() {
            @Override
            public void onSuccess(ChannelInfoEntity result) {
                saveChannel(result);
                if (iGetChannel != null) {
                    AndroidUtilities.runOnUIThread(() -> iGetChannel.onResult(HttpResponseCode.success, "", result));
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iGetChannel != null) {
                    AndroidUtilities.runOnUIThread(() -> iGetChannel.onResult(code, msg, null));

                }
            }
        }));

    }

    private void saveChannel(ChannelInfoEntity entity) {
        HashMap<String, Object> hashMap = null;
        WKChannel wkChannel = new WKChannel(entity.channel.channel_id, entity.channel.channel_type);
        WKChannel localChannel = WKIM.getInstance().getChannelManager().getChannel(entity.channel.channel_id, entity.channel.channel_type);
        boolean isRefreshContacts = false;
        if (localChannel != null && !TextUtils.isEmpty(localChannel.channelID)) {
            wkChannel.avatarCacheKey = localChannel.avatarCacheKey;
            hashMap = localChannel.localExtra;

            if (wkChannel.follow != entity.follow || wkChannel.status != entity.status)
                isRefreshContacts = true;
        }
        if (hashMap == null)
            hashMap = new HashMap<>();

        wkChannel.channelName = entity.name;
        wkChannel.avatar = entity.logo;
        wkChannel.channelRemark = entity.remark;
        wkChannel.status = entity.status;
        wkChannel.online = entity.online;
        wkChannel.lastOffline = entity.last_offline;
        wkChannel.receipt = entity.receipt;
        wkChannel.robot = entity.robot;
        wkChannel.category = entity.category;
        wkChannel.top = entity.stick;
        wkChannel.mute = entity.mute;
        wkChannel.showNick = entity.show_nick;
        wkChannel.follow = entity.follow;
        wkChannel.save = entity.save;
        wkChannel.forbidden = entity.forbidden;
        wkChannel.invite = entity.invite;
        wkChannel.flame = entity.flame;
        wkChannel.flameSecond = entity.flame_second;
        wkChannel.deviceFlag = entity.device_flag;
        if (entity.parent_channel != null) {
            wkChannel.parentChannelID = entity.parent_channel.channel_id;
            wkChannel.parentChannelType = entity.parent_channel.channel_type;
        }
        wkChannel.remoteExtraMap = (HashMap) entity.extra;
        hashMap.put(WKChannelExtras.beDeleted, entity.be_deleted);
        hashMap.put(WKChannelExtras.beBlacklist, entity.be_blacklist);
        hashMap.put(WKChannelExtras.notice, entity.notice);
//        hashMap.put(WKChannelCustomerExtras.chatBgUrl, entity.chat_bg_url);
//        hashMap.put(WKChannelCustomerExtras.chatBgIsSvg, entity.chat_bg_is_svg);
//        hashMap.put(WKChannelCustomerExtras.chatBgIsBlurred, entity.chat_bg_is_blurred);
//        hashMap.put(WKChannelCustomerExtras.chatBgIsDeleted, entity.chat_bg_is_deleted);
//        hashMap.put(WKChannelCustomerExtras.chatBgShowPattern, entity.chat_bg_show_pattern);
        wkChannel.localExtra = hashMap;
//
//        if (entity.extra != null) {
//            Set<String> set = entity.extra.keySet();
//            for (String key : set) {
//                wkChannel.remoteExtraMap.put(key, entity.extra.get(key));
//            }
//        }
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(wkChannel);
        if (isRefreshContacts) {
            EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
        }
    }

    public interface IGetChannel {
        void onResult(int code, String msg, ChannelInfoEntity entity);
    }

    public void getAppModule(@NotNull final IAppModule iAppModule) {
        request(createService(WKCommonService.class).getAppModule(), new IRequestResultListener<List<AppModule>>() {
            @Override
            public void onSuccess(List<AppModule> result) {
                String text = WKSharedPreferencesUtil.getInstance().getSPWithUID("app_module");
                List<AppModule> localSavedAppModule = new ArrayList<>();
                if (!TextUtils.isEmpty(text)) {
                    localSavedAppModule = JSON.parseArray(text, AppModule.class);
                }
                List<AppModule> tempList = new ArrayList<>();
                if (WKReader.isNotEmpty(result)) {
                    for (AppModule item : result) {
                        AppModule m = new AppModule();
                        m.setName(item.getName());
                        m.setDesc(item.getDesc());
                        m.setSid(item.getSid());
                        m.setStatus(item.getStatus());
                        if (item.getStatus() == 2) {
                            m.setChecked(true);
                        } else if (item.getStatus() == 0) {
                            m.setChecked(false);
                        } else {
                            if (WKReader.isNotEmpty(localSavedAppModule)) {
                                for (AppModule temp : localSavedAppModule) {
                                    if (temp.getSid().equals(item.getSid())) {
                                        m.setChecked(temp.getChecked());
                                    }
                                }
                            } else {
                                m.setChecked(false);
                            }
                        }
                        tempList.add(m);
                    }
                }
                String json = JSON.toJSONString(tempList);
                WKSharedPreferencesUtil.getInstance().putSPWithUID("app_module", json);

                iAppModule.onResult(HttpResponseCode.success, "", tempList);
            }

            @Override
            public void onFail(int code, String msg) {
                iAppModule.onResult(code, msg, null);
            }
        });
    }

    public interface IAppModule {
        void onResult(int code, String msg, List<AppModule> list);
    }
}
