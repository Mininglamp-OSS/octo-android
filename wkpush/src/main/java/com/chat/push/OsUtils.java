package com.chat.push;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * 2020-03-08 22:43
 * 手机型号判断
 */
public class OsUtils {

    private static final String ROM_MIUI = "MIUI";
    private static final String ROM_EMUI = "EMUI";
    private static final String ROM_FLYME = "FLYME";
    private static final String ROM_OPPO = "OPPO";
    private static final String ROM_SMARTISAN = "SMARTISAN";
    private static final String ROM_VIVO = "VIVO";
    private static final String ROM_QIKU = "QIKU";

    private static final String KEY_VERSION_MIUI = "ro.miui.ui.version.name";
    private static final String KEY_VERSION_EMUI = "ro.build.version.emui";
    private static final String KEY_VERSION_OPPO = "ro.build.version.opporom";
    private static final String KEY_VERSION_SMARTISAN = "ro.smartisan.version";
    private static final String KEY_VERSION_VIVO = "ro.vivo.os.version";

    private static String sName;
    private static String sVersion;

    public static boolean isEmui() {
        return check(ROM_EMUI);
    }

    public static boolean isMiui() {
        return check(ROM_MIUI);
    }

    public static boolean isVivo() {
        return check(ROM_VIVO);
    }

    public static boolean isOppo() {
        return check(ROM_OPPO);
    }

    public static boolean isFlyme() {
        return check(ROM_FLYME);
    }

    public static boolean is360() {
        return check(ROM_QIKU) || check("360");
    }

    public static boolean isSmartisan() {
        return check(ROM_SMARTISAN);
    }

    public static String getName() {
        if (sName == null) {
            check("");
        }
        return sName;
    }

    public static String getVersion() {
        if (sVersion == null) {
            check("");
        }
        return sVersion;
    }

    private static boolean check(String rom) {
        if (sName != null) {
            return sName.equals(rom);
        }

        if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_MIUI))) {
            sName = ROM_MIUI;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_EMUI))) {
            sName = ROM_EMUI;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_OPPO))) {
            sName = ROM_OPPO;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_VIVO))) {
            sName = ROM_VIVO;
        } else if (!TextUtils.isEmpty(sVersion = getProp(KEY_VERSION_SMARTISAN))) {
            sName = ROM_SMARTISAN;
        } else {
            sVersion = Build.DISPLAY;
            if (sVersion.toUpperCase().contains(ROM_FLYME)) {
                sName = ROM_FLYME;
            } else {
                sVersion = Build.UNKNOWN;
                sName = Build.MANUFACTURER.toUpperCase();
            }
        }
        return sName.equals(rom);
    }

    private static String getProp(String name) {
        String line;
        BufferedReader input = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + name);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
    }


    static void setBadge(Context context, int number) {
        if (isEmui()) {
            try {
                Bundle bundle = new Bundle();
                bundle.putString("package", WKPushApplication.getInstance().pushBundleID); // com.test.badge is your package name
                bundle.putString("class", WKPushApplication.getInstance().pushBundleID + ".MainActivity"); // com.test. badge.MainActivity is your apk main activity
                bundle.putInt("badgenumber", number);
                context.getContentResolver().call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null, bundle);
            } catch (Exception e) {
                Log.e("设置红点", "-->不支持");
            }
        } else if (isVivo()) {
            try {
                Intent intent = new Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM");
                intent.putExtra("packageName", context.getPackageName());
                // String launchClassName = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()).getComponent().getClassName();
                intent.putExtra("className", WKPushApplication.getInstance().pushBundleID + ".MainActivity");
                intent.putExtra("notificationNum", number);
                context.sendBroadcast(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (isOppo()) {
            try {
                if (number == 0) {
                    number = -1;
                }
                Intent intent = new Intent("com.oppo.unsettledevent");
                intent.putExtra("pakeageName", context.getPackageName());
                intent.putExtra("number", number);
                intent.putExtra("upgradeNumber", number);
                if (canResolveBroadcast(context, intent)) {
                    context.sendBroadcast(intent);
                } else {
                    try {
                        Bundle extras = new Bundle();
                        extras.putInt("app_badge_count", number);
                        context.getContentResolver().call(Uri.parse("content://com.android.badge/badge"), "setAppBadgeCount", null, extras);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public static boolean canResolveBroadcast(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> receivers = packageManager.queryBroadcastReceivers(intent, 0);
        return receivers != null && receivers.size() > 0;
    }
}
