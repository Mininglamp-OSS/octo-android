package com.chat.base.msg;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chad.library.adapter.base.BaseProviderMultiAdapter;
import com.chad.library.adapter.base.provider.BaseItemProvider;
import com.chat.base.R;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ShowMsgReactionMenu;
import com.chat.base.msgitem.WKChatBaseProvider;
import com.chat.base.msgitem.WKChatIteMsgFromType;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.SecretDeleteTimer;
import com.chat.base.utils.WKReader;
import com.chat.base.views.ChatItemView;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgReaction;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2020-08-03 13:46
 * 消息适配器
 */
public class ChatAdapter extends BaseProviderMultiAdapter<WKUIChatMsgItemEntity> {
    private final IConversationContext iConversationContext;

    public enum AdapterType {
        normalMessage, pinnedMessage
    }

    private final AdapterType adapterType;

    ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> getItemProviderList() {
        return adapterType == AdapterType.normalMessage ? WKMsgItemViewManager.getInstance().getChatItemProviderList() : WKMsgItemViewManager.getInstance().getPinnedChatItemProviderList();
    }

    public ChatAdapter(@NonNull IConversationContext iConversationContext, AdapterType adapterType) {
        super();
        this.adapterType = adapterType;
        this.iConversationContext = iConversationContext;
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        for (int type : list.keySet()) {
            addItemProvider(Objects.requireNonNull(list.get(type)));
        }
    }

    @Override
    protected int getItemType(@NotNull List<? extends WKUIChatMsgItemEntity> list, int i) {
        if (list.get(i).wkMsg.remoteExtra != null && list.get(i).wkMsg.remoteExtra.revoke == 1) {
            //撤回消息
            return WKContentType.revoke;
        } else {
            if (getItemProviderList().containsKey(list.get(i).wkMsg.type))
                return list.get(i).wkMsg.type;
            else {
                if (list.get(i).wkMsg.type >= 1000 && list.get(i).wkMsg.type <= 2000) {
                    //系统消息
                    return WKContentType.systemMsg;
                } else {
                    return WKContentType.unknown_msg;
                }
            }
        }
    }

