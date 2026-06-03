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

package com.chat.uikit;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.Manifest;
import android.app.Application;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSON;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChatFunctionMenu;
import com.chat.base.endpoint.entity.ChatItemPopupMenu;
import com.chat.base.endpoint.entity.ChatToolBarMenu;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.chat.base.endpoint.entity.ChooseContactsMenu;
import com.chat.base.endpoint.entity.ContactsMenu;
import com.chat.base.endpoint.entity.DBMenu;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.endpoint.entity.MsgConfig;
import com.chat.base.endpoint.entity.PersonalInfoMenu;
import com.chat.base.endpoint.entity.ScanResultMenu;
import com.chat.base.endpoint.entity.SearchChatContentMenu;
import com.chat.base.endpoint.entity.UserDetailMenu;
import com.chat.base.endpoint.entity.WKMsg2UiMsgMenu;
import com.chat.base.endpoint.entity.WithdrawMsgMenu;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.ChooseResultModel;
import com.chat.base.glide.GlideUtils;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msg.model.WKGifContent;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.AlertDialog;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.startup.AppStartup;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKFileUtils;
import com.chat.base.utils.WKMediaFileUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.chat.ChooseChatActivity;
import com.chat.uikit.chat.face.WKVoiceViewManager;
import com.chat.uikit.chat.manager.FaceManger;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.chat.uikit.chat.msgmodel.WKCardContent;
import com.chat.uikit.chat.msgmodel.WKRichTextContent;
import com.chat.base.msgcontent.WKFileContent;
import com.chat.uikit.chat.provider.WKFileProvider;
import com.chat.uikit.chat.provider.WKVideoProvider;
import com.chat.uikit.chat.msgmodel.WKMultiForwardContent;
import com.chat.uikit.chat.provider.LoadingProvider;
import com.chat.uikit.chat.provider.WKCardProvider;
import com.chat.uikit.chat.provider.WKEmptyProvider;
import com.chat.uikit.chat.provider.WKImageProvider;
import com.chat.uikit.chat.provider.WKMultiForwardProvider;
import com.chat.uikit.chat.provider.WKNoRelationProvider;
import com.chat.uikit.chat.provider.WKPromptNewMsgProvider;
import com.chat.uikit.chat.provider.WKSensitiveWordsProvider;
import com.chat.uikit.chat.provider.WKSpanEmptyProvider;
import com.chat.uikit.chat.provider.WKTextProvider;
import com.chat.uikit.chat.provider.WKRichTextProvider;
import com.chat.uikit.chat.provider.WKVoiceProvider;
import com.chat.uikit.thread.CreateThreadActivity;
import com.chat.uikit.thread.msgmodel.WKThreadCreatedContent;
import com.chat.uikit.thread.provider.WKThreadCreatedProvider;
import com.chat.uikit.chat.search.date.SearchWithDateActivity;
import com.chat.uikit.chat.search.image.SearchWithImgActivity;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.contacts.MyGroupsListActivity;
import com.chat.uikit.contacts.NewFriendsActivity;
import com.chat.uikit.contacts.SpaceBotsListActivity;
import com.chat.uikit.enity.SensitiveWords;
import com.chat.uikit.group.WKAllMembersActivity;
import com.chat.uikit.message.MsgModel;
import com.chat.uikit.space.SpaceEntity;
import com.chat.uikit.space.SpaceModel;
import com.chat.uikit.message.ProhibitWordModel;
import com.chat.uikit.search.AddFriendsActivity;
import com.chat.uikit.setting.MsgNoticesSettingActivity;
import com.chat.uikit.setting.SettingActivity;
import com.chat.uikit.user.UserDetailActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKTextContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 2020-03-01 17:32
 * ui kit
 */
public class WKUIKitApplication {
    int totalMsgCount = 0;
    public String chattingChannelID;
    public SensitiveWords sensitiveWords;
    public boolean isRefreshChatActivityMessage = false;
    // 注册场景设为 true，loginMenus 跳过页面导航
    public static volatile boolean skipNavigation = false;

    // 当前 Space 的会话白名单（channelID_channelType），由 ChatFragment 维护
    private final java.util.Set<String> spaceConversationKeys = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public void setSpaceConversationKeys(java.util.Set<String> keys) {
        spaceConversationKeys.clear();
        spaceConversationKeys.addAll(keys);
    }

    public boolean isInCurrentSpace(String channelID, byte channelType) {
        if (spaceConversationKeys.isEmpty()) return true;
        return spaceConversationKeys.contains(channelID + "_" + channelType);
    }

    private WKUIKitApplication() {
    }

    private static class KitApplicationBinder {
        private static final WKUIKitApplication uikit = new WKUIKitApplication();
    }

    public static WKUIKitApplication getInstance() {
        return KitApplicationBinder.uikit;
    }

    private WeakReference<Application> mContext;

