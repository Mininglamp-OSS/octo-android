package com.chat.base.entity;

/**
 * 2020-06-30 16:41
 * 用户设置
 */
public class UserInfoSetting {
    public int search_by_phone; //手机号搜索
    public int search_by_short; //ID搜索
    public int new_msg_notice; //显示消息通知
    public int msg_show_detail; //显示消息通知详情
    public int voice_on; //通知声音
    public int shock_on; //通知震动
    public int device_lock; //是否开启登录设备验证
    public int offline_protection;//离线保护，断网屏保
}
