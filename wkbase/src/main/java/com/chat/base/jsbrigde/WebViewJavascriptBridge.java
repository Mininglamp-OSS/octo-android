package com.chat.base.jsbrigde;


public interface WebViewJavascriptBridge {

    void send(String data);

    void send(String data, CallBackFunction responseCallback);
}
