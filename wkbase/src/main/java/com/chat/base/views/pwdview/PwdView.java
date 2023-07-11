package com.chat.base.views.pwdview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.ui.Theme;

/**
 * 2020-11-02 12:28
 * 密码输入
 */
public class PwdView extends LinearLayout {
    private final Context context;

    public PwdView(Context context) {
        this(context, null);
    }

    public PwdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        initView();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    private void initView() {
        View view = View.inflate(context, R.layout.wk_pwd_view_layout, null);
        Theme.setColorFilter(context, view.findViewById(R.id.closeIv),R.color.popupTextColor);
        ViewGroup.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        Animation animation = AnimationUtils.loadAnimation(context, R.anim.anim_zoom_in);
        setAnimation(animation);
        animation.start();
        addView(view, lp);
    }


    //普通密码设置
    public void showPwdView() {

//        findViewById(R.id.bottomView).setLayoutParams(new LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        findViewById(R.id.closeIv).setVisibility(VISIBLE);

        LinearLayout.LayoutParams linearParams = (LinearLayout.LayoutParams) findViewById(R.id.tv_pass1).getLayoutParams();
        linearParams.setMargins(0, 0, 0, 0);

        LinearLayout.LayoutParams linearParams2 = (LinearLayout.LayoutParams) findViewById(R.id.tv_pass2).getLayoutParams();
        linearParams.setMargins(0, 0, 0, 0);

        findViewById(R.id.tv_pass2).setLayoutParams(linearParams2);
        findViewById(R.id.tv_pass3).setLayoutParams(linearParams);
        findViewById(R.id.tv_pass4).setLayoutParams(linearParams2);
        findViewById(R.id.tv_pass5).setLayoutParams(linearParams);
        findViewById(R.id.tv_pass6).setLayoutParams(linearParams2);


        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int leftPadding = (int) (getWidth() * 0.1);
                int rightPadding = (int) (getWidth() * 0.1);
                int topPadding = (int) (getHeight() * 0.1);
                int bottomPadding = (int) (getHeight() * 0.1);
                setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
            }
        });
    }

    public void setBg() {
        findViewById(R.id.contentLayout).setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
    }

    public void hideCloseIV() {
        findViewById(R.id.closeIv).setVisibility(GONE);
    }

    public void setBottomTv(String content, int color, IBottomClick iBottomClick) {
        TextView bottomTv = findViewById(R.id.bottomTv);
        bottomTv.setText(content);
        bottomTv.setTextColor(color);
        bottomTv.setOnClickListener(v -> iBottomClick.click());
        bottomTv.setVisibility(VISIBLE);
    }

    public interface IBottomClick {
        void click();
    }
}
