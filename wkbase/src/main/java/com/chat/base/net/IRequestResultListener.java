package com.chat.base.net;

/**
 * 2020-07-20 16:13
 * 请求返还监听
 */
public interface IRequestResultListener<T> {
    void onSuccess(T result);

    void onFail(int code, String msg);
}
