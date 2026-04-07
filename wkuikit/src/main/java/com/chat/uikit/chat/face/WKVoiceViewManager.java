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
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKVoiceContent;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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

    public View getVoiceView(IConversationContext iConversationContext) {
        panelView = new WKVoicePanelView(iConversationContext.getChatActivity());

        // Fetch voice config to determine if voice input tab should be shown
        WKVoiceInputService.getInstance().fetchConfig((config, error) -> {
            if (config != null && config.getEnabled()) {
                panelView.setVoiceInputEnabled(true);
            } else {
                panelView.setVoiceInputEnabled(false);
            }
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
     * 构建 chat_context：取最近 10 条文本消息，格式为 [displayName]:content
     */
    private String buildChatContext(IConversationContext context) {
        ChatAdapter adapter = context.getChatAdapter();
        if (adapter == null) return null;

        List<WKUIChatMsgItemEntity> allData = adapter.getData();
        if (allData == null || allData.isEmpty()) return null;

        // Filter text messages
        List<WKMsg> textMessages = new ArrayList<>();
        for (WKUIChatMsgItemEntity item : allData) {
            if (item.wkMsg == null) continue;
            if (item.wkMsg.type != WKContentType.WK_TEXT) continue;
            if (item.wkMsg.baseContentMsgModel == null) continue;
            String displayContent = item.wkMsg.baseContentMsgModel.getDisplayContent();
            if (TextUtils.isEmpty(displayContent)) continue;
            textMessages.add(item.wkMsg);
        }

        if (textMessages.isEmpty()) return null;

        // Take last 10
        int count = Math.min(textMessages.size(), 10);
        List<WKMsg> recent = textMessages.subList(textMessages.size() - count, textMessages.size());

        // Format as [displayName]:content
        StringBuilder sb = new StringBuilder();
        String loginUID = WKConfig.getInstance().getUid();
        for (int i = 0; i < recent.size(); i++) {
            WKMsg msg = recent.get(i);
            String senderName = resolveSenderName(msg, loginUID);
            String content = msg.baseContentMsgModel.getDisplayContent();

            if (i > 0) sb.append("\n");
            sb.append("[").append(senderName).append("]:").append(content);
        }

        return sb.toString();
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
}
