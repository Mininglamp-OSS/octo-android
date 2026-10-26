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

package com.chat.uikit.chat.msgmodel;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * 共享文档卡片的真实资源断言：getKindLabel / getDisplayContent(Context) /
 * getSearchableWord(Context) 及 Parcel 往返。需要真实 Context 与资源，放 androidTest。
 * 不调用 WKUIKitApplication.init()，避免启动网络及数据库初始化。
 */
@RunWith(AndroidJUnit4.class)
public class WKDocumentShareContentResourceTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // 测试资源配置固定为简体中文
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.SIMPLIFIED_CHINESE);
        context = context.createConfigurationContext(config);
    }

    @Test
    public void kindLabels_mapKnownKinds() {
        assertEquals("文档", labelFor("doc"));
        assertEquals("表格", labelFor("sheet"));
        assertEquals("画板", labelFor("board"));
        assertEquals("网页", labelFor("html"));
    }

    @Test
    public void kindLabels_unknownEmptyNullFallBackToDoc() {
        WKDocumentShareContent c = contentWith("future-kind", "标题");
        assertEquals("文档", c.getKindLabel(context));
        assertEquals("future-kind", c.kind);
        assertEquals("文档", labelFor(""));
        assertEquals("文档", labelFor(null));
    }

    @Test
    public void englishResources_useDocumentAndWebPageLabels() {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.ENGLISH);
        Context english = context.createConfigurationContext(config);
        assertEquals("Web page", english.getString(com.chat.uikit.R.string.doc_share_kind_html));
        assertEquals("[Document]", english.getString(com.chat.uikit.R.string.doc_share_summary_untitled));
        WKDocumentShareContent value = contentWith("sheet", "Quarterly plan");
        assertEquals("[Document] Quarterly plan", value.getDisplayContent(english));
        assertEquals("Quarterly plan", value.getSearchableWord(english));
    }

    @Test
    public void displayContent_withTitle_unifiedDocumentPrefixAndRawSearchTitle() {
        for (String kind : new String[]{"doc", "sheet", "board", "html", "future-kind"}) {
            WKDocumentShareContent c = contentWith(kind, "季度计划");
            String expected = "[文档] 季度计划";
            assertEquals(expected, c.getDisplayContent(context));
            assertEquals("季度计划", c.getSearchableWord(context));
        }
    }

    @Test
    public void displayContent_emptyTitleUsesDocumentLabelAndWhitespaceIsPreserved() {
        String[] titles = {"", null};
        for (String kind : new String[]{"doc", "sheet", "board", "html", "future-kind"}) {
            for (String title : titles) {
                WKDocumentShareContent c = contentWith(kind, title);
                String expected = "[文档]";
                assertEquals(expected, c.getDisplayContent(context));
                assertEquals(expected, c.getSearchableWord(context));
            }
        }
        WKDocumentShareContent spaces = contentWith("doc", "   ");
        assertEquals("[文档]    ", spaces.getDisplayContent(context));
        assertEquals("   ", spaces.getSearchableWord(context));
    }

    @Test
    public void parcelRoundTrip_preservesAllFields() {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.decodeMsg(new org.json.JSONObject());
        c.docId = "7f3a2b1c";
        c.spaceId = "sp-9";
        c.kind = "sheet";
        c.title = "季度计划";
        c.ownerName = "张三";
        c.updatedAt = "2026-10-26 14:30";
        c.url = "/d/7f3a2b1c?from=share";
        c.permission = "reader";

        Parcel parcel = Parcel.obtain();
        try {
            c.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            WKDocumentShareContent reparsed = WKDocumentShareContent.CREATOR.createFromParcel(parcel);

            assertEquals("7f3a2b1c", reparsed.docId);
            assertEquals("sp-9", reparsed.spaceId);
            assertEquals("sheet", reparsed.kind);
            assertEquals("季度计划", reparsed.title);
            assertEquals("张三", reparsed.ownerName);
            assertEquals("2026-10-26 14:30", reparsed.updatedAt);
            assertEquals("/d/7f3a2b1c?from=share", reparsed.url);
            assertEquals("reader", reparsed.permission);
        } finally {
            parcel.recycle();
        }
    }

    private String labelFor(String kind) {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.kind = kind;
        return c.getKindLabel(context);
    }

    private WKDocumentShareContent contentWith(String kind, String title) {
        WKDocumentShareContent c = new WKDocumentShareContent();
        c.kind = kind;
        c.title = title;
        return c;
    }
}
