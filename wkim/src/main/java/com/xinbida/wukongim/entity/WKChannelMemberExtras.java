package com.xinbida.wukongim.entity;

/**
 * 2020-12-04 13:30
 * 频道成员扩展字段
 */
public class WKChannelMemberExtras {
    public final static String WKCode = "code";
    // 外部成员标识（来自其他 Space 的成员）
    public final static String isExternal = "is_external";
    public final static String sourceSpaceID = "source_space_id";
    public final static String sourceSpaceName = "source_space_name";
    // 成员 Home Space（viewer-relative 外部判定用，YUJ-63 引入，web #997）
    // home_space_* 表示"成员真正归属的 Space"，与 source_space_* 的语义不同：
    // - source_space_*: 成员通过哪个 Space 加入了当前群（可能为空，表示同 Space）
    // - home_space_*:   成员的归属 Space（始终有值），供客户端做 viewer-relative 外部判定
    public final static String homeSpaceID = "home_space_id";
    public final static String homeSpaceName = "home_space_name";
}
