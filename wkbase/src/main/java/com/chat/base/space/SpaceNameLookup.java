/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.space;

import androidx.annotation.Nullable;

/**
 * Space id → 名字反查桥. 让 wkbase 模块的诊断日志能拿到 Space 名(实际数据在 wkuikit 的
 * SpaceModel 缓存里), 但又不破坏 wkbase → wkuikit 这个反向依赖.
 *
 * <p>用法: wkuikit 在 Application 启动时调一次 {@link #install(Lookup)} 注入 lookup 闭包,
 * 之后 wkbase 内任意位置 {@link #nameOf(String)} 拿到 Space 名(未注入时返回 null).
 *
 * <p>仅用于诊断/日志, 不要在业务逻辑里依赖此查找(可能未初始化、可能 null).
 */
public final class SpaceNameLookup {

    public interface Lookup {
        /** 返回 spaceId 对应的 Space 名, 找不到/未初始化时返回 null. */
        @Nullable
        String nameOf(@Nullable String spaceId);
    }

    private static volatile Lookup IMPL;

    private SpaceNameLookup() {
    }

    /** 由 wkuikit 在 Application 启动时调一次. 幂等. */
    public static void install(@Nullable Lookup lookup) {
        IMPL = lookup;
    }

    /** wkbase 内查 Space 名. 未注入或查不到都返回 null, 永不抛异常. */
    @Nullable
    public static String nameOf(@Nullable String spaceId) {
        if (spaceId == null || spaceId.isEmpty()) return null;
        Lookup l = IMPL;
        if (l == null) return null;
        try {
            return l.nameOf(spaceId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
