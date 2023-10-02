package com.chat.uikit.chat;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.AvatarOtherViewMenu;
import com.chat.base.endpoint.entity.RTCMenu;
import com.chat.base.endpoint.entity.ReadMsgMenu;
import com.chat.base.endpoint.entity.SetChatBgMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.entity.UserOnlineStatus;
import com.chat.base.entity.WKChannelCustomerExtras;
import com.chat.base.entity.WKGroupType;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.NumberTextView;
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.UserUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKPlaySound;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.CommonAnim;
import com.chat.base.views.RecyclerAnimationScrollHelper;
import com.chat.base.views.keyboard.KeyboardHelper;
import com.chat.base.views.keyboard.PanelType;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.chat.manager.SendMsgEntity;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.chat.manager.WKSendMsgUtils;
import com.chat.uikit.chat.msgmodel.WKCardContent;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.databinding.ActChatLayoutBinding;
import com.chat.uikit.group.ChooseVideoCallMembersActivity;
import com.chat.uikit.group.GroupDetailActivity;
import com.chat.uikit.group.WKAllMembersActivity;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.robot.service.WKRobotModel;
import com.chat.uikit.view.ChatInputPanel;
import com.chat.uikit.view.WKPlayVoiceUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKConversationMsgExtra;
import com.xinbida.wukongim.entity.WKMentionType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgReaction;
import com.xinbida.wukongim.entity.WKMsgSetting;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.interfaces.IGetOrSyncHistoryMsgBack;
import com.xinbida.wukongim.message.type.WKSendMsgResult;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;
import com.xinbida.wukongim.msgmodel.WKReply;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 2019-11-12 15:36
 * 会话聊天页面
 */
public class ChatActivity extends WKBaseActivity<ActChatLayoutBinding> implements IConversationContext {
    private ChatAdapter chatAdapter;
    //频道ID
    private String channelId;
    //频道类型
    private byte channelType;
    //是否在查看历史消息
    private boolean isShowHistory;
    private boolean isToEnd = true;
    private boolean isViewingPicture = false;
    private boolean showNickName = true;// 是否显示聊天昵称
    private long lastPreviewMsgOrderSeq = 0;//上次浏览消息
    private long unreadStartMsgOrderSeq = 0;//新消息开始位置
    private long tipsOrderSeq = 0;//需要强提示的msg
    private int keepOffsetY = 0; // 上次浏览消息的偏移量
    private int redDot = 0;// 未读消息数量
    private int lastVisibleMsgSeq = 0;// 最后可见消息序号
    private int maxMsgSeq = 0;
    //回复的消息对象
    private WKMsg replyWKMsg;
    // 编辑对象
    private WKMsg editMsg;
    // 群成员数量
    private int count;
    private int groupType = WKGroupType.normalGroup;
    //已读消息ID
    private final List<String> readMsgIds = new ArrayList<>();
    private Disposable disposable;
    private boolean isUploadReadMsg = true;
    private NumberTextView numberTextView;
    private int signal;
    //    boolean isUpdateCoverMsg = false;
    private boolean isCanLoadMore;
    boolean isRefreshLoading = false;
    boolean isMoreLoading = false;
    boolean isCanRefresh = true;

    KeyboardHelper keyboardHelper;
    LinearLayoutManager linearLayoutManager;
    //    private ScrollHelper scrollHelper;
    private final List<WKReminder> reminderList = new ArrayList<>();
    private final List<WKReminder> groupApproveList = new ArrayList<>();
    private final List<Long> reminderIds = new ArrayList<>();
    private long browseTo = 0;
    private boolean isUpdateRedDot = true;
    private ImageView callIV;
    //查询聊天数据偏移量
    private final int limit = 20;
    private RecyclerAnimationScrollHelper scrollHelper;

    @Override
    protected void setTitle(TextView titleTv) {
    }

    @Override
    protected ActChatLayoutBinding getViewBinding() {
        return ActChatLayoutBinding.inflate(getLayoutInflater());
    }

    private void p2pCall(int callType) {
        EndpointManager.getInstance().invoke("wk_p2p_call", new RTCMenu(this, callType));
    }