    public long getLastTimeMsg() {
        long timestamp = 0;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.timestamp > 0) {
                timestamp = getData().get(i).wkMsg.timestamp;
                break;
            }
        }
        return timestamp;
    }


    public IConversationContext getConversationContext() {
        return iConversationContext;
    }

    //显示多选
    public void showMultipleChoice() {
        iConversationContext.showMultipleChoice();
    }

    public void hideSoftKeyboard() {
        iConversationContext.hideSoftKeyboard();
    }

    //回复某条消息
    public void replyMsg(WKMsg wkMsg) {
        iConversationContext.showReply(wkMsg);
    }

    public void showTitleRightText(String content) {
        iConversationContext.setTitleRightText(content);
    }

    //提示某条消息
    public void showTipsMsg(String clientMsgNo) {
        iConversationContext.tipsMsg(clientMsgNo);
    }

    //设置输入框内容
    public void setEditContent(String content) {
        iConversationContext.setEditContent(content);
    }

    //是否存在某条消息
    public boolean isExist(String clientMsgNo) {
        if (TextUtils.isEmpty(clientMsgNo)) return false;
        boolean isExist = false;
        for (int i = 0, size = getData().size(); i < size; i++) {
            if (getData().get(i).wkMsg != null && !TextUtils.isEmpty(getData().get(i).wkMsg.clientMsgNO) && getData().get(i).wkMsg.clientMsgNO.equals(clientMsgNo)) {
                isExist = true;
                break;
            }
        }
        return isExist;
    }

    //获取最后一条消息
    public WKMsg getLastMsg() {
        WKMsg wkMsg = null;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null
                    && getData().get(i).wkMsg.type != WKContentType.msgPromptNewMsg
                    && getData().get(i).wkMsg.type != WKContentType.typing) {
                wkMsg = getData().get(i).wkMsg;
                break;
            }
        }
        return wkMsg;
    }

    //获取最后一条消息是否为正在输入
    public boolean lastMsgIsTyping() {
        boolean isTyping = false;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.type == WKContentType.typing) {
                isTyping = true;
                break;
            }
        }

        return isTyping;
    }

    public long getEndMsgOrderSeq() {
        long oldestOrderSeq = 0;
        for (int i = getData().size() - 1; i >= 0; i--) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                oldestOrderSeq = getData().get(i).wkMsg.orderSeq;
                break;
            }
        }
        return oldestOrderSeq;
    }

    public long getFirstMsgOrderSeq() {
        long oldestOrderSeq = 0;
        for (int i = 0, size = getData().size(); i < size; i++) {
            if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                oldestOrderSeq = getData().get(i).wkMsg.orderSeq;
                break;
            }
        }
        return oldestOrderSeq;
    }

    public void resetData(List<WKUIChatMsgItemEntity> list) {
        if (WKReader.isEmpty(list)) return;
        for (int i = 0, size = list.size(); i < size; i++) {
            int previousIndex = i - 1;
            int nextIndex = i + 1;
            if (previousIndex >= 0) {
                list.get(i).previousMsg = list.get(previousIndex).wkMsg;
            }
            if (nextIndex <= list.size() - 1) {
                list.get(i).nextMsg = list.get(nextIndex).wkMsg;
            }
        }
    }

    public int getFirstVisibleItemIndex(int startIndex) {
        int index = startIndex;
        if (startIndex <= getData().size() - 1) {
            if (getData().get(startIndex).wkMsg == null || getData().get(startIndex).wkMsg.orderSeq == 0) {
                for (int i = startIndex; i < getData().size(); i++) {
                    if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                        index = i;
                        break;
                    }
                }
            }
        }
        return index;
    }

    public WKMsg getFirstVisibleItem(int startIndex) {
        WKMsg wkMsg = null;
        if (startIndex <= getData().size() - 1) {
            if (getData().get(startIndex).wkMsg == null || getData().get(startIndex).wkMsg.orderSeq == 0) {
                for (int i = startIndex; i < getData().size(); i++) {
                    if (getData().get(i).wkMsg != null && getData().get(i).wkMsg.orderSeq != 0) {
                        wkMsg = getData().get(i).wkMsg;
                        break;
                    }
                }
            } else {
                wkMsg = getData().get(startIndex).wkMsg;
            }
        }
        return wkMsg;
    }

    public boolean isShowChooseItem() {
        boolean isShowChoose = false;
        for (int i = 0, size = getData().size(); i < size; i++) {
            if (getData().get(i).isChoose) {
                isShowChoose = true;
                break;
            }
        }
        return isShowChoose;
    }

    public boolean isCanSwipe(int index) {
        if (index < 0 || index >= getData().size()) {
            return false;
        }
        int type = getData().get(index).wkMsg.type;
        if (type <= 0 || getData().get(index).wkMsg.flame == 1 || (getData().get(index).wkMsg.remoteExtra != null && getData().get(index).wkMsg.remoteExtra.revoke == 1)) {
            return false;
        }
        WKChannel channel = iConversationContext.getChatChannelInfo();
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        WKChatBaseProvider baseItemProvider = (WKChatBaseProvider) list.get(type);
        if (baseItemProvider != null && channel.status == 1)
            return baseItemProvider.getMsgConfig(type).isCanReply;
        return false;
    }

    public void updateDeleteTimer(int position) {
        WKUIChatMsgItemEntity entity = getData().get(position);
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
        if (linearLayoutManager == null) return;
        View view = linearLayoutManager.findViewByPosition(position);
        LinearLayout baseView = null;
        if (view != null) {
            baseView = view.findViewById(R.id.wkBaseContentLayout);
        }
        if (baseView == null) return;
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        WKChatBaseProvider baseItemProvider = (WKChatBaseProvider) list.get(entity.wkMsg.type);
        if (baseItemProvider != null) {
            SecretDeleteTimer deleteTimer = null;
            WKChatIteMsgFromType from = baseItemProvider.getMsgFromType(entity.wkMsg);
            if (baseView.getChildCount() > 1) {
                if (from == WKChatIteMsgFromType.SEND) {
                    View childView = baseView.getChildAt(0);
                    if (childView instanceof SecretDeleteTimer) {
                        deleteTimer = (SecretDeleteTimer) childView;
                    }
                } else if (from == WKChatIteMsgFromType.RECEIVED) {
                    View childView = baseView.getChildAt(1);
                    if (childView instanceof SecretDeleteTimer) {
                        deleteTimer = (SecretDeleteTimer) childView;
                    }
                }
            }

            if (deleteTimer != null) {
                deleteTimer.setVisibility(View.VISIBLE);
                deleteTimer.setDestroyTime(entity.wkMsg.clientMsgNO, entity.wkMsg.flameSecond, entity.wkMsg.viewedAt, false);
            }
        }
    }


    public enum RefreshType {
        status, background, data, reaction
    }

    public void notifyStatus(int position) {
        notify(position, RefreshType.status, null);
    }

    public void notifyData(int position) {
        notify(position, RefreshType.data, null);
    }

    public void notifyBackground(int position) {
        notify(position, RefreshType.background, null);
    }

    public void notifyReaction(int position, List<WKMsgReaction> reactionList) {
        notify(position, RefreshType.reaction, reactionList);
    }

    private void notify(int position, RefreshType refreshType, List<WKMsgReaction> reactionList) {
        WKUIChatMsgItemEntity entity = getData().get(position);
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
        if (linearLayoutManager == null) return;
        View view = linearLayoutManager.findViewByPosition(position);
        View baseView = null;
        if (view != null) {
            baseView = view.findViewById(R.id.wkBaseContentLayout);
        }
        if (baseView == null) return;
        ConcurrentHashMap<Integer, BaseItemProvider<WKUIChatMsgItemEntity>> list = getItemProviderList();
        WKChatBaseProvider baseItemProvider = (WKChatBaseProvider) list.get(entity.wkMsg.type);
        if (baseItemProvider != null) {
            WKChatIteMsgFromType from = baseItemProvider.getMsgFromType(entity.wkMsg);
            // 刷新
            if (refreshType == RefreshType.data) {
                baseItemProvider.refreshData(position, baseView, entity, from);
            }
            if (refreshType == RefreshType.reaction) {
                FrameLayout reactionsView = view.findViewById(R.id.reactionsView);
                EndpointManager.getInstance().invoke(
                        "refresh_msg_reaction", new ShowMsgReactionMenu(
                                reactionsView,
                                from,
                                this,
                                reactionList)
                );
                AvatarView avatarView = view.findViewById(R.id.avatarView);
                if (avatarView != null) {
                    baseItemProvider.setAvatarLayoutParams(entity, from, avatarView);
                }
            }
            if (refreshType == RefreshType.background) {
                AvatarView avatarView = view.findViewById(R.id.avatarView);
                if (avatarView != null) {
                    baseItemProvider.setAvatarLayoutParams(entity, from, avatarView);
                }
                baseItemProvider.resetCellBackground(baseView, entity, from);
                LinearLayout fullContentLayout = view.findViewById(R.id.fullContentLayout);
                if (fullContentLayout != null) {
                    baseItemProvider.setFullLayoutParams(entity, from, fullContentLayout);
                }
                ChatItemView viewGroupLayout = view.findViewById(R.id.viewGroupLayout);
                if (viewGroupLayout != null) {
                    baseItemProvider.setItemPadding(position, viewGroupLayout);
                }
            }

            if (refreshType == RefreshType.status) {
                baseItemProvider.resetCellListener(position, baseView, entity, from);
                baseItemProvider.setMsgTimeAndStatus(
                        entity,
                        baseView,
                        from
                );
            }

        }

    }

    public void updateReplyMsgRevoke(WKMsg wkMsg) {
        if (wkMsg == null || wkMsg.remoteExtra == null || TextUtils.isEmpty(wkMsg.remoteExtra.messageID))
            return;
        List<WKUIChatMsgItemEntity> list = getData();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).wkMsg.baseContentMsgModel != null
                    && list.get(i).wkMsg.baseContentMsgModel.reply != null
                    && !TextUtils.isEmpty(list.get(i).wkMsg.baseContentMsgModel.reply.message_id)) {
                if (list.get(i).wkMsg.baseContentMsgModel.reply.message_id.equals(wkMsg.messageID)) {
                    list.get(i).wkMsg.baseContentMsgModel.reply.revoke = wkMsg.remoteExtra.revoke;
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }
}
