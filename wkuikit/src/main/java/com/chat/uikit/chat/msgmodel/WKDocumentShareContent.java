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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.msgitem.WKContentType;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONObject;

/**
 * 共享文档卡片消息（type=18，对齐 octo-web docShareCard / DocumentShareCardContent）。
 * 文档、表格、画板、HTML 文档共用此协议。
 */
public class WKDocumentShareContent extends WKMessageContent {

    /**
     * 类别：doc=文档 / sheet=表格 / board=画板 / html=HTML 文档，其他值按文档处理。
     */
    @Nullable
    public String docId = "";
    @Nullable
    public String kind = "doc";
    @Nullable
    public String title = "";
    @Nullable
    public String ownerName = "";
    /** 预格式化的更新时间字符串（web 端下发即字符串，不是时间戳）。 */
    @Nullable
    public String updatedAt = "";
    @Nullable
    public String url = "";
    @Nullable
    public String permission = "reader";

    public WKDocumentShareContent() {
        type = WKContentType.docShareCard;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("doc_id", docId == null ? "" : docId);
            jsonObject.put("space_id", spaceId == null ? "" : spaceId);
            jsonObject.put("kind", kind == null ? "doc" : kind);
            jsonObject.put("title", title == null ? "" : title);
            jsonObject.put("owner_name", ownerName == null ? "" : ownerName);
            jsonObject.put("updated_at", updatedAt == null ? "" : updatedAt);
            jsonObject.put("url", url == null ? "" : url);
            jsonObject.put("permission", permission == null ? "reader" : permission);
        } catch (Exception ignore) {
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject != null) {
            docId = optStringOrEmpty(jsonObject, "doc_id");
            spaceId = optStringOrEmpty(jsonObject, "space_id");
            kind = enumString(jsonObject, "kind", "doc", "doc", "board", "sheet", "html");
            title = optStringOrEmpty(jsonObject, "title");
            ownerName = optStringOrEmpty(jsonObject, "owner_name");
            updatedAt = optStringOrEmpty(jsonObject, "updated_at");
            url = optStringOrEmpty(jsonObject, "url");
            permission = enumString(jsonObject, "permission", "reader", "reader", "commenter", "writer");
        }
        return this;
    }

    /**
     * 仅接受 JSON 字符串：缺失、null、数字、布尔、对象、数组一律返回空串，
     * 不做 toString() 强制转换（对齐 web 端字符串字段的类型契约）。
     */
    @NonNull
    private static String optStringOrEmpty(@NonNull JSONObject json, @NonNull String key) {
        Object value = json.opt(key);
        if (!(value instanceof String)) return "";
        return (String) value;
    }

    @NonNull
    private static String enumString(@NonNull JSONObject json, @NonNull String key, @NonNull String fallback, @NonNull String... allowed) {
        Object value = json.opt(key);
        if (!(value instanceof String)) return fallback;
        for (String candidate : allowed) if (candidate.equals(value)) return candidate;
        return fallback;
    }

    /**
     * 类别名称：未知、空、null kind 按文档处理。
     */
    @NonNull
    public String getKindLabel(@NonNull Context context) {
        if ("sheet".equals(kind)) {
            return context.getString(R.string.doc_share_kind_sheet);
        } else if ("board".equals(kind)) {
            return context.getString(R.string.doc_share_kind_board);
        } else if ("html".equals(kind)) {
            return context.getString(R.string.doc_share_kind_html);
        }
        return context.getString(R.string.doc_share_kind_doc);
    }

    /**
     * 摘要：所有类型统一使用 [文档] 标题；空标题显示 [文档]。
     */
    @NonNull
    private String summary(@NonNull Context context) {
        String value = title == null || title.isEmpty() ? "" : title;
        return value.isEmpty() ? context.getString(R.string.doc_share_summary_untitled) : context.getString(R.string.doc_share_summary_doc, value);
    }

    @NonNull
    public String getDisplayContent(@Nullable Context context) {
        if (context == null) return title == null ? "" : title;
        return summary(context);
    }

    @Nullable
    @Override
    public String getDisplayContent() {
        Context context = WKUIKitApplication.getInstance().getContext();
        return getDisplayContent(context);
    }

    @NonNull
    public String getSearchableWord(@Nullable Context context) {
        if (context == null) return title == null ? "" : title;
        return title == null || title.isEmpty() ? context.getString(R.string.doc_share_summary_untitled) : title;
    }

    @Nullable
    @Override
    public String getSearchableWord() {
        Context context = WKUIKitApplication.getInstance().getContext();
        return getSearchableWord(context);
    }

    protected WKDocumentShareContent(Parcel in) {
        super(in);
        docId = in.readString();
        kind = in.readString();
        title = in.readString();
        ownerName = in.readString();
        updatedAt = in.readString();
        url = in.readString();
        permission = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(docId);
        dest.writeString(kind);
        dest.writeString(title);
        dest.writeString(ownerName);
        dest.writeString(updatedAt);
        dest.writeString(url);
        dest.writeString(permission);
    }

    public static final Parcelable.Creator<WKDocumentShareContent> CREATOR = new Parcelable.Creator<WKDocumentShareContent>() {
        @Override
        public WKDocumentShareContent createFromParcel(Parcel in) {
            return new WKDocumentShareContent(in);
        }

        @Override
        public WKDocumentShareContent[] newArray(int size) {
            return new WKDocumentShareContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
