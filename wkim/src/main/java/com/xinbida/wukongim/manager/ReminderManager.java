package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import com.xinbida.wukongim.db.ReminderDBManager;
import com.xinbida.wukongim.entity.WKReminder;
import com.xinbida.wukongim.interfaces.INewReminderListener;
import com.xinbida.wukongim.message.WKRead;
import com.xinbida.wukongim.utils.WKCommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/21/21 12:13 PM
 * 提醒管理
 */
public class ReminderManager extends BaseManager {
    private ReminderManager() {
    }

    private static class RemindManagerBinder {
        final static ReminderManager manager = new ReminderManager();
    }

    public static ReminderManager getInstance() {
        return RemindManagerBinder.manager;
    }

    private ConcurrentHashMap<String, INewReminderListener> newReminderMaps;
    // 内存缓存：key = channelID_channelType
    private ConcurrentHashMap<String, ArrayList<WKReminder>> reminderList;

    public void addOnNewReminderListener(String key, INewReminderListener iNewReminderListener) {
        if (TextUtils.isEmpty(key) || iNewReminderListener == null) return;
        if (newReminderMaps == null) newReminderMaps = new ConcurrentHashMap<>();
        newReminderMaps.put(key, iNewReminderListener);
    }

    public void removeNewReminderListener(String key) {
        if (newReminderMaps != null) newReminderMaps.remove(key);
    }

    private void setNewReminders(List<WKReminder> list) {
        if (newReminderMaps != null && !newReminderMaps.isEmpty()) {
            runOnMainThread(() -> {
                for (Map.Entry<String, INewReminderListener> entry : newReminderMaps.entrySet()) {
                    entry.getValue().newReminder(list);
                }
            });
        }
    }

    /**
     * 获取某个类型的提醒
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param type        提醒类型
     * @return WKReminder
     */
    public WKReminder getReminder(String channelID, byte channelType, int type) {
        List<WKReminder> list = getReminders(channelID, channelType);
        WKReminder wkReminder = null;
        for (int i = 0, size = list.size(); i < size; i++) {
            if (list.get(i).type == type) {
                wkReminder = list.get(i);
                break;
            }
        }
        return wkReminder;
    }

    /**
     * 查询某个会话的高光内容
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @return List<WKReminder>
     */
    public List<WKReminder> getReminders(String channelID, byte channelType) {
        // 先查内存缓存
        String key = getCacheKey(channelID, channelType);
        if (reminderList != null && reminderList.containsKey(key)) {
            ArrayList<WKReminder> cached = reminderList.get(key);
            if (cached != null) {
                return new ArrayList<>(cached); // 返回副本，防止被修改
            }
        }
        
        // 缓存未命中，查询数据库
        List<WKReminder> list = ReminderDBManager.getInstance().queryWithChannelAndDone(channelID, channelType, 0);
        
        // 放入缓存
        if (list != null) {
            cacheReminders(channelID, channelType, list);
        }
        
        return list;
    }
    
    /**
     * 生成缓存 key
     */
    private String getCacheKey(String channelID, byte channelType) {
        return channelID + "_" + channelType;
    }
    
    /**
     * 缓存 Reminder 列表
     */
    private void cacheReminders(String channelID, byte channelType, List<WKReminder> list) {
        if (reminderList == null) {
            reminderList = new ConcurrentHashMap<>();
        }
        String key = getCacheKey(channelID, channelType);
        reminderList.put(key, new ArrayList<>(list));
    }
    
    /**
     * 清除指定频道的缓存
     */
    private void clearCache(String channelID, byte channelType) {
        if (reminderList != null) {
            String key = getCacheKey(channelID, channelType);
            reminderList.remove(key);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        if (reminderList != null) {
            reminderList.clear();
        }
    }

    public List<WKReminder> getRemindersWithType(String channelID, byte channelType, int type) {
        return ReminderDBManager.getInstance().queryWithChannelAndTypeAndDone(channelID, channelType, type, 0);
    }

    public void saveOrUpdateReminders(List<WKReminder> reminderList) {

        if (WKCommonUtils.isNotEmpty(reminderList)) {
            // 更新内存缓存（按 channelID 和 channelType 分组）
            updateMemoryCache(reminderList);
            // 通知监听器
            setNewReminders(reminderList);
            // 更新db
            ReminderDBManager.getInstance().insertOrUpdateReminders(reminderList);
        }


    }
    
    /**
     * 更新内存缓存
     */
    private void updateMemoryCache(List<WKReminder> reminders) {
        if (this.reminderList == null) {
            this.reminderList = new ConcurrentHashMap<>();
        }
        
        for (WKReminder reminder : reminders) {
            String key = getCacheKey(reminder.channelID, reminder.channelType);
            
            if (reminder.done == 1) {
                // done=1 时，从缓存中移除
                removeReminderFromCache(key, reminder);
            } else {
                // done=0 时，更新或添加到缓存
                updateOrAddReminderToCache(key, reminder);
            }
        }
    }
    
    /**
     * 更新或添加 Reminder 到缓存
     */
    private void updateOrAddReminderToCache(String key, WKReminder newReminder) {
        // 获取该频道的缓存列表
        ArrayList<WKReminder> list = this.reminderList.get(key);
        
        if (list == null) {
            // 缓存中没有该频道的数据，创建新列表
            list = new ArrayList<>();
            list.add(newReminder);
            this.reminderList.put(key, list);
            return;
        }
        
        // 查找是否已存在相同 reminderID 的记录
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).reminderID == newReminder.reminderID) {
                // 找到了，更新
                list.set(i, newReminder);
                found = true;
                break;
            }
        }
        
        // 如果不存在，添加到列表
        if (!found) {
            list.add(newReminder);
        }

    }
    
    /**
     * 从缓存中移除指定的 Reminder
     */
    private void removeReminderFromCache(String key, WKReminder reminderToRemove) {
        if (this.reminderList == null || !this.reminderList.containsKey(key)) {
            return;
        }
        
        ArrayList<WKReminder> list = this.reminderList.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        
        // 移除匹配的 reminder（根据 reminderID 匹配）
        // 使用迭代器移除，兼容 API 21
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).reminderID == reminderToRemove.reminderID) {
                list.remove(i);
            }
        }
        
        // 如果列表为空，移除整个 key
        if (list.isEmpty()) {
            this.reminderList.remove(key);
        }
    }

    public long getMaxVersion() {
        return ReminderDBManager.getInstance().queryMaxVersion();
    }

    public void done() {

    }
}
