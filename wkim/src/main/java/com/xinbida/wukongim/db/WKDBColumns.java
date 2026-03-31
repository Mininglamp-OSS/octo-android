package com.xinbida.wukongim.db;

/**
 * 2020-05-20 17:26
 * 数据库字段
 */
public interface WKDBColumns {
    class TABLE{
        public static final String message = "message";
        public static final String messageReaction = "message_reaction";
        public static final String messageExtra = "message_extra";
        public static final String conversation = "conversation";
        public static final String conversationExtra = "conversation_extra";
        public static final String channel = "channel";
        public static final String channelMembers = "channel_members";
        public static final String reminders = "reminders";
        public static final String robot = "robot";
        public static final String robotMenu = "robot_menu";
    }
    //频道db字段
    class WKChannelColumns {
        //自增ID
        public static final String id = "id";
        //频道ID
        public static final String channel_id = "channel_id";
        //频道类型
        public static final String channel_type = "channel_type";
        //频道名称
        public static final String channel_name = "channel_name";
        //频道备注(频道的备注名称，个人的话就是个人备注，群的话就是群别名)
        public static final String channel_remark = "channel_remark";
        //是否置顶
        public static final String top = "top";
        //免打扰
        public static final String mute = "mute";
        //是否保存在通讯录
        public static final String save = "save";
        //频道状态
        public static final String status = "status";
        //是否禁言
        public static final String forbidden = "forbidden";
        //是否开启邀请确认
        public static final String invite = "invite";
        //是否已关注 0.未关注（陌生人） 1.已关注（好友）
        public static final String follow = "follow";
        //是否删除
        public static final String is_deleted = "is_deleted";
        //是否显示频道名称
        public static final String show_nick = "show_nick";
        //创建时间
        public static final String created_at = "created_at";
        //修改时间
        public static final String updated_at = "updated_at";
        //版本
        public static final String version = "version";
        //扩展字段
        public static final String localExtra = "extra";
        //头像
        public static final String avatar = "avatar";
        //是否在线
        public static final String online = "online";
        //最后一次离线时间
        public static final String last_offline = "last_offline";
        //分类
        public static final String category = "category";
        //是否回执消息
        public static final String receipt = "receipt";
        // 机器人0.否1.是
        public static final String robot = "robot";
        public static final String username = "username";
        public static final String avatar_cache_key = "avatar_cache_key";
        public static final String remote_extra = "remote_extra";
        public static final String flame = "flame";
        public static final String flame_second = "flame_second";
        public static final String device_flag = "device_flag";
        public static final String parent_channel_id = "parent_channel_id";
        public static final String parent_channel_type = "parent_channel_type";
    }

    //频道成员db字段
    class WKChannelMembersColumns {
        //自增ID
        public static final String id = "id";
        //成员状态
        public static final String status = "status";
        //频道id
        public static final String channel_id = "channel_id";
        //频道类型
        public static final String channel_type = "channel_type";
        // 邀请者uid
        public static final String member_invite_uid = "member_invite_uid";
        //成员id
        public static final String member_uid = "member_uid";
        //成员名称
        public static final String member_name = "member_name";
        //成员头像
        public static final String member_avatar = "member_avatar";
        //成员备注
        public static final String member_remark = "member_remark";
        //成员角色
        public static final String role = "role";
        //是否删除
        public static final String is_deleted = "is_deleted";
        //创建时间
        public static final String created_at = "created_at";
        //修改时间
        public static final String updated_at = "updated_at";
        //版本
        public static final String version = "version";
        // 机器人0.否1.是
        public static final String robot = "robot";
        // 禁言到期时间
        public static final String forbidden_expiration_time = "forbidden_expiration_time";
        //扩展字段
        public static final String extra = "extra";
        public static final String memberAvatarCacheKey = "member_avatar_cache_key";
    }

    //消息db字段
    class WKMessageColumns {
        //服务器消息ID(全局唯一，无序)
        public static final String message_id = "message_id";
        //服务器消息序号(有序递增)
        public static final String message_seq = "message_seq";
        //客户端序号
        public static final String client_seq = "client_seq";
        //消息时间10位时间戳
        public static final String timestamp = "timestamp";
        //消息来源发送者
        public static final String from_uid = "from_uid";
        //频道id
        public static final String channel_id = "channel_id";
        //频道类型
        public static final String channel_type = "channel_type";
        //消息正文类型
        public static final String type = "type";
        //消息内容Json
        public static final String content = "content";
        //发送状态
        public static final String status = "status";
        //语音是否已读
        public static final String voice_status = "voice_status";
        //创建时间
        public static final String created_at = "created_at";
        //修改时间
        public static final String updated_at = "updated_at";
        //扩展字段
        public static final String extra = "extra";
        //搜索的关键字 如：[红包]
        public static final String searchable_word = "searchable_word";
        //客户端唯一ID
        public static final String client_msg_no = "client_msg_no";
        //消息是否删除
        public static final String is_deleted = "is_deleted";
        //排序编号
        public static final String order_seq = "order_seq";
        //消息设置
        public static final String setting = "setting";
        // 是否开启阅后即焚
        public static final String flame = "flame";
        // 阅后即焚秒数
        public static final String flame_second = "flame_second";
        // 是否已查看 0.未查看 1.已查看 （这个字段跟已读的区别在于是真正的查看了消息内容，比如图片消息 已读是列表滑动到图片消息位置就算已读，viewed是表示点开图片才算已查看，语音消息类似）
        public static final String viewed = "viewed";
        // 查看时间戳
        public static final String viewed_at = "viewed_at";
        // 话题ID
        public static final String topic_id = "topic_id";
        public static final String expire_time = "expire_time";
        public static final String expire_timestamp = "expire_timestamp";
    }

    //最近会话db字段
    class WKCoverMessageColumns {
        //频道id
        public static final String channel_id = "channel_id";
        //频道类型
        public static final String channel_type = "channel_type";
        //最后一条消息id
        public static final String last_msg_seq = "last_msg_seq";
        //最后一条消息时间
        public static final String last_msg_timestamp = "last_msg_timestamp";
        //未读消息数量
        public static final String unread_count = "unread_count";
        //扩展字段
        public static final String extra = "extra";
        //客户端唯一ID
        public static final String last_client_msg_no = "last_client_msg_no";
        //是否删除
        public static final String is_deleted = "is_deleted";
        //版本
        public static final String version = "version";
        // 父channelID
        public static final String parent_channel_id = "parent_channel_id";
        // 父channelType
        public static final String parent_channel_type = "parent_channel_type";
    }
}
