package com.chat.base.endpoint.entity;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.fragment.app.Fragment;

public class EditImgMenu {
    public Fragment fragment;
    public String path;
    public int requestCode;
    public IBack iBack;
    public Context context;
    public boolean isShowSaveDialog;
    public EditImgMenu(Context context, boolean isShowSaveDialog, String path, Fragment fragment, int requestCode, IBack iBack) {
        this.context = context;
        this.fragment = fragment;
        this.path = path;
        this.requestCode = requestCode;
        this.isShowSaveDialog = isShowSaveDialog;
        this.iBack = iBack;
    }

    public interface IBack {
        void onBack(Bitmap bitmap, String path);
    }
}
