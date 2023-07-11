package com.chat.uikit.view.voice;

import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

import kotlin.collections.ArrayDeque;

/**
 * 录制音频的控制器
 */
public class AudioRecordManager {

    private volatile static AudioRecordManager INSTANCE;
    private MediaRecorder mediaRecorder;
    private String audioFileName;
    private RecordStatus recordStatus = RecordStatus.STOP;

    public enum RecordStatus {
        READY,
        START,
        STOP
    }

    private AudioRecordManager() {

    }

    public static AudioRecordManager getInstance() {
        if (INSTANCE == null) {
            synchronized (AudioRecordManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AudioRecordManager();
                }
            }
        }
        return INSTANCE;
    }

    public void init(String audioFileName) {
        this.audioFileName = audioFileName;
        recordStatus = RecordStatus.READY;
    }

    public void startRecord() {
        if (recordStatus == RecordStatus.READY) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFileName);

            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaRecorder.start();
            dbs.clear();
            updateMicStatus();
            recordStatus = RecordStatus.START;
        }
    }

    private final Handler mHandler = new Handler();
    private final Runnable mUpdateMicStatusTimer = new Runnable() {
        public void run() {
            updateMicStatus();
        }
    };
    private final List<Integer> dbs = new ArrayDeque<>();

    private void updateMicStatus() {
        if (mediaRecorder == null)return;
        double ratio = mediaRecorder.getMaxAmplitude();
        double db = 0;// 分贝
        if (ratio > 1)
            db = 20 * Math.log10(ratio);
        Log.e("分贝大小", "--->" + db);
        dbs.add((int) db);
        mHandler.postDelayed(mUpdateMicStatusTimer, 100);

    }


    public void stopRecord() {
        if (recordStatus == RecordStatus.START && mediaRecorder != null) {
            try {
                mediaRecorder.setOnErrorListener(null);
                mediaRecorder.setOnInfoListener(null);
                mediaRecorder.setPreviewDisplay(null);
                mediaRecorder.stop();
                mHandler.removeCallbacks(mUpdateMicStatusTimer);
            } catch (Exception ignored) {
            } finally {
                mediaRecorder.release();
                mediaRecorder = null;
                recordStatus = RecordStatus.STOP;
                audioFileName = null;
            }

        }
    }

    public void cancelRecord() {
        if (recordStatus == RecordStatus.START) {
            String file = audioFileName;
            stopRecord();
            File file1 = new File(file);
            file1.delete();
        }
    }

    /**
     * 获得录音的音量，范围 0-32767, 归一化到0 ~ 1
     *
     * @return
     */
    public float getMaxAmplitude() {
        if (recordStatus == RecordStatus.START) {
            try {
                return mediaRecorder.getMaxAmplitude() * 1.0f / 32768;
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public byte[] getDbs() {
        byte[] bytes = new byte[dbs.size()];
        for (int i = 0; i < dbs.size(); i++) {
            byte bt = dbs.get(i).byteValue();
            if (bt == 0) bt = 2;
            bytes[i] = bt;
        }

//        if (bytes.length > 60.f) {
//            List<Byte> tempList =new ArrayList<>();
//            float proportion = 60.f / bytes.length;
//            float key = 0;
//            for (byte aByte : bytes) {
//                key += proportion;
//                if (key >= 1) {
//                    tempList.add(aByte);
//                    key = 0;
//                }
//            }
//
//            byte[] tempBytes = new byte[tempList.size()];
//            for (int i = 0; i < tempList.size(); i++) {
//                tempBytes[i] = tempList.get(i);
//            }
//            bytes = tempBytes;
//        }
        return bytes;
    }
}
