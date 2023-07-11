package com.chat.uikit.view.voice;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.chat.uikit.R;

import java.util.LinkedList;
import java.util.List;

/**
 * 语音录制的动画效果
 * Created by chenxf on 17-6-12.
 */
public class LineWaveVoiceView extends View {
    private static final String TAG = LineWaveVoiceView.class.getSimpleName();

    private Paint paint;
    //矩形波纹颜色
    private int lineColor;
    //矩形波纹宽度
    private float lineWidth;
    private float textSize;
    private static final String DEFAULT_TEXT = " 倒计时 1:00 ";
    private String text = DEFAULT_TEXT;
    private int textColor;
    private boolean isStart = false;
    private Runnable mRunable;

    private final int LINE_W = 9;//默认矩形波纹的宽度，9像素, 原则上从layout的attr获得
    private final int MIN_WAVE_H = 2;//最小的矩形线高，是线宽的2倍，线宽从lineWidth获得
    private final int MAX_WAVE_H = 7;//最高波峰，是线宽的4倍

    //默认矩形波纹的高度，总共10个矩形，左右各有10个
    private final int[] DEFAULT_WAVE_HEIGHT = {2, 3, 4, 3, 2, 2, 2, 2, 2, 2};
    private final LinkedList<Integer> mWaveList = new LinkedList<>();

    private final RectF rectRight = new RectF();//右边波纹矩形的数据，10个矩形复用一个rectF
    private final RectF rectLeft = new RectF();//左边波纹矩形的数据

    LinkedList<Integer> list = new LinkedList<>();

    private static final int UPDATE_INTERVAL_TIME = 100;//100ms更新一次

    public LineWaveVoiceView(Context context) {
        super(context);
    }

    public LineWaveVoiceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LineWaveVoiceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint();
        resetList(list, DEFAULT_WAVE_HEIGHT);

        mRunable = () -> {
            while (isStart) {
                refreshElement();
                try {
                    Thread.sleep(UPDATE_INTERVAL_TIME);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                postInvalidate();
            }
        };
        TypedArray mTypedArray = context.obtainStyledAttributes(attrs, R.styleable.LineWaveVoiceView);
        lineColor = mTypedArray.getColor(R.styleable.LineWaveVoiceView_voiceLineColor, Color.parseColor("#ff9c00"));
        lineWidth = mTypedArray.getDimension(R.styleable.LineWaveVoiceView_voiceLineWidth, LINE_W);
        textSize = mTypedArray.getDimension(R.styleable.LineWaveVoiceView_voiceTextSize, 42);
        textColor = mTypedArray.getColor(R.styleable.LineWaveVoiceView_voiceTextColor, Color.parseColor("#666666"));
        mTypedArray.recycle();
    }

    public void setLineColor(int color) {
        lineColor = color;
        paint.setColor(lineColor);
        invalidate();
    }

    public void setTextColor(int color) {
        textColor = color;
        paint.setColor(textColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int widthcentre = getWidth() / 2;
        int heightcentre = getHeight() / 2;

        //更新时间
        paint.setStrokeWidth(0);
        paint.setColor(textColor);
        paint.setTextSize(textSize);
        float textWidth = paint.measureText(text);
        canvas.drawText(text, widthcentre - textWidth / 2, heightcentre - (paint.ascent() + paint.descent()) / 2, paint);

        //更新左右两边的波纹矩形
        paint.setColor(lineColor);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(lineWidth);
        paint.setAntiAlias(true);
        for (int i = 0; i < 10; i++) {
            //右边矩形
            rectRight.left = widthcentre + 2 * i * lineWidth + textWidth / 2 + lineWidth;
            rectRight.top = heightcentre - list.get(i) * lineWidth / 2;
            rectRight.right = widthcentre + 2 * i * lineWidth + 2 * lineWidth + textWidth / 2;
            rectRight.bottom = heightcentre + list.get(i) * lineWidth / 2;

            //左边矩形
            rectLeft.left = widthcentre - (2 * i * lineWidth + textWidth / 2 + 2 * lineWidth);
            rectLeft.top = heightcentre - list.get(i) * lineWidth / 2;
            rectLeft.right = widthcentre - (2 * i * lineWidth + textWidth / 2 + lineWidth);
            rectLeft.bottom = heightcentre + list.get(i) * lineWidth / 2;

            canvas.drawRoundRect(rectRight, 6, 6, paint);
            canvas.drawRoundRect(rectLeft, 6, 6, paint);
        }
    }

    private synchronized void refreshElement() {
        float maxAmp = AudioRecordManager.getInstance().getMaxAmplitude();
        int waveH = MIN_WAVE_H + Math.round(maxAmp * (MAX_WAVE_H - 2));//wave 在 2 ~ 7 之间
        list.add(0, waveH);
        list.removeLast();
    }

    public synchronized void setText(String text) {
        this.text = text;
        postInvalidate();
    }

    public synchronized void startRecord() {
        isStart = true;
        ThreadPool.getInstance().getCachedPools().execute(mRunable);
    }

    public synchronized void stopRecord() {
        isStart = false;
        mWaveList.clear();
        resetList(list, DEFAULT_WAVE_HEIGHT);
        text = DEFAULT_TEXT;
        postInvalidate();
    }

    private void resetList(List list, int[] array) {
        list.clear();
        for (int i = 0; i < array.length; i++) {
            list.add(array[i]);
        }
    }
}