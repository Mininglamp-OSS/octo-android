package com.chat.base.endpoint.entity;

import android.graphics.Bitmap;

import androidx.appcompat.app.AppCompatActivity;

public class ParseQrCodeMenu {
    public boolean isJump;
    public Bitmap bitmap;
    public IResult iResult;
    public AppCompatActivity activity;

    public ParseQrCodeMenu(AppCompatActivity activity, Bitmap bitmap, boolean isJump, IResult iResult) {
        this.bitmap = bitmap;
        this.isJump = isJump;
        this.iResult = iResult;
        this.activity = activity;
    }

    public interface IResult {
        void onResult(String codeContentStr);
    }
}