    public void init(Application mContext) {
        this.mContext = new WeakReference<>(mContext);

        // initKitModuleListener 在主线程同步执行（纯内存操作，无 I/O）
        // 避免与 ChatActivity 产生竞态导致 NPE
        initKitModuleListener();

        //  (fixing  ReviewBot P1-#3) · 绑定 SpaceSyncCoordinator 到
        // wkim 层的 SyncGate，让 WKConnection 连接成功后的 sync 也走 debounce 守卫。
        // 必须在 WKIM 的首次连接成功回调之前完成——放到同步 init 段开头最安全。
        com.chat.uikit.chat.SpaceSyncCoordinator.installSyncGate();

        //  (P-04) · App Startup Initializer 分阶段化
        //   Phase-A（同步，上面已完成）：initKitModuleListener——纯内存映射，必须在
        //       Intent 到达 ChatActivity 之前就绪。
        //   Phase-B（首屏依赖，立即异步）：WKIM.init + IMListener 注册——会话列表/
        //       聊天页面都必须等这步完成才能读数据。
        //       本地 sensitiveWords 缓存解析（SP → SensitiveWords）也归此阶段，
        //       必须在 initIMListener 之前完成，保证 WKIM 入站消息过滤不出现冷启
        //       窗口内敏感词失效的短暂窗口（ / ReviewBot P2-1）。
        //   Phase-C（idle 后执行）：sensitive_words / prohibit words 的 **网络同步**
        //       + 阅后即焚清理——纯远端拉新，首屏不依赖，延迟到首帧之后不影响体验。

        // Phase-B — 本地 sensitiveWords 解析 + WKIM 监听绑定
        //
        // initIM() 必须同步执行：ViewPager2 重构后 ChatFragment 布局阶段即触发 DB 查询，
        // 若 initIM 仍在后台异步，重启时 UI 跑在 init 前面导致会话列表空白。
        parseLocalSensitiveWords();
        initIM();

        AppStartup.postPhaseB("wkim", () -> {
            WKIMUtils.getInstance().initIMListener();
        });

        // Phase-C — sensitive_words / prohibit words 网络同步 + 阅后即焚清理（首屏不依赖）
        AppStartup.postPhaseC("post-wkim-idle", () -> {
            MsgModel.getInstance().syncSensitiveWords();
            ProhibitWordModel.Companion.getInstance().sync();
            MsgModel.getInstance().deleteFlameMsg();
        });
    }

    /**
     * 从 SP 读取并反序列化本地 sensitiveWords 缓存（）。
     *
     * <p>纯内存操作（SP 已由  P-01 预热），无网络；抛出异常只打日志、不影响
     * 后续启动链——与 AppStartup runWithTrace 的 fire-and-forget 语义一致。
     *
     * <p>调用方必须保证此方法在 {@code WKIMUtils.initIMListener()} 之前执行，
     * 否则入站消息过滤可能命中 null sensitiveWords。
     */
    private void parseLocalSensitiveWords() {
        try {
            String json = WKSharedPreferencesUtil.getInstance().getSP("wk_sensitive_words");
            if (!TextUtils.isEmpty(json)) {
                sensitiveWords = JSON.parseObject(json, SensitiveWords.class);
            }
        } catch (Throwable t) {
            Log.e("WKUIKitApplication", "parseLocalSensitiveWords failed", t);
        }
    }

    public Context getContext() {
        return mContext.get();
    }


