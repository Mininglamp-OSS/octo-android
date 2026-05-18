/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
