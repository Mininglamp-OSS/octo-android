package com.chat.base.utils;

import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.chat.base.utils.language.WKLanguageType;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 2019-12-01 11:40
 * 时间处理
 */
public class WKTimeUtils {
    private WKTimeUtils() {
    }

    private static class TimeUtilsBinder {
        private final static WKTimeUtils utils = new WKTimeUtils();
    }

    public static WKTimeUtils getInstance() {
        return TimeUtilsBinder.utils;
    }

    public String getTimeSpace(long time) {
        Date date = new Date(time);
        SimpleDateFormat df = new SimpleDateFormat("HH", Locale.getDefault());
        String str = df.format(date);
        int a = Integer.parseInt(str);
        if (a <= 12) {
            return WKBaseApplication.getInstance().getContext().getString(R.string.time_am);
        } else {
            return WKBaseApplication.getInstance().getContext().getString(R.string.time_pm);
        }
    }

    public String getTimeString(long timestamp) {
        String result;
        //  String[] weekNames = {WKBaseApplication.getInstance().getContext().getString(R.string.weak_7), WKBaseApplication.getInstance().getContext().getString(R.string.weak_1), WKBaseApplication.getInstance().getContext().getString(R.string.weak_2), WKBaseApplication.getInstance().getContext().getString(R.string.weak_3), WKBaseApplication.getInstance().getContext().getString(R.string.weak_4), WKBaseApplication.getInstance().getContext().getString(R.string.weak_5), WKBaseApplication.getInstance().getContext().getString(R.string.weak_6)};
        String hourTimeFormat = "HH:mm";
        String monthTimeFormat = "M-d HH:mm";
        String yearTimeFormat = "yyyy-M-d HH:mm";
        try {
            Calendar todayCalendar = Calendar.getInstance();
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);

            if (todayCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {//当年
                if (todayCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) {//当月
                    int temp = todayCalendar.get(Calendar.DAY_OF_MONTH) - calendar.get(Calendar.DAY_OF_MONTH);
                    switch (temp) {
                        case 0://今天
                            result = getTime(timestamp, hourTimeFormat);
                            break;
                        case 1://昨天
                            result = WKBaseApplication.getInstance().getContext().getString(R.string.yesterday) + getTime(timestamp, hourTimeFormat);
                            break;
//                        case 2:
//                        case 3:
//                        case 4:
//                        case 5:
//                        case 6:
//                            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
//                            result = weekNames[dayOfWeek - 1] + " " + getTime(timestamp, hourTimeFormat);
//                            break;
                        default:
                            result = getTime(timestamp, monthTimeFormat);
                            break;
                    }
                } else {
                    result = getTime(timestamp, monthTimeFormat);
                }
            } else {
                result = getTime(timestamp, yearTimeFormat);
            }
            return result;
        } catch (Exception e) {
            Log.e("getTimeString", e.getMessage());
            return "";
        }
    }

    String[] dayNames = {WKBaseApplication.getInstance().getContext().getString(R.string.sunday), WKBaseApplication.getInstance().getContext().getString(R.string.monday), WKBaseApplication.getInstance().getContext().getString(R.string.tuesday), WKBaseApplication.getInstance().getContext().getString(R.string.wednesday), WKBaseApplication.getInstance().getContext().getString(R.string.thursday), WKBaseApplication.getInstance().getContext().getString(R.string.friday), WKBaseApplication.getInstance().getContext().getString(R.string.saterday)};

