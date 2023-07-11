package com.chat.base.endpoint.entity;

import android.content.Context;
import android.view.View;

import java.lang.ref.WeakReference;

public class SearchChatEditStickerMenu {

    public String content;
    public View view;
    public final WeakReference<Context> context;
    public IResult iResult;
    public interface IResult {
        void onResult();
    }

    public SearchChatEditStickerMenu(Context context, String content, View view, IResult iResult) {
        this.content = content;
        this.view = view;
        this.context = new WeakReference<>(context);
        this.iResult = iResult;
    }

}
