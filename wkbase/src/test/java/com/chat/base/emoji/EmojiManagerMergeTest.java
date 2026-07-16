/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chat.base.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EmojiManager#mergeXmlAndManifest} 的合并契约测试。
 *
 * 核心保证：
 * 1. 内置 xml 打底始终保留（"merge 不删"）
 * 2. manifest 有对应 xml asset 的 key → 合并，id/assetPath 沿用 xml，name/url 用 manifest
 * 3. manifest 中 xml 未打包的 key → <b>整条跳过</b>（不进 text2entry、不进 defaultEntries），
 *    避免面板显示空白格子（PR #94 review Jerry-Xin B1）
 * 4. defaultEntries 排序：manifest customs 在前 → xml-only customs → Unicode
 * 5. 空 manifest 不破坏原状态（只留 xml）
 */
public class EmojiManagerMergeTest {

    private static EmojiManifestItem item(String key, String name, String url) {
        EmojiManifestItem it = new EmojiManifestItem();
        it.key = key;
        it.name = name;
        it.url = url;
        return it;
    }

    private static EmojiManager.Entry xmlEntry(String id, String text, String assetPath) {
        return new EmojiManager.Entry(id, text, assetPath);
    }

    /** 模拟真实的 xml 状态：4 custom + 2 Unicode。 */
    private static Map<String, EmojiManager.Entry> buildXmlMap(List<EmojiManager.Entry> defaults) {
        Map<String, EmojiManager.Entry> m = new LinkedHashMap<>();
        for (EmojiManager.Entry e : defaults) m.put(e.text, e);
        return m;
    }

    private List<EmojiManager.Entry> xmlDefaults;
    private Map<String, EmojiManager.Entry> xmlMap;

    private void setupTypicalXml() {
        xmlDefaults = new ArrayList<>();
        xmlDefaults.add(xmlEntry("custom_mission", "[使命必达]", "emoji/default/custom_mission.png"));
        xmlDefaults.add(xmlEntry("custom_action", "[崇尚行动]", "emoji/default/custom_action.png"));
        xmlDefaults.add(xmlEntry("custom_taste", "[有品位]", "emoji/default/custom_taste.png"));
        xmlDefaults.add(xmlEntry("custom_shangfang", "[尚方宝剑]", "emoji/default/custom_shangfang.png"));
        xmlDefaults.add(xmlEntry("0_0", "😀", "emoji/default/0_0.png"));
        xmlDefaults.add(xmlEntry("0_1", "😃", "emoji/default/0_1.png"));
        xmlMap = buildXmlMap(xmlDefaults);
    }

    // ---- 核心契约：manifest 全部对齐 xml → 全部合并 ----

    @Test
    public void merge_all_manifest_keys_match_xml() {
        setupTypicalXml();
        List<EmojiManifestItem> manifest = Arrays.asList(
                item("[使命必达]", "使命必达", ""),
                item("[崇尚行动]", "崇尚行动", ""),
                item("[有品位]", "有品位", ""),
                item("[尚方宝剑]", "尚方宝剑", ""));

        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(xmlMap, xmlDefaults, manifest);

        assertEquals(0, r.skippedNoAsset);
        assertEquals(6, r.defaultEntries.size()); // 4 custom + 2 Unicode
        // manifest 顺序在前 → 4 个 custom + 2 个 Unicode
        assertEquals("[使命必达]", r.defaultEntries.get(0).text);
        assertEquals("[尚方宝剑]", r.defaultEntries.get(3).text);
        assertEquals("😀", r.defaultEntries.get(4).text);
        // text2entry 里全部 4 个 custom 都能查到
        assertTrue(r.text2entry.containsKey("[使命必达]"));
        assertTrue(r.text2entry.containsKey("[尚方宝剑]"));
        assertTrue(r.text2entry.containsKey("😀"));
    }

    // ---- 核心契约（B1）：manifest-only key（xml 未打包）不能进面板也不能进 text2entry ----

    @Test
    public void merge_manifest_only_key_is_excluded_entirely() {
        setupTypicalXml();
        List<EmojiManifestItem> manifest = Arrays.asList(
                item("[使命必达]", "使命必达", ""),
                item("[未来新表情]", "未来新", "https://cdn.example.com/new.png"), // xml 未打包
                item("[尚方宝剑]", "尚方宝剑", ""));

        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(xmlMap, xmlDefaults, manifest);

        assertEquals("manifest-only key 应计入 skipped", 1, r.skippedNoAsset);
        // 关键：panel 里不该出现 [未来新表情]（否则显示为空白 tap-able cell）
        for (EmojiManager.Entry e : r.defaultEntries) {
            assertFalse("panel 不该含 manifest-only key: " + e.text,
                    "[未来新表情]".equals(e.text));
        }
        // 关键：text2entry 里也不该有 [未来新表情]（否则消息 pattern 会匹配但 getDrawable 返 null）
        assertFalse("text2entry 不该含 manifest-only key",
                r.text2entry.containsKey("[未来新表情]"));
        // sanity：xml 打底的仍在
        assertTrue(r.text2entry.containsKey("[使命必达]"));
        assertTrue(r.text2entry.containsKey("[尚方宝剑]"));
    }

    // ---- 合并契约：xml 已有的 key，url 用 manifest 下发的（remoteUrl 存进 Entry） ----

