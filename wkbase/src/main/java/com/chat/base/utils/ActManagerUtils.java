package com.chat.base.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-12-04 11:51
 * activity管理
 */
public class ActManagerUtils {

    private final ArrayList<Activity> activityList = new ArrayList<>();
    private Activity currentActivity;

    private ActManagerUtils() {
    }

    private static class ActManagerUtilsBinder {
        private static final ActManagerUtils actManagerUtils = new ActManagerUtils();
    }

    public static ActManagerUtils getInstance() {
        return ActManagerUtilsBinder.actManagerUtils;
    }


    /**
     * 添加activity方法、将所有打开的页面保存在集合中。退出APP时kill所有页面
     *
     * @param mActivity 类名
     */
    public void addActivity(Activity mActivity) {
        for (int i = 0, size = activityList.size(); i < size; i++) {
            if (activityList.get(i) == mActivity) {
                return;
            }
        }
        activityList.add(mActivity);
    }

    /**
     * 移除某个activity方法
     */
    public void removeActivity(Activity mActivity) {
        for (int i = 0, size = activityList.size(); i < size; i++) {
            if (activityList.get(i) == mActivity) {
                activityList.remove(i);
                break;
            }
        }
    }

    /**
     * 退出整个app时，kill所有activity
     */
    public void clearAllActivity() {
        for (int i = 0, size = activityList.size(); i < size; i++) {
            if (activityList.get(i) != null) {
                activityList.get(i).finish();
            }
        }
        activityList.clear();
    }

    public boolean isActivityTop(String className, Context mContext) {
        if (TextUtils.isEmpty(className) || mContext == null) return false;
        String topActivityName = "";
        ActivityManager am = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTasks = am
                .getRunningTasks(1);
        if (runningTasks != null && !runningTasks.isEmpty()) {
            ActivityManager.RunningTaskInfo taskInfo = runningTasks.get(0);
            topActivityName = taskInfo.topActivity.getClassName();
        }
        return className.equals(topActivityName);
    }

    public Activity getCurrentActivity() {
        return currentActivity;
    }

    public void setCurrentActivity(Activity activity) {
        this.currentActivity = activity;
    }
}
