package com.chat.uikit.chat.face;

import android.view.View;
import android.widget.EditText;

import com.chat.base.msg.IConversationContext;
import com.chat.base.net.voice.WKVoiceInputService;
import com.chat.base.utils.WKCommonUtils;
import com.chat.uikit.view.voice.AudioRecordManager;
import com.chat.uikit.view.voice.SpeechToTextView;
import com.chat.uikit.view.voice.TalkBackView;
import com.chat.uikit.view.voice.VoiceInputView;
import com.chat.uikit.view.voice.WKVoicePanelView;
import com.xinbida.wukongim.msgmodel.WKVoiceContent;

import org.jetbrains.annotations.NotNull;

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
            });
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