    public String getNewChatTime(long timeStamp) {
        String result;
        Calendar todayCalendar = Calendar.getInstance();
        Calendar otherCalendar = Calendar.getInstance();
        otherCalendar.setTimeInMillis(timeStamp);

        String timeFormat;
        String yearTimeFormat;

        timeFormat = "M/d";
        yearTimeFormat = "yyyy/M/d";
        if (WKLanguageType.isCN()) {
            timeFormat = "M月d日";
            yearTimeFormat = "yyyy年M月d日";
        }
        boolean yearTemp = todayCalendar.get(Calendar.YEAR) == otherCalendar.get(Calendar.YEAR);
        if (yearTemp) {
            int todayMonth = todayCalendar.get(Calendar.MONTH);
            int otherMonth = otherCalendar.get(Calendar.MONTH);
            if (todayMonth == otherMonth) {//表示是同一个月
                int temp = todayCalendar.get(Calendar.DATE) - otherCalendar.get(Calendar.DATE);
                switch (temp) {
                    case 0:
                        String timeSpace = WKTimeUtils.getInstance().getTimeSpace(timeStamp);
                        result = String.format("%s %s", timeSpace, time2HourStr(timeStamp));
//                        result = time2HourStr(timeStamp);
                        break;
                    case 1:
//                        result = String.format("%s %s", WKBaseApplication.getInstance().getContext().getString(R.string.yesterday), time2HourStr(timeStamp));
                        result = String.format("%s", WKBaseApplication.getInstance().getContext().getString(R.string.yesterday));
                        break;
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                        int dayOfMonth = otherCalendar.get(Calendar.WEEK_OF_MONTH);
                        int todayOfMonth = todayCalendar.get(Calendar.WEEK_OF_MONTH);
                        if (dayOfMonth == todayOfMonth) {//表示是同一周
                            int dayOfWeek = otherCalendar.get(Calendar.DAY_OF_WEEK);
                            if (dayOfWeek != 1) {//判断当前是不是星期日     如想显示为：周日 12:09 可去掉此判断
                                result = dayNames[otherCalendar.get(Calendar.DAY_OF_WEEK) - 1] + time2HourStr(timeStamp);
                            } else {
                                result = getTime(timeStamp, timeFormat);
                            }
                        } else {
                            result = getTime(timeStamp, timeFormat);
                        }
                        break;
                    default:
                        result = getTime(timeStamp, timeFormat);
                        break;
                }
            } else {
                result = getTime(timeStamp, timeFormat);
            }
        } else {
            result = getYearTime(timeStamp, yearTimeFormat);
        }
        return result;
    }

    public String getYearTime(long time, String yearTimeFormat) {
        SimpleDateFormat format = new SimpleDateFormat(yearTimeFormat, Locale.getDefault());
        return format.format(new Date(time));
    }

    private String getTime(long time, String pattern) {
        Date date = new Date(time);
        return dateFormat(date, pattern);
    }

    private String dateFormat(Date date, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        return format.format(date);
    }

