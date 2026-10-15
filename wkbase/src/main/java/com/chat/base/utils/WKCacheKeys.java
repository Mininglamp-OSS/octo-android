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
 * 替换与截断，各字段按 {@link #lengthPrefixed} 做长度前缀编码后再用 0x1F 连接
 * ——长度前缀使拼接对任意输入单射，分隔符出现在业务值里也不会产生跨 bundle 的
 * 身份碰撞（不再依赖"该字节不可能出现"这一错误假设）；磁盘名
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

    /**
     * 字段分隔符：ASCII Unit Separator。
     *
     * <p>**不假设**该字节不出现在业务值里——JSON 可用 0x1F 的转义序列编码它，
     * clientMsgNO 来自服务端未校验。单射性由 {@link #lengthPrefixed} 的长度前缀
     * 保证，SEP 仅作可读性分隔。
     */
    private static final char SEP = '\u001f';

    /** 条目分隔符：ASCII Record Separator。 */
    private static final char REC = '\u001e';

    /**
     * 长度前缀编码：把一个字段写成 {@code <UTF-16 长度>:<原文>}。
     *
     * <p>身份串拼接的唯一正确形状。原先只用 {@code 0x1F} 做分隔符并假设它不出现在
     * 业务值里——但代码既不拒绝也不转义该字符，于是 {@code (b:p, "x<0x1F>id:y")} 与
     * {@code ("b:pid:x", "y")} 拼出同一身份串，两个不同 bundle 的不同条目共享
     * 身份（进而共享磁盘路径与校验记录）。带长度前缀后任何字节序列只有唯一一种解析
     * 方式，值域里出现什么字符都无妨。
     *
     * <p>长度用 {@link String#length()}（UTF-16 code unit 数）而非字节数：编解码只在
     * 本类内部，两侧一致即保证单射，且省掉一次 UTF-8 编码。
     */
    private static String lengthPrefixed(String field) {
        String s = field == null ? "" : field;
        return s.length() + ":" + s;
    }

    /**
     * 合并转发单条目的完整原始身份串：bundle 作用域 + 条目标识，原样拼接不做清洗。
     *
     * @param bundleScope   bundle 层作用域。一级转发传父消息 clientMsgNO；嵌套转发
     *                      传 {@link #payloadDigest} 的结果。原样使用。
     * @param messageID     子消息的 messageID，原样使用；为 null/空时走 index 命名空间。
     * @param indexFallback messageID 缺失时使用的 bundle 内下标。
     * @return 身份串。messageID 存在与缺失走两个不同前缀（{@code id:} / {@code ix:}），
     *         避免一个叫 "3" 的 messageID 与下标 3 撞同一身份。
     *
     * <p>各字段均按 {@link #lengthPrefixed} 编码，身份串对任意输入单射——分隔符出现
     * 在业务值里也不会产生跨 bundle 的身份碰撞。
     */
    public static String entryIdentity(String bundleScope, String messageID, int indexFallback) {
        String scope = bundleScope == null ? "" : bundleScope;
        StringBuilder sb = new StringBuilder();
        // v2：字段改长度前缀编码，拼接无歧义（v1 的纯分隔符拼接可被 0x1F 构造出跨
        // bundle 的身份碰撞）。版本号变化使本分支中间提交产生的磁盘名失效——该中间
        // 态从未发布，线上无此类文件，无需兼容。
        sb.append("v2").append(SEP).append(lengthPrefixed(scope)).append(SEP);
        if (messageID != null && !messageID.isEmpty()) {
            sb.append("id:").append(lengthPrefixed(messageID));
        } else {
            sb.append("ix:").append(lengthPrefixed(String.valueOf(indexFallback)));
        }
        return sb.toString();
    }

    /**
     * 把 bundle 内下标并入作用域。合并转发的 payload 不保证 message_id 唯一
     * （decodeMsg 不做去重），仅按 messageID 建身份会让两行自别名到同一路径。
     */
    public static String scopeWithIndex(String scope, int index) {
        return (scope == null ? "" : scope) + SEP + "n:" + lengthPrefixed(String.valueOf(index));
    }

    /**
     * 普通聊天（非转发）文件缓存的完整原始身份串。
     *
     * <p>与 {@link #entryIdentity} 分开命名空间（{@code c:} 前缀），避免转发身份与
     * 普通消息身份巧合相同。身份由 (频道类型, 频道ID, clientMsgNO) 构成。
     *
     * <p>clientMsgNO 原样进入、不做任何清洗截断。这是 B-1 普通聊天分支的修复点：
     * 旧路径交给 WKFileProvider.buildCacheFileName，后者把 {@code : ? * " < > |} 替换
     * 成下划线、删控制字符并截断，于是 {@code m:a} 与 {@code m?a} 塌成同一磁盘名。
     * clientMsgNO 不保证是本地生成的 UUID 形状——收消息解码（WKProto.java:214 的
     * readString）与历史同步（MsgManager.java:1250 的 client_msg_no）都直接采用服务端
     * 下发的字符串，存储层只校验非空、不校验字符域，本地 createClientMsgNO 的格式
     * 不构成不变量。
     */
    public static String chatFileIdentity(byte channelType, String channelID, String clientMsgNO) {
        StringBuilder sb = new StringBuilder();
        sb.append("v2").append(SEP).append("c:")
          .append(lengthPrefixed(String.valueOf((int) channelType))).append(SEP)
          .append(lengthPrefixed(channelID == null ? "" : channelID)).append(SEP)
          .append(lengthPrefixed(clientMsgNO == null ? "" : clientMsgNO));
        return sb.toString();
    }

    /**
     * 语音缓存的完整原始身份串。
     *
     * <p>与 {@link #chatFileIdentity} 共享命名空间（{@code c:} 前缀）——语音是特殊
     * 类型的普通消息，身份同样由 (频道类型, 频道ID, clientMsgNO) 唯一确定。
     *
     * <p>B-3 修复点：旧路径用 safePathSegment(clientMsgNO) 这一有损变换做磁盘名，
     * {@code m:a} 与 {@code m?a} 塌成同一 {@code m_a.amr}，先下载的消息 A 的字节
     * 会被消息 B 的 exists 分支误当作 B 的缓存直接播放（一条语音播另一条的音频）。
     * 本方法返回无损身份串，磁盘名由 {@link #basenameForIdentity} 派生为 SHA-256。
     */
    public static String voiceIdentity(byte channelType, String channelID, String clientMsgNO) {
        // 复用 chatFileIdentity 实现——语音与普通文件的身份构成完全一致
        return chatFileIdentity(channelType, channelID, clientMsgNO);
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
            sb.append(lengthPrefixed(nz(messageIDs.get(i)))).append(SEP)
              .append(lengthPrefixed(String.valueOf(
                      timestamps == null || timestamps.get(i) == null ? 0L : timestamps.get(i)))).append(SEP)
              .append(lengthPrefixed(fromUIDs == null ? "" : nz(fromUIDs.get(i)))).append(SEP)
              .append(lengthPrefixed(contents == null ? "" : nz(contents.get(i)))).append(REC);
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
