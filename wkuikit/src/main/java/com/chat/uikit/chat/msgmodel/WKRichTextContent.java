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

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 图文混排消息（RichText，ContentType=14，Phase 1 仅接收渲染）。
 *
 * <p>线上 payload 形态对齐 octo-lib/common/richtext.go 与 octo-matter
 * richtext.go 锁定的契约：{@code content} 是 block 数组，{@code plain} 是 server
 * 权威生成的纯文本镜像。每个 block 至少含 {@code type} 字段：
 * <pre>
 * {
 *   "content": [
 *     {"type":"text","text":"上线方案"},
 *     {"type":"image","url":"https://x/y.png","width":10,"height":10}
 *   ],
 *   "plain": "上线方案[图片]"
 * }
 * </pre>
 *
 * <p>本类只做解析 + 展示文本归一化：
 * <ul>
 *   <li>{@link #blocks} 保留有序 block 列表供 provider 按序穿插渲染；</li>
 *   <li>{@link #getDisplayContent()} / {@link #getSearchableWord()} 返回顶层
 *       plain（会话列表预览、复制、引用预览、搜索都取它，勿丢字）。</li>
 * </ul>
 *
 * <p>前向兼容：未知 block.type 若带 text 仍取其 text 拼进 plain，二期扩展新
 * block 类型时老端不至于丢字。image block 在 plain 中以 {@link #IMAGE_PLACEHOLDER}
 * 占位（与 octo-lib RichTextImagePlaceholder 同语义）。
 */
public class WKRichTextContent extends WKMessageContent {

    /** image block 在纯文本中的占位符，对齐 octo-lib RichTextImagePlaceholder。 */
    public static final String IMAGE_PLACEHOLDER = "[图片]";

    public static final String BLOCK_TYPE_TEXT = "text";
    public static final String BLOCK_TYPE_IMAGE = "image";

    /** 有序 block 列表（text / image 穿插）。 */
    public List<RichTextBlock> blocks = new ArrayList<>();

    /** 顶层 plain：server 权威纯文本，会话列表/复制/搜索/引用统一取它。 */
    public String plain = "";

    public WKRichTextContent() {
        type = WKContentType.richText;
    }

    /**
     * 序列化回 RichText payload（content block 数组 + 顶层 plain），与 decodeMsg
     * 对称。Phase 1 不主动发送 RichText，但 SDK 在转存 / 引用 / 合并转发等路径会
     * 调用 encodeMsg，缺省基类实现返回空 {@code {}} 会丢字段，故补齐 round-trip。
     */
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONArray contentArr = new JSONArray();
            if (blocks != null) {
                for (RichTextBlock block : blocks) {
                    if (block == null) {
                        continue;
                    }
                    JSONObject blockJson = new JSONObject();
                    blockJson.put("type", block.type);
                    if (BLOCK_TYPE_IMAGE.equals(block.type)) {
                        blockJson.put("url", block.url);
                        blockJson.put("width", block.width);
                        blockJson.put("height", block.height);
                    } else {
                        blockJson.put("text", block.text);
                    }
                    contentArr.put(blockJson);
                }
            }
            jsonObject.put("content", contentArr);
            if (!TextUtils.isEmpty(plain)) {
                jsonObject.put("plain", plain);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject == null) {
            return this;
        }
        blocks = new ArrayList<>();
        JSONArray contentArr = jsonObject.optJSONArray("content");
        if (contentArr != null) {
            for (int i = 0, size = contentArr.length(); i < size; i++) {
                JSONObject blockJson = contentArr.optJSONObject(i);
                if (blockJson == null) {
                    continue;
                }
                RichTextBlock block = new RichTextBlock();
                block.type = blockJson.optString("type");
                block.text = blockJson.optString("text");
                block.url = blockJson.optString("url");
                block.width = blockJson.optInt("width");
                block.height = blockJson.optInt("height");
                blocks.add(block);
            }
        }
        // 优先顶层 plain（server 权威）；缺失时按 blocks 现场拼接，避免会话列表空白。
        if (jsonObject.has("plain") && !jsonObject.isNull("plain")) {
            plain = jsonObject.optString("plain");
        }
        if (TextUtils.isEmpty(plain)) {
            plain = buildPlainFromBlocks(blocks);
        }
        return this;
    }

    /**
     * 遍历 blocks 生成纯文本，对齐 octo-lib BuildRichTextPlain：text 取 text；
     * image 注入占位符；未知 type 有 text 则写 text，否则跳过（前向兼容）。
     */
    public static String buildPlainFromBlocks(List<RichTextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RichTextBlock block : blocks) {
            if (block == null) {
                continue;
            }
            if (BLOCK_TYPE_IMAGE.equals(block.type)) {
                sb.append(IMAGE_PLACEHOLDER);
            } else if (BLOCK_TYPE_TEXT.equals(block.type)) {
                if (!TextUtils.isEmpty(block.text)) {
                    sb.append(block.text);
                }
            } else if (!TextUtils.isEmpty(block.text)) {
                sb.append(block.text);
            }
        }
        return sb.toString();
    }

    @Override
    public String getDisplayContent() {
        return plain;
    }

    @Override
    public String getSearchableWord() {
        return plain;
    }

    protected WKRichTextContent(Parcel in) {
        super(in);
        plain = in.readString();
        blocks = in.createTypedArrayList(RichTextBlock.CREATOR);
        if (blocks == null) {
            blocks = new ArrayList<>();
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(plain);
        dest.writeTypedList(blocks);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WKRichTextContent> CREATOR = new Creator<WKRichTextContent>() {
        @Override
        public WKRichTextContent createFromParcel(Parcel in) {
            return new WKRichTextContent(in);
        }

        @Override
        public WKRichTextContent[] newArray(int size) {
            return new WKRichTextContent[size];
        }
    };

    /** content 数组中的单个 block。text block 用 text；image block 用 url + 尺寸。 */
    public static class RichTextBlock implements android.os.Parcelable {
        public String type;
        public String text;
        public String url;
        public int width;
        public int height;

        public RichTextBlock() {
        }

        protected RichTextBlock(Parcel in) {
            type = in.readString();
            text = in.readString();
            url = in.readString();
            width = in.readInt();
            height = in.readInt();
        }

        public boolean isImage() {
            return BLOCK_TYPE_IMAGE.equals(type);
        }

        public boolean isText() {
            return BLOCK_TYPE_TEXT.equals(type);
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(type);
            dest.writeString(text);
            dest.writeString(url);
            dest.writeInt(width);
            dest.writeInt(height);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<RichTextBlock> CREATOR = new Creator<RichTextBlock>() {
            @Override
            public RichTextBlock createFromParcel(Parcel in) {
                return new RichTextBlock(in);
            }

            @Override
            public RichTextBlock[] newArray(int size) {
                return new RichTextBlock[size];
            }
        };
    }
}
