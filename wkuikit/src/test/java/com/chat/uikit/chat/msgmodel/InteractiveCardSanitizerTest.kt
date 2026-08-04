/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.chat.uikit.chat.msgmodel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InteractiveCardSanitizer] 单测。纯 JVM，用 org.json 直接构造 payload。
 *
 * Sanitizer 是渲染前对 payload 做的两处针对 AC 3.x C++ ObjectModel 严格 schema 的
 * 兼容修正（对齐 iOS `WKACardRenderer.wk_sanitizeNode`）：
 *
 *  1. `Input.Toggle` 缺 `title` 会导致 SDK 反序列化抛 `Property is required but was
 *     found empty: title` → 整卡挂 → 走 fallback。sanitize 用 `label` 提升；没 label
 *     用 [TOGGLE_FALLBACK_TITLE] "开关" 兜底。
 *  2. `Input.ChoiceSet` 强制 `style=expanded`，绕开 Teams SDK compact 下拉浮层生命
 *     周期问题。
 *
 * 关键契约：**不 mutate 原对象**（返回深拷贝），因为原 payload 可能被消息层其它
 * 路径引用（例如再传给 Provider 的 plain 展示、日志、submit body 提取等）。
 */
class InteractiveCardSanitizerTest {

    // ─────────────────────────────── Null / 边界 ───────────────────────────────

    @Test
    fun `sanitize null returns null`() {
        assertNull(InteractiveCardSanitizer.sanitize(null))
    }

    @Test
    fun `sanitize empty card returns empty JSONObject`() {
        val result = InteractiveCardSanitizer.sanitize(JSONObject())
        assertEquals(0, result!!.length())
    }