    @Test
    public void merge_updates_remote_url_for_matched_xml_key() {
        setupTypicalXml();
        String futureUrl = "https://cdn.example.com/mission_v2.png";
        List<EmojiManifestItem> manifest = Collections.singletonList(
                item("[使命必达]", "使命必达", futureUrl));

        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(xmlMap, xmlDefaults, manifest);

        EmojiManager.Entry mission = r.text2entry.get("[使命必达]");
        // id / assetPath 沿用 xml，remoteUrl 用 manifest
        assertEquals("custom_mission", mission.id);
        assertEquals("emoji/default/custom_mission.png", mission.assetPath);
        assertEquals(futureUrl, mission.remoteUrl);
    }

    // ---- merge 不删：xml 里有但 manifest 没下发的，保留 ----

    @Test
    public void merge_preserves_xml_only_customs() {
        setupTypicalXml();
        // manifest 只有 2 个，xml 有 4 个 custom
        List<EmojiManifestItem> manifest = Arrays.asList(
                item("[使命必达]", "使命必达", ""),
                item("[尚方宝剑]", "尚方宝剑", ""));

        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(xmlMap, xmlDefaults, manifest);

        assertEquals(0, r.skippedNoAsset);
        // 4 custom（2 manifest + 2 xml-only）+ 2 Unicode
        assertEquals(6, r.defaultEntries.size());
        // manifest 顺序在前：使命必达, 尚方宝剑
        assertEquals("[使命必达]", r.defaultEntries.get(0).text);
        assertEquals("[尚方宝剑]", r.defaultEntries.get(1).text);
        // xml-only customs 追加：崇尚行动, 有品位
        assertEquals("[崇尚行动]", r.defaultEntries.get(2).text);
        assertEquals("[有品位]", r.defaultEntries.get(3).text);
        // Unicode 最后
        assertEquals("😀", r.defaultEntries.get(4).text);
        assertEquals("😃", r.defaultEntries.get(5).text);
    }

    // ---- 空 manifest：只有 xml 打底 ----

    @Test
    public void merge_empty_manifest_returns_xml_state() {
        setupTypicalXml();
        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(
                xmlMap, xmlDefaults, Collections.<EmojiManifestItem>emptyList());

        assertEquals(0, r.skippedNoAsset);
        assertEquals(6, r.defaultEntries.size());
        // 顺序与 xml 完全一致
        for (int i = 0; i < xmlDefaults.size(); i++) {
            assertEquals(xmlDefaults.get(i).text, r.defaultEntries.get(i).text);
        }
    }

    // ---- 返回的容器不可变 ----

    @Test(expected = UnsupportedOperationException.class)
    public void merge_result_text2entry_is_immutable() {
        setupTypicalXml();
        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(
                xmlMap, xmlDefaults, Collections.<EmojiManifestItem>emptyList());
        r.text2entry.put("[hacker]", null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void merge_result_defaults_is_immutable() {
        setupTypicalXml();
        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(
                xmlMap, xmlDefaults, Collections.<EmojiManifestItem>emptyList());
        r.defaultEntries.clear();
    }

    // ---- xml 未打包 + 多 manifest 项 → 全部跳过而不影响其它 ----

    @Test
    public void merge_multiple_manifest_only_all_skipped_others_intact() {
        setupTypicalXml();
        List<EmojiManifestItem> manifest = Arrays.asList(
                item("[使命必达]", "使命必达", ""),
                item("[新表情A]", "A", "https://cdn/a.png"),
                item("[新表情B]", "B", "https://cdn/b.png"),
                item("[新表情C]", "C", "https://cdn/c.png"));

        EmojiManager.MergeResult r = EmojiManager.mergeXmlAndManifest(xmlMap, xmlDefaults, manifest);

        assertEquals(3, r.skippedNoAsset);
        // manifest 只处理成功 1 个 (使命必达)，加上 xml-only 3 个 custom + 2 unicode = 6
        assertEquals(6, r.defaultEntries.size());
        assertFalse(r.text2entry.containsKey("[新表情A]"));
        assertFalse(r.text2entry.containsKey("[新表情B]"));
        assertFalse(r.text2entry.containsKey("[新表情C]"));
    }

    // ---- buildPattern 空 list 兜底（P2-2）：无 entry 时返回永不匹配的 (?!) 而不是零宽 () ----

    @Test
    public void buildPattern_empty_list_returns_never_matching_pattern() {
        java.util.regex.Pattern p = EmojiManager.buildPattern(Collections.<EmojiManager.Entry>emptyList());
        // 空 pattern 不该匹配任何字符串——包括空串
        assertFalse(p.matcher("").find());
        assertFalse(p.matcher("hello [使命必达] world").find());
        assertFalse(p.matcher("[a]").find());
    }

    @Test
    public void buildPattern_non_empty_matches_registered_tokens() {
        List<EmojiManager.Entry> entries = new ArrayList<>();
        entries.add(xmlEntry("custom_a", "[a]", "emoji/a.png"));
        entries.add(xmlEntry("custom_b", "[b]", "emoji/b.png"));
        java.util.regex.Pattern p = EmojiManager.buildPattern(entries);
        assertTrue(p.matcher("hello [a] world").find());
        assertTrue(p.matcher("[b]").find());
        assertFalse(p.matcher("[c]").find());
        assertFalse(p.matcher("plain text").find());
    }
}
