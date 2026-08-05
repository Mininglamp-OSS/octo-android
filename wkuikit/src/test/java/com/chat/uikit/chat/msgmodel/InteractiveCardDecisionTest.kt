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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InteractiveCardDecision] 单测——承载 InteractiveCard(type=17) 渲染前**安全决策**
 * 的核心：sender trust 门禁、profile / cardVersion 协商、octo 白名单校验、URL scheme
 * 校验、结构预算校验。纯 JVM 可测（org.json + Android Log 只是打点没影响）。
 *
 * 服务端 direct-socket 写入可绕过 HTTP ingress，本层是**残余防线**——普通用户塞
 * 的 type-17 一律 fail-closed 退 plain，否则可造钓鱼卡冒充 bot 交互。
 *
 * 覆盖三部分：
 *  1. [decide] 顶层：sender trust → profile → validateOcto 各分支
 *  2. [InteractiveCardDecision.isSupportedProfile] / [InteractiveCardDecision.isSupportedCardVersion]
 *     / [InteractiveCardDecision.isSafeUrl] 纯函数
 *  3. [InteractiveCardDecision.validateOcto] 白名单 + 结构约束 + 预算 + 唯一 id
 */
class InteractiveCardDecisionTest {

    // ─────────────────────────────── decide: sender trust gate ───────────────────────────────

