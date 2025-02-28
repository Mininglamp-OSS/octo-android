package com.chat.base.act;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.chat.base.R;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

/**
 * 4/7/21 3:21 PM
 */
public class VideoPlayer extends StandardGSYVideoPlayer {
    public VideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public VideoPlayer(Context context) {
        super(context);
    }

    public VideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void init(Context context) {
        post(new Runnable() {
            @Override
            public void run() {
                gestureDetector = new GestureDetector(getContext().getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        touchDoubleUp(e);
                        return super.onDoubleTap(e);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (!mChangePosition && !mChangeVolume && !mBrightness) {
                            onClickUiToggle(e);
                        }
                        return super.onSingleTapConfirmed(e);
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        super.onLongPress(e);
                        if (iLongClick != null)
                            iLongClick.onLongClick();
                    }
                });
            }
        });
        super.init(context);
    }

    private ILongClick iLongClick;

    public void setLongClick(ILongClick iLongClick) {
        this.iLongClick = iLongClick;
    }

    public interface ILongClick {
        void onLongClick();
    }


    @Override
    public int getLayoutId() {
        return R.layout.video_layout_normal;
    }

    @Override
    protected void updateStartImage() {
        if(mStartButton instanceof ImageView imageView) {
            if (mCurrentState == CURRENT_STATE_PLAYING) {
                imageView.setImageResource(com.shuyu.gsyvideoplayer.R.drawable.video_click_pause_selector);
            } else if (mCurrentState == CURRENT_STATE_ERROR) {
                imageView.setImageResource(com.shuyu.gsyvideoplayer.R.drawable.video_click_play_selector);
            } else {
                imageView.setImageResource(com.shuyu.gsyvideoplayer.R.drawable.video_click_play_selector);
            }
        }
    }
}
