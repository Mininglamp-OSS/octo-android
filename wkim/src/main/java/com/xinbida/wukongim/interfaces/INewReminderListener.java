package com.xinbida.wukongim.interfaces;

import com.xinbida.wukongim.entity.WKReminder;

import java.util.List;

public interface INewReminderListener {
    void newReminder(List<WKReminder> list);
}
