package com.chat.base.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;

import com.chat.base.R;
import com.chat.base.WKBaseApplication;

import java.io.IOException;

public class WKPlaySound {
    private int soundIn;
    private int soundOut;
    private int soundRecord;
    private boolean soundInLoaded;
    private boolean soundOutLoaded;
    private boolean soundRecordLoaded;
    private SoundPool soundPool;

    private WKPlaySound() {
    }

    private static class PlaySoundBinder {
        static final WKPlaySound play = new WKPlaySound();
    }

    public static WKPlaySound getInstance() {
        return PlaySoundBinder.play;
    }

    public void playRecordMsg(int playID) {
        try {
            if (soundPool == null) {
                soundPool = new SoundPool(3, AudioManager.STREAM_SYSTEM, 0);
                soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                    if (status == 0) {
                        try {
                            soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            if (soundRecord == 0 && !soundRecordLoaded) {
                soundRecordLoaded = true;
                soundRecord = soundPool.load(WKBaseApplication.getInstance().getContext(), playID, 1);
            }
            if (soundRecord != 0) {
                try {
                    soundPool.play(soundRecord, 1.0f, 1.0f, 1, 0, 1.0f);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void playOutMsg(int playID) {
        try {
            if (soundPool == null) {
                soundPool = new SoundPool(3, AudioManager.STREAM_SYSTEM, 0);
                soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                    if (status == 0) {
                        try {
                            soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            if (soundOut == 0 && !soundOutLoaded) {
                soundOutLoaded = true;
                soundOut = soundPool.load(WKBaseApplication.getInstance().getContext(), playID, 1);
            }
            if (soundOut != 0) {
                try {
                    soundPool.play(soundOut, 1.0f, 1.0f, 1, 0, 1.0f);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void playInMsg(int playID) {
        try {
            if (soundPool == null) {
                soundPool = new SoundPool(3, AudioManager.STREAM_SYSTEM, 0);
                soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                    if (status == 0) {
                        try {
                            soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            if (soundIn == 0 && !soundInLoaded) {
                soundInLoaded = true;
                soundIn = soundPool.load(WKBaseApplication.getInstance().getContext(), playID, 1);
            }
            if (soundIn != 0) {
                try {
                    soundPool.play(soundIn, 1.0f, 1.0f, 1, 0, 1.0f);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

}
