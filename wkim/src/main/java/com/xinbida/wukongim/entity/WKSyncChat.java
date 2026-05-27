package com.xinbida.wukongim.entity;

import java.util.List;

/**
 * 同步会话
 */
public class WKSyncChat {
    public long cmd_version;
    public List<WKSyncCmd> cmds;
    public String uid;
    public List<WKSyncConvMsg> conversations;
    public List<WKChannelState> channel_status;
    public List<WKSpaceMembership> space_memberships;
}
