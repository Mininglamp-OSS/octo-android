package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.db.ConversationDbManager;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKCMD;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.interfaces.ICMDListener;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.utils.DateUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/21/21 11:54 AM
 * cmd manager
 */
public class CMDManager extends BaseManager {
    private final String TAG = "CMDManager";

    private CMDManager() {
    }

    private static class CMDManagerBinder {
        static final CMDManager cmdManager = new CMDManager();
    }

    public static CMDManager getInstance() {
        return CMDManagerBinder.cmdManager;
    }

    private ConcurrentHashMap<String, ICMDListener> cmdListenerMap;

    public void handleCMD(JSONObject jsonObject, String channelID, byte channelType) {
        if (jsonObject == null) {
            jsonObject = new JSONObject();
        }
        try {
            if (!jsonObject.has("channel_id"))
                jsonObject.put("channel_id", channelID);
            if (!jsonObject.has("channel_type"))
                jsonObject.put("channel_type", channelType);
        } catch (JSONException e) {
            WKLoggerUtils.getInstance().e(TAG, "handleCMD put json error");
        }
        handleCMD(jsonObject);
    }

    public void handleCMD(JSONObject json) {
        if (json == null) return;
        //内部消息
        if (json.has("cmd")) {
            String cmd = json.optString("cmd");
//            String sign = "";
//            if (json.has("sign"))
//                sign = json.optString("sign");
//            if (TextUtils.isEmpty(sign)) return;

            JSONObject jsonObject = null;
            if (json.has("param")) {
                jsonObject = json.optJSONObject("param");
            }
            if (jsonObject == null) {
                jsonObject = new JSONObject();
            }

//            Iterator<String> it = jsonObject.keys();
//            StringBuilder sb = new StringBuilder();
//
//            ArrayList<String> keys = new ArrayList<>();
//            while (it.hasNext()) {
//                String key = it.next();
//                keys.add(key);
//            }
//            Collections.sort(keys);
//
//            for (String item : keys) {
//                Object value = jsonObject.opt(item);
//                if (!TextUtils.isEmpty(sb)) {
//                    sb.append("&");
//                }
//                sb.append(item).append("=").append(value);
//            }

//            String content = WKAESEncryptUtils.digest(cmd + sb);
//            boolean verifyResult = WKAESEncryptUtils.checkRSASign(content, sign);
//            if (!verifyResult) {
//                WKLoggerUtils.getInstance().e("verify cmd error");
////                return;
//            } else {
//                Log.e("cmd解密成功", "--->");
//            }
            try {
                if (json.has("channel_id") && !jsonObject.has("channel_id")) {
                    jsonObject.put("channel_id", json.optString("channel_id"));
                }
                if (json.has("channel_type") && !jsonObject.has("channel_type")) {
                    jsonObject.put("channel_type", json.optString("channel_type"));
                }
            } catch (JSONException e) {
               WKLoggerUtils.getInstance().e(TAG,"handleCMD put json error");
            }
            if (cmd.equalsIgnoreCase(WKCMDKeys.wk_memberUpdate)) {
                //更新频道成员
                if (jsonObject.has("group_no")) {
                    String group_no = jsonObject.optString("group_no");
                    ChannelMembersManager.getInstance().setOnSyncChannelMembers(group_no, WKChannelType.GROUP);
                }
            } else if (cmd.equalsIgnoreCase(WKCMDKeys.wk_groupAvatarUpdate)) {
                //更新频道头像
                if (jsonObject.has("group_no")) {
                    String group_no = jsonObject.optString("group_no");
                    WKIM.getInstance().getChannelManager().setOnRefreshChannelAvatar(group_no, WKChannelType.GROUP);
                }
            } else if (cmd.equals(WKCMDKeys.wk_userAvatarUpdate)) {
                //个人头像更新
                if (jsonObject.has("uid")) {
                    String uid = jsonObject.optString("uid");
                    WKIM.getInstance().getChannelManager().setOnRefreshChannelAvatar(uid, WKChannelType.PERSONAL);
                }
            } else if (cmd.equalsIgnoreCase(WKCMDKeys.wk_channelUpdate)) {
                //频道修改
                if (jsonObject.has("channel_id") && jsonObject.has("channel_type")) {
                    String channelID = jsonObject.optString("channel_id");
                    byte channelType = (byte) jsonObject.optInt("channel_type");
                    WKIM.getInstance().getChannelManager().fetchChannelInfo(channelID, channelType);
                }
            } else if (cmd.equalsIgnoreCase(WKCMDKeys.wk_unreadClear)) {
                //清除消息红点
                if (jsonObject.has("channel_id") && jsonObject.has("channel_type")) {
                    String channelId = jsonObject.optString("channel_id");
                    int channelType = jsonObject.optInt("channel_type");
                    int unreadCount = jsonObject.optInt("unread");
                    WKIM.getInstance().getConversationManager().updateRedDot(channelId, (byte) channelType, unreadCount);
                }
            } else if (cmd.equalsIgnoreCase(WKCMDKeys.wk_voiceReaded)) {
                //语音已读
                if (jsonObject.has("message_id")) {
                    String messageId = jsonObject.optString("message_id");
                    MsgDbManager.getInstance().updateFieldWithMessageID(messageId, WKDBColumns.WKMessageColumns.voice_status, 1 + "");
                }
            } else if (cmd.equalsIgnoreCase(WKCMDKeys.wk_onlineStatus)) {
                //对方是否在线
//                int online = jsonObject.optInt("online");

                int online;
                String uid = jsonObject.optString("uid");
//                int device_flag = jsonObject.optInt("device_flag");
                int main_device_flag = jsonObject.optInt("main_device_flag");
                int all_offline = 0;
                if (jsonObject.has("all_offline")) all_offline = jsonObject.optInt("all_offline");
                online = all_offline == 1 ? 0 : 1;
                WKChannel wkChannel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
                if (wkChannel != null) {
                    wkChannel.online = online;
                    if (wkChannel.online == 0) {
                        wkChannel.lastOffline = DateUtils.getInstance().getCurrentSeconds();
                    }
//                    wkChannel.allOffline = all_offline;
//                    wkChannel.mainDeviceFlag = main_device_flag;
                    wkChannel.deviceFlag = main_device_flag;
//                    wkChannel.deviceFlag = device_flag;
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannel(wkChannel);
                }
            } else if (cmd.equals(WKCMDKeys.wk_message_erase)) {
                String erase_type = "";
                String from_uid = "";
                if (jsonObject.has("erase_type")) {
                    erase_type = jsonObject.optString("erase_type");
                }
                if (jsonObject.has("from_uid")) {
                    from_uid = jsonObject.optString("from_uid");
                }
                String channelID = jsonObject.optString("channel_id");
                byte channelType = (byte) jsonObject.optInt("channel_type");
                if (!TextUtils.isEmpty(erase_type)) {
                    if (erase_type.equals("all")) {
                        if (!TextUtils.isEmpty(channelID)) {
                            WKIM.getInstance().getMsgManager().clearWithChannel(channelID, channelType);
                        }
                    } else {
                        if (!TextUtils.isEmpty(from_uid)) {
                            WKIM.getInstance().getMsgManager().clearWithChannelAndFromUID(channelID, channelType, from_uid);
                        }
                    }
                }
            } else if (cmd.equals(WKCMDKeys.wk_conversation_delete)) {
                String channelID = jsonObject.optString("channel_id");
                byte channelType = (byte) jsonObject.optInt("channel_type");
                if (!TextUtils.isEmpty(channelID)) {
                    ConversationDbManager.getInstance().deleteWithChannel(channelID, channelType, 1);
                }
            }
            WKCMD wkcmd = new WKCMD(cmd, jsonObject);
            WKLoggerUtils.getInstance().e("处理cmd："+cmd);
            pushCMDs(wkcmd);
        }

    }

