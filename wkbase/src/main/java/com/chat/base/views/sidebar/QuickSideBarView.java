package com.chat.base.views.sidebar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.chat.base.R;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;

import java.util.Arrays;
import java.util.List;

/**
 * 快速选择侧边栏
 * Created by Sai on 16/3/25.
 */
public class QuickSideBarView extends View {

    private OnQuickSideBarTouchListener listener;
    private List<String> mLetters;
    private int mChoose = -1;
    private final Paint mPaint = new Paint();
    private float mTextSize;
    private float mTextSizeChoose;
    private int mTextColor;
    private int mTextColorChoose;
    private int mWidth;
    private int mHeight;
    private float mItemHeight;
    private float mItemStartY;

    public QuickSideBarView(Context context) {
        this(context, null);
    }

    public QuickSideBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickSideBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mLetters = Arrays.asList(context.getResources().getStringArray(R.array.quickSideBarLetters));

        mTextColor = context.getResources().getColor(android.R.color.black);
        mTextColorChoose = context.getResources().getColor(android.R.color.black);
        mTextSize = context.getResources().getDimensionPixelSize(R.dimen.font_size_10);
        mTextSizeChoose = context.getResources().getDimensionPixelSize(R.dimen.font_size_16);
        mItemHeight = context.getResources().getDimension(R.dimen.font_size_20);
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.QuickSideBarView);

            mTextColor = a.getColor(R.styleable.QuickSideBarView_sidebarTextColor, mTextColor);
            mTextColorChoose = a.getColor(R.styleable.QuickSideBarView_sidebarTextColorChoose, mTextColorChoose);
            mTextSize = a.getDimension(R.styleable.QuickSideBarView_sidebarTextSize, mTextSize);
            mTextSizeChoose = a.getDimension(R.styleable.QuickSideBarView_sidebarTextSizeChoose, mTextSizeChoose);
            mItemHeight = a.getDimension(R.styleable.QuickSideBarView_sidebarItemHeight, mItemHeight);
            a.recycle();
        }
    }

    public void setTextChooseColor(int color) {
        mTextColorChoose = color;
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < mLetters.size(); i++) {
            mPaint.setColor(mTextColor);

            mPaint.setAntiAlias(true);
            mPaint.setTextSize(mTextSize);
            if (i == mChoose) {
                mPaint.setColor(mTextColorChoose);
                mPaint.setFakeBoldText(true);
                mPaint.setTypeface(Typeface.DEFAULT_BOLD);
                mPaint.setTextSize(mTextSizeChoose);
            }


            //计算位置
            Rect rect = new Rect();
            mPaint.getTextBounds(mLetters.get(i), 0, mLetters.get(i).length(), rect);
            float xPos = (int) ((mWidth - rect.width()) * 0.5);
            float yPos = mItemHeight * i + (int) ((mItemHeight - rect.height()) * 0.5) + mItemStartY;


            canvas.drawText(mLetters.get(i), xPos, yPos, mPaint);
            mPaint.reset();
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mHeight = getMeasuredHeight();
        mWidth = getMeasuredWidth();
        mItemStartY = (mHeight - mLetters.size() * mItemHeight) / 2;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final float y = event.getY();
        final int oldChoose = mChoose;
        final int newChoose = (int) ((y - mItemStartY) / mItemHeight);
        switch (action) {
            case MotionEvent.ACTION_UP:
                mChoose = -1;
                if (listener != null) {
                    listener.onLetterTouching(false);
                }
                invalidate();
                break;
            default:
                if (oldChoose != newChoose) {
                    if (newChoose >= 0 && newChoose < mLetters.size()) {
                        mChoose = newChoose;
                        if (listener != null) {
                            //计算位置
                            Rect rect = new Rect();
                            mPaint.getTextBounds(mLetters.get(mChoose), 0, mLetters.get(mChoose).length(), rect);
                            float yPos = mItemHeight * mChoose + (int) ((mItemHeight - rect.height()) * 0.5) + mItemStartY;
                            listener.onLetterChanged(mLetters.get(newChoose), mChoose, yPos);
                        }
                    }
                    invalidate();
                }
                //如果是cancel也要调用onLetterUpListener 通知
                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (listener != null) {
                        listener.onLetterTouching(false);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_DOWN) {//按下调用 onLetterDownListener
                    if (listener != null) {
                        listener.onLetterTouching(true);
                    }
                }

                break;
        }
        return true;
    }

    public OnQuickSideBarTouchListener getListener() {
        return listener;
    }

    public void setOnQuickSideBarTouchListener(OnQuickSideBarTouchListener listener) {
        this.listener = listener;
    }

    public List<String> getLetters() {
        return mLetters;
    }

    /**
     * 设置字母表
     *
     * @param letters
     */
    public void setLetters(List<String> letters) {
        this.mLetters = letters;
        invalidate();
    }
}