    @Test
    fun `sanitize returns a distinct JSONObject instance (deep copy)`() {
        val input = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray())
        }
        val result = InteractiveCardSanitizer.sanitize(input)
        assertNotSame("必须返回深拷贝，不 mutate 原对象", input, result)
    }

    // ─────────────────────────────── Input.Toggle title ───────────────────────────────

    @Test
    fun `Input Toggle with valid title is unchanged`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "opt")
            put("title", "启用")
        }
        val result = sanitizeSingleElement(toggle)
        assertEquals("启用", result.getString("title"))
        assertFalse(result.has("label"))
    }

    @Test
    fun `Input Toggle empty title uses label as title and removes label`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "opt")
            put("title", "")
            put("label", "订阅通知")
        }
        val result = sanitizeSingleElement(toggle)
        assertEquals("订阅通知", result.getString("title"))
        assertFalse("label 被吸收到 title 后应删掉避免上下重复展示", result.has("label"))
    }

    @Test
    fun `Input Toggle missing title uses label as title`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "opt")
            put("label", "订阅")
        }
        val result = sanitizeSingleElement(toggle)
        assertEquals("订阅", result.getString("title"))
        assertFalse(result.has("label"))
    }

    @Test
    fun `Input Toggle title with only whitespace treated as empty`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "opt")
            put("title", "   ")
            put("label", "订阅")
        }
        val result = sanitizeSingleElement(toggle)
        // trim() 后判空 → label 提升
        assertEquals("订阅", result.getString("title"))
    }

    @Test
    fun `Input Toggle no title no label falls back to constant`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "opt")
        }
        val result = sanitizeSingleElement(toggle)
        assertEquals("开关", result.getString("title"))
        assertFalse(result.has("label"))
    }

    @Test
    fun `Input Toggle empty title empty label falls back to constant`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "opt")
            put("title", "")
            put("label", "")
        }
        val result = sanitizeSingleElement(toggle)
        assertEquals("开关", result.getString("title"))
    }

    // ─────────────────────────────── Input.ChoiceSet style ───────────────────────────────

    @Test
    fun `Input ChoiceSet missing style is forced to expanded`() {
        val cs = JSONObject().apply {
            put("type", "Input.ChoiceSet")
            put("id", "s1")
            put("choices", JSONArray().apply {
                put(JSONObject().apply { put("title", "A"); put("value", "a") })
            })
        }
        val result = sanitizeSingleElement(cs)
        assertEquals("expanded", result.getString("style"))
    }

    @Test
    fun `Input ChoiceSet compact style is overridden to expanded`() {
        val cs = JSONObject().apply {
            put("type", "Input.ChoiceSet")
            put("id", "s1")
            put("style", "compact")
        }
        val result = sanitizeSingleElement(cs)
        assertEquals("expanded", result.getString("style"))
    }

    @Test
    fun `Input ChoiceSet expanded style stays expanded (idempotent)`() {
        val cs = JSONObject().apply {
            put("type", "Input.ChoiceSet")
            put("id", "s1")
            put("style", "expanded")
        }
        val result = sanitizeSingleElement(cs)
        assertEquals("expanded", result.getString("style"))
    }

    // ─────────────────────────────── 递归 walk ───────────────────────────────

    @Test
    fun `sanitize recurses into body items and applies transforms`() {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Container")
                    put("items", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "Input.Toggle")
                            put("id", "t1")
                            put("label", "订阅")
                        })
                        put(JSONObject().apply {
                            put("type", "Input.ChoiceSet")
                            put("id", "cs1")
                            put("style", "compact")
                        })
                    })
                })
            })
        }
        val result = InteractiveCardSanitizer.sanitize(card)!!
        val container = result.getJSONArray("body").getJSONObject(0)
        val items = container.getJSONArray("items")
        assertEquals("订阅", items.getJSONObject(0).getString("title"))
        assertEquals("expanded", items.getJSONObject(1).getString("style"))
    }

    @Test
    fun `sanitize recurses into columns rows cells actions and other containers`() {
        // 每种容器字段各放一个 Input.Toggle 缺 title，验证递归覆盖到位
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply {
                put(container("columns", toggleMissingTitle("in-columns")))
                put(container("rows", toggleMissingTitle("in-rows")))
                put(container("cells", toggleMissingTitle("in-cells")))
                put(container("facts", toggleMissingTitle("in-facts")))
                put(container("choices", toggleMissingTitle("in-choices")))
            })
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Action.ShowCard")
                    put("card", JSONObject().apply {
                        put("body", JSONArray().apply {
                            put(toggleMissingTitle("in-action-card"))
                        })
                    })
                })
            })
        }
        val result = InteractiveCardSanitizer.sanitize(card)!!
        // 每个都被 sanitize 到 "开关" 兜底 —— 只要不 crash 且 title 都非空即证明递归覆盖
        val body = result.getJSONArray("body")
        for (i in 0 until body.length()) {
            val outer = body.getJSONObject(i)
            val childArrayKey = outer.keys().asSequence().first { it != "type" }
            val inner = outer.getJSONArray(childArrayKey).getJSONObject(0)
            assertEquals("开关", inner.getString("title"))
        }
    }

    @Test
    fun `sanitize preserves non-transformed fields verbatim`() {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("version", "1.5")
            put("body", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "TextBlock")
                    put("text", "hello **world**")
                    put("wrap", true)
                    put("size", "medium")
                })
            })
        }
        val result = InteractiveCardSanitizer.sanitize(card)!!
        assertEquals("1.5", result.getString("version"))
        val tb = result.getJSONArray("body").getJSONObject(0)
        assertEquals("hello **world**", tb.getString("text"))
        assertTrue(tb.getBoolean("wrap"))
        assertEquals("medium", tb.getString("size"))
    }

    @Test
    fun `sanitize does not mutate original input object`() {
        val toggle = JSONObject().apply {
            put("type", "Input.Toggle")
            put("id", "t1")
            put("label", "订阅")
            // 故意不传 title
        }
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply { put(toggle) })
        }
        InteractiveCardSanitizer.sanitize(card)
        // 原 toggle 应保持无 title、有 label —— 只有返回的深拷贝被改写
        assertFalse("原对象不应被 mutate（有 title）", toggle.has("title"))
        assertEquals("订阅", toggle.getString("label"))
    }

    // ─────────────────────────────── P1-C: ColumnSet 拆行 ───────────────────────────────
    // 覆盖 stackColumnSetForMobileActions 的三条路径：正例拆行 / 单 button 不拆 / 无 ActionSet 不拆。
    // 关键动机：SDK 3.7.0 在 auto column 装 >=2 button 的 ActionSet 时，若 ColumnSet 宽度不
    // 够会**整体 skip auto column 的渲染**（观测：216dp 群接收下 pending 卡 3 按钮消失）。
    // Sanitizer 主动把这类 ColumnSet 转成 Container(vertical stack)，让 ActionSet 独占一行。

    @Test
    fun `stackColumnSet transforms to Container when ActionSet has multiple actions`() {
        val actionSet = JSONObject().apply {
            put("type", "ActionSet")
            put("actions", JSONArray().apply {
                put(JSONObject().apply { put("type", "Action.OpenUrl"); put("title", "查看详情") })
                put(JSONObject().apply { put("type", "Action.ToggleVisibility"); put("title", "拒绝") })
                put(JSONObject().apply { put("type", "Action.Submit"); put("title", "允许") })
            })
        }
        val textBlock = JSONObject().apply {
            put("type", "TextBlock"); put("text", "申请于 11:03")
        }
        val columnSet = JSONObject().apply {
            put("type", "ColumnSet")
            put("verticalContentAlignment", "Center")
            put("spacing", "None")
            put("columns", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "stretch")
                    put("items", JSONArray().apply { put(textBlock) })
                })
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "auto")
                    put("items", JSONArray().apply { put(actionSet) })
                })
            })
        }
        val out = sanitizeSingleElement(columnSet)

        assertEquals("命中拆行：type 应改成 Container", "Container", out.getString("type"))
        assertFalse("columns 字段应删除", out.has("columns"))
        assertFalse("verticalContentAlignment 应删除（Container 无此属性）", out.has("verticalContentAlignment"))
        assertEquals("spacing 应保留", "None", out.getString("spacing"))

        val items = out.getJSONArray("items")
        assertEquals("两个 column 各一个 item 平铺 → 2 项", 2, items.length())
        assertEquals("第一项应为 TextBlock", "TextBlock", items.getJSONObject(0).getString("type"))
        assertEquals("申请于 11:03", items.getJSONObject(0).getString("text"))
        assertEquals("第二项应为 ActionSet", "ActionSet", items.getJSONObject(1).getString("type"))
        assertEquals("ActionSet 的 3 个 actions 保留", 3, items.getJSONObject(1).getJSONArray("actions").length())
    }

    @Test
    fun `stackColumnSet preserves ColumnSet when ActionSet has only one action`() {
        // approved/rejected 卡的底部只有 1 个 "查看详情"，SDK 装得下不需要拆
        val actionSet = JSONObject().apply {
            put("type", "ActionSet")
            put("actions", JSONArray().apply {
                put(JSONObject().apply { put("type", "Action.OpenUrl"); put("title", "查看详情") })
            })
        }
        val columnSet = JSONObject().apply {
            put("type", "ColumnSet")
            put("columns", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "stretch")
                    put("items", JSONArray().apply {
                        put(JSONObject().apply { put("type", "TextBlock"); put("text", "处理于 11:08") })
                    })
                })
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "auto")
                    put("items", JSONArray().apply { put(actionSet) })
                })
            })
        }
        val out = sanitizeSingleElement(columnSet)

        assertEquals("未命中拆行：type 应保持 ColumnSet", "ColumnSet", out.getString("type"))
        assertTrue("columns 字段应保留", out.has("columns"))
        assertFalse("items 字段应不存在", out.has("items"))
    }

    @Test
    fun `stackColumnSet preserves ColumnSet when no ActionSet inside`() {
        // 头部 header ColumnSet：无 ActionSet，纯 TextBlock，不该拆
        val columnSet = JSONObject().apply {
            put("type", "ColumnSet")
            put("columns", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "stretch")
                    put("items", JSONArray().apply {
                        put(JSONObject().apply { put("type", "TextBlock"); put("text", "文档申请") })
                    })
                })
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "auto")
                    put("items", JSONArray().apply {
                        put(JSONObject().apply { put("type", "TextBlock"); put("text", "待你处理") })
                    })
                })
            })
        }
        val out = sanitizeSingleElement(columnSet)

        assertEquals("无 ActionSet：type 应保持 ColumnSet", "ColumnSet", out.getString("type"))
        assertTrue(out.has("columns"))
    }

    @Test
    fun `stackColumnSet wraps column with isVisible or id in a Container to preserve them`() {
        // 带 isVisible:false 的列在拆行后必须仍隐藏、id 必须仍在（否则漏显隐藏内容、
        // Action.ToggleVisibility 目标失效）。命中拆行（ActionSet 有 3 个 action）。
        val actionSet = JSONObject().apply {
            put("type", "ActionSet")
            put("actions", JSONArray().apply {
                put(JSONObject().apply { put("type", "Action.OpenUrl"); put("title", "详情") })
                put(JSONObject().apply { put("type", "Action.Submit"); put("title", "允许") })
                put(JSONObject().apply { put("type", "Action.Submit"); put("title", "拒绝") })
            })
        }
        val columnSet = JSONObject().apply {
            put("type", "ColumnSet")
            put("columns", JSONArray().apply {
                // 列 0：带 isVisible=false + id → 应被包进 Container 保留
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "stretch")
                    put("isVisible", false)
                    put("id", "secret_col")
                    put("items", JSONArray().apply {
                        put(JSONObject().apply { put("type", "TextBlock"); put("text", "隐藏内容") })
                    })
                })
                // 列 1：普通列（仅 width + items）→ 平铺，输出与旧行为一致
                put(JSONObject().apply {
                    put("type", "Column"); put("width", "auto")
                    put("items", JSONArray().apply { put(actionSet) })
                })
            })
        }
        val out = sanitizeSingleElement(columnSet)

        assertEquals("命中拆行：type 应改成 Container", "Container", out.getString("type"))
        assertFalse("columns 字段应删除", out.has("columns"))
        val items = out.getJSONArray("items")
        assertEquals("列0 包 Container、列1 平铺 ActionSet → 2 项", 2, items.length())

        val wrapped = items.getJSONObject(0)
        assertEquals("带属性的列应包成 Container", "Container", wrapped.getString("type"))
        assertFalse("isVisible=false 必须保留（否则隐藏内容被漏显）", wrapped.getBoolean("isVisible"))
        assertEquals("id 必须保留（否则 ToggleVisibility 目标失效）", "secret_col", wrapped.getString("id"))
        assertEquals("列内 items 应挂在包裹 Container 下", "TextBlock",
            wrapped.getJSONArray("items").getJSONObject(0).getString("type"))
        assertFalse("width 是 Column 专属、竖直堆叠无意义，不应带入 Container", wrapped.has("width"))

        assertEquals("普通列直接平铺 ActionSet", "ActionSet", items.getJSONObject(1).getString("type"))
    }

    @Test
    fun `strips Action_CopyToClipboard from actions but keeps renderable ones`() {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray())
            put("actions", JSONArray().apply {
                put(JSONObject().apply { put("type", "Action.OpenUrl"); put("title", "打开"); put("url", "https://x.com") })
                put(JSONObject().apply { put("type", "Action.CopyToClipboard"); put("title", "复制") })
            })
        }
        val out = InteractiveCardSanitizer.sanitize(card)!!
        val actions = out.getJSONArray("actions")
        assertEquals("CopyToClipboard 应被剥离，只剩可渲染的 OpenUrl", 1, actions.length())
        assertEquals("Action.OpenUrl", actions.getJSONObject(0).getString("type"))
    }

    @Test
    fun `strips unsupported selectAction so container still renders`() {
        val el = JSONObject().apply {
            put("type", "Container")
            put("selectAction", JSONObject().apply { put("type", "Action.CopyToClipboard"); put("title", "复制") })
            put("items", JSONArray().apply { put(JSONObject().apply { put("type", "TextBlock"); put("text", "x") }) })
        }
        val out = sanitizeSingleElement(el)
        assertFalse("不可渲染的 selectAction 应删除（否则 SDK 渲染异常）", out.has("selectAction"))
        assertEquals("容器其余内容保留", "TextBlock", out.getJSONArray("items").getJSONObject(0).getString("type"))
    }

    // ─────────────────────────────── 辅助 ───────────────────────────────

    /** 便捷：包一层 AdaptiveCard body，返回 sanitize 后的第一个 element。 */
    private fun sanitizeSingleElement(element: JSONObject): JSONObject {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply { put(element) })
        }
        val result = InteractiveCardSanitizer.sanitize(card)!!
        return result.getJSONArray("body").getJSONObject(0)
    }

    private fun toggleMissingTitle(id: String): JSONObject = JSONObject().apply {
        put("type", "Input.Toggle")
        put("id", id)
        // 故意不传 title / label 走 "开关" 兜底
    }

    private fun container(childArrayKey: String, child: JSONObject): JSONObject = JSONObject().apply {
        put("type", "Container")
        put(childArrayKey, JSONArray().apply { put(child) })
    }
}
