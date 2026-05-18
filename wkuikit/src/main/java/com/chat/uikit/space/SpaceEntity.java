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

package com.chat.uikit.space;

import java.util.List;

public class SpaceEntity {
    public String space_id;
    public String name;
    public String description;
    public String owner_uid;
    public int member_count;
    public String invite_code;
    public String created_at;
    public String updated_at;

    public static class SpaceMember {
        public String uid;
        public String name;
        public int role; // 0=member, 1=admin, 2=owner
        public int robot; // 0=user, 1=bot
        public String created_at;
    }

    public static class InviteResult {
        public String invite_code;
    }

    public static class SpaceListResult {
        public List<SpaceEntity> list;
    }
}
