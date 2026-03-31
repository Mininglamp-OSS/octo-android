package com.xinbida.wukongim.utils;

import android.annotation.SuppressLint;
import android.util.Log;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * 2019-11-10 17:22
 * 日志管理
 */
public class WKLoggerUtils {

    /**
     * log TAG
     */
    private final String TAG = "WKLogger" + WKIM.getInstance().getVersion();
    //Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    private final String FILE_NAME = "wkLogger_" + WKIM.getInstance().getVersion() + ".log";

    //
    private String getLogFilePath() {
        final String ROOT = Objects.requireNonNull(WKIMApplication.getInstance().getContext().getExternalFilesDir(null)).getAbsolutePath() + "/";
        return ROOT + FILE_NAME;
    }

    private WKLoggerUtils() {

    }

    private static class LoggerUtilsBinder {
        private final static WKLoggerUtils utils = new WKLoggerUtils();
    }

    public static WKLoggerUtils getInstance() {
        return LoggerUtilsBinder.utils;
    }

    /**
     * 获取函数名称
     */
    private String getFunctionName() {
        StackTraceElement[] sts = Thread.currentThread().getStackTrace();

        for (StackTraceElement st : sts) {
            if (st.isNativeMethod()) {
                continue;
            }

            if (st.getClassName().equals(Thread.class.getName())) {
                continue;
            }

            if (st.getClassName().equals(this.getClass().getName())) {
                continue;
            }

            return "[" + Thread.currentThread().getName() + "(" + Thread.currentThread().getId()
                    + "): " + st.getFileName() + ":" + st.getLineNumber() + "]";
        }

        return null;
    }

    private String createMessage(String msg) {
        String functionName = getFunctionName();
        return (functionName == null ? msg : (functionName + " - " + msg));
    }

    /**
     * log.i
     */
    private void info(String tag, String msg) {
        String message = createMessage(msg);
        if (WKIM.getInstance().isDebug()) {
            Log.i(TAG + " " + tag, message);
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    public void i(String tag, String msg) {
        info(tag, msg);
    }

    public void i(String tag, Exception e) {
        info(tag, e != null ? e.toString() : "null");
    }

    /**
     * log.v
     */
    private void verbose(String msg) {
        String message = createMessage(msg);
        if (WKIM.getInstance().isDebug()) {
            Log.v(TAG, message);
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    public void v(String msg) {
        if (WKIM.getInstance().isDebug()) {
            verbose(msg);
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(msg);
        }
    }

    public void v(Exception e) {
        if (WKIM.getInstance().isDebug()) {
            verbose(e != null ? e.toString() : "null");
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(e.toString());
        }
    }

    /**
     * log.d
     */
    private void debug(String msg) {
        if (WKIM.getInstance().isDebug()) {
            String message = createMessage(msg);
            Log.d(TAG, message);
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(msg);
        }
    }

    /**
     * log.e
     */
    public void error(String tag, String msg) {
        String message = createMessage(msg);
        if (WKIM.getInstance().isDebug()) {
            Log.e(TAG + " " + tag, message);
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    /**
     * log.error
     */
    public void error(Exception e) {
        StringBuilder sb = new StringBuilder();
        String name = getFunctionName();
        StackTraceElement[] sts = e.getStackTrace();

        if (name != null) {
            sb.append(name).append(" - ").append(e).append("\r\n");
        } else {
            sb.append(e).append("\r\n");
        }
        if (sts.length > 0) {
            for (StackTraceElement st : sts) {
                if (st != null) {
                    sb.append("[ ").append(st.getFileName()).append(":").append(st.getLineNumber()).append(" ]\r\n");
                }
            }
        }
        if (WKIM.getInstance().isDebug()) {
            Log.e(TAG, sb.toString());
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(sb.toString());
        }
    }

    /**
     * log.warn
     */
    private void warn(String tag, String msg) {
        String message = createMessage(msg);
        if (WKIM.getInstance().isDebug()) {
            System.out.println(message);
        } else {
            Log.w(TAG + " " + tag, message);
        }
        if (WKIM.getInstance().isDebug()) {
            writeLog(message);
        }
    }

    public void d(String msg) {
        debug(msg);

    }

    public void d(Exception e) {
        debug(e != null ? e.toString() : "null");
    }


    public void e(String msg) {
        error("", msg);
    }

    public void e(String tag, String msg) {
        error(tag, msg);
    }

    public void e(Exception e) {
        error(e);
    }

    /**
     * log.w
     */
    public void w(String tag, String msg) {
        warn(tag, msg);
    }

    public void w(String tag,Exception e) {
        warn(tag,e != null ? e.toString() : "null");
    }
//
//    public  void resetLogFile() {
//        File file = new File(logFile);
//        file.delete();
//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    @SuppressLint("SimpleDateFormat")
    private void writeLog(String content) {
        try {
            if (WKIMApplication.getInstance().getContext() == null || !WKIM.getInstance().isWriteLog()) {
                return;
            }
            File file = new File(getLogFilePath());
            if (!file.exists()) {
                file.createNewFile();
            }
//			DateFormat formate = SimpleDateFormat.getDateTimeInstance();
            SimpleDateFormat formate = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            FileWriter write = new FileWriter(file, true);
            write.write(formate.format(new Date()) + "   " +
                    content + "\n");
            write.flush();
            write.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
