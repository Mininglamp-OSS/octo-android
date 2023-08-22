package com.chat.uikit.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.utils.AndroidUtilities;
import com.scwang.smart.drawable.ProgressDrawable;
import com.scwang.smart.refresh.classics.ArrowDrawable;
import com.scwang.smart.refresh.layout.api.RefreshHeader;
import com.scwang.smart.refresh.layout.api.RefreshKernel;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.constant.RefreshState;
import com.scwang.smart.refresh.layout.constant.SpinnerStyle;

/**
 * 2020-01-16 11:41
 * im聊天页面下拉头
 */
@SuppressLint("RestrictedApi")
public class ClassicsHeader extends LinearLayout implements RefreshHeader {

    private final TextView mHeaderText;//标题文本
    private final ImageView mArrowView;//下拉箭头
    private final ImageView mProgressView;//刷新动画视图
    private final ProgressDrawable mProgressDrawable;//刷新动画

    public ClassicsHeader(Context context) {
        this(context, null);
    }

    public ClassicsHeader(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        setGravity(Gravity.CENTER);
        mHeaderText = new TextView(context);
        mProgressDrawable = new ProgressDrawable();
        mArrowView = new ImageView(context);
        mProgressView = new ImageView(context);
        mProgressView.setImageDrawable(mProgressDrawable);
        mArrowView.setImageDrawable(new ArrowDrawable());
        addView(mProgressView, AndroidUtilities.dp( 20), AndroidUtilities.dp( 20));
        addView(mArrowView, AndroidUtilities.dp( 20), AndroidUtilities.dp( 20));
        addView(new Space(context), AndroidUtilities.dp( 20), AndroidUtilities.dp( 20));
        addView(mHeaderText, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        setMinimumHeight(AndroidUtilities.dp( 50));
    }

    @NonNull
    public View getView() {
        return this;//真实的视图就是自己，不能返回null
    }

    @NonNull
    @Override
    public SpinnerStyle getSpinnerStyle() {
        return SpinnerStyle.Translate;//指定为平移，不能null
    }

    @Override
    public void onStartAnimator(@NonNull RefreshLayout layout, int height, int maxDragHeight) {
        mProgressDrawable.start();//开始动画
    }


    @Override
    public int onFinish(@NonNull RefreshLayout layout, boolean success) {
        mProgressDrawable.stop();//停止动画
        mProgressView.setVisibility(GONE);//隐藏动画
        if (success) {
            // mHeaderText.setText("刷新完成");
        } else {
            //mHeaderText.setText("刷新失败");
        }
        return 500;//延迟500毫秒之后再弹回
    }

    @Override
    public void onStateChanged(@NonNull RefreshLayout refreshLayout, @NonNull RefreshState oldState, @NonNull RefreshState newState) {
        switch (newState) {
            case None:
            case PullDownToRefresh:
                //mHeaderText.setText("下拉开始刷新");
                mArrowView.setVisibility(VISIBLE);//显示下拉箭头
                mProgressView.setVisibility(GONE);//隐藏动画
                mArrowView.animate().rotation(0);//还原箭头方向
                break;
            case Refreshing:
                //mHeaderText.setText("正在刷新");
                mProgressView.setVisibility(VISIBLE);//显示加载动画
                mArrowView.setVisibility(GONE);//隐藏箭头
                break;
            case ReleaseToRefresh:
                //mHeaderText.setText("释放立即刷新");
                mArrowView.animate().rotation(180);//显示箭头改为朝上
                break;
        }
    }

    @Override
    public void setPrimaryColors(int... colors) {

    }

    @Override
    public void onInitialized(@NonNull RefreshKernel kernel, int height, int maxDragHeight) {

    }

    @Override
    public void onMoving(boolean isDragging, float percent, int offset, int height, int maxDragHeight) {

    }

//        @Override
//        public void onPulling(float percent, int offset, int height, int maxDragHeight) {
//
//        }
//        @Override
//        public void onReleasing(float percent, int offset, int height, int maxDragHeight) {
//
//        }

    @Override
    public void onReleased(@NonNull RefreshLayout refreshLayout, int height, int maxDragHeight) {

    }

    @Override
    public void onHorizontalDrag(float percentX, int offsetX, int offsetMax) {

    }

    @Override
    public boolean isSupportHorizontalDrag() {
        return false;
    }

    @Override
    public boolean autoOpen(int duration, float dragRate, boolean animationOnly) {
        return false;
    }
}
