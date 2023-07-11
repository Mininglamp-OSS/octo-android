package com.chat.base.utils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * description: 基类用户关系判断
 * created by ypp on 2021/10/27
 */
public class UserUtils {

    private static UserUtils userUtils;

    public static synchronized UserUtils getInstance() {
        if (userUtils == null) {
            userUtils = new UserUtils();
        }
        return userUtils;
    }

    /**
     * （主动）无使用场景
     * 判断自己是不是
     * 把
     * 对方删除、解除好友关系
     *
     * @param uid 对方uid
     * @return true  主动删除 false 正常好友关系
     */
    public boolean checkMyFriendDelete(String uid) {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (channel != null) {
                int i = channel.follow;
                return i == 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * （主动）无使用场景
     * 判断自己有没有
     * 把
     * 好友拉入黑名单
     *
     * @param uid 对方uid
     * @return true 主动拉黑好友 false 没拉入
     */
    public boolean checkMyFriendBlacklist(String uid) {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (channel != null) {
                int i = channel.status;
                return i == 2;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * （被动）
     * 判断自己是不是
     * 被
     * 对方删除、解除好友关系
     *
     * @param uid 对方uid
     * @return true 被好友删除 false 正常
     */
    public boolean checkFriendRelation(String uid) {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (channel != null && channel.localExtra != null) {
                Object o = channel.localExtra.get(WKChannelExtras.beDeleted);
                if (o instanceof Integer) {
                    return (int) o == 1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * （被动）
     * 判断自己有没有
     * 被
     * 好友拉入黑名单
     *
     * @param uid 对方uid
     * @return true 被好友拉入 false 没拉入
     */
    public boolean checkBlacklist(String uid) {
        try {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (channel != null && channel.localExtra != null) {
                Object o = channel.localExtra.get(WKChannelExtras.beBlacklist);
                if (o instanceof Integer) {
                    return (int) o == 1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 判断某一个uid（可以传自己）是否在还在某个群内
     *
     * @param channelId 群频道ID
     * @param uid       自己的uid
     * @return true 在群内 false 不在群内、被踢
     */
    public boolean checkInGroupOk(String channelId, String uid) {
        try {
            WKChannelMember channelMember = WKIM.getInstance().getChannelMembersManager().
                    getMember(channelId, WKChannelType.GROUP, uid);
            if (channelMember != null && channelMember.isDeleted == 1) {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 判断某一个uid（可以传自己）是否在还在某个群黑名单内
     *
     * @param channelId
     * @param uid
     * @return true 在群黑名单内 false 正常用户
     */
    public boolean checkGroupBlacklist(String channelId, String uid) {
        try {
            WKChannelMember channelMember = WKIM.getInstance().getChannelMembersManager().
                    getMember(channelId, WKChannelType.GROUP, uid);
            //status==1是正常用户，2是黑名单用户
            if (channelMember != null && 2 == channelMember.status) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
