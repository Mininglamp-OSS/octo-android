package com.chat.base.endpoint.entity;

import android.content.Context;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

public class UserDetailViewMenu {
    public WeakReference<Context> context;
    public String uid;
    public String groupNo;
    public ViewGroup parentView;

    public UserDetailViewMenu(Context context, ViewGroup parentView, String uid, String groupNo) {
        this.context = new WeakReference<>(context);
        this.groupNo = groupNo;
        this.uid = uid;
        this.parentView = parentView;
    }
}
