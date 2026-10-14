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

package com.chat.base.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 文件缓存身份（identity）与磁盘名（basename）的派生。
 *
 * <p>设计约束——这是 B-1 的根因修复：旧实现先用 sanitizeScope 把 messageID 里的
 * {@code : ? * " < > |} 等字符统统替换成同一个下划线、再截断到 64 字符，然后把
 * <b>清洗后的结果</b>当作缓存键与校验键。该变换不是单射：{@code m:a} 与 {@code m?a}
 * 都塌成 {@code m_a}，只在第 64 字符之后不同的两个 ID 也塌成同一个键。于是两条
 * 不同的子消息映射到同一磁盘路径，且因为校验键本身就是那个已塌掉的名字，
 * "按缓存键绑定"根本无法区分它们——先下载的 A 的字节会被当成 B 的缓存打开。
 *
 * <p>修复方式：身份串（{@link #entryIdentity}）保留全部原始字节、不做任何字符
 * 替换与截断，只用 0x1F 作不可出现在 ID 里的字段分隔符；磁盘名
 * （{@link #basenameForIdentity}）是该身份串的 SHA-256 全量十六进制（64 字符，
 * 定长、文件系统安全、无需再清洗），碰撞概率由 SHA-256 保证。校验
 * （WKFileProvider.markDownloadVerified / isValidFileCache）绑定身份串而不是磁盘名。
 *
 * <p>纯 Java + 仅依赖 {@link MessageDigest}，不碰 Android API，因此可被 JVM 单测
 * 直接调用——生产代码与测试走同一份实现，不存在"测试里复制一份算法"的漂移风险。
 */
public final class WKCacheKeys {

    private WKCacheKeys() {
    }

    /** 字段分隔符：ASCII Unit Separator，不可能出现在 messageID / uid / JSON 内容里。 */
    private static final char SEP = '\u001f';

    /** 条目分隔符：ASCII Record Separator。 */
    private static final char REC = '\u001e';

    /**
     * 合并转发单条目的完整原始身份串：bundle 作用域 + 条目标识，原样拼接不做清洗。
     *
     * @param bundleScope   bundle 层作用域。一级转发传父消息 clientMsgNO；嵌套转发
     *                      传 {@link #payloadDigest} 的结果。原样使用。
     * @param messageID     子消息的 messageID，原样使用；为 null/空时走 index 命名空间。
     * @param indexFallback messageID 缺失时使用的 bundle 内下标。
     * @return 身份串。messageID 存在与缺失走两个不同前缀（{@code id:} / {@code ix:}），
     *         避免一个叫 "3" 的 messageID 与下标 3 撞同一身份。
     */
    public static String entryIdentity(String bundleScope, String messageID, int indexFallback) {
        String scope = bundleScope == null ? "" : bundleScope;
        StringBuilder sb = new StringBuilder();
        sb.append("v1").append(SEP).append(scope).append(SEP);
        if (messageID != null && !messageID.isEmpty()) {
            sb.append("id:").append(messageID);
        } else {
            sb.append("ix:").append(indexFallback);
        }
        return sb.toString();
    }

    /**
     * 身份串 → 磁盘 basename：SHA-256 全量十六进制（64 字符）。
     *
     * <p>全量而不是截断到 8 字节：磁盘名是唯一性的最后一道防线，64 位摘要在同一
     * 目录下虽实际够用，但没有理由自降强度——64 字符仍远低于 NAME_MAX 预算。
     * 输出只含 {@code [0-9a-f]}，无需再过 sanitizePathSegment。
     */
    public static String basenameForIdentity(String identity) {
        return sha256Hex(identity == null ? "" : identity);
    }

    /**
     * 嵌套转发的 bundle 作用域摘要：对子消息序列的稳定内容字段做 SHA-256 全量十六进制。
     *
     * <p>用 content 原始字符串（WKMultiForwardContent.decodeMsg 从 payload JSON 原样
     * 赋值）而不是 encodeMsg()：content 跨 decode 逐字节稳定，且不依赖 Android 的
     * JSONObject/TextUtils。禁止使用 clientMsgNO（decodeMsg 不赋值，由 WKMsg 构造
     * 函数生成随机 UUID）、List.hashCode()、System.identityHashCode。
     *
     * @param messageIDs 与 timestamps/fromUIDs/contents 等长的并行列表；元素可为 null
     */
    public static String payloadDigest(
            List<String> messageIDs,
            List<Long> timestamps,
            List<String> fromUIDs,
            List<String> contents) {
        StringBuilder sb = new StringBuilder();
        int n = messageIDs == null ? 0 : messageIDs.size();
        for (int i = 0; i < n; i++) {
            sb.append(nz(messageIDs.get(i))).append(SEP)
              .append(timestamps == null || timestamps.get(i) == null ? 0L : timestamps.get(i)).append(SEP)
              .append(fromUIDs == null ? "" : nz(fromUIDs.get(i))).append(SEP)
              .append(contents == null ? "" : nz(contents.get(i))).append(REC);
        }
        return sha256Hex(sb.toString());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** SHA-256 全量十六进制小写。MessageDigest 在 Android/JVM 上必然可用。 */
    public static String sha256Hex(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int v = b & 0xFF;
                if (v < 0x10) hex.append('0');
                hex.append(Integer.toHexString(v));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // 理论不可达。绝不退回 String.hashCode（32 位、极易碰撞），抛出让问题可见。
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
