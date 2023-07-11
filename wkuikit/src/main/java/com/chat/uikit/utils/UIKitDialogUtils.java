package com.chat.uikit.utils;

import android.content.Context;

import com.chat.uikit.view.UpdateNameInGroupView;
import com.lxj.xpopup.XPopup;

/**
 * 2020-03-01 16:45
 */
public class UIKitDialogUtils {
    private UIKitDialogUtils() {
    }

    private static class KitDialogUtilsBinder {
        private static final UIKitDialogUtils uikit = new UIKitDialogUtils();
    }

    public static UIKitDialogUtils getInstance() {
        return KitDialogUtilsBinder.uikit;
    }

    public void showUpdateNameInGroupDialog(Context context, String groupNo, final UpdateNameInGroupView.IUpdateListener iUpdateListener) {
        new XPopup.Builder(context).moveUpToKeyboard(true).autoOpenSoftInput(true).asCustom(new UpdateNameInGroupView(context, groupNo, iUpdateListener)).show();
    }

}
