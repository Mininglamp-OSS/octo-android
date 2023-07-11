package com.chat.base.views;

import com.chat.base.R;

/**
 * 2020-07-01 23:05
 * 底部提示弹框实体
 */
public class BottomEntity {
    public String content;
    public int textColor;
    public boolean isCanClick;
    public String subContent;
    public int subColor;

    public BottomEntity(String content, int textColor, boolean isCanClick) {
        this.content = content;
        this.textColor = textColor;
        this.isCanClick = isCanClick;
        this.subColor = R.color.color999;
    }

    public BottomEntity(String content, int textColor) {
        this.content = content;
        this.textColor = textColor;
        this.isCanClick = true;
        this.subColor = R.color.color999;
    }

    public BottomEntity(String content) {
        this.content = content;
        this.textColor = R.color.colorDark;
        this.subColor = R.color.color999;
        this.isCanClick = true;
    }

    public BottomEntity(String content, String subContent) {
        this.content = content;
        this.subContent = subContent;
        this.textColor = R.color.colorDark;
        this.subColor = R.color.color999;
        this.isCanClick = true;
    }

    public BottomEntity(String content, String subContent, int textColor, int subColor) {
        this.content = content;
        this.subColor = R.color.color999;
        this.subContent = subContent;
        this.textColor = textColor;
        this.subColor = subColor;
    }
}
