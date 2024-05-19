package com.chat.base.msgitem;


import com.xinbida.wukongim.message.type.WKMsgContentType;

/**
 * 2019-11-15 17:01
 * 消息正文类型
 */
public class WKContentType extends WKMsgContentType {
    //系统消息
    public final static int systemMsg = 0;
    //以下是新消息提示分割线
    public final static int msgPromptNewMsg = -1;
    //消息时间
    public final static int msgPromptTime = -2;
    //未知消息
    public final static int unknown_msg = -3;
    //正在输入
    public final static int typing = -4;
    //撤回消息
    public final static int revoke = -5;
    //加载中
    public final static int loading = -6;
    //本地显示的群会议音视频
    public final static int videoCallGroup = -7;
    // 非好友
    public final static int noRelation = -9;
    // 敏感词提醒
    public final static int sensitiveWordsTips = -10;
    public final static int emptyView = -12;
    public final static int spanEmptyView = -13;
    // 富文本
    public final static int richText = 14;
    //新朋友
    public final static int newFriendsMsg = 1000;
    //创建群聊系统消息
    public final static int createGroupSysMsg = 1001;
    //群聊加人
    public final static int addGroupMembersMsg = 1002;
    //群聊减人
    public final static int removeGroupMembersMsg = 1003;
    //通过好友验证
    public final static int newFriendsApproved = 1004;
    //群系统消息
    public final static int groupSystemInfo = 1005;
    //撤回消息
    public final static int withdrawSystemInfo = 1006;
    //设置新的管理员
    public final static int setNewGroupAdmin = 1008;
    //审核群成员
    public final static int approveGroupMember = 1009;
    //成员拒绝入群
    public final static int groupMemberRefund = 1010;
    //群内禁止添加好友
    public final static int forbiddenAddFriend = 1013;
    //截屏消息
    public final static int screenshot = 1014;

    public static boolean isSystemMsg(int type) {
        return (type >= 1000 && type <= 2000);
    }

    public static boolean isLocalMsg(int type) {
        return type <= 0;
    }

    public static boolean isSupportNotification(int type) {
        return type >= WK_TEXT && type <= richText;
    }
}
