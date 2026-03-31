package com.xinbida.wukongim;

import android.content.Context;
import android.text.TextUtils;

import com.xinbida.wukongim.manager.CMDManager;
import com.xinbida.wukongim.manager.ChannelManager;
import com.xinbida.wukongim.manager.ChannelMembersManager;
import com.xinbida.wukongim.manager.ConnectionManager;
import com.xinbida.wukongim.manager.ConversationManager;
import com.xinbida.wukongim.manager.MsgManager;
import com.xinbida.wukongim.manager.ReminderManager;
import com.xinbida.wukongim.manager.RobotManager;
import com.xinbida.wukongim.message.MessageHandler;
import com.xinbida.wukongim.utils.CryptoUtils;

/**
 * 5/20/21 5:25 PM
 */
public class WKIM {
    private final String Version = "V1.4.0";

    private WKIM() {

    }

    private static class WKIMBinder {
        static final WKIM im = new WKIM();
    }

    public static WKIM getInstance() {
        return WKIMBinder.im;
    }

    private boolean isDebug = false;
    private boolean isWriteLog = false;
    private String deviceId = "";

    public boolean isDebug() {
        return isDebug;
    }

    public boolean isWriteLog() {
        return isWriteLog;
    }

    public String getDeviceID(){
        return deviceId;
    }

    public void setWriteLog(boolean isWriteLog) {
        this.isWriteLog = isWriteLog;
    }

    // debug模式会输出一些连接信息，发送消息情况等
    public void setDebug(boolean isDebug) {
        this.isDebug = isDebug;
    }

    //设置文件目录
    public void setFileCacheDir(String fileDir) {
        WKIMApplication.getInstance().setFileCacheDir(fileDir);
    }

    public String getVersion() {
        return Version;
    }
    public void setDeviceId(String deviceID){
        this.deviceId = deviceID;
    }
    /**
     * 初始化IM
     *
     * @param context context
     * @param uid     用户ID
     * @param token   im token
     */
    public void init(Context context, String uid, String token) {
        if (context == null || TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) {
            throw new NullPointerException("context,uid and token cannot be null");
        }

        WKIMApplication.getInstance().closeDbHelper();
        WKIMApplication.getInstance().initContext(context);
        WKIMApplication.getInstance().setUid(uid);
        WKIMApplication.getInstance().setToken(token);
        // 初始化加密key
        CryptoUtils.getInstance().initKey();
        // 初始化默认消息类型
        getMsgManager().initNormalMsg();
        // 初始化数据库
        WKIMApplication.getInstance().getDbHelper();
        // 将上次发送消息中的队列标志为失败
        MessageHandler.getInstance().updateLastSendingMsgFail();
    }

    // 获取消息管理
    public MsgManager getMsgManager() {
        return MsgManager.getInstance();
    }

    // 获取连接管理
    public ConnectionManager getConnectionManager() {
        return ConnectionManager.getInstance();
    }

    // 获取频道管理
    public ChannelManager getChannelManager() {
        return ChannelManager.getInstance();
    }

    // 获取最近会话管理
    public ConversationManager getConversationManager() {
        return ConversationManager.getInstance();
    }

    // 获取频道成员管理
    public ChannelMembersManager getChannelMembersManager() {
        return ChannelMembersManager.getInstance();
    }

    //获取提醒管理
    public ReminderManager getReminderManager() {
        return ReminderManager.getInstance();
    }

    // 获取cmd管理
    public CMDManager getCMDManager() {
        return CMDManager.getInstance();
    }

    public RobotManager getRobotManager() {
        return RobotManager.getInstance();
    }

}