    public boolean initIM() {
        if (!TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
            //设置开发模式
//            WKIM.getInstance().setDebug(WKBinder.isDebug);
            WKIM.getInstance().setDebug(true);
            WKIM.getInstance().setFileCacheDir("wkIMFile");

            String imToken = WKConfig.getInstance().getImToken();
            String uid = WKConfig.getInstance().getUid();
            if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(imToken)) {
                Log.e("WKUIKitApplication", "initIM skipped: uid or imToken is empty");
                return false;
            }
            WKIM.getInstance().init(mContext.get(), uid, imToken);
            return true;
        }
        return false;
    }

    public boolean isWkimReady() {
        return WKIM.getInstance().isInitialized();
    }

    public void startChat() {
        if (!TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
            if (!WKIM.getInstance().isInitialized()) {
                // WKIM 后台初始化尚未完成，延迟重试
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::startChat, 100);
                return;
            }
            Log.e("去连接", "-->");
            WKIM.getInstance().getConnectionManager().connection();
        }
    }

    public void stopConn() {
        EndpointManager.getInstance().invoke("push_update_device_badge", totalMsgCount);
        WKIM.getInstance().getConnectionManager().disconnect(false);
    }

    private void initKitModuleListener() {
        // 注册消息model到sdk
        WKIM.getInstance().getMsgManager().registerContentMsg(WKCardContent.class);
        WKIM.getInstance().getMsgManager().registerContentMsg(WKFileContent.class);

        WKIM.getInstance().getMsgManager().registerContentMsg(WKMultiForwardContent.class);
        WKIM.getInstance().getMsgManager().registerContentMsg(WKThreadCreatedContent.class);
        WKIM.getInstance().getMsgManager().registerContentMsg(WKRichTextContent.class);
        //添加消息item
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.sensitiveWordsTips, new WKSensitiveWordsProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.noRelation, new WKNoRelationProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.msgPromptNewMsg, new WKPromptNewMsgProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_TEXT, new WKTextProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.richText, new WKRichTextProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_IMAGE, new WKImageProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.emptyView, new WKEmptyProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.spanEmptyView, new WKSpanEmptyProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_VOICE, new WKVoiceProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_CARD, new WKCardProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_MULTIPLE_FORWARD, new WKMultiForwardProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_FILE, new WKFileProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_VIDEO, new WKVideoProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.loading, new LoadingProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.threadCreated, new WKThreadCreatedProvider());
        // 设置消息长按选项
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_TEXT, object -> new MsgConfig(true));
        // 图文混排 Phase 1 仅接收渲染：禁用转发/回复/多选（多选工具栏含转发，
        // 且不逐条复核 isCanForward，故一并关闭，留 Phase 2 发送端），保留
        // 复制/删除/reaction/撤回等接收侧操作。
        // 参数顺序：forward, withdraw, multipleChoice, reply, reaction, pin。
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.richText, object -> new MsgConfig(false, true, false, false, true, true));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_IMAGE, object -> new MsgConfig(true));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_CARD, object -> new MsgConfig(true));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_VOICE, object -> new MsgConfig(true));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_MULTIPLE_FORWARD, object -> new MsgConfig(true));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_FILE, object -> new MsgConfig(true));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_VIDEO, object -> new MsgConfig(true));
        EndpointManager.getInstance().setMethod("uikit_sql", EndpointCategory.wkDBMenus, object -> new DBMenu("uikit_sql"));
        //注册消息长按菜单配置
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_VOICE, object -> new MsgConfig(false, true, true, false, false, false));
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.typing, object -> new MsgConfig(false));
        EndpointManager.getInstance().setMethod("", EndpointCategory.wkChatPopupItem, 90, object -> {
            WKMsg wkMsg = (WKMsg) object;
            if (wkMsg.type == WKContentType.WK_TEXT) {
                return new ChatItemPopupMenu(R.mipmap.msg_copy, getContext().getString(R.string.copy), (msg, iConversationContext) -> {
                    WKTextContent textContent = (WKTextContent) msg.baseContentMsgModel;
                    String content = textContent.content;
                    if (msg.remoteExtra.contentEditMsgModel != null) {
                        content = msg.remoteExtra.contentEditMsgModel.getDisplayContent();
                    }
                    ClipboardManager cm = (ClipboardManager) iConversationContext.getChatActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData mClipData = ClipData.newPlainText("Label", content);
                    assert cm != null;
                    cm.setPrimaryClip(mClipData);
                    WKToastUtils.getInstance().showToastNormal(iConversationContext.getChatActivity().getString(R.string.copyed));
                });
            }
            if (wkMsg.type == WKContentType.richText) {
                // 图文混排复制取顶层 plain（server 权威纯文本，勿丢字）。
                return new ChatItemPopupMenu(R.mipmap.msg_copy, getContext().getString(R.string.copy), (msg, iConversationContext) -> {
                    String content = msg.baseContentMsgModel != null
                            ? msg.baseContentMsgModel.getDisplayContent() : "";
                    ClipboardManager cm = (ClipboardManager) iConversationContext.getChatActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData mClipData = ClipData.newPlainText("Label", content);
                    assert cm != null;
                    cm.setPrimaryClip(mClipData);
                    WKToastUtils.getInstance().showToastNormal(iConversationContext.getChatActivity().getString(R.string.copyed));
                });
            }
            return null;
        });
        // 创建子区长按菜单
        EndpointManager.getInstance().setMethod("create_thread", EndpointCategory.wkChatPopupItem, 10, object -> {
            WKMsg wkMsg = (WKMsg) object;
            // 仅在群聊中、thread_on开启、非系统消息时显示
            if (wkMsg.channelType == WKChannelType.GROUP
                    && WKConfig.getInstance().getAppConfig().thread_on == 1
                    && !WKContentType.isSystemMsg(wkMsg.type)) {
                return new ChatItemPopupMenu(R.mipmap.msg_forward, getContext().getString(R.string.str_create_thread), (msg, iConversationContext) -> {
                    Intent intent = new Intent(iConversationContext.getChatActivity(), CreateThreadActivity.class);
                    intent.putExtra("groupNo", msg.channelID);
                    intent.putExtra("sourceMessageId", msg.messageID);
                    if (msg.baseContentMsgModel != null) {
                        intent.putExtra("sourceContent", msg.baseContentMsgModel.getDisplayContent());
                    }
                    if (msg.getFrom() != null) {
                        intent.putExtra("sourceFromName", msg.getFrom().channelName);
                    }
                    intent.putExtra("sourceFromUid", msg.fromUID);
                    iConversationContext.getChatActivity().startActivity(intent);
                });
            }
            return null;
        });

        //添加个人中心
        EndpointManager.getInstance().setMethod("personal_center_currency", EndpointCategory.personalCenter, 2, object -> new PersonalInfoMenu(R.mipmap.icon_setting, mContext.get().getString(R.string.currency), () -> {
            Intent intent = new Intent(mContext.get(), SettingActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
        }));
        EndpointManager.getInstance().setMethod("personal_center_new_msg_notice", EndpointCategory.personalCenter, 3, object -> new PersonalInfoMenu(R.mipmap.icon_notice, mContext.get().getString(R.string.new_msg_notice), () -> {
            Intent intent = new Intent(mContext.get(), MsgNoticesSettingActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
        }));
        EndpointManager.getInstance().setMethod("personal_center_web_login", EndpointCategory.personalCenter, 1000, object -> new PersonalInfoMenu(R.mipmap.icon_web_login, mContext.get().getString(R.string.web_login), () -> EndpointManager.getInstance().invoke("show_web_login_desc", mContext.get())));

        //添加通讯录
        EndpointManager.getInstance().setMethod(EndpointCategory.mailList + "_friends", EndpointCategory.mailList, 100,
                object -> new ContactsMenu("friend", R.drawable.ic_contacts_new_friend, mContext.get().getString(R.string.new_friends), NewFriendsActivity.class));
        // 群聊
        EndpointManager.getInstance().setMethod(EndpointCategory.mailList + "_groups", EndpointCategory.mailList, 90,
                object -> new ContactsMenu("group_chat", R.drawable.ic_contacts_my_groups, mContext.get().getString(R.string.contacts_section_group_chat), MyGroupsListActivity.class));
        // 已添加AI
        EndpointManager.getInstance().setMethod(EndpointCategory.mailList + "_added_ai", EndpointCategory.mailList, 80,
                object -> new ContactsMenu("added_ai", R.drawable.ic_contacts_added_ai, mContext.get().getString(R.string.contacts_section_added_ai), SpaceBotsListActivity.class));

        // 添加聊天工具栏菜单语音
        EndpointManager.getInstance().setMethod(EndpointCategory.wkChatToolBar + "_voice", EndpointCategory.wkChatToolBar, 97, object -> {
            IConversationContext iConversationContext = (IConversationContext) object;
            View voiceView = WKVoiceViewManager.getInstance().getVoiceView(iConversationContext);
            return new ChatToolBarMenu("wk_chat_toolbar_voice", R.mipmap.icon_chat_toolbar_voice, R.mipmap.icon_chat_toolbar_voice, voiceView, (isSelected, iConversationContext14) -> {
                // TODO: 1/1/21
            });
        });
        //聊天工具栏相册
        EndpointManager.getInstance().setMethod(EndpointCategory.wkChatToolBar + "_album", EndpointCategory.wkChatToolBar, 99, object -> new ChatToolBarMenu("wk_chat_toolbar_album", R.mipmap.icon_chat_toolbar_album, -1, null, (isSelected, iConversationContext1) -> {
            if (isSelected) {
                chooseIMG(iConversationContext1);
            }
        }));
        //聊天工具栏@
        EndpointManager.getInstance().setMethod(EndpointCategory.wkChatToolBar + "_remind", EndpointCategory.wkChatToolBar, 96, object
                -> {
            IConversationContext iConversationContext = (IConversationContext) object;
            if (iConversationContext.getChatChannelInfo().channelType == WKChannelType.PERSONAL)
                return null;
            return new ChatToolBarMenu("wk_chat_toolbar_remind", R.mipmap.icon_chat_toolbar_aite, -1, null, (isSelected, iConversationContext12) -> {

            });
        });

        // 添加聊天工具栏菜单
        EndpointManager.getInstance().setMethod(EndpointCategory.wkChatToolBar + "_more", EndpointCategory.wkChatToolBar, 40, object -> {
            IConversationContext iConversationContext = (IConversationContext) object;
            View moreView = FaceManger.getInstance().getFunctionView(iConversationContext, chatFunctionMenu -> chatFunctionMenu.iChatFunctionCLick.onClick(iConversationContext));

            return new ChatToolBarMenu("wk_chat_toolbar_more", R.mipmap.icon_chat_toolbar_more, R.mipmap.icon_chat_toolbar_more, moreView, (isSelected, iConversationContext13) -> {

            });
        });
        //添加聊天功能面板
        EndpointManager.getInstance().setMethod(EndpointCategory.chatFunction + "_chooseImg", EndpointCategory.chatFunction, 100, object -> new ChatFunctionMenu("chooseImg", R.mipmap.icon_func_album, mContext.get().getString(R.string.image), this::chooseIMG));
        EndpointManager.getInstance().setMethod(EndpointCategory.chatFunction + "_chooseCard", EndpointCategory.chatFunction, 95, object -> new ChatFunctionMenu("chooseCard", R.mipmap.icon_func_card, mContext.get().getString(R.string.card), IConversationContext::sendCardMsg));
        EndpointManager.getInstance().setMethod(EndpointCategory.chatFunction + "_chooseFile", EndpointCategory.chatFunction, 90, object -> new ChatFunctionMenu("chooseFile", R.drawable.ic_func_file, mContext.get().getString(R.string.str_file), IConversationContext::chooseFile));

        //添加tab页
        EndpointManager.getInstance().setMethod(EndpointCategory.tabMenus + "_start_chat", EndpointCategory.tabMenus, 200, object -> new PopupMenuItem(mContext.get().getString(R.string.start_group_chat), R.mipmap.menu_chats, () -> {
            Intent intent = new Intent(mContext.get(), ChooseContactsActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
        }));
        EndpointManager.getInstance().setMethod(EndpointCategory.tabMenus + "_add_friends", EndpointCategory.tabMenus, 99, object -> new PopupMenuItem(mContext.get().getString(R.string.add_friends), R.mipmap.menu_invite, () -> {
            Intent intent = new Intent(mContext.get(), AddFriendsActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
        }));
        EndpointManager.getInstance().setMethod(EndpointCategory.tabMenus + "_create_category", EndpointCategory.tabMenus, 150, object -> new PopupMenuItem(mContext.get().getString(R.string.create_category), R.mipmap.menu_add, () ->
                EndpointManager.getInstance().invoke("show_create_category_dialog", null)
        ));

        //显示聊天页面
        EndpointManager.getInstance().setMethod(EndpointSID.chatView, object -> {
            if (object instanceof ChatViewMenu chatViewMenu) {
                if (!TextUtils.isEmpty(chatViewMenu.channelID)) {
                    WKIMUtils.getInstance().startChatActivity(chatViewMenu);
                }
            }
            return null;
        });

        //撤回消息
        EndpointManager.getInstance().setMethod("chat_withdraw_msg", object -> {
            final WithdrawMsgMenu withdrawMsgMenu = (WithdrawMsgMenu) object;
            if (withdrawMsgMenu != null) {
                MsgModel.getInstance().revokeMsg(withdrawMsgMenu.message_id, withdrawMsgMenu.channel_id, withdrawMsgMenu.channel_type, withdrawMsgMenu.client_msg_no, (code, msg) -> {
                    if (code != HttpResponseCode.success) {
                        WKToastUtils.getInstance().showToastNormal(msg);
                        //  WKIM.getInstance().getMsgManager().updateMsgRevokeWithMessageID(withdrawMsgMenu.message_id, 1);
//                        WKIM.getInstance().getMessageManager().deleteMsgByClientMsgNo(client_msg_no);
                    }
                });
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("str_delete_msg", object -> {
            WKMsg msg = (WKMsg) object;
            if (msg != null) {
                List<WKMsg> list = new ArrayList<>();
                list.add(msg);
                MsgModel.getInstance().deleteMsg(list, null);
            }
            return null;
        });
        //选择会话
        EndpointManager.getInstance().setMethod(EndpointSID.showChooseChatView, object -> {
            ChooseChatMenu messageContent = (ChooseChatMenu) object;
            Intent intent = new Intent(mContext.get(), ChooseChatActivity.class);
            intent.putExtra("isChoose", true);
            intent.putExtra("singleSelect", messageContent.singleSelect);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
            WKUIKitApplication.this.messageContentList = messageContent.list;
            WKUIKitApplication.this.chooseChatCallBack = messageContent.mChatChooseContacts;
            return null;
        });

        //处理扫一扫结果
        EndpointManager.getInstance().setMethod("", EndpointCategory.wkScan, object -> new ScanResultMenu(hashMap -> {
            String type = Objects.requireNonNull(hashMap.get("type")).toString();
            if (type.equals("userInfo")) {
                JSONObject dataJson = (JSONObject) hashMap.get("data");
                if (dataJson != null && dataJson.has("uid")) {
                    String uid = dataJson.optString("uid");
                    String verCode = dataJson.optString("vercode");
                    if (!TextUtils.isEmpty(uid)) {
                        Intent intent = new Intent(mContext.get(), UserDetailActivity.class);
                        intent.putExtra("uid", uid);
                        intent.putExtra("vercode", verCode);
                        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
                        mContext.get().startActivity(intent);
                    }
                }
                return true;
            } else return false;

        }));
        //选择联系人
        EndpointManager.getInstance().setMethod("choose_contacts", object -> {
            Intent intent = new Intent(mContext.get(), ChooseContactsActivity.class);
            intent.putExtra("type", 2);
            this.contactsMenu = (ChooseContactsMenu) object;
            if (contactsMenu != null) {
                intent.putParcelableArrayListExtra("defaultSelected", (ArrayList<? extends Parcelable>) contactsMenu.defaultSelected);
                intent.putExtra("isShowSaveLabelDialog", contactsMenu.isShowSaveLabelDialog);
                if (WKReader.isNotEmpty(contactsMenu.defaultSelected) && !contactsMenu.isCanDeselect) {
                    String unSelectUids = "";
                    for (int i = 0, size = contactsMenu.defaultSelected.size(); i < size; i++) {
                        if (TextUtils.isEmpty(unSelectUids)) {
                            unSelectUids = contactsMenu.defaultSelected.get(i).channelID;
                        } else
                            unSelectUids = unSelectUids + "," + contactsMenu.defaultSelected.get(i).channelID;
                    }
                    intent.putExtra("unSelectUids", unSelectUids);
                }
            }
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
            return null;
        });
        EndpointManager.getInstance().setMethod("exit_login", object -> {
            exitLogin(0);
            return null;
        });
        //查看用户详情
        EndpointManager.getInstance().setMethod(EndpointSID.userDetailView, object -> {
            UserDetailMenu wkUserDetailMenu = (UserDetailMenu) object;
            if (wkUserDetailMenu != null) {
                if (!TextUtils.isEmpty(wkUserDetailMenu.uid)) {
                    Intent intent = new Intent(mContext.get(), UserDetailActivity.class);
                    intent.putExtra("uid", wkUserDetailMenu.uid);
                    if (!TextUtils.isEmpty(wkUserDetailMenu.groupID)) {
                        intent.putExtra("groupID", wkUserDetailMenu.groupID);
                    }
                    wkUserDetailMenu.context.startActivity(intent);
                }

            }
            return null;
        });

        EndpointManager.getInstance().setMethod("set_skip_navigation", object -> {
            skipNavigation = object != null && (boolean) object;
            return null;
        });
        EndpointManager.getInstance().setMethod("show_tab_main", object -> {
            Intent intent = new Intent(mContext.get(), TabActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.get().startActivity(intent);
            return null;
        });
        //监听登录状态
        EndpointManager.getInstance().setMethod("uikit_login_menu", EndpointCategory.loginMenus, object -> new LoginMenu(() -> {
            Log.e("接受登录", "-->3");
            WKSharedPreferencesUtil.getInstance().putInt("wk_lock_screen_pwd_count", 5);
            WKSharedPreferencesUtil.getInstance().putBoolean("sync_friend", true);
            //初始化im
            WKUIKitApplication.getInstance().initIM();
            //初始化密钥
//            WKIM.getInstance().getSignalProtocolManager().init();
            UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
            if (userInfo != null) {
                WKIM.getInstance().getCMDManager().setRSAPublicKey(userInfo.rsa_public_key);
                WKIM.getInstance().getChannelManager().updateAvatarCacheKey(userInfo.uid, WKChannelType.PERSONAL, UUID.randomUUID().toString().replaceAll("-", ""));
            }
            SpaceModel.getInstance().invalidateCache();
            // 注册场景跳过导航（由注册页面自己跳引导页）；登录场景先获取Space再进主页
            if (skipNavigation) {
                skipNavigation = false;
            } else {
                MsgModel.getInstance().loadCurrentSpaceId();
                String cachedSpaceId = MsgModel.getInstance().getCurrentSpaceId();
                if (!TextUtils.isEmpty(cachedSpaceId)) {
                    Intent intent = new Intent(mContext.get(), TabActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    mContext.get().startActivity(intent);
                } else {
                    SpaceModel.getInstance().getMySpaces(new SpaceModel.ISpaceListListener() {
                        @Override
                        public void onResult(List<SpaceEntity> list) {
                            if (list != null && !list.isEmpty() && list.get(0).space_id != null) {
                                MsgModel.getInstance().setCurrentSpaceId(list.get(0).space_id, list.get(0).name);
                                Intent intent = new Intent(mContext.get(), TabActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                mContext.get().startActivity(intent);
                            } else {
                                // 用户没有Space，进入引导页
                                EndpointManager.getInstance().invoke("show_space_guide", null);
                            }
                        }

                        @Override
                        public void onError(int code, String msg) {
                            // 网络失败也进引导页，让用户重试
                            EndpointManager.getInstance().invoke("show_space_guide", null);
                        }
                    });
                }
            }
            startChat();
            ProhibitWordModel.Companion.getInstance().sync();
            MsgModel.getInstance().deleteFlameMsg();
            //更新文件传输助手时间
            // WKIM.getInstance().getConversationManager().updateLastMsgTime(WKSystemAccount.system_file_helper, WKChannelType.PERSONAL, TimeUtils.getInstance().getCurrentSeconds());
        }));

        EndpointManager.getInstance().setMethod("syncExtraMsg", object -> {
            if (object != null) {
                WKChannel channel = (WKChannel) object;
                MsgModel.getInstance().syncExtraMsg(channel.channelID, channel.channelType);
            }
            return null;
        });

        EndpointManager.getInstance().setMethod("deleteRemoteMsg", object -> {
            if (object instanceof String clientMsgNo) {
                WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
                if (msg != null) {
                    List<WKMsg> list = new ArrayList<>();
                    list.add(msg);
                    MsgModel.getInstance().deleteMsg(list, null);
                }
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("get_chat_uid_msg", object -> {
            if (object instanceof WKMsg2UiMsgMenu wkMsg2UiMsgMenu) {
                return WKIMUtils.getInstance().msg2UiMsg(wkMsg2UiMsgMenu.getIConversationContext(), wkMsg2UiMsgMenu.getWkMsg(), wkMsg2UiMsgMenu.getMemberCount(), wkMsg2UiMsgMenu.getShowNickName(), wkMsg2UiMsgMenu.isChoose());
            }
            return null;
        });

        // 搜索消息按群成员搜索
        EndpointManager.getInstance().setMethod("search_message_with_member", EndpointCategory.wkSearchChatContent, 101, object -> {
            if (object instanceof WKChannel) {
                if (((WKChannel) object).channelType == WKChannelType.GROUP) {
                    return new SearchChatContentMenu(WKBaseApplication.getInstance().getContext().getString(R.string.uikit_search_member), (channelID, channelType) -> {
                        Intent intent = new Intent(WKBaseApplication.getInstance().getContext(), WKAllMembersActivity.class);
                        intent.putExtra("channelID", ((WKChannel) object).channelID);
                        intent.putExtra("channelType", WKChannelType.GROUP);
                        intent.putExtra("searchMessage", true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        WKBaseApplication.getInstance().getContext().startActivity(intent);
                    });
                }
            }
            return null;
        });

        // 搜索消息按日期搜索
        EndpointManager.getInstance().setMethod("search_message_with_date", EndpointCategory.wkSearchChatContent, 96, object -> {
            if (object instanceof WKChannel) {
                return new SearchChatContentMenu(WKBaseApplication.getInstance().getContext().getString(R.string.uikit_search_for_date), (channelID, channelType) -> {
                    Intent intent = new Intent(WKBaseApplication.getInstance().getContext(), SearchWithDateActivity.class);
                    intent.putExtra("channel_id", ((WKChannel) object).channelID);
                    intent.putExtra("channel_type", ((WKChannel) object).channelType);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    WKBaseApplication.getInstance().getContext().startActivity(intent);
                });
            }
            return null;
        });


        // 搜索消息按图片搜索
        EndpointManager.getInstance().setMethod("search_message_with_img", EndpointCategory.wkSearchChatContent, 98, object -> {
            if (object instanceof WKChannel) {
                return new SearchChatContentMenu(WKBaseApplication.getInstance().getContext().getString(R.string.uikit_search_for_image), (channelID, channelType) -> {
                    Intent intent = new Intent(WKBaseApplication.getInstance().getContext(), SearchWithImgActivity.class);
                    intent.putExtra("channel_id", ((WKChannel) object).channelID);
                    intent.putExtra("channel_type", ((WKChannel) object).channelType);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    WKBaseApplication.getInstance().getContext().startActivity(intent);
                });
            }
            return null;
        });

        // 搜索消息按文件搜索
        EndpointManager.getInstance().setMethod("search_message_with_file", EndpointCategory.wkSearchChatContent, 97, object -> {
            if (object instanceof WKChannel) {
                return new SearchChatContentMenu(WKBaseApplication.getInstance().getContext().getString(R.string.uikit_search_for_file), (channelID, channelType) -> {
                    Intent intent = new Intent(WKBaseApplication.getInstance().getContext(), com.chat.uikit.chat.search.file.SearchWithFileActivity.class);
                    intent.putExtra("channel_id", ((WKChannel) object).channelID);
                    intent.putExtra("channel_type", ((WKChannel) object).channelType);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    WKBaseApplication.getInstance().getContext().startActivity(intent);
                });
            }
            return null;
        });

    }

    public void sendChooseChatBack(List<WKChannel> list) {
        String extraText = pendingExtraText;
        pendingExtraText = null;

        if (chooseChatCallBack != null) {
            chooseChatCallBack.iChoose.onResult(list);
            chooseChatCallBack = null;
        }

        // 媒体消息已由回调发出，延迟发送留言文字确保服务端顺序：内容在前、文字在后
        if (!TextUtils.isEmpty(extraText) && list != null) {
            List<WKChannel> channels = new ArrayList<>(list);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                WKTextContent textContent = new WKTextContent(extraText);
                for (WKChannel channel : channels) {
                    WKSendOptions options = new WKSendOptions();
                    options.setting.receipt = channel.receipt;
                    WKIM.getInstance().getMsgManager().sendWithOptions(textContent, channel, options);
                }
            }, 200);
        }
    }

    public List<WKMessageContent> getMessageContentList() {
        return messageContentList;
    }

    public void setChooseContactsBack(List<WKChannel> list) {
        if (contactsMenu != null) {
            contactsMenu.iChooseBack.onBack(list);
            contactsMenu = null;
        }
    }

    private ChatChooseContacts chooseChatCallBack;
    private ChooseContactsMenu contactsMenu;
    private List<WKMessageContent> messageContentList;
    private String pendingExtraText;

    public void exitLogin(int from) {
        MsgModel.getInstance().stopTimer();
        EndpointManager.getInstance().invoke("wk_logout", null);
        WKConfig.getInstance().clearInfo();
        WKIM.getInstance().getConnectionManager().disconnect(true);
        // 清除 WebView Cookie 和 Storage，防止 OIDC SSO 会话残留导致下次自动登录
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.WebStorage.getInstance().deleteAllData();
        ActManagerUtils.getInstance().clearAllActivity();
        EndpointManager.getInstance().invoke("main_show_home_view", from);
        //关闭UI层数据库
        WKBaseApplication.getInstance().closeDbHelper();

    }

    private void chooseIMG(IConversationContext iConversationContext) {
        String[] permissionStr = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
//        String permissionStr = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 33) {
//            permissionStr = Manifest.permission.READ_MEDIA_IMAGES;
            permissionStr = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO};
        }
        String desc = String.format(iConversationContext.getChatActivity().getString(R.string.album_permissions_desc), iConversationContext.getChatActivity().getString(R.string.app_name));
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                ChooseMimeType mimeType = ChooseMimeType.img;
                if (result) {
                    Object isRegisterVideo = EndpointManager.getInstance().invoke("is_register_video", null);
                    if (isRegisterVideo instanceof Boolean) {
                        boolean isRegister = (boolean) isRegisterVideo;
                        if (isRegister) {
                            mimeType = ChooseMimeType.all;
                        }
                    }
                    GlideUtils.getInstance().chooseIMG(iConversationContext.getChatActivity(), 9, true, mimeType, true, new GlideUtils.ISelectBack() {
                        @Override
                        public void onBack(List<ChooseResult> paths) {
                            if (paths.size() == 1 && paths.get(0).model == ChooseResultModel.video) {
//                                EndpointManager.getInstance().invoke("videoCompress",paths.get(0).path);
                                WKVideoContent videoContent = new WKVideoContent();
                                videoContent.coverLocalPath = WKMediaFileUtils.getInstance().getVideoCover(paths.get(0).path);
                                videoContent.localPath = paths.get(0).path;
                                videoContent.second = WKMediaFileUtils.getInstance().getVideoTime(paths.get(0).path) / 1000;
                                videoContent.size = WKFileUtils.getInstance().getFileSize(paths.get(0).path);
                                iConversationContext.sendMessage(videoContent);
                                return;
                            }

                            for (int i = 0, size = paths.size(); i < size; i++) {
                                String path = paths.get(i).path;
                                if (paths.get(i).model == ChooseResultModel.video) {
                                    WKVideoContent videoContent = new WKVideoContent();
                                    videoContent.coverLocalPath = WKMediaFileUtils.getInstance().getVideoCover(path);
                                    videoContent.localPath = path;
                                    videoContent.second = WKMediaFileUtils.getInstance().getVideoTime(path) / 1000;
                                    videoContent.size = WKFileUtils.getInstance().getFileSize(path);
                                    iConversationContext.sendMessage(videoContent);
                                } else {
                                    if (WKFileUtils.getInstance().isGif(path)) {
                                        Object isRegisterSticker = EndpointManager.getInstance().invoke("is_register_sticker", null);
                                        if (isRegisterSticker instanceof Boolean) {
                                            WKGifContent mGifContent = new WKGifContent();
                                            mGifContent.format = "gif";
                                            mGifContent.localPath = path;
                                            Bitmap bitmap = BitmapFactory.decodeFile(path);
                                            if (bitmap != null) {
                                                mGifContent.height = bitmap.getHeight();
                                                mGifContent.width = bitmap.getWidth();
                                            }
                                            iConversationContext.sendMessage(mGifContent);
                                            return;
                                        }
                                    }
                                    WKImageContent imageContent = new WKImageContent(path);
                                    iConversationContext.sendMessage(imageContent);

                                }

                            }
                        }

                        @Override
                        public void onCancel() {

                        }
                    });
                }
            }

            @Override
            public void clickResult(boolean isCancel) {
            }
        }, iConversationContext.getChatActivity(), desc, permissionStr);
    }

    public interface IShowChatConfirm {
        void onBack(@NonNull List<WKChannel> list, @NonNull List<WKMessageContent> messageContentList);
    }

    public void showChatConfirmDialog(@NonNull Context context, @NonNull List<WKChannel> list, @NonNull List<WKMessageContent> messageContentList, final IShowChatConfirm iShowChatConfirm) {
        View view = LayoutInflater.from(context).inflate(R.layout.chat_confirm_dialog_view, null, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        AvatarView avatarView = view.findViewById(R.id.avatarView);
        TextView nameTv = view.findViewById(R.id.nameTv);
        ImageView imageView = view.findViewById(R.id.imageView);
        TextView contentTv = view.findViewById(R.id.contentTv);
        if (list.size() == 1) {
            avatarView.showAvatar(list.get(0));
            String showName = list.get(0).channelRemark;
            if (TextUtils.isEmpty(showName)) showName = list.get(0).channelName;
            if (list.get(0).channelID.equals(WKSystemAccount.system_file_helper)) {
                showName = context.getString(R.string.wk_file_helper);
            }
            if (list.get(0).channelID.equals(WKSystemAccount.system_team)) {
                showName = context.getString(R.string.wk_system_notice);
            }
            nameTv.setText(showName);
            recyclerView.setVisibility(View.GONE);
            avatarView.setVisibility(View.VISIBLE);
            nameTv.setVisibility(View.VISIBLE);
        } else {
            class AvatarViewHolder extends RecyclerView.ViewHolder {
                final AvatarView avatarView;

                public AvatarViewHolder(@NonNull View itemView) {
                    super(itemView);
                    avatarView = itemView.findViewWithTag("avatar");
                }
            }
            recyclerView.setLayoutManager(new GridLayoutManager(context, 5));
            recyclerView.setAdapter(new RecyclerView.Adapter<AvatarViewHolder>() {
                @NonNull
                @Override
                public AvatarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    LinearLayout view1 = new LinearLayout(parent.getContext());
                    AvatarView avatarView1 = new AvatarView(parent.getContext());
                    avatarView1.setTag("avatar");
                    view1.addView(avatarView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 5, 5, 5));
                    return new AvatarViewHolder(view1);
                }

                @Override
                public void onBindViewHolder(@NonNull AvatarViewHolder holder, int position) {
                    holder.avatarView.setSize(40);
                    holder.avatarView.showAvatar(list.get(position));
                }

                @Override
                public int getItemCount() {
                    return list.size();
                }
            });
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 0, size = list.size(); i < size; i++) {
                String name = list.get(i).channelRemark;
                if (TextUtils.isEmpty(name)) name = list.get(i).channelName;
                if (list.get(i).channelID.equals(WKSystemAccount.system_file_helper)) {
                    name = context.getString(R.string.wk_file_helper);
                }
                if (list.get(i).channelID.equals(WKSystemAccount.system_team)) {
                    name = context.getString(R.string.wk_system_notice);
                }
                if (!TextUtils.isEmpty(name)) {
                    if (nameBuilder.length() > 0) nameBuilder.append("、");
                    nameBuilder.append(name);
                }
            }
            nameTv.setText(nameBuilder.toString());
            nameTv.setVisibility(View.VISIBLE);
            avatarView.setVisibility(View.GONE);
        }

        FrameLayout videoPreviewLayout = view.findViewById(R.id.videoPreviewLayout);
        ImageView videoCoverIv = view.findViewById(R.id.videoCoverIv);
        LinearLayout filePreviewLayout = view.findViewById(R.id.filePreviewLayout);
        ImageView filePreviewIconIv = view.findViewById(R.id.filePreviewIconIv);
        TextView filePreviewNameTv = view.findViewById(R.id.filePreviewNameTv);
        TextView filePreviewSizeTv = view.findViewById(R.id.filePreviewSizeTv);

        if (messageContentList.size() == 1) {
            WKMessageContent messageContent = messageContentList.get(0);
            if (messageContent.type == WKContentType.WK_IMAGE) {
                WKImageContent imgMsgModel = (WKImageContent) messageContent;
                ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                int[] ints = ImageUtils.getInstance().getImageWidthAndHeightToTalk(imgMsgModel.width, imgMsgModel.height);
                layoutParams.height = ints[1];
                layoutParams.width = ints[0];
                imageView.setLayoutParams(layoutParams);
                String showUrl;
                if (!TextUtils.isEmpty(imgMsgModel.localPath)) {
                    showUrl = imgMsgModel.localPath;
                    File file = new File(showUrl);
                    if (!file.exists()) {
                        showUrl = WKApiConfig.getShowUrl(imgMsgModel.url);
                    }
                } else {
                    showUrl = WKApiConfig.getShowUrl(imgMsgModel.url);
                }
                GlideUtils.getInstance().showImg(context, showUrl, ints[0], ints[1], imageView);
                imageView.setVisibility(View.VISIBLE);
                contentTv.setVisibility(View.GONE);
            } else if (messageContent.type == WKContentType.WK_VIDEO) {
                WKVideoContent videoContent = (WKVideoContent) messageContent;
                int[] ints = ImageUtils.getInstance().getImageWidthAndHeightToTalk(videoContent.width, videoContent.height);
                ViewGroup.LayoutParams coverParams = videoCoverIv.getLayoutParams();
                coverParams.width = ints[0];
                coverParams.height = ints[1];
                videoCoverIv.setLayoutParams(coverParams);
                String coverUrl = "";
                if (!TextUtils.isEmpty(videoContent.coverLocalPath)) {
                    File f = new File(videoContent.coverLocalPath);
                    if (f.exists()) coverUrl = videoContent.coverLocalPath;
                }
                if (TextUtils.isEmpty(coverUrl) && !TextUtils.isEmpty(videoContent.cover)) {
                    coverUrl = WKApiConfig.getShowUrl(videoContent.cover);
                }
                GlideUtils.getInstance().showImg(context, coverUrl, ints[0], ints[1], videoCoverIv);
                videoPreviewLayout.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                contentTv.setVisibility(View.GONE);
            } else if (messageContent instanceof WKFileContent) {
                WKFileContent fileContent = (WKFileContent) messageContent;
                filePreviewNameTv.setText(fileContent.name != null ? fileContent.name : "");
                filePreviewSizeTv.setText(WKFileProvider.formatFileSize(fileContent.size));
                WKFileProvider.setFileIcon(filePreviewIconIv, fileContent.extension, fileContent.name);
                filePreviewLayout.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                contentTv.setVisibility(View.GONE);
            } else {
                String content = messageContent.getDisplayContent();
                if (messageContent.type == WKContentType.WK_CARD) {
                    WKCardContent WKCardContent = (WKCardContent) messageContent;
                    content = content + WKCardContent.name;
                }
                contentTv.setText(content);
                imageView.setVisibility(View.GONE);
                contentTv.setVisibility(View.VISIBLE);
            }
        } else {
            imageView.setVisibility(View.GONE);
            contentTv.setVisibility(View.VISIBLE);
            contentTv.setText(String.format(context.getString(R.string.item_forward_count), messageContentList.size()));
        }
        EditText messageEt = view.findViewById(R.id.messageEt);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.send_to));

        builder.setView(view);
        builder.setPositiveButton(context.getString(R.string.sure), (dialog, which) -> {
            String extraText = messageEt.getText() != null ? messageEt.getText().toString().trim() : "";
            pendingExtraText = TextUtils.isEmpty(extraText) ? null : extraText;
            iShowChatConfirm.onBack(list, messageContentList);
        });
        builder.setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> {

        });

        AlertDialog dialog = builder.create();
        dialog.setBlurParams(1f, true, true);
        dialog.show();
        TextView sureTv = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
        sureTv.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));

    }

}
