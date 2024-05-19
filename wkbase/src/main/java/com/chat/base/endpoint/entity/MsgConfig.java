package com.chat.base.endpoint.entity;

/**
 * 2020-09-23 17:04
 * 消息配置
 */
public class MsgConfig {
    public boolean isCanForward;//是否能转发
    public boolean isCanWithdraw;//是否能撤回
    public boolean isCanMultipleChoice;//是否能多选
    public boolean isCanReply;//是否能回复
    public boolean isCanShowReaction;// 是否能显示消息回应点赞
    public boolean isCanDelete; // 是否能删除
    public boolean isCanShowPinMenu; // 是否能显示置顶菜单

    public MsgConfig(
            boolean isCanForward,
            boolean isCanWithdraw,
            boolean isCanMultipleChoice,
            boolean isCanReply,
            boolean isCanShowReaction,
            boolean isCanShowPinMenu) {
        this.isCanForward = isCanForward;
        this.isCanWithdraw = isCanWithdraw;
        this.isCanMultipleChoice = isCanMultipleChoice;
        this.isCanReply = isCanReply;
        this.isCanShowReaction = isCanShowReaction;
        this.isCanShowPinMenu = isCanShowPinMenu;
        this.isCanDelete = true;
    }

    public MsgConfig(boolean allCanUse) {
        this.isCanForward = allCanUse;
        this.isCanWithdraw = allCanUse;
        this.isCanMultipleChoice = allCanUse;
        this.isCanReply = allCanUse;
        this.isCanShowReaction = allCanUse;
        this.isCanDelete = allCanUse;
        this.isCanShowPinMenu = allCanUse;
    }

    @Deprecated
    public MsgConfig() {
        this.isCanForward = true;
        this.isCanWithdraw = true;
        this.isCanMultipleChoice = true;
        this.isCanReply = true;
        this.isCanShowReaction = true;
        this.isCanDelete = true;
        this.isCanShowPinMenu = true;
    }
}
