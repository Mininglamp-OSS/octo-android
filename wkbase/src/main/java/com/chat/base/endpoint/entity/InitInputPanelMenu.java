package com.chat.base.endpoint.entity;

import android.view.View;
import android.widget.FrameLayout;

import com.chat.base.msg.IConversationContext;

import java.lang.ref.WeakReference;

public class InitInputPanelMenu {
    public WeakReference<IConversationContext> iConversationContext;
    public FrameLayout frameLayout;
    public View bottomView;

    public InitInputPanelMenu(View bottomView, IConversationContext iConversationContext, FrameLayout frameLayout) {
        this.iConversationContext = new WeakReference<>(iConversationContext);
        this.frameLayout = frameLayout;
        this.bottomView = bottomView;
    }
}
