package com.chat.uikit.contacts;

import com.xinbida.wukongim.entity.WKChannel;

public class BotStoreUIEntity {
    public WKChannel channel;
    public String pying;
    public String status; // "not_added" | "pending" | "added"
    public String description;

    public BotStoreUIEntity(WKChannel channel, String status, String description) {
        this.channel = channel;
        this.status = status;
        this.description = description;
    }
}
