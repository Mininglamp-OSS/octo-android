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

import java.util.Map;

public class ChannelInfoEntity {
    public ChannelIDEntity channel;
    public ParentChannelEntity parent_channel;
    public String name;
    public String logo;
    public String remark;
    public int status;
    public int online;
    public long last_offline;
    public int receipt;
    public int robot;
    public String category;
    public int stick;
    public int mute;
    public int show_nick;
    public int follow;
    public int be_deleted;
    public int be_blacklist;
    public String notice;
    public int group_type;
    public int save;
    public int forbidden;
    public int invite;
    public int flame;
    public int flame_second;
    public int device_flag;
    public String space_id;
    public String bot_creator_uid;
    public String avatar_cache_key;
    public Map extra;


    public static class ChannelIDEntity {
        public String channel_id;
        public byte channel_type;
    }

    public static class ParentChannelEntity {
        public String channel_id;
        public byte channel_type;
    }

}
