package com.chat.uikit.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.uikit.R;

/** Shared entry point for report pages, which use the currently selected API server. */
public final class ReportNavigation {
    private ReportNavigation() {
    }

    public static boolean open(Context context, String channelId, byte channelType) {
        String url = WKApiConfig.getReportUrl(context, channelId, channelType);
        if (url == null || url.isEmpty()) {
            Toast.makeText(context, R.string.report_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent intent = new Intent(context, WKWebViewActivity.class);
        intent.putExtra("channelType", channelType);
        intent.putExtra("channelID", channelId);
        intent.putExtra("url", url);
        context.startActivity(intent);
        return true;
    }
}