    @Override
    protected void initPresenter() {
        //频道ID
        channelId = getIntent().getStringExtra("channelId");
        //频道类型
        channelType = getIntent().getByteExtra("channelType", WKChannelType.PERSONAL);

        maxMsgSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
        // 是否含有带转发的消息
        if (getIntent().hasExtra("msgContentList")) {
            List<WKMessageContent> msgContentList = getIntent().getParcelableArrayListExtra("msgContentList");
            if (msgContentList != null && msgContentList.size() > 0) {
                List<WKChannel> list = new ArrayList<>();
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
                list.add(channel);
                WKUIKitApplication.getInstance().showChatConfirmDialog(this, list, msgContentList, (list1, messageContentList) -> {
                    List<SendMsgEntity> msgList = new ArrayList<>();
                    WKMsgSetting setting = new WKMsgSetting();
                    //setting.signal = signal;
                    setting.receipt = getChatChannelInfo().receipt;
                    for (int i = 0, size = msgContentList.size(); i < size; i++) {
                        msgList.add(new SendMsgEntity(msgContentList.get(i), new WKChannel(channelId, channelType), setting));
                    }
                    WKSendMsgUtils.getInstance().sendMessages(msgList);
                });

            }
        }

        String desc = String.format(getString(R.string.microphone_permissions_des), getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {

                }

                @Override
                public void clickResult(boolean isCancel) {
                    finish();
                }
            }, this, desc, Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO);

        } else {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {

                }

                @Override
                public void clickResult(boolean isCancel) {
                    finish();
                }
            }, this, desc, Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        }
    }

    public int getLastItemToBottom() {
        if (chatAdapter.getData().size() > 0) {
            int distance = 0;
            if (linearLayoutManager != null) {
                View lastItem = linearLayoutManager.findViewByPosition(linearLayoutManager.findLastVisibleItemPosition());
                if (lastItem != null) {
                    distance = wkVBinding.recyclerViewLayout.getHeight() - lastItem.getBottom() - AndroidUtilities.dp(5);
                }
            }
            return Math.max(distance, 0);
        } else {
            return wkVBinding.recyclerViewLayout.getHeight();
        }
    }


    @Override
    protected void initView() {

        EndpointManager.getInstance().invoke("set_chat_bg", new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootLayout, wkVBinding.blurView));
        wkVBinding.timeTv.setShadowLayer(AndroidUtilities.dp(5f), 0f, 0f, 0);
        CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, true);
        Theme.setPressedBackground(wkVBinding.topLayout.backIv);
        wkVBinding.topLayout.backIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.titleBarIcon), PorterDuff.Mode.MULTIPLY));
        wkVBinding.topLayout.avatarView.setSize(40);
        int h = WKConstants.getKeyboardHeight();
        keyboardHelper = new KeyboardHelper();
        keyboardHelper.init(this).bindInputPanel(wkVBinding.chatInputPanel).bindRecyclerView(wkVBinding.recyclerView).bindMorePanel(wkVBinding.morePanel).bindRootLayout(wkVBinding.viewGroupLayout).setScrollBodyLayout(true).setKeyboardHeight(h).setOnKeyboardStateListener(new KeyboardHelper.OnKeyboardStateListener() {
            @Override
            public void onOpened(int keyboardHeight) {
                WKConstants.setKeyboardHeight(keyboardHeight);
            }

            @Override
            public void onClosed() {

            }

            @Override
            public int getLastItemToBottom() {
//                        return wkVBinding.chatInputPanel.lastBottom(wkVBinding.recyclerView);
                return ChatActivity.this.getLastItemToBottom();
            }
        });


        wkVBinding.chatInputPanel.initView(ChatActivity.this, wkVBinding.recyclerViewContentLayout, wkVBinding.chatUnreadLayout.allUnreadView, wkVBinding.morePanel);
        wkVBinding.chatUnreadLayout.msgCountTv.setColors(R.color.white, R.color.reminderColor);
        wkVBinding.chatUnreadLayout.remindCountTv.setColors(R.color.white, R.color.reminderColor);
        wkVBinding.chatUnreadLayout.approveCountTv.setColors(R.color.white, R.color.reminderColor);

        numberTextView = new NumberTextView(this);
        numberTextView.setTextSize(18);
        numberTextView.setTextColor(Theme.colorAccount);
        wkVBinding.topLayout.rightView.addView(numberTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END, 0, 0, 15, 0));

        callIV = new AppCompatImageView(this);
        callIV.setImageResource(R.mipmap.ic_call);
        wkVBinding.topLayout.rightView.addView(callIV, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END, 0, 0, 15, 0));
        callIV.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.popupTextColor), PorterDuff.Mode.MULTIPLY));
        callIV.setBackground(Theme.createSelectorDrawable(Theme.getPressedColor()));

        CommonAnim.getInstance().showOrHide(numberTextView, false, false);

        //去除刷新条目闪动动画
        ((DefaultItemAnimator) Objects.requireNonNull(wkVBinding.recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
        chatAdapter = new ChatAdapter(this);
        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        wkVBinding.recyclerView.setLayoutManager(linearLayoutManager);
        wkVBinding.recyclerView.setAdapter(chatAdapter);
        chatAdapter.setAnimationFirstOnly(true);
        chatAdapter.setAnimationEnable(false);
        scrollHelper = new RecyclerAnimationScrollHelper(wkVBinding.recyclerView, linearLayoutManager);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN)
            EndpointManager.getInstance().invoke("chat_activity_touch", null);
        return super.dispatchTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        wkVBinding.recyclerView.setOnTouchListener((v, event) -> {
            keyboardHelper.reset();
            return false;
        });
        MessageSwipeController controller = new MessageSwipeController(this, new SwipeControllerActions() {
            @Override
            public void showReplyUI(int position) {
                showReply(chatAdapter.getData().get(position).wkMsg);
            }

            @Override
            public void hideSoft() {
                wkVBinding.recyclerView.setOnTouchListener((v, event) -> {
                    keyboardHelper.reset();
                    return false;
                });
            }
        });
        ItemTouchHelper helper = new ItemTouchHelper(controller);
        helper.attachToRecyclerView(wkVBinding.recyclerView);
        wkVBinding.topLayout.backIv.setOnClickListener(v -> setBackListener());
        callIV.setOnClickListener(view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
            if (getChatChannelInfo().forbidden == 1 || (member != null && member.forbiddenExpirationTime > 0)) {
                showToast(R.string.can_not_call_forbidden);
                return;
            }
            String desc = String.format(getString(R.string.microphone_permissions_des), getString(R.string.app_name));
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {
                    if (result) {
                        if (channelType == WKChannelType.PERSONAL) {
                            if (UserUtils.getInstance().checkMyFriendDelete(channelId) || UserUtils.getInstance().checkFriendRelation(channelId)) {
                                showToast(R.string.non_friend_relationship);
                                return;
                            }
                            if (UserUtils.getInstance().checkBlacklist(channelId)) {
                                showToast(R.string.call_be_blacklist);
                                return;
                            }
                            if (getChatChannelInfo().status == WKChannelStatus.statusBlacklist) {
                                showToast(R.string.call_blacklist);
                                return;
                            }
                            List<PopupMenuItem> list = new ArrayList<>();
                            list.add(new PopupMenuItem(getString(R.string.video_call), R.mipmap.chat_calls_video, () -> p2pCall(1)));
                            list.add(new PopupMenuItem(getString(R.string.audio_call), R.mipmap.chat_calls_voice, () -> p2pCall(0)));
                            WKDialogUtils.getInstance().showScreenPopup(view, list);
                        } else {
                            WKChannelMember channelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
                            if (channelMember != null && channelMember.status == WKChannelStatus.statusBlacklist) {
                                showToast(R.string.call_blacklist_group);
                                return;
                            }
                            Intent intent = new Intent(ChatActivity.this, ChooseVideoCallMembersActivity.class);
                            intent.putExtra("channelID", channelId);
                            intent.putExtra("channelType", channelType);
                            intent.putExtra("isCreate", true);
                            startActivity(intent);
                        }
                    }
                }

                @Override
                public void clickResult(boolean isCancel) {
                }
            }, this, desc, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);


        });

        wkVBinding.topLayout.subtitleView.setOnClickListener(view1 -> {
            if (channelType == WKChannelType.GROUP) {
                WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
                if (member == null || member.isDeleted == 1) return;
                Intent intent = new Intent(this, WKAllMembersActivity.class);
                intent.putExtra("channelID", channelId);
                intent.putExtra("channelType", WKChannelType.GROUP);
                startActivity(intent);
            }
        });
        WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.chatUnreadLayout.groupApproveLayout, getGroupApprovePopupItems());
        wkVBinding.chatUnreadLayout.groupApproveLayout.setOnClickListener(view -> {
            if (groupApproveList.size() > 0) {
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(groupApproveList.get(0).messageID);
                if (msg != null && !TextUtils.isEmpty(msg.clientMsgNO)) {
                    tipsMsg(msg.clientMsgNO);
                }
            }
        });
        WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.chatUnreadLayout.remindLayout, getRemindPopupItems());
        wkVBinding.chatUnreadLayout.remindLayout.setOnClickListener(view -> {

            if (reminderList.size() > 0) {
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithMessageID(reminderList.get(0).messageID);
                if (msg != null && !TextUtils.isEmpty(msg.clientMsgNO)) {
                    tipsMsg(msg.clientMsgNO);
                } else {
                    long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(reminderList.get(0).messageSeq, channelId, channelType);
                    unreadStartMsgOrderSeq = 0;
                    tipsOrderSeq = orderSeq;
                    getData(1, true, orderSeq, false);
                    isCanLoadMore = true;
                }
            }
        });

        wkVBinding.chatInputPanel.addInputPanelListener(new ChatInputPanel.IInputPanelListener() {
            @Override
            public void onResetTitleView() {
                CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_ab_back);
                numberTextView.setNumber(0, true);
                CommonAnim.getInstance().showOrHide(numberTextView, false, true);
                CommonAnim.getInstance().showOrHide(callIV, true, true);
            }

            @Override
            public void previewNewImg(@NonNull String path) {
                Intent intent = new Intent(ChatActivity.this, PreviewNewImgActivity.class);
                intent.putExtra("path", path);
                previewNewImgResultLac.launch(intent);
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.topLayout.titleView, view -> {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());

            if ((member != null && member.isDeleted == 1) || channelType == WKChannelType.CUSTOMER_SERVICE)
                return;
//              SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.toolbarView.editText);
            Intent intent = new Intent(ChatActivity.this, channelType == WKChannelType.GROUP ? GroupDetailActivity.class : ChatPersonalActivity.class);
            intent.putExtra("channelId", channelId);
            startActivity(intent);
        });

        wkVBinding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                setShowTime();

                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                if (lastItemPosition < chatAdapter.getItemCount() - 1) {
                    wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, dy > 0 || redDot > 0, true, true));
                } else {
                    wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, redDot > 0, true, true));
                }
                resetRemindView();
                resetGroupApproveView();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int lastItemPosition = linearLayoutManager.findLastVisibleItemPosition();
                isShowHistory = lastItemPosition < chatAdapter.getItemCount() - 1;
                if (newState == SCROLL_STATE_IDLE) {
                    CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, false, true);
                    EndpointManager.getInstance().invoke("stop_reaction_animation", null);
                    if (!wkVBinding.recyclerView.canScrollVertically(1)) { // 到达底部
                        showMoreLoading();
                    } else if (!wkVBinding.recyclerView.canScrollVertically(-1)) { // 到达顶部
                        showRefreshLoading();
                    }
                } else {
                    MsgModel.getInstance().doneReminder(reminderIds);
                    if (!isUpdateRedDot) return;
                    MsgModel.getInstance().clearUnread(channelId, channelType, redDot, (code, msg) -> {
                        if (code == HttpResponseCode.success && redDot == 0) {
                            isUpdateRedDot = false;
                        }
                    });
                }
            }
        });

        wkVBinding.chatUnreadLayout.newMsgLayout.setOnClickListener(v -> {
            redDot = 0;
            MsgModel.getInstance().clearUnread(channelId, channelType, redDot, (code, msg) -> {
                if (code == HttpResponseCode.success && redDot == 0) {
                    isUpdateRedDot = false;
                }
            });
            if (isCanLoadMore) {
                chatAdapter.setList(new ArrayList<>());
                unreadStartMsgOrderSeq = 0;
                lastPreviewMsgOrderSeq = 0;
                getData(0, true, 0, true);
            } else {
                scrollToPosition(chatAdapter.getItemCount() - 1);
            }
            showUnReadCountView();
            isShowHistory = false;
            isCanLoadMore = false;
        });

        //监听频道改变通知
        WKIM.getInstance().getChannelManager().addOnRefreshChannelInfo(channelId, (channel, isEnd) -> {
            if (channel == null) return;
            if (channel.channelID.equals(channelId) && channel.channelType == channelType) { //同一个会话
                showChannelName(channel);
                wkVBinding.topLayout.avatarView.showAvatar(channel);
                EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(wkVBinding.topLayout.otherLayout, channel, wkVBinding.topLayout.avatarView, true));
                //用户在线状态
                if (channel.channelType == WKChannelType.PERSONAL) {
                    setOnlineView(channel);
                } else {
                    if (channel.remoteExtraMap != null) {
                        Object memberCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.memberCount);
                        if (memberCountObject instanceof Integer) {
                            int count = (int) memberCountObject;
                            wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                        }
                        Object onlineCountObject = channel.remoteExtraMap.get(WKChannelCustomerExtras.onlineCount);
                        if (onlineCountObject instanceof Integer) {
                            int onlineCount = (int) onlineCountObject;
                            if (onlineCount > 0) {
                                wkVBinding.topLayout.subtitleCountTv.setVisibility(View.VISIBLE);
                                wkVBinding.topLayout.subtitleCountTv.setText(String.format(getString(R.string.online_count), onlineCount));
                            }
                        }
                    }
                }
                //判断是否显示聊天昵称
//                boolean showNick = channel.showNick == 1;
//                if (showNickName != showNick) {
//                    showNickName = showNick;
//                    for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
//                        chatAdapter.getData().get(i).showNickName = showNickName;
//                    }
//                    chatAdapter.notifyItemRangeChanged(0, chatAdapter.getItemCount());
//                }
                EndpointManager.getInstance().invoke("set_chat_bg", new SetChatBgMenu(channelId, channelType, wkVBinding.imageView, wkVBinding.rootLayout, wkVBinding.blurView));
            } else {
                for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                    if (TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.fromUID)) continue;
                    boolean isRefresh = false;
                    if (chatAdapter.getData().get(i).wkMsg.fromUID.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                        chatAdapter.getData().get(i).wkMsg.setFrom(channel);
                        isRefresh = true;
                    }
                    if (chatAdapter.getData().get(i).wkMsg.getMemberOfFrom() != null && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID.equals(channel.channelID) && channel.channelType == WKChannelType.PERSONAL) {
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberRemark = channel.channelRemark;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberName = channel.channelName;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatar = channel.avatar;
                        chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatarCacheKey = channel.avatarCacheKey;
                        isRefresh = true;
                    }
                    if (chatAdapter.getData().get(i).wkMsg.baseContentMsgModel != null && chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities != null && chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities.size() > 0) {
                        for (WKMsgEntity entity : chatAdapter.getData().get(i).wkMsg.baseContentMsgModel.entities) {
                            if (entity.type.equals(ChatContentSpanType.getMention()) && !TextUtils.isEmpty(entity.value) && entity.value.equals(channel.channelID)) {
                                isRefresh = true;
                                chatAdapter.getData().get(i).formatSpans(ChatActivity.this, chatAdapter.getData().get(i).wkMsg);
                                break;
                            }
                        }
                    }
                    if (isRefresh) {
                        chatAdapter.notifyItemChanged(i);
                    }
                }

            }
        });

        //监听频道成员信息改变通知
        WKIM.getInstance().getChannelMembersManager().addOnRefreshChannelMemberInfo(channelId, (channelMember, isEnd) -> {
            if (channelMember != null && !TextUtils.isEmpty(channelMember.channelID)) {
                if (channelMember.channelID.equals(channelId) && channelMember.channelType == channelType) {
                    if (channelMember.channelType == WKChannelType.PERSONAL) {
                        String name = channelMember.memberRemark;
                        if (TextUtils.isEmpty(name)) name = channelMember.memberName;
                        wkVBinding.topLayout.titleCenterTv.setText(name);
                    } else {
                        //成员名字改变
                        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                            if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom() != null && !TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID) && chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberUID.equals(channelMember.memberUID)) {
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberName = channelMember.memberName;
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberRemark = channelMember.memberRemark;
                                chatAdapter.getData().get(i).wkMsg.getMemberOfFrom().memberAvatar = channelMember.memberAvatar;
                                chatAdapter.notifyItemChanged(i);
                            }
                        }
                    }
                }
            }
            if (isEnd) {
                checkLoginUserInGroupStatus();
            }
        });

        //监听移除频道成员
        WKIM.getInstance().getChannelMembersManager().addOnRemoveChannelMemberListener(channelId, list -> {
            if (list != null && list.size() > 0 && !TextUtils.isEmpty(list.get(0).channelID) && list.get(0).channelID.equals(channelId) && list.get(0).channelType == channelType) {
                if (groupType == WKGroupType.normalGroup) {
                    count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                    wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                }
                //查询登录用户是否在本群
                checkLoginUserInGroupStatus();
                WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
            }
        });
        //监听添加频道成员
        WKIM.getInstance().getChannelMembersManager().addOnAddChannelMemberListener(channelId, list -> {
            if (list != null && list.size() > 0 && !TextUtils.isEmpty(list.get(0).channelID) && list.get(0).channelID.equals(channelId) && list.get(0).channelType == channelType && groupType == WKGroupType.normalGroup) {
                count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
                WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
                checkLoginUserInGroupStatus();
            }
        });
        //监听删除消息
        WKIM.getInstance().getMsgManager().addOnDeleteMsgListener(channelId, msg -> {
            if (msg != null) {
                removeMsg(msg);
            }
        });
        // 命令消息监听
        WKIM.getInstance().getCMDManager().addCmdListener(channelId, wkCmd -> {
            if (wkCmd == null || TextUtils.isEmpty(wkCmd.cmdKey)) return;
            // 监听正在输入
            if (wkCmd.cmdKey.equals(WKCMDKeys.wk_typing)) {
                if (redDot > 0) return;
                String channel_id = wkCmd.paramJsonObject.optString("channel_id");
                byte channel_type = (byte) wkCmd.paramJsonObject.optInt("channel_type");
                String from_uid = wkCmd.paramJsonObject.optString("from_uid");
                String from_name = wkCmd.paramJsonObject.optString("from_name");
                int isRobot;
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(from_uid, WKChannelType.PERSONAL);
                if (channel == null) {
                    channel = new WKChannel(from_uid, WKChannelType.PERSONAL);
                    channel.channelName = from_name;
                }
                isRobot = channel.robot;
                if (channelId.equals(channel_id) && channelType == channel_type && !TextUtils.equals(from_uid, WKConfig.getInstance().getUid())) {
                    WKChannelMember mChannelMember = null;
                    if (channelType == WKChannelType.GROUP && isRobot == 0) {
                        // 没在群内的cmd不显示
                        mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, from_uid);
                        if (mChannelMember == null || mChannelMember.isDeleted == 1) return;
                    }
                    if (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.type == WKContentType.typing) {
                        chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.setFrom(channel);
                        chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.fromUID = from_uid;
                        chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.setMemberOfFrom(mChannelMember);
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    } else {
                        addTimeMsg(WKTimeUtils.getInstance().getCurrentSeconds());
                        int index = chatAdapter.getData().size() - 1;
                        if (chatAdapter.lastMsgIsTyping()) index--;
                        if (index < 0) index = 0;

                        WKUIChatMsgItemEntity msgItemEntity = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                        msgItemEntity.wkMsg.channelType = channelType;
                        msgItemEntity.wkMsg.channelID = channelId;
                        msgItemEntity.wkMsg.type = WKContentType.typing;
                        msgItemEntity.wkMsg.setFrom(channel);
                        msgItemEntity.showNickName = showNickName;
                        msgItemEntity.wkMsg.fromUID = channel.channelID;
                        WKChannelMember member = new WKChannelMember();
                        member.memberUID = channel.channelID;
                        member.channelID = channelId;
                        member.channelType = channelType;
                        member.memberName = channel.channelName;
                        member.memberRemark = channel.channelRemark;
                        msgItemEntity.wkMsg.setMemberOfFrom(member);
                        msgItemEntity.previousMsg = chatAdapter.getLastMsg();
                        chatAdapter.addData(msgItemEntity);
                        chatAdapter.getData().get(index).nextMsg = msgItemEntity.wkMsg;

                        int type = chatAdapter.getData().get(index).wkMsg.type;
                        if (WKContentType.isLocalMsg(type) || WKContentType.isSystemMsg(type)) {
                            chatAdapter.notifyItemChanged(index);
                        } else {
                            chatAdapter.notifyBackground(index);
                        }

                        if (!isShowHistory && !isCanLoadMore) {
                            scrollToEnd();
                        }
                    }
                }
            }
        });

        //监听消息刷新
        WKIM.getInstance().getMsgManager().addOnRefreshMsgListener(channelId, (wkMsg, left) -> {
            if (wkMsg.remoteExtra.isMutualDeleted == 1) {
                removeMsg(wkMsg);
                return;
            }

            WKIMUtils.getInstance().resetMsgProhibitWord(wkMsg);
            List<WKUIChatMsgItemEntity> list = chatAdapter.getData();
            for (int i = 0, size = list.size(); i < size; i++) {
                boolean isNotify = false;
                if (list.get(i).wkMsg != null && (list.get(i).wkMsg.clientSeq == wkMsg.clientSeq || list.get(i).wkMsg.clientMsgNO.equals(wkMsg.clientMsgNO))) {
                    if (wkMsg.messageSeq > maxMsgSeq) {
                        maxMsgSeq = wkMsg.messageSeq;
                    }
                    if (wkMsg.messageSeq > lastVisibleMsgSeq) {
                        lastVisibleMsgSeq = wkMsg.messageSeq;
                    }
                    if (list.get(i).wkMsg.remoteExtra.revoke != wkMsg.remoteExtra.revoke) {
                        isNotify = true;
                    }
                    if (list.get(i).wkMsg.remoteExtra.revoke != wkMsg.remoteExtra.revoke) {
                        // 消息撤回
                        chatAdapter.updateReplyMsgRevoke(wkMsg);
                    }
                    list.get(i).wkMsg.remoteExtra.revoke = wkMsg.remoteExtra.revoke;
                    list.get(i).wkMsg.remoteExtra.revoker = wkMsg.remoteExtra.revoker;
                    if (list.get(i).wkMsg.status != WKSendMsgResult.send_success && wkMsg.status == WKSendMsgResult.send_success) {
                        WKPlaySound.getInstance().playOutMsg(R.raw.sound_out);
                    }
                    boolean isResetStatus = false;
                    boolean isResetData = false;
                    boolean isResetReaction = false;
                    if (list.get(i).wkMsg.status != wkMsg.status || (list.get(i).wkMsg.remoteExtra.readedCount != wkMsg.remoteExtra.readedCount && list.get(i).wkMsg.remoteExtra.readedCount == 0) || list.get(i).wkMsg.remoteExtra.editedAt != wkMsg.remoteExtra.editedAt) {
                        list.get(i).isUpdateStatus = true;
                        isResetStatus = true;
                    }

                    list.get(i).wkMsg.voiceStatus = wkMsg.voiceStatus;
                    list.get(i).wkMsg.remoteExtra.readed = wkMsg.remoteExtra.readed;
                    list.get(i).wkMsg.remoteExtra.readedCount = wkMsg.remoteExtra.readedCount;
                    list.get(i).wkMsg.remoteExtra.needUpload = wkMsg.remoteExtra.needUpload;
                    if (list.get(i).wkMsg.remoteExtra.readedCount == 0) {
                        list.get(i).wkMsg.remoteExtra.unreadCount = count - 1;
                    } else
                        list.get(i).wkMsg.remoteExtra.unreadCount = wkMsg.remoteExtra.unreadCount;
                    if ((TextUtils.isEmpty(list.get(i).wkMsg.remoteExtra.contentEdit) && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEdit)) || (!TextUtils.isEmpty(list.get(i).wkMsg.remoteExtra.contentEdit) && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEdit) && !list.get(i).wkMsg.remoteExtra.contentEdit.equals(wkMsg.remoteExtra.contentEdit))) {
                        list.get(i).wkMsg.remoteExtra.editedAt = wkMsg.remoteExtra.editedAt;
                        list.get(i).wkMsg.remoteExtra.contentEdit = wkMsg.remoteExtra.contentEdit;
                        list.get(i).wkMsg.remoteExtra.contentEditMsgModel = wkMsg.remoteExtra.contentEditMsgModel;
                        list.get(i).isUpdateStatus = true;
                        list.get(i).formatSpans(ChatActivity.this, chatAdapter.getData().get(i).wkMsg);
                        isResetData = true;
                    }

                    list.get(i).wkMsg.isDeleted = wkMsg.isDeleted;
                    list.get(i).wkMsg.messageID = wkMsg.messageID;
                    list.get(i).wkMsg.messageSeq = wkMsg.messageSeq;
                    list.get(i).wkMsg.orderSeq = wkMsg.orderSeq;
                    if ((wkMsg.localExtraMap != null && wkMsg.localExtraMap.size() > 0)) {
                        isNotify = true;
                    }
                    if (isRefreshReaction(list.get(i).wkMsg.reactionList, wkMsg.reactionList)) {
                        isResetReaction = true;
                    }
                    list.get(i).wkMsg.localExtraMap = wkMsg.localExtraMap;
                    list.get(i).wkMsg.content = wkMsg.content;
                    list.get(i).wkMsg.reactionList = wkMsg.reactionList;
                    list.get(i).wkMsg.baseContentMsgModel = wkMsg.baseContentMsgModel;
                    list.get(i).wkMsg.status = wkMsg.status;
                    if (isNotify) {
                        EndpointManager.getInstance().invoke("stop_reaction_animation", null);
                        chatAdapter.notifyItemChanged(i);
                    } else {
                        if (isResetStatus) {
                            chatAdapter.notifyStatus(i);
                        }
                        if (isResetData) {
                            chatAdapter.notifyData(i);
                        }
                        if (isResetReaction) {
                            list.get(i).isRefreshReaction = true;
                            chatAdapter.notifyItemChanged(i, list.get(i));
                            //chatAdapter.notifyReaction(i, wkMsg.reactionList);
                        }
                    }

                    if (list.get(i).wkMsg.remoteExtra.revoke == 1) {
                        int previousIndex = i - 1;
                        int nextIndex = i + 1;
                        if (previousIndex >= 0 && list.get(previousIndex).wkMsg.remoteExtra.revoke == 0) {
                            chatAdapter.notifyItemChanged(previousIndex);
                        }
                        if (nextIndex <= chatAdapter.getData().size() - 1 && list.get(nextIndex).wkMsg.remoteExtra.revoke == 0) {
                            chatAdapter.notifyItemChanged(nextIndex);
                        }
                    }

                    if ((wkMsg.status == WKSendMsgResult.no_relation || wkMsg.status == WKSendMsgResult.not_on_white_list) && channelType == WKChannelType.PERSONAL) {
                        if (UserUtils.getInstance().checkBlacklist(channelId)) {
                            return;
                        }
                        // 不是好友
                        WKMsg noRelationMsg = new WKMsg();
                        noRelationMsg.channelID = channelId;
                        noRelationMsg.channelType = channelType;
                        noRelationMsg.type = WKContentType.noRelation;
                        long tempOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(0, wkMsg.channelID, wkMsg.channelType);
                        noRelationMsg.orderSeq = tempOrderSeq + 1;
                        noRelationMsg.status = WKSendMsgResult.send_success;

                        int index = chatAdapter.getData().size() - 1;
                        if (chatAdapter.lastMsgIsTyping()) index--;
                        WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(this, noRelationMsg, count, showNickName, chatAdapter.isShowChooseItem());
                        chatAdapter.getData().get(index).nextMsg = noRelationMsg;
                        itemEntity.previousMsg = chatAdapter.getData().get(index).wkMsg;

                        chatAdapter.notifyItemChanged(index);
                        chatAdapter.addData(index + 1, itemEntity);
                        if (isToEnd) {
                            scrollToEnd();
                        }
                        WKIM.getInstance().getMsgManager().saveMsg(noRelationMsg);
                    }

                    break;
                }
            }
            if (wkMsg.remoteExtra.editedAt != 0 && !TextUtils.isEmpty(wkMsg.messageID)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    if (list.get(i).wkMsg.baseContentMsgModel != null && list.get(i).wkMsg.baseContentMsgModel.reply != null && !TextUtils.isEmpty(list.get(i).wkMsg.baseContentMsgModel.reply.message_id) && list.get(i).wkMsg.baseContentMsgModel.reply.message_id.equals(wkMsg.messageID)) {
                        list.get(i).wkMsg.baseContentMsgModel.reply.contentEditMsgModel = wkMsg.remoteExtra.contentEditMsgModel;
                        list.get(i).wkMsg.baseContentMsgModel.reply.contentEdit = wkMsg.remoteExtra.contentEdit;
                        list.get(i).wkMsg.baseContentMsgModel.reply.editAt = wkMsg.remoteExtra.editedAt;
                        chatAdapter.notifyItemChanged(i);
                    }
                }
            }

        });
        //监听发送消息返回
        WKIM.getInstance().getMsgManager().addOnSendMsgCallback(channelId, msg -> {
            if (msg.channelType == channelType && msg.channelID.equals(channelId) && msg.isDeleted == 0 && !msg.header.noPersist) {
                WKMsg timeMsg = addTimeMsg(msg.timestamp);
                //判断当前会话是否存在正在输入
                int index = chatAdapter.getData().size() - 1;
                if (chatAdapter.lastMsgIsTyping()) index--;
                if (index < 0) index = 0;
                WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(this, msg, count, showNickName, chatAdapter.isShowChooseItem());
                if (timeMsg == null) {
                    if (chatAdapter.getData().size() != 0) {
                        chatAdapter.getData().get(index).nextMsg = msg;
                        itemEntity.previousMsg = chatAdapter.getData().get(index).wkMsg;
                    }
                } else {
                    chatAdapter.getData().get(index).nextMsg = timeMsg;
                    itemEntity.previousMsg = timeMsg;
                }
                chatAdapter.addData(index + 1, itemEntity);
                int type = chatAdapter.getData().get(index).wkMsg.type;
                if (WKContentType.isLocalMsg(type) || WKContentType.isSystemMsg(type)) {
                    chatAdapter.notifyItemChanged(index);
                } else {
                    chatAdapter.notifyBackground(index);
                }

                if (isToEnd) {
                    scrollToEnd();
                }
                isToEnd = true;
            }
        });

        //监听新消息
        WKIM.getInstance().getMsgManager().addOnNewMsgListener(channelId, list -> {
            if (list != null && list.size() > 0) {
                for (WKMsg msg : list) {
                    // 命令消息和撤回消息不显示在聊天
                    if (msg.type == WKContentType.WK_INSIDE_MSG || msg.type == WKContentType.withdrawSystemInfo || msg.isDeleted == 1 || msg.header.noPersist)
                        continue;

                    if (msg.remoteExtra.readedCount == 0) {
                        msg.remoteExtra.unreadCount = count - 1;
                    }

                    if (msg.channelID.equals(channelId) && msg.channelType == channelType) {
                        if (!chatAdapter.isExist(msg.clientMsgNO)) {
                            if (!isCanLoadMore) {
                                //移除正在输入
                                if (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg != null && chatAdapter.getData().get(chatAdapter.getItemCount() - 1).wkMsg.type == WKContentType.typing) {
                                    chatAdapter.removeAt(chatAdapter.getItemCount() - 1);
                                }
                                WKMsg timeMsg = addTimeMsg(msg.timestamp);
                                WKUIChatMsgItemEntity itemEntity = WKIMUtils.getInstance().msg2UiMsg(this, msg, count, showNickName, chatAdapter.isShowChooseItem());
                                if (timeMsg != null && chatAdapter.getData().size() > 1) {
                                    chatAdapter.getData().get(chatAdapter.getData().size() - 2).nextMsg = timeMsg;
                                }
                                int previousMsgIndex = -1;
                                if (timeMsg == null) {
                                    if (chatAdapter.getData().size() > 0) {
                                        itemEntity.previousMsg = chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg;
                                        chatAdapter.getData().get(chatAdapter.getData().size() - 1).nextMsg = itemEntity.wkMsg;
                                    }
                                } else {
                                    itemEntity.previousMsg = timeMsg;
                                }
                                if (chatAdapter.getData().size() > 0) {
                                    previousMsgIndex = chatAdapter.getData().size() - 1;
                                }
                                if (!isShowHistory && redDot == 0 && itemEntity.wkMsg.flame == 1 && itemEntity.wkMsg.type != WKContentType.WK_VOICE && itemEntity.wkMsg.type != WKContentType.WK_IMAGE && itemEntity.wkMsg.type != WKContentType.WK_VIDEO) {
                                    itemEntity.wkMsg.viewed = 1;
                                    itemEntity.wkMsg.viewedAt = WKTimeUtils.getInstance().getCurrentMills();
                                    WKIM.getInstance().getMsgManager().updateViewedAt(1, itemEntity.wkMsg.viewedAt, itemEntity.wkMsg.clientMsgNO);
                                }
                                WKPlaySound.getInstance().playInMsg(R.raw.sound_in);
                                chatAdapter.addData(itemEntity);
                                if (msg.messageSeq > maxMsgSeq) {
                                    maxMsgSeq = msg.messageSeq;
                                }
                                if (previousMsgIndex != -1) {
                                    chatAdapter.notifyBackground(previousMsgIndex);
                                }
                            }
                            if (isShowHistory || redDot > 0) {
                                redDot += 1;
                                showUnReadCountView();
                            } else {
                                scrollToEnd();
                                if (msg.setting.receipt == 1) readMsgIds.add(msg.messageID);
                            }
                        }
                    }

                }
            }
        });
        //监听清空聊天记录
        WKIM.getInstance().getMsgManager().addOnClearMsgListener(channelId, (channelID, channelType, fromUID) -> {
            if (!TextUtils.isEmpty(channelID) && ChatActivity.this.channelId.equals(channelID) && ChatActivity.this.channelType == channelType) {
                if (TextUtils.isEmpty(fromUID)) {
                    chatAdapter = new ChatAdapter(ChatActivity.this);
                    wkVBinding.recyclerView.setAdapter(chatAdapter);
                } else {
                    for (int i = 0; i < chatAdapter.getData().size(); i++) {
                        if (chatAdapter.getData().get(i).wkMsg != null && !TextUtils.isEmpty(chatAdapter.getData().get(i).wkMsg.fromUID) && chatAdapter.getData().get(i).wkMsg.fromUID.equals(fromUID)) {
                            chatAdapter.removeAt(i);
                            i--;
                        }
                    }
                }
            }

        });
        WKIM.getInstance().getReminderManager().addOnNewReminderListener(channelId, this::resetReminder);
        EndpointManager.getInstance().setMethod(channelId, EndpointCategory.wkExitChat, object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                if (channelId.equals(channel.channelID) && channel.channelType == channelType) {
                    finish();
                }
            }
            return null;
        });

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        initPresenter();
        initData();
    }

    @Override
    protected void initData() {
        super.initData();
        startTimer();
        //获取网络频道信息
        WKIM.getInstance().getChannelManager().fetchChannelInfo(channelId, channelType);
        MsgModel.getInstance().syncExtraMsg(channelId, channelType);
        MsgModel.getInstance().syncReaction(channelId, channelType);
        WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
        WKCommonModel.getInstance().getChannelState(channelId, channelType, channelState -> {
            if (channelState != null) {
                signal = channelState.signal_on;
                wkVBinding.chatInputPanel.setSignal(signal);
                if (channelType == WKChannelType.GROUP && channelState.online_count > 0) {
                    wkVBinding.topLayout.subtitleCountTv.setVisibility(View.VISIBLE);
                    wkVBinding.topLayout.subtitleCountTv.setText(String.format(getString(R.string.online_count), channelState.online_count));
                }
            }
        });

        chatAdapter.setList(new ArrayList<>());
        if (WKSystemAccount.isSystemAccount(channelId) || channelType == WKChannelType.CUSTOMER_SERVICE) {
            CommonAnim.getInstance().showOrHide(callIV, false, false);
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);

        String avatarKey = "";
        if (channel != null) {
            wkVBinding.topLayout.categoryLayout.removeAllViews();
            avatarKey = channel.avatarCacheKey;
            if (channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
                Object object = channel.remoteExtraMap.get(WKChannelExtras.groupType);
                if (object instanceof Integer) {
                    groupType = (int) object;
                }
            }
            if (!TextUtils.isEmpty(channel.category)) {
                if (channel.category.equals(WKSystemAccount.accountCategorySystem)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.official), ContextCompat.getColor(this, R.color.transparent), ContextCompat.getColor(this, R.color.reminderColor), ContextCompat.getColor(this, R.color.reminderColor)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.accountCategoryCustomerService)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.customer_service), Theme.colorAccount, ContextCompat.getColor(this, R.color.white), Theme.colorAccount), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
                if (channel.category.equals(WKSystemAccount.accountCategoryVisitor)) {
                    wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.visitor), ContextCompat.getColor(this, R.color.transparent), ContextCompat.getColor(this, R.color.colorFFC107), ContextCompat.getColor(this, R.color.colorFFC107)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 0, 0));
                }
            }
            showChannelName(channel);
            if (channel.robot == 1) {
                wkVBinding.topLayout.categoryLayout.addView(Theme.getChannelCategoryTV(this, getString(R.string.bot), ContextCompat.getColor(this, R.color.colorFFC107), ContextCompat.getColor(this, R.color.white), ContextCompat.getColor(this, R.color.colorFFC107)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 1, 1, 0));
            }
            EndpointManager.getInstance().invoke("show_avatar_other_info", new AvatarOtherViewMenu(wkVBinding.topLayout.otherLayout, channel, wkVBinding.topLayout.avatarView, true));
        }
        wkVBinding.topLayout.avatarView.showAvatar(channelId, channelType, avatarKey);

        //如果是群聊就同步群成员信息
        if (channelType == WKChannelType.GROUP) {
            if (groupType == WKGroupType.normalGroup) {
                GroupModel.getInstance().groupMembersSync(channelId, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
                        hideOrShowRightView(member != null && member.isDeleted != 1);
                        WKRobotModel.getInstance().syncRobotData(getChatChannelInfo());
                        if (channel != null && channel.forbidden == 1) {
                            wkVBinding.chatInputPanel.showOrHideForbiddenView();
                        }
                        wkVBinding.chatInputPanel.showOrHideForbiddenView();
                    }
                });
            }
            //获取sdk频道信息
            if (channel != null) {
                count = WKIM.getInstance().getChannelMembersManager().getMemberCount(channelId, channelType);
                showChannelName(channel);
                // showNickName = channel.showNick == 1;
                if (channel.forbidden == 1) {
                    wkVBinding.chatInputPanel.showOrHideForbiddenView();
                }
                if (channel.status == WKChannelStatus.statusDisabled) {
                    wkVBinding.chatInputPanel.showBan();
                } else {
                    wkVBinding.chatInputPanel.hideBan();
                }
            }

            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
            hideOrShowRightView(member != null && member.isDeleted == 0);
            if (groupType == WKGroupType.normalGroup) {
                wkVBinding.topLayout.subtitleTv.setText(String.format(getString(R.string.group_member), count));
            }
            wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
            wkVBinding.chatInputPanel.showOrHideForbiddenView();
        } else {
            hideOrShowRightView(true);
            wkVBinding.topLayout.subtitleCountTv.setVisibility(View.GONE);
            if (channel != null) {
                setOnlineView(channel);
                showChannelName(channel);
            }
        }


        //定位消息
        if (getIntent().hasExtra("lastPreviewMsgOrderSeq")) {
            lastPreviewMsgOrderSeq = getIntent().getLongExtra("lastPreviewMsgOrderSeq", 0L);
            isCanLoadMore = lastPreviewMsgOrderSeq > 0;
        }
        if (getIntent().hasExtra("keepOffsetY")) {
            keepOffsetY = getIntent().getIntExtra("keepOffsetY", 0);
        }
        if (getIntent().hasExtra("redDot")) redDot = getIntent().getIntExtra("redDot", 0);
        if (getIntent().hasExtra("tipsOrderSeq")) {
            tipsOrderSeq = getIntent().getLongExtra("tipsOrderSeq", 0);
        }
        if (getIntent().hasExtra("unreadStartMsgOrderSeq")) {
            unreadStartMsgOrderSeq = getIntent().getLongExtra("unreadStartMsgOrderSeq", 0);
        }

        List<WKReminder> allReminder = WKIM.getInstance().getReminderManager().getReminders(channelId, channelType);
        if (allReminder != null && allReminder.size() > 0) {
            String loginUID = WKConfig.getInstance().getUid();
            for (WKReminder reminder : allReminder) {

                boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
                if (reminder.type == WKMentionType.WKReminderTypeMentionMe && reminder.done == 0 && !isPublisher) {
                    reminderList.add(reminder);
                }
                if (reminder.type == WKMentionType.WKApplyJoinGroupApprove && reminder.done == 0) {
                    groupApproveList.add(reminder);
                }
            }
        }
        // 先获取聊天数据
        boolean isScrollToEnd = unreadStartMsgOrderSeq == 0 && lastPreviewMsgOrderSeq == 0;
        long aroundMsgSeq = 0;
        if (unreadStartMsgOrderSeq != 0) {
            aroundMsgSeq = unreadStartMsgOrderSeq;
            isCanLoadMore = true;
        }
        isUpdateRedDot = unreadStartMsgOrderSeq > 0;
        if (lastPreviewMsgOrderSeq != 0) aroundMsgSeq = lastPreviewMsgOrderSeq;
        if (tipsOrderSeq != 0) {
            aroundMsgSeq = tipsOrderSeq;
            isCanLoadMore = true;
        }
        if (aroundMsgSeq == 0 && getIntent().hasExtra("aroundMsgSeq")) {
            aroundMsgSeq = getIntent().getLongExtra("aroundMsgSeq", 0);
        }
        getData(lastPreviewMsgOrderSeq == 0 ? 0 : 1, unreadStartMsgOrderSeq > 0, aroundMsgSeq, isScrollToEnd);

        //查询高光内容
        WKConversationMsgExtra extra = WKIM.getInstance().getConversationManager().getMsgExtraWithChannel(channelId, channelType);
        if (extra != null) {
            if (!TextUtils.isEmpty(extra.draft)) {
                wkVBinding.chatInputPanel.setEditContent(extra.draft);
            }
            browseTo = extra.browseTo;
        }
        resetRemindView();
        resetGroupApproveView();
    }

    // 获取聊天记录
    private void getData(int pullMode, boolean isSetNewData, long aroundMsgSeq, boolean isScrollToEnd) {
        boolean contain = false;
        long oldestOrderSeq;
        if (pullMode == 1) {
            oldestOrderSeq = chatAdapter.getEndMsgOrderSeq();
        } else {
            oldestOrderSeq = chatAdapter.getFirstMsgOrderSeq();
        }
        //定位消息
        if (lastPreviewMsgOrderSeq != 0) {
            contain = true;
            oldestOrderSeq = lastPreviewMsgOrderSeq;
        }
        if (unreadStartMsgOrderSeq != 0) contain = true;
        WKIM.getInstance().getMsgManager().getOrSyncHistoryMessages(channelId, channelType, oldestOrderSeq, contain, pullMode, limit, aroundMsgSeq, new IGetOrSyncHistoryMsgBack() {
            @Override
            public void onSyncing() {
                if (WKReader.isEmpty(chatAdapter.getData())) {
                    WKMsg wkMsg = new WKMsg();
                    wkMsg.type = WKContentType.loading;
                    chatAdapter.addData(new WKUIChatMsgItemEntity(ChatActivity.this, wkMsg, null));
                }
            }

            @Override
            public void onResult(List<WKMsg> list) {
                showData(list, pullMode, isSetNewData, isScrollToEnd);
                if (WKReader.isNotEmpty(chatAdapter.getData())) {
                    for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
                        if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.type == WKContentType.loading) {
                            chatAdapter.removeAt(i);
                            break;
                        }
                    }
                }
                isRefreshLoading = false;
                isMoreLoading = false;
                if (pullMode == 0) {
                    if (WKReader.isEmpty(list) || list.size() < limit)
                        isCanRefresh = false;
                } else {
                    if (WKReader.isEmpty(list) || list.size() < limit) {
                        isCanLoadMore = false;
                    }
                }
            }
        });


    }

    /**
     * 显示数据
     *
     * @param msgList       数据源
     * @param pullMode      拉取模式 0:向下拉取 1:向上拉取
     * @param isSetNewData  是否重新显示新数据
     * @param isScrollToEnd 是否滚动到底部
     */
    private void showData(List<WKMsg> msgList, int pullMode, boolean isSetNewData, boolean isScrollToEnd) {
        boolean isAddEmptyView = msgList.size() != 0 && msgList.size() < limit;
        if (isAddEmptyView) {
            WKMsg msg = new WKMsg();
            msg.timestamp = 0;
            msg.type = WKContentType.emptyView;
            msgList.add(0, msg);
        }

        List<WKUIChatMsgItemEntity> list = new ArrayList<>();
        if (WKReader.isNotEmpty(msgList)) {
            long pre_msg_time = chatAdapter.getLastTimeMsg();
            for (int i = 0, size = msgList.size(); i < size; i++) {
                if (!WKTimeUtils.getInstance().isSameDay(msgList.get(i).timestamp, pre_msg_time) && msgList.get(i).type != WKContentType.emptyView) {
                    //显示聊天时间
                    WKUIChatMsgItemEntity uiChatMsgEntity = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                    uiChatMsgEntity.wkMsg.type = WKContentType.msgPromptTime;
                    uiChatMsgEntity.wkMsg.content = WKTimeUtils.getInstance().getShowDate(msgList.get(i).timestamp * 1000);
                    uiChatMsgEntity.wkMsg.timestamp = msgList.get(i).timestamp;
                    list.add(uiChatMsgEntity);
                }
                pre_msg_time = msgList.get(i).timestamp;
                list.add(WKIMUtils.getInstance().msg2UiMsg(this, msgList.get(i), count, showNickName, chatAdapter.isShowChooseItem()));
            }
        }

        if (isSetNewData) {
            if (unreadStartMsgOrderSeq != 0) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    if (list.get(i).wkMsg != null && list.get(i).wkMsg.orderSeq == unreadStartMsgOrderSeq) {
                        //插入一条本地的新消息分割线
                        WKUIChatMsgItemEntity uiChatMsgItemEntity = new WKUIChatMsgItemEntity(this, new WKMsg(), null);
                        uiChatMsgItemEntity.wkMsg.type = WKContentType.msgPromptNewMsg;
                        int index = i;
                        if (index <= 0) index = 0;
                        if (index > list.size() - 1) index = list.size() - 1;
                        list.add(index, uiChatMsgItemEntity);
                        if (index >= 1) {
                            linearLayoutManager.scrollToPositionWithOffset(index, 50);
                        } else wkVBinding.recyclerView.scrollToPosition(index);
                        unreadStartMsgOrderSeq = 0;
                        break;
                    }
                }
            }
            chatAdapter.resetData(list);
            chatAdapter.setNewInstance(list);
        } else {
            chatAdapter.resetData(list);
            if (pullMode == 1) {
                if (chatAdapter.getData().size() > 0 && list.size() > 0)
                    list.get(0).previousMsg = chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg;
                chatAdapter.addData(list);
            } else {
                if (list.size() > 0 && chatAdapter.getData().size() > 0) {
                    list.get(list.size() - 1).nextMsg = chatAdapter.getData().get(0).wkMsg;
                }
                chatAdapter.addData(0, list);
            }
        }
        if (tipsOrderSeq != 0 || lastPreviewMsgOrderSeq != 0) {
            wkVBinding.recyclerView.setVisibility(View.VISIBLE);
            if (tipsOrderSeq != 0) {
                for (int i = 0; i < chatAdapter.getData().size(); i++) {
                    if (chatAdapter.getItem(i).wkMsg.orderSeq == tipsOrderSeq) {
                        wkVBinding.recyclerView.scrollToPosition(i);
                        chatAdapter.getItem(i).isShowTips = true;
                        chatAdapter.notifyItemChanged(i);
                        tipsOrderSeq = 0;
                        break;
                    }
                }
            }
            if (lastPreviewMsgOrderSeq != 0) {
                for (int i = 0; i < chatAdapter.getData().size(); i++) {
                    if (chatAdapter.getItem(i).wkMsg.orderSeq == lastPreviewMsgOrderSeq) {
                        linearLayoutManager.scrollToPositionWithOffset(i, keepOffsetY);
                        break;
                    }
                }
            }
        } else {
            if (isScrollToEnd)
                wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            else wkVBinding.recyclerView.setVisibility(View.VISIBLE);
        }
        if (isCanLoadMore && chatAdapter.getData().size() > 0 && chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg != null) {
            int maxSeq = WKIM.getInstance().getMsgManager().getMaxMessageSeqWithChannel(channelId, channelType);
            if (chatAdapter.getData().get(chatAdapter.getData().size() - 1).wkMsg.messageSeq == maxSeq) {
                isCanLoadMore = false;
            }
        }

    }

    /**
     * 发送消息
     *
     * @param messageContent 消息model
     */
    private void sendMsg(WKMessageContent messageContent) {
        if (redDot > 0) {
            wkVBinding.chatUnreadLayout.newMsgLayout.performClick();
        }
        WKMsg wkMsg = new WKMsg();
        wkMsg.channelID = channelId;
        wkMsg.channelType = channelType;
        wkMsg.type = messageContent.type;
        wkMsg.baseContentMsgModel = messageContent;
        WKChannel channel = getChatChannelInfo();
        if (channel.robot == 1) signal = 0;
        WKMsgSetting setting = new WKMsgSetting();
        setting.receipt = channel.receipt;
        wkMsg.setting = setting;
        wkMsg.setChannelInfo(channel);
        wkMsg.fromUID = WKConfig.getInstance().getUid();
        WKSendMsgUtils.getInstance().sendMessage(wkMsg);
        //setting.signal = signal;
//        messageContent.flame = channel.flame;
//        messageContent.flameSecond = channel.flameSecond;
//        WKSendMsgUtils.getInstance().sendMessage(messageContent, setting, channelId, channelType);
    }

    ActivityResultLauncher<Intent> previewNewImgResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
            String path = result.getData().getStringExtra("path");
            if (!TextUtils.isEmpty(path)) {
                sendMsg(new WKImageContent(path));
            }
        }
    });
    ActivityResultLauncher<Intent> chooseCardResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<>() {

        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
                String uid = result.getData().getStringExtra("uid");
                if (!TextUtils.isEmpty(uid)) {
                    WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
                    WKCardContent WKCardContent = new WKCardContent();
                    WKCardContent.name = channel.channelName;
                    WKCardContent.uid = channel.channelID;
                    if (channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.vercode))
                        WKCardContent.vercode = (String) channel.remoteExtraMap.get(WKChannelExtras.vercode);
                    List<WKMessageContent> messageContentList = new ArrayList<>();
                    messageContentList.add(WKCardContent);
                    List<WKChannel> list = new ArrayList<>();
                    list.add(WKIM.getInstance().getChannelManager().getChannel(channelId, channelType));
                    WKUIKitApplication.getInstance().showChatConfirmDialog(ChatActivity.this, list, messageContentList, (list1, messageContentList1) -> sendMsg(WKCardContent));
                }
            }
        }
    });

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        WKUIKitApplication.getInstance().chattingChannelID = "";
        isUploadReadMsg = false;
        WKPlayVoiceUtils.getInstance().stopPlay();
        MsgModel.getInstance().doneReminder(reminderIds);
        EndpointManager.getInstance().invoke("stop_screen_shot", this);
    }

    @Override
    public void finish() {
        super.finish();
        EndpointManager.getInstance().remove(channelId);
        EndpointManager.getInstance().invoke("stop_screen_shot", this);
        WKIM.getInstance().getMsgManager().removeDeleteMsgListener(channelId);
        WKIM.getInstance().getMsgManager().removeNewMsgListener(channelId);
        WKIM.getInstance().getMsgManager().removeRefreshMsgListener(channelId);
        WKIM.getInstance().getMsgManager().removeSendMsgCallBack(channelId);
        WKIM.getInstance().getChannelManager().removeRefreshChannelInfo(channelId);
        WKIM.getInstance().getChannelMembersManager().removeRefreshChannelMemberInfo(channelId);
        WKIM.getInstance().getChannelMembersManager().removeAddChannelMemberListener(channelId);
        WKIM.getInstance().getChannelMembersManager().removeRemoveChannelMemberListener(channelId);
        WKIM.getInstance().getCMDManager().removeCmdListener(channelId);
        WKIM.getInstance().getMsgManager().removeSendMsgAckListener(channelId);
        WKIM.getInstance().getMsgManager().removeClearMsg(channelId);
        WKIM.getInstance().getRobotManager().removeRefreshRobotMenu(channelId);
        WKIM.getInstance().getReminderManager().removeNewReminderListener(channelId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wkVBinding.chatInputPanel.onDestroy();
        keyboardHelper.release();
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
        if (readMsgIds.size() > 0) {
            EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(channelId, channelType, readMsgIds));
        }
        MsgModel.getInstance().startCheckFlameMsgTimer();
        saveEditContent();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return setBackListener();
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean setBackListener() {
        if (!isViewingPicture) {
            if (wkVBinding.chatInputPanel.onBackListener()) {
                return true;
            }
            if (numberTextView.getVisibility() == View.VISIBLE) {
                for (int i = 0, size = chatAdapter.getItemCount(); i < size; i++) {
                    chatAdapter.getItem(i).isChoose = false;
                    chatAdapter.getItem(i).isChecked = false;
                }
                chatAdapter.notifyItemRangeChanged(0, chatAdapter.getItemCount());
                wkVBinding.chatInputPanel.hideMultipleChoice();
                CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_ab_back);
                numberTextView.setNumber(0, true);
                hideOrShowRightView(true);
                CommonAnim.getInstance().showOrHide(numberTextView, false, true);
            } else {
                if (wkVBinding.chatInputPanel.isCanBack()) {
                    new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(this::finish, 150);
                }
            }
        }
        return false;
    }

    private void saveEditContent() {
        //停止语音播放
        //AudioPlaybackManager.getInstance().stopAudio();
        int firstItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
        int endItemPosition = linearLayoutManager.findLastVisibleItemPosition();
        long keepMsgSeq = 0;
        int offsetY = 0;
        if (endItemPosition != chatAdapter.getData().size() - 1) {
            WKMsg msg = chatAdapter.getFirstVisibleItem(firstItemPosition);
            if (msg != null) {
                keepMsgSeq = msg.messageSeq;
                int index = chatAdapter.getFirstVisibleItemIndex(firstItemPosition);
                View view = linearLayoutManager.findViewByPosition(index);
                if (view != null) {
                    offsetY = view.getTop();
                }
            }
        }
        int unreadCount = wkVBinding.chatUnreadLayout.msgCountTv.getCount();
        MsgModel.getInstance().clearUnread(channelId, channelType, unreadCount, null);
        String content = Objects.requireNonNull(wkVBinding.chatInputPanel.getEditText().getText()).toString();
        MsgModel.getInstance().updateCoverExtra(channelId, channelType, browseTo, keepMsgSeq, offsetY, content);
        MsgModel.getInstance().deleteFlameMsg();
    }

    @Override
    public void sendMessage(WKMessageContent messageContent) {
        if (messageContent.type == WKContentType.WK_TEXT && editMsg != null) {
            wkVBinding.chatInputPanel.hideTopView();
            JSONObject jsonObject = messageContent.encodeMsg();
            if (jsonObject == null) jsonObject = new JSONObject();
            try {
                jsonObject.put("type", messageContent.type);
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
            if (!editMsg.baseContentMsgModel.getDisplayContent().equals(messageContent.getDisplayContent())) {
                WKIM.getInstance().getMsgManager().updateMsgEdit(editMsg.messageID, channelId, channelType, jsonObject.toString());
                //   MsgModel.getInstance().editMsg(editMsg.messageID, editMsg.messageSeq, channelId, channelType, jsonObject.toString(), null);
            }
            deleteOperationMsg();
            return;
        }
        if (messageContent.type == WKContentType.WK_TEXT && replyWKMsg != null) {
            WKReply wkReply = new WKReply();
            wkReply.payload = replyWKMsg.baseContentMsgModel;

            String showName = "";
            if (replyWKMsg.getFrom() != null) {
                showName = replyWKMsg.getFrom().channelName;
            } else {
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(replyWKMsg.fromUID, WKChannelType.PERSONAL);
                if (channel != null) showName = channel.channelName;
            }
            wkReply.from_name = showName;
            wkReply.from_uid = replyWKMsg.fromUID;
            wkReply.message_id = replyWKMsg.messageID;
            wkReply.message_seq = replyWKMsg.messageSeq;
            if (replyWKMsg.baseContentMsgModel.reply != null && !TextUtils.isEmpty(replyWKMsg.baseContentMsgModel.reply.root_mid)) {
                wkReply.root_mid = replyWKMsg.baseContentMsgModel.reply.root_mid;
            } else {
                wkReply.root_mid = wkReply.message_id;
            }
            messageContent.reply = wkReply;
        }
        sendMsg(messageContent);
        replyWKMsg = null;
        wkVBinding.chatInputPanel.hideTopView();
    }

    @Override
    public WKChannel getChatChannelInfo() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
        }
        return channel;
    }

    @Override
    public void showMultipleChoice() {
        wkVBinding.chatInputPanel.showMultipleChoice();
        CommonAnim.getInstance().rotateImage(wkVBinding.topLayout.backIv, 180f, 360f, R.mipmap.ic_close_white);
        CommonAnim.getInstance().showOrHide(numberTextView, true, true);
        CommonAnim.getInstance().showOrHide(callIV, false, false);
    }

    @Override
    public void setTitleRightText(String text) {
        int num = Integer.parseInt(text);
        wkVBinding.chatInputPanel.updateForwardView(num);
        numberTextView.setNumber(num, true);
        CommonAnim.getInstance().showOrHide(numberTextView, true, true);
        CommonAnim.getInstance().showOrHide(callIV, false, false);
    }

    @Override
    public void showReply(WKMsg wkMsg) {
        this.editMsg = null;
        boolean showDialog = false;
        WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && mChannelMember != null) {
            if ((channel.forbidden == 1 && mChannelMember.role == WKChannelMemberRole.normal) || mChannelMember.forbiddenExpirationTime > 0) {
                //普通成员
                showDialog = true;
            }
        }

        if (showDialog) {
            showSingleBtnDialog(getString(R.string.cannot_reply_msg));
            return;
        }

        if (channelType == WKChannelType.GROUP && !wkMsg.fromUID.equals(WKConfig.getInstance().getUid())) {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, wkMsg.fromUID);
            if (member != null) {
                wkVBinding.chatInputPanel.getEditText().addSpan("@" + member.memberName + " ", member.memberUID);
            } else {
                WKChannel mChannel = WKIM.getInstance().getChannelManager().getChannel(wkMsg.fromUID, WKChannelType.PERSONAL);
                if (mChannel != null) {
                    wkVBinding.chatInputPanel.getEditText().addSpan("@" + mChannel.channelName + " ", mChannel.channelID);
                }
            }
