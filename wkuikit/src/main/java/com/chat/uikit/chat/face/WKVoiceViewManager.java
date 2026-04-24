package com.chat.uikit.chat.face;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.chat.base.config.WKConfig;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.net.voice.WKVoiceInputService;
import com.chat.uikit.view.voice.SpeechToTextView;
import com.chat.uikit.view.voice.TalkBackView;
import com.chat.uikit.view.voice.VoiceInputView;
import com.chat.uikit.view.voice.WKVoicePanelView;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKVoiceContent;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 语音管理类 - 三 Tab 语音面板
 */
public class WKVoiceViewManager {

    private WKVoiceViewManager() {
    }

    private static class VoiceViewManagerBinder {
        final static WKVoiceViewManager manager = new WKVoiceViewManager();
    }

    public static WKVoiceViewManager getInstance() {
        return VoiceViewManagerBinder.manager;
    }

    private WKVoicePanelView panelView;
    private int panelGeneration;

    public View getVoiceView(IConversationContext iConversationContext) {
        panelView = new WKVoicePanelView(iConversationContext.getChatActivity());
        final int generation = ++panelGeneration;

        // Fetch voice config to determine if voice input tab should be shown
        WKVoiceInputService.getInstance().fetchConfig((config, error) -> {
            // panelView 可能已被 release() 置空，或已被新的 getVoiceView() 替换
            if (panelView == null || generation != panelGeneration) return;
            panelView.setVoiceInputEnabled(config != null && config.getEnabled());
            panelView.setup();
            connectCallbacks(iConversationContext);
        });

        // Default: setup without voice input, will be reconfigured after config fetch
        panelView.setVoiceInputEnabled(false);
        panelView.setup();
        connectCallbacks(iConversationContext);

        return panelView;
    }

    private void connectCallbacks(IConversationContext iConversationContext) {
        // TalkBack: send voice message (same as original)
        TalkBackView talkBack = panelView.getTalkBackView();
        if (talkBack != null) {
            talkBack.setListener(new TalkBackView.TalkBackViewListener() {
                @Override
                public void onSendRecord(int seconds, @NotNull String audioPath, @NotNull String waveform) {
                    WKVoiceContent voiceContent = new WKVoiceContent(audioPath, seconds);
                    voiceContent.waveform = waveform;
                    iConversationContext.sendMessage(voiceContent);
                }
            });
        }

        // Speech-to-Text: insert recognized text into EditText
        SpeechToTextView sttView = panelView.getSpeechToTextView();
        if (sttView != null) {
            sttView.setListener(new SpeechToTextView.SpeechToTextListener() {
                @Override
                public void onRecognizedText(@NotNull String text) {
                    insertTextToEditText(iConversationContext, text);
                }

                @Override
                public void onRecordingStarted() {
                    // Can be used to pause audio playback
                }
            });
        }

        // Voice Input: insert transcribed text into EditText
        VoiceInputView voiceInput = panelView.getVoiceInputView();
        if (voiceInput != null) {
            voiceInput.setListener(new VoiceInputView.VoiceInputListener() {
                @Override
                public void onTranscribed(@NotNull String text, boolean shouldReplace) {
                    if (shouldReplace) {
                        setEditTextContent(iConversationContext, text);
                    } else {
                        insertTextToEditText(iConversationContext, text);
                    }
                }

                @Override
                public void onRecordingStarted() {
                }

                @Override
                public void onRecordingStopped() {
                }

                @Override
                public String getCurrentInputText() {
                    EditText editText = getEditText(iConversationContext);
                    return editText != null ? editText.getText().toString() : null;
                }

                @Override
                public String getChatContext() {
                    return buildChatContext(iConversationContext);
                }

                @Override
                public void onInsertText(@NotNull String text) {
                    insertTextToEditText(iConversationContext, text);
                }

                @Override
                public void onDeleteBackward() {
                    deleteBackwardInEditText(iConversationContext);
                }
            });
        }
    }

    /**
     * 构建 chat_context：注入聊天成员名称 + 最近 10 条文本消息
     * 格式：聊天成员：张三,李四,王五\n[张三]: 消息内容
     */
    private String buildChatContext(IConversationContext context) {
        WKChannel channel = context.getChatChannelInfo();
        String loginUID = WKConfig.getInstance().getUid();

        // 1. 收集成员名称
        Set<String> names = new LinkedHashSet<>();
        if (channel != null && channel.channelType == WKChannelType.GROUP) {
            int memberCount = WKIM.getInstance().getChannelMembersManager()
                    .getMemberCount(channel.channelID, channel.channelType);
            if (memberCount <= 100) {
                // 策略1：≤100人，收集全部成员名称
                List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager()
                        .getMembers(channel.channelID, channel.channelType);
                for (WKChannelMember member : members) {
                    if (loginUID.equals(member.memberUID)) continue;
                    if (member.isDeleted == 1) continue;
                    addNameIfValid(names, member.memberName);
                    if (!TextUtils.equals(member.remark, member.memberName)) {
                        addNameIfValid(names, member.remark);
                    }
                }
            } else {
                // 策略2：>100人，只收集最近消息中活跃发言者的名称
                Set<String> activeUIDs = collectActiveUIDs(context, loginUID);
                List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager()
                        .getMembers(channel.channelID, channel.channelType);
                for (WKChannelMember member : members) {
                    if (!activeUIDs.contains(member.memberUID)) continue;
                    if (member.isDeleted == 1) continue;
                    addNameIfValid(names, member.memberName);
                    if (!TextUtils.equals(member.remark, member.memberName)) {
                        addNameIfValid(names, member.remark);
                    }
                }
            }
        } else if (channel != null && channel.channelType == WKChannelType.PERSONAL) {
            // 私聊：收集对方的名称和备注
            addNameIfValid(names, channel.channelName);
            if (!TextUtils.equals(channel.channelRemark, channel.channelName)) {
                addNameIfValid(names, channel.channelRemark);
            }
        }

        // 2. 收集最近 10 条文本消息
        List<String> msgLines = buildMessageLines(context, loginUID);

        // 3. 拼接结果
        List<String> parts = new ArrayList<>();
        if (!names.isEmpty()) {
            parts.add("聊天成员：" + TextUtils.join(",", names));
        }
        if (!msgLines.isEmpty()) {
            parts.add(TextUtils.join("\n", msgLines));
        }

        return parts.isEmpty() ? null : TextUtils.join("\n", parts);
    }

