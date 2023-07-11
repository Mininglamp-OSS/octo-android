package com.chat.uikit.chat.face;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chat.base.msg.IConversationContext;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKCommonUtils;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.chat.uikit.view.voice.AudioRecordManager;
import com.chat.uikit.view.voice.LineWaveVoiceView;
import com.chat.uikit.view.voice.RecordAudioView;
import com.xinbida.wukongim.msgmodel.WKVoiceContent;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * 1/1/21 9:39 PM
 * 语音管理类
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

    private String[] recordStatusDescription;
    private Handler mainHandler;
    private long recordTotalTime = 0;
    private final long maxRecordTime = 60000;
    private final long minRecordTime = 1000;
    private Timer timer;
    private TimerTask timerTask;
    private String audioFileName;
    private RecordAudioView recordAudioView;
    private TextView tvRecordTips;
    private LinearLayout layoutCancelView;
    private LineWaveVoiceView mHorVoiceView;

    public View getVoiceView(IConversationContext iConversationContext) {

        View view = LayoutInflater.from(iConversationContext.getChatActivity()).inflate(R.layout.frag_recording_voice_layout, null);
        recordAudioView = view.findViewById(R.id.ivRecording);
        tvRecordTips = view.findViewById(R.id.record_tips);
        layoutCancelView = view.findViewById(R.id.pp_layout_cancel);
        mHorVoiceView = view.findViewById(R.id.waveVoiceView);
        mHorVoiceView.setTextColor(Theme.colorAccount);
        mHorVoiceView.setLineColor(Theme.colorAccount);
        recordAudioView.setRecordAudioListener(new RecordAudioView.IRecordAudioListener() {
            @Override
            public boolean onRecordPrepare() {
                return true;
            }

            @Override
            public String onRecordStart() {
                recordTotalTime = 0;
                initTimer();
                timer.schedule(timerTask, 0, 1000);
                audioFileName = WKUIKitApplication.getInstance().getContext().getExternalCacheDir() + File.separator + createAudioName();
                mHorVoiceView.startRecord();
                return audioFileName;
            }

            @Override
            public boolean onRecordStop() {
                Log.e("录制的总时长：", recordTotalTime + "");
                if (recordTotalTime >= minRecordTime) {
                    timer.cancel();
                    // TODO: 2020-06-15  录制完成
//                    int duration = AudioPlaybackManager.getDuration(audioFileName);
                    int time = (int) recordTotalTime / 1000;
                    if (time <= 0) return false;
                    WKVoiceContent audioMsgModel = new WKVoiceContent(audioFileName, time);
                    byte[] dbs = AudioRecordManager.getInstance().getDbs();
                    audioMsgModel.waveform = WKCommonUtils.getInstance().base64Encode(dbs);
                    iConversationContext.sendMessage(audioMsgModel);
                }
                onRecordCancel();
                return false;
            }

            @Override
            public boolean onRecordCancel() {
                if (timer != null) {
                    timer.cancel();
                }
                updateCancelUi();
                return false;
            }

            @Override
            public void onSlideTop() {
                mHorVoiceView.setVisibility(View.INVISIBLE);
                tvRecordTips.setVisibility(View.INVISIBLE);
                layoutCancelView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFingerPress() {
                mHorVoiceView.setVisibility(View.VISIBLE);
                tvRecordTips.setVisibility(View.VISIBLE);
                tvRecordTips.setText(recordStatusDescription[1]);
                layoutCancelView.setVisibility(View.INVISIBLE);
            }
        });
        recordStatusDescription = new String[]{
                iConversationContext.getChatActivity().getString(R.string.press_talk),
                iConversationContext.getChatActivity().getString(R.string.hold_to_record)
        };
        mainHandler = new Handler(Looper.myLooper());
        return view;
    }

    /**
     * 初始化计时器用来更新倒计时
     */
    private void initTimer() {
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(() -> {
                    //每隔1000毫秒更新一次ui
                    recordTotalTime = recordTotalTime + 1000;
                    updateTimerUI();
                });
            }
        };
    }

    private void updateTimerUI() {
        if (recordTotalTime >= maxRecordTime) {
            recordAudioView.invokeStop();
        } else {
            String content = recordAudioView.getContext().getString(R.string.time_remaining);
            String string = String.format(" %s %s ", content, StringUtils.formatRecordTime(recordTotalTime, maxRecordTime));
            mHorVoiceView.setText(string);
        }
    }

    private void updateCancelUi() {
        mHorVoiceView.setVisibility(View.INVISIBLE);
        tvRecordTips.setVisibility(View.VISIBLE);
        layoutCancelView.setVisibility(View.INVISIBLE);
        tvRecordTips.setText(recordStatusDescription[0]);
        mHorVoiceView.stopRecord();
        //   deleteTempFile();
    }


    private void deleteTempFile() {
        //取消录制后删除文件
        if (audioFileName != null) {
            File tempFile = new File(audioFileName);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String createAudioName() {
        long time = System.currentTimeMillis();
        return UUID.randomUUID().toString().replaceAll("-", "") + "_" + time + ".amr";
    }

}