    /**
     * 处理cmd
     *
     * @param cmd   cmd
     * @param param 参数
     */
    public void handleCMD(String cmd, String param, String sign) {
        if (TextUtils.isEmpty(cmd)) return;
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("cmd", cmd);
            if (!TextUtils.isEmpty(param)) {
                JSONObject paramJson = new JSONObject(param);
                jsonObject.put("param", paramJson);
            }
            if (!TextUtils.isEmpty(sign)) {
                jsonObject.put("sign", sign);
            }

        } catch (JSONException e) {
           WKLoggerUtils.getInstance().e(TAG,"handleCMD put json error");
            return;
        }
        handleCMD(jsonObject);
    }

    public synchronized void addCmdListener(String key, ICMDListener icmdListener) {
        if (TextUtils.isEmpty(key) || icmdListener == null) return;
        if (cmdListenerMap == null) cmdListenerMap = new ConcurrentHashMap<>();
        cmdListenerMap.put(key, icmdListener);
    }

    public void removeCmdListener(String key) {
        if (TextUtils.isEmpty(key) || cmdListenerMap == null) return;
        cmdListenerMap.remove(key);
    }

    private void pushCMDs(WKCMD wkcmd) {
        if (cmdListenerMap != null && !cmdListenerMap.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, ICMDListener> entry : cmdListenerMap.entrySet()) {
                    entry.getValue().onMsg(wkcmd);
                }
            });
        }
    }

    public void setRSAPublicKey(String key) {
        //  key = new String(WKAESEncryptUtils.base64Decode(key));
        WKIMApplication.getInstance().setRSAPublicKey(key);
    }

    public String getRSAPublicKey() {
        return WKIMApplication.getInstance().getRSAPublicKey();
    }
}
