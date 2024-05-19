package com.chat.base.utils;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.tbruyelle.rxpermissions3.RxPermissions;

public class WKPermissions {
    private WKPermissions() {
    }

    private static class PermissionsBinder {
        final static WKPermissions permissions = new WKPermissions();
    }

    public static WKPermissions getInstance() {
        return PermissionsBinder.permissions;
    }

    public void checkPermissions(final IPermissionResult iPermissionResult, FragmentActivity activity, String authDesc, String... permissions) {
        RxPermissions rxPermissions = new RxPermissions(activity);
        rxPermissions.request(permissions).subscribe(aBoolean -> {
            if (!aBoolean) {
                WKDialogUtils.getInstance().showDialog(activity, activity.getString(R.string.authorization_request), authDesc ,false,activity.getString(R.string.cancel), activity.getString(R.string.to_set),0, Theme.colorAccount, index -> {
                    if (index == 1) {
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    }
                    iPermissionResult.clickResult(index == 0);
                });
            }
            iPermissionResult.onResult(aBoolean);
        });
    }

    public interface IPermissionResult {
        void onResult(boolean result);

        void clickResult(boolean isCancel);
    }
}