    @Test
    fun `decide with HUMAN sender always returns Plain (fail-closed)`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.HUMAN,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.5",
            validCard(),
        )
        assertEquals(InteractiveCardDecision.Decision.Plain, d)
    }

    @Test
    fun `decide with PENDING sender returns Plain until channelInfo hydrates`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.PENDING,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.5",
            validCard(),
        )
        assertEquals(InteractiveCardDecision.Decision.Plain, d)
    }

    // ─────────────────────────────── decide: profile / version negotiate ───────────────────────────────

    @Test
    fun `decide with unsupported profile returns Hint (update client)`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            "some/future-profile",
            "1.5",
            validCard(),
        )
        assertEquals(InteractiveCardDecision.Decision.Hint, d)
    }

    @Test
    fun `decide with version above client MAX returns Hint`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.9",
            validCard(),
        )
        assertEquals(InteractiveCardDecision.Decision.Hint, d)
    }

    @Test
    fun `decide with malformed version returns Hint`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "abc",
            validCard(),
        )
        assertEquals(InteractiveCardDecision.Decision.Hint, d)
    }

    // ─────────────────────────────── decide: validateOcto failures ───────────────────────────────

    @Test
    fun `decide with null card returns Plain`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.5",
            null,
        )
        assertEquals(InteractiveCardDecision.Decision.Plain, d)
    }

    @Test
    fun `decide with forbidden Action Execute returns Plain`() {
        val card = validCard().apply {
            put("actions", JSONArray().apply {
                put(JSONObject().apply { put("type", "Action.Execute"); put("id", "a") })
            })
        }
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.5",
            card,
        )
        assertEquals(InteractiveCardDecision.Decision.Plain, d)
    }

    // ─────────────────────────────── decide: success paths ───────────────────────────────

    @Test
    fun `decide BOT v1 returns Card allowInteractive=false interactive=true`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V1,
            "1.5",
            validCard(),
        ) as InteractiveCardDecision.Decision.Card
        assertFalse("v1 不允许交互输入", d.allowInteractive)
        assertTrue("BOT 卡开放提交", d.interactive)
    }

    @Test
    fun `decide BOT v2 returns Card allowInteractive=true interactive=true`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.5",
            validCard(),
        ) as InteractiveCardDecision.Decision.Card
        assertTrue(d.allowInteractive)
        assertTrue(d.interactive)
    }

    @Test
    fun `decide WEBHOOK v2 returns Card with interactive=false (display-only)`() {
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.WEBHOOK,
            InteractiveCardDecision.PROFILE_OCTO_V2,
            "1.5",
            validCard(),
        ) as InteractiveCardDecision.Decision.Card
        assertTrue(d.allowInteractive)
        assertFalse("webhook 卡展示-only，无事件消费端", d.interactive)
    }

    // ─────────────────────────────── isSupportedProfile ───────────────────────────────

    @Test
    fun `isSupportedProfile accepts octo v1 and v2 only`() {
        assertTrue(InteractiveCardDecision.isSupportedProfile("octo/v1"))
        assertTrue(InteractiveCardDecision.isSupportedProfile("octo/v2"))
        assertFalse(InteractiveCardDecision.isSupportedProfile(""))
        assertFalse(InteractiveCardDecision.isSupportedProfile("octo/v3"))
        assertFalse(InteractiveCardDecision.isSupportedProfile("v1"))
        assertFalse(InteractiveCardDecision.isSupportedProfile("OCTO/V1"))
    }

    // ─────────────────────────────── isSupportedCardVersion ───────────────────────────────

    @Test
    fun `isSupportedCardVersion accepts 1_5 and 1_6 (equal to MAX)`() {
        assertTrue(InteractiveCardDecision.isSupportedCardVersion("1.5"))
        assertTrue(InteractiveCardDecision.isSupportedCardVersion("1.6"))
    }

    @Test
    fun `isSupportedCardVersion rejects version above MAX minor`() {
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("1.7"))
    }

    @Test
    fun `isSupportedCardVersion rejects version above MAX major`() {
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("2.0"))
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("3.5"))
    }

    @Test
    fun `isSupportedCardVersion accepts version below MAX major`() {
        assertTrue(InteractiveCardDecision.isSupportedCardVersion("0.5"))
        assertTrue(InteractiveCardDecision.isSupportedCardVersion("0.9"))
    }

    @Test
    fun `isSupportedCardVersion rejects malformed strings`() {
        assertFalse(InteractiveCardDecision.isSupportedCardVersion(""))
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("1"))
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("1.5.0"))
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("abc"))
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("1.x"))
        assertFalse(InteractiveCardDecision.isSupportedCardVersion("-1.5"))
    }

    @Test
    fun `isSupportedCardVersion trims whitespace`() {
        assertTrue(InteractiveCardDecision.isSupportedCardVersion("  1.5  "))
    }

    // ─────────────────────────────── isSafeUrl ───────────────────────────────

    @Test
    fun `isSafeUrl accepts http and https`() {
        assertTrue(InteractiveCardDecision.isSafeUrl("http://example.com"))
        assertTrue(InteractiveCardDecision.isSafeUrl("https://example.com"))
        assertTrue(InteractiveCardDecision.isSafeUrl("HTTPS://EXAMPLE.COM/path?q=1"))
    }

    @Test
    fun `isSafeUrl rejects javascript file intent data content schemes`() {
        assertFalse(InteractiveCardDecision.isSafeUrl("javascript:alert(1)"))
        assertFalse(InteractiveCardDecision.isSafeUrl("JAVASCRIPT:alert(1)"))
        assertFalse(InteractiveCardDecision.isSafeUrl("file:///etc/passwd"))
        assertFalse(InteractiveCardDecision.isSafeUrl("intent://scan/#Intent;end"))
        assertFalse(InteractiveCardDecision.isSafeUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(InteractiveCardDecision.isSafeUrl("content://media/xyz"))
    }

    @Test
    fun `isSafeUrl rejects blank whitespace and non-URL strings`() {
        assertFalse(InteractiveCardDecision.isSafeUrl(""))
        assertFalse(InteractiveCardDecision.isSafeUrl("   "))
        assertFalse(InteractiveCardDecision.isSafeUrl("example.com"))
        assertFalse(InteractiveCardDecision.isSafeUrl("//example.com"))
    }

    @Test
    fun `isSafeUrl trims surrounding whitespace before scheme check`() {
        assertTrue(InteractiveCardDecision.isSafeUrl("  https://example.com  "))
    }

    // ─────────────────────────────── validateOcto: element whitelist ───────────────────────────────

    @Test
    fun `validateOcto accepts empty card`() {
        assertTrue(InteractiveCardDecision.validateOcto(JSONObject(), allowInteractive = false))
    }

    @Test
    fun `validateOcto accepts v1 basic elements`() {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply {
                put(el("TextBlock"))
                put(el("Image"))
                put(el("Container"))
                put(el("ColumnSet"))
                put(el("FactSet"))
                put(el("ActionSet"))
            })
        }
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto rejects Input under v1 (interactive-only element)`() {
        val card = cardWithBody(inputToggle("i1"))
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto accepts Input under v2`() {
        val card = cardWithBody(inputToggle("i1"))
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto rejects unknown element type`() {
        val card = cardWithBody(el("Mystery.Widget"))
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto accepts element without type field (Column TableRow SDK infers)`() {
        // AC 3.x Column / TableRow / TableCell 的 type 字段可选，走父上下文推断
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "ColumnSet")
                    put("columns", JSONArray().apply {
                        put(JSONObject().apply {
                            // 没有 type 字段
                            put("items", JSONArray().apply { put(el("TextBlock")) })
                        })
                    })
                })
            })
        }
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    // ─────────────────────────────── validateOcto: action whitelist ───────────────────────────────

    @Test
    fun `validateOcto accepts Action OpenUrl with safe url`() {
        val card = cardWithActions(action("Action.OpenUrl", "a1").apply {
            put("url", "https://example.com/x")
        })
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto rejects Action OpenUrl with javascript url`() {
        val card = cardWithActions(action("Action.OpenUrl", "a1").apply {
            put("url", "javascript:alert(1)")
        })
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto rejects Action OpenUrl with file url`() {
        val card = cardWithActions(action("Action.OpenUrl", "a1").apply {
            put("url", "file:///etc/passwd")
        })
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto accepts Action ToggleVisibility`() {
        val card = cardWithActions(action("Action.ToggleVisibility", "a1"))
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto rejects Action Submit under v1`() {
        val card = cardWithActions(action("Action.Submit", "a1"))
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto accepts Action Submit under v2`() {
        val card = cardWithActions(action("Action.Submit", "a1"))
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto rejects Action Submit without id under v2`() {
        val card = cardWithActions(JSONObject().apply { put("type", "Action.Submit") })
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto rejects Action Execute even under v2`() {
        val card = cardWithActions(action("Action.Execute", "a1"))
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `decide BOT v1 display card with CopyToClipboard returns Card not Plain`() {
        val card = cardWithActions(JSONObject().apply {
            put("type", "Action.CopyToClipboard"); put("title", "复制")
        })
        val d = InteractiveCardDecision.decide(
            CardSenderTrust.BOT,
            InteractiveCardDecision.PROFILE_OCTO_V1,
            "1.5",
            card,
        )
        assertTrue("含复制按钮的展示卡应正常渲染而非整卡降级 plain", d is InteractiveCardDecision.Decision.Card)
    }

    @Test
    fun `validateOcto rejects Action ShowCard even under v2`() {
        val card = cardWithActions(action("Action.ShowCard", "a1"))
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto tolerates unknown action type (not whole-card fatal)`() {
        // 对齐 iOS：未知 action 不毙整卡，渲染时由 sanitizer 剥离该按钮。
        val card = cardWithActions(action("Action.WeirdCustom", "a1"))
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto rejects action without type`() {
        val card = cardWithActions(JSONObject().apply { put("id", "a1") })
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto tolerates Action CopyToClipboard (stripped at render)`() {
        // AC 3.7.0 Android SDK 无内置 CopyToClipboardActionParser，但不应毙整卡（对齐 iOS）：
        // 校验容忍 + 由 sanitizer 剥离该按钮，展示卡正常渲染、仅复制按钮不出现。
        val card = cardWithActions(action("Action.CopyToClipboard", "a1"))
        assertTrue(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    // ─────────────────────────────── validateOcto: id uniqueness ───────────────────────────────

    @Test
    fun `validateOcto rejects Input without id`() {
        val card = cardWithBody(JSONObject().apply { put("type", "Input.Text") })
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto rejects duplicate Input ids`() {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply {
                put(inputText("dup"))
                put(inputText("dup"))
            })
        }
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    @Test
    fun `validateOcto rejects duplicate Input and Submit action ids`() {
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", JSONArray().apply { put(inputText("dup")) })
            put("actions", JSONArray().apply {
                put(action("Action.Submit", "dup"))
            })
        }
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = true))
    }

    // ─────────────────────────────── validateOcto: budget ───────────────────────────────

    @Test
    fun `validateOcto rejects card exceeding MAX_DEPTH`() {
        // 构造 20 层嵌套 Container 超过 MAX_DEPTH=16
        var innermost: JSONObject = el("TextBlock")
        for (i in 0 until 20) {
            val outer = JSONObject().apply {
                put("type", "Container")
                put("items", JSONArray().apply { put(innermost) })
            }
            innermost = outer
        }
        val card = cardWithBody(innermost)
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto rejects card exceeding MAX_NODES`() {
        // 300 个 TextBlock 超过 MAX_NODES=200
        val body = JSONArray()
        for (i in 0 until 300) body.put(el("TextBlock"))
        val card = JSONObject().apply {
            put("type", "AdaptiveCard")
            put("body", body)
        }
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    // ─────────────────────────────── validateOcto: recursion coverage ───────────────────────────────

    @Test
    fun `validateOcto walks selectAction and rejects unsafe url`() {
        val card = cardWithBody(JSONObject().apply {
            put("type", "TextBlock")
            put("selectAction", action("Action.OpenUrl", "a1").apply {
                put("url", "javascript:alert(1)")
            })
        })
        assertFalse(InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `validateOcto walks nested container items`() {
        val innerEl = JSONObject().apply { put("type", "Mystery.Widget") }
        val card = cardWithBody(JSONObject().apply {
            put("type", "Container")
            put("items", JSONArray().apply { put(innerEl) })
        })
        assertFalse("嵌套里的未知元素也应被拒", InteractiveCardDecision.validateOcto(card, allowInteractive = false))
    }

    @Test
    fun `Decision Card is a data class with expected fields`() {
        val c = InteractiveCardDecision.Decision.Card(allowInteractive = true, interactive = false)
        assertNotNull(c)
        assertTrue(c.allowInteractive)
        assertFalse(c.interactive)
    }

    // ─────────────────────────────── 辅助 ───────────────────────────────

    private fun validCard(): JSONObject = JSONObject().apply {
        put("type", "AdaptiveCard")
        put("body", JSONArray().apply { put(el("TextBlock")) })
    }

    private fun el(type: String): JSONObject = JSONObject().apply { put("type", type) }

    private fun action(type: String, id: String): JSONObject = JSONObject().apply {
        put("type", type)
        put("id", id)
    }

    private fun inputText(id: String): JSONObject = JSONObject().apply {
        put("type", "Input.Text")
        put("id", id)
    }

    private fun inputToggle(id: String): JSONObject = JSONObject().apply {
        put("type", "Input.Toggle")
        put("id", id)
        put("title", "标签")
    }

    private fun cardWithBody(vararg elements: JSONObject): JSONObject = JSONObject().apply {
        put("type", "AdaptiveCard")
        put("body", JSONArray().apply { elements.forEach { put(it) } })
    }

    private fun cardWithActions(vararg actions: JSONObject): JSONObject = JSONObject().apply {
        put("type", "AdaptiveCard")
        put("actions", JSONArray().apply { actions.forEach { put(it) } })
    }
}