//            WKVBinding.toolbarView.editText.addAtSpan("@", member.memberName, member.memberUID);
        }
        this.replyWKMsg = wkMsg;
        if (replyWKMsg != null) {
            wkVBinding.chatInputPanel.showReplyLayout(replyWKMsg);
        }
    }

    @Override
    public void showEdit(WKMsg wkMsg) {
        boolean showDialog = false;
        WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel != null && mChannelMember != null) {
            if ((channel.forbidden == 1 && mChannelMember.role == WKChannelMemberRole.normal) || mChannelMember.forbiddenExpirationTime > 0) {
                //普通成员
                showDialog = true;
            }
        }

        if (showDialog) {
            showSingleBtnDialog(getString(R.string.cannot_edit_msg));
            return;
        }
        this.replyWKMsg = null;
        if (wkMsg != null) {
            this.editMsg = wkMsg;
            wkVBinding.chatInputPanel.showEditLayout(wkMsg);
        }
    }

    @Override
    public void tipsMsg(String clientMsgNo) {
        int index = -1;
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            if (chatAdapter.getData().get(i).wkMsg != null && chatAdapter.getData().get(i).wkMsg.clientMsgNO.equals(clientMsgNo)) {
                chatAdapter.getData().get(i).isShowTips = true;
                index = i;
                break;
            }
        }
        if (index != -1) {
            wkVBinding.recyclerView.scrollToPosition(index);
            //scrollToPosition(index);
            chatAdapter.notifyItemChanged(index);
        } else {
            WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
            if (msg != null && msg.isDeleted == 0) {
                unreadStartMsgOrderSeq = 0;
                tipsOrderSeq = msg.orderSeq;
                // keepMessageSeq = msg.orderSeq;
                getData(0, true, msg.orderSeq, true);
                isCanLoadMore = true;
            } else {
                showToast(getString(R.string.cannot_tips_msg));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WKUIKitApplication.getInstance().chattingChannelID = channelId;
        isUploadReadMsg = true;
        wkVBinding.chatInputPanel.initRefreshListener();
        EndpointManager.getInstance().invoke("start_screen_shot", this);
    }


    @Override
    public void setEditContent(String content) {
        int curPosition = wkVBinding.chatInputPanel.getEditText().getSelectionStart();
        StringBuilder sb = new StringBuilder(Objects.requireNonNull(wkVBinding.chatInputPanel.getEditText().getText()).toString());
        sb.insert(curPosition, content);
        wkVBinding.chatInputPanel.getEditText().setText(MoonUtil.getEmotionContent(this, wkVBinding.chatInputPanel.getEditText(), sb.toString()));
        // 将光标设置到新增完表情的右侧
        wkVBinding.chatInputPanel.getEditText().setSelection(curPosition + content.length());
    }

    @Override
    public AppCompatActivity getChatActivity() {
        return this;
    }

    @Override
    public WKMsg getReplyMsg() {
        return replyWKMsg;
    }

    @Override
    public void hideSoftKeyboard() {
    }

    @Override
    public ChatAdapter getChatAdapter() {
        return chatAdapter;
    }


    @Override
    public void sendCardMsg() {
        Intent intent = new Intent(this, ChooseContactsActivity.class);
        intent.putExtra("chooseBack", true);
        intent.putExtra("singleChoose", true);
        if (channelType == WKChannelType.PERSONAL) {
            intent.putExtra("unVisibleUIDs", channelId);
        }
        chooseCardResultLac.launch(intent);
    }

    @Override
    public void chatRecyclerViewScrollToEnd() {
        if (isToEnd) {
            scrollToEnd();
        }
    }

    @Override
    public void deleteOperationMsg() {
        this.replyWKMsg = null;
        this.editMsg = null;
    }

    @Override
    public void onChatAvatarClick(String uid, boolean isLongClick) {
        wkVBinding.chatInputPanel.chatAvatarClick(uid, isLongClick);
    }

    @Override
    public void onViewPicture(boolean isViewing) {
        isViewingPicture = isViewing;
    }

    @Override
    public void onMsgViewed(WKMsg wkMsg, int position) {
        if (wkMsg == null) return;

        if (wkMsg.flame == 1 && wkMsg.viewed == 0 && wkMsg.type != WKContentType.WK_IMAGE && wkMsg.type != WKContentType.WK_VIDEO && wkMsg.type != WKContentType.WK_VOICE) {

            wkMsg.viewed = 1;
            wkMsg.viewedAt = WKTimeUtils.getInstance().getCurrentMills();
            chatAdapter.updateDeleteTimer(position);
            WKIM.getInstance().getMsgManager().updateViewedAt(1, wkMsg.viewedAt, wkMsg.clientMsgNO);
        }
        if (wkMsg.viewed == 0 && wkMsg.type == WKContentType.WK_TEXT) {
            wkMsg.viewed = 1;
        }

        if (wkMsg.remoteExtra.readed == 0 && wkMsg.setting != null && wkMsg.setting.receipt == 1 && !TextUtils.isEmpty(wkMsg.fromUID) && !wkMsg.fromUID.equals(WKConfig.getInstance().getUid())) {
            boolean isAdd = true;
            for (int j = 0, size = readMsgIds.size(); j < size; j++) {
                if (readMsgIds.get(j).equals(wkMsg.messageID)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                readMsgIds.add(wkMsg.messageID);
            }
        }
        boolean isResetRemind = false;
        if (reminderList.size() > 0 && !TextUtils.isEmpty(wkMsg.messageID)) {
            for (int j = 0; j < reminderList.size(); j++) {
                if (reminderList.get(j).messageID.equals(wkMsg.messageID)) {
                    if (reminderList.get(j).done == 0) {
                        reminderIds.add(reminderList.get(j).reminderID);
                    }
                    reminderList.remove(j);
                    j = j - 1;
                    isResetRemind = true;
                }
            }
        }

        boolean isResetGroupApprove = false;
        if (groupApproveList.size() > 0 && !TextUtils.isEmpty(wkMsg.messageID)) {
            for (int j = 0, size = groupApproveList.size(); j < size; j++) {
                if (groupApproveList.get(j).messageID.equals(wkMsg.messageID) && groupApproveList.get(j).done == 0) {
                    reminderIds.add(groupApproveList.get(j).reminderID);
                    groupApproveList.remove(j);
                    isResetGroupApprove = true;
                    break;
                }
            }
        }

        // 保存最新浏览到的位置
        if (wkMsg.messageSeq > browseTo) {
            browseTo = wkMsg.messageSeq;
        }
        boolean isResetUnread = false;
        if (wkMsg.messageSeq > lastVisibleMsgSeq) {
            lastVisibleMsgSeq = wkMsg.messageSeq;
        }
        if (lastVisibleMsgSeq != 0) {
            long lastVisibleMsgOrderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(lastVisibleMsgSeq, channelId, channelType);
            if (lastVisibleMsgOrderSeq < unreadStartMsgOrderSeq) {
                lastVisibleMsgSeq = (int) WKIM.getInstance().getMsgManager().getReliableMessageSeq(unreadStartMsgOrderSeq);
                lastVisibleMsgSeq = lastVisibleMsgSeq - 1;
            }
        }
        if (redDot > 0) {
            if (lastVisibleMsgSeq != 0) {
                redDot = maxMsgSeq - lastVisibleMsgSeq;
            }
            if (redDot < 0) redDot = 0;
            isResetUnread = true;
            if (isUpdateRedDot) {
                MsgModel.getInstance().clearUnread(channelId, channelType, redDot, (code, msg) -> {
                    if (code == HttpResponseCode.success && redDot == 0) {
                        isUpdateRedDot = false;
                    }
                });
            }
        }
        if (isResetGroupApprove) resetGroupApproveView();
        if (isResetRemind) resetRemindView();
        if (isResetUnread) showUnReadCountView();
    }

    @Override
    public View getRecyclerViewLayout() {
        return wkVBinding.recyclerViewLayout;
    }

    // 显示一条时间消息
    private WKMsg addTimeMsg(long newMsgTime) {
        long lastMsgTime = chatAdapter.getLastTimeMsg();
        WKMsg msg = null;
        if (!WKTimeUtils.getInstance().isSameDay(newMsgTime, lastMsgTime)) {
            int lastIndex = chatAdapter.getData().size() - 1;
            WKUIChatMsgItemEntity uiChatMsgEntity = new WKUIChatMsgItemEntity(this, null, null);
            msg = new WKMsg();
            uiChatMsgEntity.wkMsg = msg;
            uiChatMsgEntity.isChoose = (chatAdapter.getItemCount() > 0 && chatAdapter.getData().get(0).isChoose);
            uiChatMsgEntity.wkMsg.type = WKContentType.msgPromptTime;
            uiChatMsgEntity.wkMsg.content = WKTimeUtils.getInstance().getShowDate(newMsgTime * 1000);
            uiChatMsgEntity.wkMsg.timestamp = WKTimeUtils.getInstance().getCurrentSeconds();
            chatAdapter.addData(uiChatMsgEntity);
            if (lastIndex >= 0) {
                chatAdapter.notifyBackground(lastIndex);
            }
        }
        return msg;
    }

    // 定时上报已读消息
    private void startTimer() {
        Observable.interval(0, 3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<>() {
            @Override
            public void onComplete() {
            }

            @Override
            public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
            }

            @Override
            public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                disposable = d;
            }

            @Override
            public void onNext(@io.reactivex.rxjava3.annotations.NonNull Long value) {
                if (readMsgIds.size() == 0 || !isUploadReadMsg) {
                    return;
                }
                List<String> msgIds = new ArrayList<>(readMsgIds);
                EndpointManager.getInstance().invoke("read_msg", new ReadMsgMenu(channelId, channelType, msgIds));
                readMsgIds.clear();
            }
        });
    }

    private void scrollToEnd() {
        linearLayoutManager.scrollToPosition(chatAdapter.getItemCount() - 1);
//        scrollHelper.scrollToPosition(chatAdapter.getItemCount() - 1, 0);
        wkVBinding.recyclerView.postDelayed(() -> {
            if (wkVBinding.chatInputPanel.getLastPanelType() != PanelType.NONE) {
                int marginBottom = wkVBinding.chatInputPanel.lastBottom(wkVBinding.recyclerView);
                int referHeight = WKConstants.getKeyboardHeight();
                if (marginBottom - referHeight < 0) {
                    Animator animator = ObjectAnimator.ofFloat(wkVBinding.recyclerView, "translationY", wkVBinding.recyclerView.getTranslationY(), (marginBottom - referHeight));
                    animator.start();
                }
            }
        }, 200);
    }

    private void setOnlineView(WKChannel channel) {
        if (channel.online == 1) {
            String device = getString(R.string.phone);
            if (channel.deviceFlag == UserOnlineStatus.Web) device = getString(R.string.web);
            else if (channel.deviceFlag == UserOnlineStatus.PC) device = getString(R.string.pc);
            String content = String.format("%s%s", device, getString(R.string.online));
            wkVBinding.topLayout.subtitleTv.setText(content);
            wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
        } else {
            if (channel.lastOffline > 0) {
                String showTime = WKTimeUtils.getInstance().getOnlineTime(channel.lastOffline);
                if (TextUtils.isEmpty(showTime)) {
                    wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                    String time = WKTimeUtils.getInstance().getShowDateAndMinute(channel.lastOffline * 1000L);
                    String content = String.format("%s%s", getString(R.string.last_seen_time), time);
                    wkVBinding.topLayout.subtitleTv.setText(content);
                } else {
                    wkVBinding.topLayout.subtitleTv.setText(showTime);
                    wkVBinding.topLayout.subtitleView.setVisibility(View.VISIBLE);
                }
            } else wkVBinding.topLayout.subtitleView.setVisibility(View.GONE);
        }
    }

    private void checkLoginUserInGroupStatus() {
        if (channelType == WKChannelType.GROUP) {
            WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(channelId, channelType, WKConfig.getInstance().getUid());
            if (mChannelMember != null) {
                hideOrShowRightView(mChannelMember.isDeleted == 0);
            }
        }
    }

    private void hideOrShowRightView(boolean isShow) {
        if (((channelId.equals(WKSystemAccount.system_file_helper) || channelId.equals(WKSystemAccount.system_team)) && channelType == WKChannelType.PERSONAL) || channelType == WKChannelType.CUSTOMER_SERVICE) {
            isShow = false;
        }
        WKChannel channel = getChatChannelInfo();
        if (channelType == WKChannelType.PERSONAL && (channel.isDeleted == 1 || UserUtils.getInstance().checkFriendRelation(channelId))) {
            isShow = false;
        }
        CommonAnim.getInstance().showOrHide(callIV, isShow, true);
    }

    private void resetReminder(List<WKReminder> list) {
        if (WKReader.isNotEmpty(list)) {
            List<WKUIChatMsgItemEntity> msgList = chatAdapter.getData();
            List<Long> ids = new ArrayList<>();
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).done == 1) continue;
                for (int j = 0, len = msgList.size(); j < len; j++) {
                    if (msgList.get(j).wkMsg != null && !TextUtils.isEmpty(msgList.get(j).wkMsg.messageID) && msgList.get(j).wkMsg.messageID.equals(list.get(i).messageID)) {
                        if (msgList.get(j).wkMsg.viewed == 1) {
                            ids.add(list.get(i).reminderID);
                            list.remove(i);
                            i--;
                            size--;
                            break;
                        }
                    }
                }
            }
            MsgModel.getInstance().doneReminder(ids);
            if (WKReader.isEmpty(list)) {
                return;
            }
            String loginUID = WKConfig.getInstance().getUid();
            for (WKReminder reminder : list) {
                boolean isPublisher = !TextUtils.isEmpty(reminder.publisher) && reminder.publisher.equals(loginUID);
                if (!reminder.channelID.equals(channelId) || isPublisher) continue;
                if (reminder.done == 0) {
                    boolean isAdd = true;
                    for (int i = 0, size = reminderList.size(); i < size; i++) {
                        if (reminder.reminderID == reminderList.get(i).reminderID && reminder.type == reminderList.get(i).type) {
                            isAdd = false;
                            reminderList.get(i).done = 0;
                            break;
                        }
                    }
                    if (isAdd) reminderList.add(reminder);
                    boolean isAddApprove = true;
                    for (int i = 0, size = groupApproveList.size(); i < size; i++) {
                        if (reminder.reminderID == groupApproveList.get(i).reminderID && reminder.type == groupApproveList.get(i).type) {
                            isAddApprove = false;
                            groupApproveList.get(i).done = 0;
                            break;
                        }
                    }
                    if (isAddApprove && reminder.type == WKMentionType.WKApplyJoinGroupApprove)
                        groupApproveList.add(reminder);
                }
            }
            resetRemindView();
        }
    }

    private void resetRemindView() {
        wkVBinding.chatUnreadLayout.remindCountTv.setCount(reminderList.size(), true);
        wkVBinding.chatUnreadLayout.remindCountTv.setVisibility(reminderList.size() > 0 ? View.VISIBLE : View.GONE);
        wkVBinding.chatUnreadLayout.remindLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.remindLayout, reminderList.size() > 0, true, true));
    }

    private void resetGroupApproveView() {
        wkVBinding.chatUnreadLayout.approveCountTv.setCount(groupApproveList.size(), true);
        wkVBinding.chatUnreadLayout.approveCountTv.setVisibility(groupApproveList.size() > 0 ? View.VISIBLE : View.GONE);
        wkVBinding.chatUnreadLayout.groupApproveLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.groupApproveLayout, groupApproveList.size() > 0, true, true));
    }

    private void showUnReadCountView() {
        wkVBinding.chatUnreadLayout.msgCountTv.setCount(redDot, true);
        wkVBinding.chatUnreadLayout.msgCountTv.setVisibility(redDot > 0 ? View.VISIBLE : View.GONE);
        wkVBinding.chatUnreadLayout.newMsgLayout.post(() -> CommonAnim.getInstance().showOrHide(wkVBinding.chatUnreadLayout.newMsgLayout, redDot > 0, true, true));
    }

    private void showChannelName(WKChannel channel) {
        if (channelId.equals(WKSystemAccount.system_team)) {
            wkVBinding.topLayout.titleCenterTv.setText(R.string.wk_system_notice);
        } else if (channelId.equals(WKSystemAccount.system_file_helper)) {
            wkVBinding.topLayout.titleCenterTv.setText(R.string.wk_file_helper);
        } else {
            String showName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
            wkVBinding.topLayout.titleCenterTv.setText(showName);
        }
    }

    private void removeMsg(WKMsg msg) {
        EndpointManager.getInstance().invoke("stop_reaction_animation", null);
        int tempIndex = 0;
        for (int i = 0, size = chatAdapter.getData().size(); i < size; i++) {
            if (chatAdapter.getData().get(i).wkMsg != null && (chatAdapter.getData().get(i).wkMsg.clientSeq == msg.clientSeq || chatAdapter.getData().get(i).wkMsg.clientMsgNO.equals(msg.clientMsgNO))) {
                tempIndex = i;
                if (i - 1 >= 0) {
                    if (i + 1 <= chatAdapter.getData().size() - 1) {
                        chatAdapter.getData().get(i - 1).nextMsg = chatAdapter.getData().get(i + 1).wkMsg;
                    } else {
                        chatAdapter.getData().get(i - 1).nextMsg = null;
                    }
                }
                if (i + 1 <= chatAdapter.getData().size() - 1) {
                    if (i - 1 >= 0) {
                        chatAdapter.getData().get(i + 1).previousMsg = chatAdapter.getData().get(i - 1).wkMsg;
                    } else chatAdapter.getData().get(i + 1).previousMsg = null;
                }
//                isUpdateCoverMsg = true;
                chatAdapter.removeAt(i);
                break;
            }
        }

        int timeIndex = tempIndex - 1;
        if (timeIndex < 0) return;
        //如果是时间也删除
        if (chatAdapter.getData().size() >= timeIndex) {
            if (chatAdapter.getData().get(timeIndex).wkMsg.type == WKContentType.msgPromptTime) {

                if (timeIndex - 1 >= 0) {
                    if (timeIndex + 1 <= chatAdapter.getData().size() - 1) {
                        chatAdapter.getData().get(timeIndex - 1).nextMsg = chatAdapter.getData().get(timeIndex + 1).wkMsg;
                    } else {
                        chatAdapter.getData().get(timeIndex - 1).nextMsg = null;
                    }
                }
                if (timeIndex + 1 <= chatAdapter.getData().size() - 1) {
                    if (timeIndex - 1 >= 0) {
                        chatAdapter.getData().get(timeIndex + 1).previousMsg = chatAdapter.getData().get(timeIndex - 1).wkMsg;
                    } else chatAdapter.getData().get(timeIndex + 1).previousMsg = null;
                }
                chatAdapter.removeAt(timeIndex);
            }
        }
    }

    private void setShowTime() {
        String showTime = "";
        int index = linearLayoutManager.findFirstVisibleItemPosition();
        if (index > 0 && index < chatAdapter.getData().size()) {
            WKUIChatMsgItemEntity WKUIChatMsgItemEntity = chatAdapter.getData().get(index);
            if (WKUIChatMsgItemEntity.wkMsg != null && WKUIChatMsgItemEntity.wkMsg.timestamp > 0) {
                showTime = WKTimeUtils.getInstance().getShowDate(WKUIChatMsgItemEntity.wkMsg.timestamp * 1000);
            }
        }
        if (!TextUtils.isEmpty(showTime)) {
            SpannableString str = new SpannableString(showTime);
            str.setSpan(new SystemMsgBackgroundColorSpan(ContextCompat.getColor(this, R.color.colorSystemBg), AndroidUtilities.dp(5), AndroidUtilities.dp(2 * 5)), 0, showTime.length(), 0);
            wkVBinding.timeTv.setText(str);
            CommonAnim.getInstance().showOrHide(wkVBinding.timeTv, true, true);
        }
    }

    private boolean isRefreshReaction(List<WKMsgReaction> oldList, List<WKMsgReaction> newList) {
        if (WKReader.isEmpty(oldList) && WKReader.isEmpty(newList)) return false;
        if ((WKReader.isEmpty(oldList) && WKReader.isNotEmpty(newList)) || (WKReader.isEmpty(newList) && WKReader.isNotEmpty(oldList)) || (oldList.size() != newList.size())) {
            return true;
        }
        boolean isRefresh = false;
        for (WKMsgReaction reaction : newList) {
            boolean refresh = true;
            for (WKMsgReaction reaction1 : oldList) {
                if (reaction1.messageID.equals(reaction.messageID) && reaction1.emoji.equals(reaction.emoji) && reaction1.isDeleted == reaction.isDeleted) {
                    refresh = false;
                    break;
                }
            }
            if (refresh) {
                isRefresh = true;
                break;
            }
        }
        return isRefresh;
    }

    private void scrollToPosition(int index) {
        scrollHelper.setScrollDirection(linearLayoutManager.findFirstVisibleItemPosition() < index ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
        scrollHelper.scrollToPosition(index, 0, false, true);
    }


    private void showRefreshLoading() {
        if (isRefreshLoading || !isCanRefresh) return;
        isRefreshLoading = true;
        WKMsg wkMsg = new WKMsg();
        wkMsg.type = WKContentType.loading;
        chatAdapter.addData(0, new WKUIChatMsgItemEntity(this, wkMsg, null));
        wkVBinding.recyclerView.scrollToPosition(0);
        lastPreviewMsgOrderSeq = 0;
        new Handler().postDelayed(() -> getData(0, false, 0, false), 500);
    }

    private void showMoreLoading() {
        if (isMoreLoading || !isCanLoadMore) return;
        isMoreLoading = true;
        WKMsg wkMsg = new WKMsg();
        wkMsg.type = WKContentType.loading;
        chatAdapter.addData(new WKUIChatMsgItemEntity(this, wkMsg, null));
        wkVBinding.recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        lastPreviewMsgOrderSeq = 0;
        unreadStartMsgOrderSeq = 0;
        new Handler().postDelayed(() -> getData(1, false, 0, false), 500);
    }

    private List<PopupMenuItem> getGroupApprovePopupItems() {
        PopupMenuItem item = new PopupMenuItem(getString(R.string.clear_all_remind), R.mipmap.msg_seen, () -> {
            List<WKReminder> list = WKIM.getInstance().getReminderManager().getRemindersWithType(channelId, channelType, WKMentionType.WKApplyJoinGroupApprove);
            List<Long> ids = new ArrayList<>();
            for (WKReminder reminder : list) {
                if (reminder.done == 0) {
                    ids.add(reminder.reminderID);
                }
            }
            groupApproveList.clear();
            resetGroupApproveView();
            MsgModel.getInstance().doneReminder(ids);
        });

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private List<PopupMenuItem> getRemindPopupItems() {
        PopupMenuItem item = new PopupMenuItem(getString(R.string.clear_all_remind), R.mipmap.msg_seen, () -> {
            List<WKReminder> list = WKIM.getInstance().getReminderManager().getRemindersWithType(channelId, channelType, WKMentionType.WKReminderTypeMentionMe);
            List<Long> ids = new ArrayList<>();
            for (WKReminder reminder : list) {
                if (reminder.done == 0) {
                    ids.add(reminder.reminderID);
                }
            }
            reminderList.clear();
            resetRemindView();
            MsgModel.getInstance().doneReminder(ids);
        });

        List<PopupMenuItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }
}
