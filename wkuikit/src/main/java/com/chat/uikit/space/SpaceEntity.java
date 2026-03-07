package com.chat.uikit.space;

import java.util.List;

public class SpaceEntity {
    public String space_id;
    public String name;
    public String description;
    public String owner_uid;
    public int member_count;
    public String created_at;
    public String updated_at;

    public static class SpaceMember {
        public String uid;
        public String name;
        public int role; // 0=member, 1=admin, 2=owner
        public String created_at;
    }

    public static class InviteResult {
        public String invite_code;
    }

    public static class SpaceListResult {
        public List<SpaceEntity> list;
    }
}
