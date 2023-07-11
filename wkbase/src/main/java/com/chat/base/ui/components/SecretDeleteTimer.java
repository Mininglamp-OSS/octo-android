package com.chat.base.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.core.content.res.ResourcesCompat;

import com.chat.base.R;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.AndroidUtilities;
import com.xinbida.wukongim.WKIM;

public class SecretDeleteTimer extends FrameLayout {

    private final Paint afterDeleteProgressPaint;
    private final Paint circlePaint;
    private final Paint particlePaint;
    private final RectF deleteProgressRect = new RectF();
    private final TimerParticles timerParticles = new TimerParticles();

    private long destroyTtl;
    private boolean useVideoProgress;

    private final Drawable drawable;
    private int size = 64;
    private String clientMsgNo;
    private long viewedTime;


    public SecretDeleteTimer(Context context) {
        super(context);
        setWillNotDraw(false);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        particlePaint.setColor(0xffe6e6e6);
        particlePaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setStyle(Paint.Style.STROKE);

        afterDeleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        afterDeleteProgressPaint.setStyle(Paint.Style.STROKE);
        afterDeleteProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        afterDeleteProgressPaint.setColor(0xffe6e6e6);
        afterDeleteProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(0x7f000000);

        drawable = ResourcesCompat.getDrawable(context.getResources(), R.mipmap.flame_small, null);
//        Bitmap oldBmp = BitmapFactory.decodeResource(context.getResources(), R.mipmap.flame_small);
//        Bitmap newBmp = Bitmap.createScaledBitmap(oldBmp,AndroidUtilities.dp(8), AndroidUtilities.dp(8), true);
//        drawable = new BitmapDrawable(context.getResources(), newBmp);
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setDestroyTime(String clientMsgNo, int flameSecond, long viewedTime, boolean videoProgress) {
        this.clientMsgNo = clientMsgNo;
        destroyTtl = flameSecond * 1000L;
        useVideoProgress = videoProgress;
        this.viewedTime = viewedTime;
        if (viewedTime > 0) {
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int y = getMeasuredHeight() / 2 - AndroidUtilities.dp(size - 6) / 2;
        deleteProgressRect.set(getMeasuredWidth() - AndroidUtilities.dp(size - 3), y, getMeasuredWidth() - AndroidUtilities.dp(3), y + AndroidUtilities.dp(size - 6));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
//        if (currentMessageObject == null || currentMessageObject.messageOwner.destroyTime == 0) {
//            return;
//        }

        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, AndroidUtilities.dp(size / 2), circlePaint);

        float progress;

        if (useVideoProgress) {
//            if (videoPlayer != null) {
//                long duration = videoPlayer.getDuration();
//                long position = videoPlayer.getCurrentPosition();
//                if (duration != C.TIME_UNSET && position != C.TIME_UNSET) {
//                    progress = 1.0f - (position / (float) duration);
//                } else {
//                    progress = 1;
//                }
//            } else {
//                progress = 1;
//            }
            progress = 1;
        } else {
            long x = System.currentTimeMillis() - viewedTime; // 10ms
            float temp = (float) x;
            if (x - destroyTtl >= 0) {
                progress = 0;
            } else
                progress = (destroyTtl - temp) / (destroyTtl);
//            long msTime = System.currentTimeMillis() + x;
//            float v = Math.max(0, destroyTime - msTime);
//            progress = v / (destroyTtl);
//            Log.e("查看时间", x + "_" + msTime + "_" + v);
        }
        int x = (getMeasuredWidth() / 2) - AndroidUtilities.dp(5);
        int y = ((getMeasuredHeight()) / 2) - AndroidUtilities.dp(7);
        drawable.setBounds(x, y, x + AndroidUtilities.dp(10), y + AndroidUtilities.dp(14));
        drawable.draw(canvas);

        if (progress == 0 && viewedTime > 0) {
            EndpointManager.getInstance().invoke("deleteRemoteMsg", clientMsgNo);
            WKIM.getInstance().getMsgManager().deleteWithClientMsgNO(clientMsgNo);
            return;
        }
        if (viewedTime != 0) {
            float radProgress = -360 * progress;
            canvas.drawArc(deleteProgressRect, -90, radProgress, false, afterDeleteProgressPaint);
            timerParticles.draw(canvas, particlePaint, deleteProgressRect, radProgress, 1.0f);
            if (progress != 0 && viewedTime > 0) {
                invalidate();
            }
        }

    }
}