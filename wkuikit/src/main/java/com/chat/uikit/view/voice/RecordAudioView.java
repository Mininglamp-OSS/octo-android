package com.chat.uikit.view.voice;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatButton;

import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.view.WKPlayVoiceUtils;


public class RecordAudioView extends AppCompatButton {
    private IRecordAudioListener recordAudioListener;
    private AudioRecordManager audioRecordManager;
    private boolean isCanceled;
    private float downPointY;
    private static final float DEFAULT_SLIDE_HEIGHT_CANCEL = 150;
    private boolean isRecording;


    public RecordAudioView(Context context) {
        super(context);
        initView(context);
    }

    public RecordAudioView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public RecordAudioView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        audioRecordManager = AudioRecordManager.getInstance();
    }

    long downTime = 0;
    long upTime = 0;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (recordAudioListener != null) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = WKTimeUtils.getInstance().getCurrentMills();
                    if (downTime - upTime > 1000) {
                        WKPlayVoiceUtils.getInstance().stopPlay();
                        setSelected(true);
                        downPointY = event.getY();
                        recordAudioListener.onFingerPress();
                        startRecordAudio();
                    }
                    break;
                case MotionEvent.ACTION_UP:
//                    long nowTime = WKTimeUtils.getInstance().getCurrentMills();
//                    if (nowTime - downTime >= 1000) {
//                        setSelected(false);
//                        onFingerUp();
//                    } else {
//                        isRecording = false;
//                        audioRecordManager.cancelRecord();
//                        recordAudioListener.onRecordCancel();
//                    }
//                    upTime = TimeUtils.getInstance().getCurrentMills();

                    onFingerUp();
                    break;
                case MotionEvent.ACTION_MOVE:
                    upTime = WKTimeUtils.getInstance().getCurrentMills();
                    onFingerMove(event);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    upTime = WKTimeUtils.getInstance().getCurrentMills();
                    isCanceled = true;
                    onFingerUp();
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    /**
     * 手指抬起,可能是取消录制也有可能是录制成功
     */
    private void onFingerUp() {
        setSelected(false);
        if (isRecording) {
            if (isCanceled) {
                isRecording = false;
                audioRecordManager.cancelRecord();
                recordAudioListener.onRecordCancel();
            } else {
                stopRecordAudio();
            }
        }
        downTime = 0;
        upTime = 0;
        downPointY = 0;

    }

    private void onFingerMove(MotionEvent event) {
        float currentY = event.getY();
        isCanceled = checkCancel(currentY);
        if (isCanceled) {
            recordAudioListener.onSlideTop();
            // onFingerUp();
        } else {
            recordAudioListener.onFingerPress();
        }
    }

    private boolean checkCancel(float currentY) {
        return downPointY - currentY >= DEFAULT_SLIDE_HEIGHT_CANCEL;
    }

    /**
     * 检查是否ready录制,如果已经ready则开始录制
     */
    private void startRecordAudio() throws RuntimeException {
        boolean isPrepare = recordAudioListener.onRecordPrepare();
        if (isPrepare) {
            String audioFileName = recordAudioListener.onRecordStart();
            //准备就绪开始录制
            try {
                audioRecordManager.init(audioFileName);
                audioRecordManager.startRecord();
                isRecording = true;
            } catch (Exception e) {
                this.recordAudioListener.onRecordCancel();
            }
        }
    }

    /**
     * 停止录音
     */
    private void stopRecordAudio() throws RuntimeException {
        if (isRecording) {
            try {
                isRecording = false;
                audioRecordManager.stopRecord();
                this.recordAudioListener.onRecordStop();
            } catch (Exception e) {
                this.recordAudioListener.onRecordCancel();
            }
        }
    }

    /**
     * 需要设置IRecordAudioStatus,来监听开始录音结束录音等操作,并对权限进行处理
     *
     * @param recordAudioListener
     */
    public void setRecordAudioListener(IRecordAudioListener recordAudioListener) {
        this.recordAudioListener = recordAudioListener;
    }

    public void invokeStop() {
        onFingerUp();
    }

    public interface IRecordAudioListener {
        boolean onRecordPrepare();

        String onRecordStart();

        boolean onRecordStop();

        boolean onRecordCancel();

        void onSlideTop();

        void onFingerPress();
    }
}
