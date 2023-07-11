package com.chat.base.endpoint.entity;

import android.content.Context;

import org.jetbrains.annotations.NotNull;

/**
 * 2020-11-25 18:37
 * 个人资料
 */
public class UserDetailMenu extends BaseEndpoint {
    public String uid;
    public String groupID;
    public Context context;
    public UserDetailMenu(@NotNull Context context, String uid) {
        this.uid = uid;
        this.context = context;
    }

    public UserDetailMenu(@NotNull Context context,String uid, String groupID) {
        this.uid = uid;
        this.groupID = groupID;
        this.context = context;
    }
}
