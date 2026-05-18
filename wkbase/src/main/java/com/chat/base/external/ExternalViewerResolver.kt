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

package com.chat.base.external

/**
 * 外部成员视角相对化判定工具 —— Android 版。
 *
 * 与 Web 的 `resolveExternalForViewer` ( #997 / #1013) 语义一致，
 * 用同一份 home_space_id 规则回答「对当前 viewer 而言，这个成员是否算外部？
 * 如果是，该显示什么 Space 名？」
 *
 * 规则：
 * ```
 * if homeSpaceId 存在:
 *     isExternal = homeSpaceId != viewerSpaceId
 *     sourceSpaceName = isExternal ? homeSpaceName else ""
 * else:  // 降级兼容旧后端（仅有 is_external + source_space_name）
 *     isExternal = isExternalLegacy == 1
 *     sourceSpaceName = if (isExternal) sourceSpaceNameLegacy ?: "" else ""
 * ```
 *
 * 调用方（成员列表 / 群详情缩略图 / UserInfo 面板）把结果里的
 * [sourceSpaceName] 作为「@SpaceName」后缀/来源行渲染；空串代表不渲染。
 *
 * 注意：自看（memberUID == viewerUid）与 Bot 统一按同规则处理，上层决定是否早退，
 * 这里只做纯数据判定，方便单测。
 */
object ExternalViewerResolver {

    /**
     * 轻量输入结构。字段与后端 DTO / WKChannelMember.extraMap 的 key 直接对应，
     * 调用方负责从自己的数据源里抽字段传进来。
     */
    data class MemberOrgData(
        val homeSpaceId: String? = null,
        val homeSpaceName: String? = null,
        /** 降级兼容：0 / 1 */
        val isExternalLegacy: Int = 0,
        /** 降级兼容：旧后端的 source_space_name */
        val sourceSpaceNameLegacy: String? = null,
    )

    data class Resolution(
        val isExternal: Boolean,
        /** 要渲染的 Space 名；空串代表「不渲染后缀/来源行」 */
        val sourceSpaceName: String,
    )

    @JvmStatic
    fun resolve(org: MemberOrgData?, viewerSpaceId: String?): Resolution {
        if (org == null) return Resolution(false, "")
        val viewer = viewerSpaceId ?: ""
        val homeId = org.homeSpaceId

        // 主路径：新后端透传了 home_space_id（包括空字符串 → 未识别 Space，当作未知）
        if (!homeId.isNullOrEmpty()) {
            val isExternal = homeId != viewer
            val name = if (isExternal) (org.homeSpaceName ?: "") else ""
            return Resolution(isExternal, name)
        }

        // 降级路径：旧后端只有 is_external + source_space_name，按绝对属性兜底渲染
        val isExternal = org.isExternalLegacy == 1
        val name = if (isExternal) (org.sourceSpaceNameLegacy ?: "") else ""
        return Resolution(isExternal, name)
    }

    /**
     * 便捷：直接从 WKChannelMember-style extraMap（key 为 String，value 为 Any?）抽字段。
     * 适配 [com.xinbida.wukongim.entity.WKChannelMemberExtras] 的约定 key：
     *   - "home_space_id" / "home_space_name"
     *   - "is_external" (Int|Boolean) / "source_space_name"
     */
    @JvmStatic
    fun resolveFromExtras(
        extras: Map<String, Any?>?,
        viewerSpaceId: String?,
    ): Resolution {
        val map = extras ?: return Resolution(false, "")
        val homeId = map["home_space_id"]?.toString()
        val homeName = map["home_space_name"]?.toString()
        val legacyFlag = when (val v = map["is_external"]) {
            is Number -> v.toInt()
            is Boolean -> if (v) 1 else 0
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }
        val legacyName = map["source_space_name"]?.toString()
        return resolve(
            MemberOrgData(
                homeSpaceId = homeId,
                homeSpaceName = homeName,
                isExternalLegacy = legacyFlag,
                sourceSpaceNameLegacy = legacyName,
            ),
            viewerSpaceId,
        )
    }
}