    public String time2DataDay(long timeStamp) {
        if (String.valueOf(timeStamp).length() < 13) {
            timeStamp = timeStamp * 1000;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public String time2Day(long timeStamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public String time2YearMonth(long timeStamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public String time2DataDay1(long timeStamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public String getNowDate() {
        String temp_str;
        Date dt = new Date();
        //最后的aa表示“上午”或“下午”    HH表示24小时制    如果换成hh表示12小时制
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        temp_str = sdf.format(dt);
        return temp_str;
    }

    public String getNowDate1() {
        String temp_str;
        Date dt = new Date();
        //最后的aa表示“上午”或“下午”    HH表示24小时制    如果换成hh表示12小时制
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        temp_str = sdf.format(dt);
        return temp_str;
    }

    public String time2DateStr1(long timeStamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public long date2TimeStamp(String date_str, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
            return sdf.parse(date_str).getTime() / 1000;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public String time2DateStr(long timeStamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public String time2HourStr(long timeStamp) {
        SimpleDateFormat sdf;
        if (is24Hour()) {
            sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        } else {
            sdf = new SimpleDateFormat("hh:mm", Locale.getDefault());
        }
        return sdf.format(new Date(timeStamp));

    }

    public String time2DateStr4(long timeStamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timeStamp));
    }

    public String getShowDate(long time) {
        String nowDate = getNowDate();
        String showDate = time2DataDay(time);
        if (nowDate.split("-")[0].equalsIgnoreCase(showDate.split("-")[0])) {
            return time2Day(time);
        }
        return time2DataDay(time);
    }

    public String getShowDateAndMinute(long time) {
        if (time < 100) return "";
        String nowDate = getNowDate();
        String showDate = time2DataDay(time);
        if (!nowDate.split("-")[0].equalsIgnoreCase(showDate.split("-")[0])) {
            return time2DateStr1(time);
        } else {
            return time2DateStr4(time);
        }
    }

    /**
     * @param time long
     * @return String
     */
    public String getTimeFormatText(long time) {
        if (String.valueOf(time).length() == 10)
            time = time * 1000;
        Date date = new Date(time);
        return getTimeFormatText(date);
    }

    private final long minute = 60 * 1000;// 1分钟
    private final long hour = 60 * minute;// 1小时
    private final long day = 24 * hour;// 1天
    private final long month = 31 * day;// 月
    private final long year = 12 * month;// 年

    public String getTimeFormatText(Date date) {
        if (date == null) {
            return "";
        }
        long diff = System.currentTimeMillis() - date.getTime();
        long r;
        if (diff > year) {
            r = (diff / year);
            return r + " 年前";
        }
        if (diff > month) {
            r = (diff / month);
            return r + " 月前";
        }
        if (diff > day) {
            r = (diff / day);
            return r + " 天前";
        }
        if (diff > hour) {
            r = (diff / hour);
            return r + " 小时前";
        }
        if (diff > minute) {
            r = (diff / minute);
            return r + " 分钟前";
        }
        return "刚刚";
    }

    public String getOnlineTime(long time) {
        long diff = WKTimeUtils.getInstance().getCurrentSeconds() - time;
        long r;

        if (diff > 60) {
            r = (diff / 60);
            if (r > 60) {
                return "";
            } else
                return String.format(WKBaseApplication.getInstance().getContext().getString(R.string.str_min), r + "");
        }
        return WKBaseApplication.getInstance().getContext().getString(R.string.str_just);
    }

    private final int SECONDS_IN_DAY = 60 * 60 * 24;
    private final long MILLIS_IN_DAY = 1000L * SECONDS_IN_DAY;

    public boolean isSameDayOfMillis(final long ms1, final long ms2) {
        final long interval = ms1 - ms2;
        return interval < MILLIS_IN_DAY
                && interval > -1L * MILLIS_IN_DAY
                && toDay(ms1) == toDay(ms2);
    }

    private long toDay(long millis) {
        return (millis + TimeZone.getDefault().getOffset(millis)) / MILLIS_IN_DAY;
    }


    /**
     * 毫秒
     *
     * @return 毫秒
     */
    public long getCurrentMills() {
        return System.currentTimeMillis();
    }

    /**
     * 秒
     *
     * @return 秒
     */
    public long getCurrentSeconds() {
        return (System.currentTimeMillis() / 1000);
    }

    public int getNowYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    public int getNowMonth() {
        return Calendar.getInstance().get(Calendar.MONTH) + 1;
    }

    public int getNowDay() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    public int getWeek(String date) {
        int[] weekDaysName = {7, 1, 2, 3, 4, 5, 6};
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date dt1;
        int intWeek = 0;
        try {
            dt1 = df.parse(date);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dt1);
            intWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return weekDaysName[intWeek];
    }

    public boolean isSameDay(long time1, long time2) {
        if (String.valueOf(time1).length() < 13)
            time1 = time1 * 1000;
        if (String.valueOf(time2).length() < 13)
            time2 = time2 * 1000;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return fmt.format(new Date(time1)).equals(fmt.format(new Date(time2)));
    }

    public boolean is24Hour() {
        return android.text.format.DateFormat.is24HourFormat(WKBaseApplication.getInstance().getContext());
    }

    private static long lastClickTime = 0;

    //防止重复点击 事件间隔，在这里我定义的是1000毫秒
    public static boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long timeD = time - lastClickTime;
        if (timeD >= 0 && timeD <= 1000) {
            return true;
        } else {
            lastClickTime = time;
            return false;
        }
    }

    public  long getTimeWithFormat(String dateStr) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        try {
            Date date = df.parse(dateStr);
            assert date != null;
            return date.getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
