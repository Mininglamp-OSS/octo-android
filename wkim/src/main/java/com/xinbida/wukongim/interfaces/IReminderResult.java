package com.xinbida.wukongim.interfaces;

import com.xinbida.wukongim.entity.WKReminder;

import java.util.List;

public interface IReminderResult {
    void onResult(List<WKReminder> reminders);
}
