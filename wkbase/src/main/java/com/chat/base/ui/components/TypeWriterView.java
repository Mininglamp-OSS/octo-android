package com.chat.base.ui.components;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatTextView;

public class TypeWriterView extends AppCompatTextView {

    private CharSequence mText;
    private String mPrintingText;
    private int mIndex;
    private final long mDelay = 100;

    private Context mContext;

    private boolean animating = false;

    private Runnable mBlinker;
    private int i = 0;
    private final Handler mHandler = new Handler();
    private final Runnable mCharacterAdder = new Runnable() {
        @Override
        public void run() {
            if (animating) {
                setText(String.format("%s _", mText.subSequence(0, mIndex++)));
                //typing typed
                if (mIndex <= mText.length()) {
                    mHandler.postDelayed(mCharacterAdder, mDelay);
                } else {
                    animating = false;
                    callBlink();
                }
            }
        }
    };

    public TypeWriterView(Context context) {
        super(context);
        mContext = context;
    }

    public TypeWriterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void append(String str) {
        mText = mText + str;
        mPrintingText = mPrintingText + str;
        if (!animating) {
            mHandler.removeCallbacks(mCharacterAdder);
            animating = true;
            mHandler.removeCallbacks(mBlinker);
            mHandler.postDelayed(mCharacterAdder, mDelay);
        }
    }

    private void callBlink() {
        mBlinker = new Runnable() {
            @Override
            public void run() {
                if (i <= 10) {
                    if (i++ % 2 != 0) {
                        setText(String.format("%s _", mText));
                        mHandler.postDelayed(mBlinker, 150);
                    } else {
                        setText(String.format("%s   ", mText));
                        mHandler.postDelayed(mBlinker, 150);
                    }
                } else
                    i = 0;
            }
        };
        mHandler.postDelayed(mBlinker, 150);
    }


    public void animateText(String text) {
        if (!animating) {
            animating = true;
            mText = text;
            mPrintingText = text;
            mIndex = 0;
            setText("");
            mHandler.removeCallbacks(mCharacterAdder);
            mHandler.postDelayed(mCharacterAdder, mDelay);
        } else {
            //CAUTION: Already typing something..
            Toast.makeText(mContext, "Typewriter busy typing: " + mText, Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Call this to remove animation at any time
     */
    public void removeAnimation() {
        mHandler.removeCallbacks(mCharacterAdder);

        animating = false;
        setText(mPrintingText);

    }

}