    private void addNameIfValid(Set<String> names, String name) {
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(name.trim())) {
            names.add(name);
        }
    }

    /**
     * 从最近消息中提取活跃发言者 UID，最多 100 个
     */
    private Set<String> collectActiveUIDs(IConversationContext context, String loginUID) {
        Set<String> activeUIDs = new LinkedHashSet<>();
        ChatAdapter adapter = context.getChatAdapter();
        if (adapter == null) return activeUIDs;

        List<WKUIChatMsgItemEntity> allData = adapter.getData();
        if (allData == null) return activeUIDs;

        for (int i = allData.size() - 1; i >= 0 && activeUIDs.size() < 100; i--) {
            WKMsg msg = allData.get(i).wkMsg;
            if (msg != null && msg.fromUID != null && !msg.fromUID.equals(loginUID)) {
                activeUIDs.add(msg.fromUID);
            }
        }
        return activeUIDs;
    }

    /**
     * 取最近 10 条文本消息，格式化为 [displayName]: content
     */
    private List<String> buildMessageLines(IConversationContext context, String loginUID) {
        List<String> lines = new ArrayList<>();
        ChatAdapter adapter = context.getChatAdapter();
        if (adapter == null) return lines;

        List<WKUIChatMsgItemEntity> allData = adapter.getData();
        if (allData == null || allData.isEmpty()) return lines;

        List<WKMsg> textMessages = new ArrayList<>();
        for (WKUIChatMsgItemEntity item : allData) {
            if (item.wkMsg == null) continue;
            if (item.wkMsg.type != WKContentType.WK_TEXT) continue;
            if (item.wkMsg.baseContentMsgModel == null) continue;
            String displayContent = item.wkMsg.baseContentMsgModel.getDisplayContent();
            if (TextUtils.isEmpty(displayContent)) continue;
            textMessages.add(item.wkMsg);
        }

        int count = Math.min(textMessages.size(), 10);
        List<WKMsg> recent = textMessages.subList(textMessages.size() - count, textMessages.size());
        for (WKMsg msg : recent) {
            String senderName = resolveSenderName(msg, loginUID);
            String content = msg.baseContentMsgModel.getDisplayContent();
            lines.add("[" + senderName + "]: " + content);
        }
        return lines;
    }

    private String resolveSenderName(WKMsg msg, String loginUID) {
        if (msg.fromUID == null) return "Unknown";
        if (msg.fromUID.equals(loginUID)) return "Me";

        WKChannel channel = WKIM.getInstance().getChannelManager()
                .getChannel(msg.fromUID, WKChannelType.PERSONAL);
        if (channel != null) {
            if (!TextUtils.isEmpty(channel.channelRemark)) {
                return channel.channelRemark;
            }
            if (!TextUtils.isEmpty(channel.channelName)) {
                return channel.channelName;
            }
        }
        return msg.fromUID;
    }

    private void deleteBackwardInEditText(IConversationContext context) {
        EditText editText = getEditText(context);
        if (editText == null) return;
        int start = editText.getSelectionStart();
        if (start > 0) {
            editText.getText().delete(start - 1, start);
        }
    }

    private void insertTextToEditText(IConversationContext context, String text) {
        EditText editText = getEditText(context);
        if (editText != null) {
            int start = editText.getSelectionStart();
            if (start < 0) start = editText.getText().length();
            editText.getText().insert(start, text);
        }
    }

    private void setEditTextContent(IConversationContext context, String text) {
        EditText editText = getEditText(context);
        if (editText != null) {
            editText.setText(text);
            editText.setSelection(text.length());
        }
    }

    private EditText getEditText(IConversationContext context) {
        try {
            View chatView = context.getChatActivity().getWindow().getDecorView();
            // The editText ID is defined in the chat layout
            return chatView.findViewById(com.chat.uikit.R.id.editText);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Prefetch voice config when entering a conversation
     */
    public void prefetchConfig() {
        WKVoiceInputService.getInstance().prefetchConfig();
    }

    /**
     * Cancel all recordings when leaving conversation
     */
    public void cancelAll() {
        if (panelView != null) {
            panelView.cancelAllRecording();
        }
    }

    /**
     * 释放 panelView 引用，防止单例持有已销毁的 Activity 上下文导致内存泄漏。
     * 应在 ChatActivity#onDestroy 中调用。
     */
    public void release() {
        if (panelView != null) {
            panelView.cancelAllRecording();
            panelView = null;
        }
    }
}
