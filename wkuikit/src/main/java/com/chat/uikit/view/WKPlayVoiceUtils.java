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

package com.chat.uikit.view;

import android.media.MediaPlayer;
import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 2021/8/2 17:01
 */
public class WKPlayVoiceUtils {
    private WKPlayVoiceUtils() {

    }

    private static class PlayVoiceUtilsBinder {
        static WKPlayVoiceUtils playVoiceUtils = new WKPlayVoiceUtils();
    }

    public static WKPlayVoiceUtils getInstance() {
        return PlayVoiceUtilsBinder.playVoiceUtils;
    }

    private List<IPlayListener> iPlayListener;
    private String oldPlayKey;
    public MediaPlayer mediaPlayer;

    public String getPlayKey() {
        return oldPlayKey;
    }

    public void playVoice(String voicePath, String playKey) {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying())
                mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        this.oldPlayKey = playKey;
        try {
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mediaPlayer.seekTo(position);
                    mediaPlayer.start();
                    handler.post(runnable);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            handler.removeCallbacks(runnable);
                            for (int i = 0; i < iPlayListener.size(); i++) {
                                iPlayListener.get(i).onCompletion(oldPlayKey);
                            }
                            position = 0;
                        }
                    });
                }
            });
            mediaPlayer.reset();
            mediaPlayer.setDataSource(voicePath);
            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String getOldPlayKey() {
        return oldPlayKey;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void stopPlay() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            handler.removeCallbacks(runnable);
            position = 0;
            try {
                for (int i = 0; i < iPlayListener.size(); i++) {
                    iPlayListener.get(i).onStop(oldPlayKey);
                }
                //    mediaPlayer.prepare();
                //    mediaPlayer.seekTo(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onPause() {
        if (mediaPlayer != null) {
            handler.removeCallbacks(runnable);
            mediaPlayer.pause();
        }
    }

    private int position;
    Handler handler = new Handler();
    public Runnable runnable = new Runnable() {

        @Override
        public void run() {
            position = mediaPlayer.getCurrentPosition();
            int total = mediaPlayer.getDuration();
            for (int i = 0; i < iPlayListener.size(); i++) {
                iPlayListener.get(i).onProgress(oldPlayKey, (float) position / total);
            }
            handler.postDelayed(runnable, 100);
        }
    };

    public void setPlayListener(IPlayListener iPlayListener) {
        if (this.iPlayListener == null) this.iPlayListener = new ArrayList<>();
        this.iPlayListener.add(iPlayListener);
    }

    public void removePlayListener(IPlayListener listener) {
        if (this.iPlayListener != null) {
            this.iPlayListener.remove(listener);
        }
    }

    public interface IPlayListener {
        void onCompletion(String key);

        void onProgress(String key, float pg);

        void onStop(String key);
    }
}
