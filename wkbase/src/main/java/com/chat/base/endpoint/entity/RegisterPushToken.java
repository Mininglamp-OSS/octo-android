package com.chat.base.endpoint.entity;

public class RegisterPushToken {
    public String type;
    public String token;

    public RegisterPushToken(String type, String token) {
        this.type = type;
        this.token = token;
    }
}
