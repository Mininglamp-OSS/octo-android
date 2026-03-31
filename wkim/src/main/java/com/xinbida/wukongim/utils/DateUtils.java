package com.xinbida.wukongim.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 2019-11-10 16:32
 * 日期时间处理
 */
public class DateUtils {
    private DateUtils() {

    }

    private static class DateUtilsBinder {
        private static final DateUtils DATE_UTILS = new DateUtils();
    }

    public static DateUtils getInstance() {
        return DateUtilsBinder.DATE_UTILS;
    }

    public String time2DateStr(long timeStamp) {
        if (String.valueOf(timeStamp).length() < 13) {
            timeStamp = timeStamp * 1000;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }


    // 毫秒
    public long getCurrentMills() {
        return System.currentTimeMillis();
    }

    // 秒
    public long getCurrentSeconds() {
        return (System.currentTimeMillis() / 1000);
    }

    // 小时
    public int getHour() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR);
    }

    // 分钟
    public int getMinute() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.MINUTE);
    }

}
