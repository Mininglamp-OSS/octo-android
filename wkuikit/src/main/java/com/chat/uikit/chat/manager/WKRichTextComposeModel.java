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

package com.chat.uikit.chat.manager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 图文混排（RichText=14）<em>发送侧输入框附件托盘</em>的有序暂存模型（Phase 2，对齐 web#237）。
 *
 * <p>Phase 1（#31）的混排入口是「聊天工具栏相册选图 + 输入框已有文本 → 聚合成单条 type=14」，
 * 但<strong>非穿插、顺序固定</strong>（文本块固定在前、图片块按选取顺序在后），用户无法调序、
 * 也无法分多批选图。Phase 2 升级为<strong>主聊天输入框附件托盘</strong>：选中的图片以缩略图挂在
 * 输入框上方，用户可继续打字、可多批追加、可拖拽调序、可单张移除；点发送时按托盘的<em>真实
 * 当前顺序</em>整体打成单条 type=14（文本块在前 + 图片按托盘顺序；真·块级文/图交错留 Phase 3）。
 *
 * <p>本类是该托盘的<strong>纯数据模型</strong>（无任何 Android UI 依赖），把「有序增删调序」这条
 * 核心逻辑从 View 层剥离出来，可在纯 JVM 单测下被断言（对齐 Phase 1 把 WKRichTextSender 的原子性
 * 逻辑抽成可注入 seam 的做法）。UI 层（ChatPanelManager 的缩略图条）只是这个模型的渲染镜像。
 *
 * <p>Android 与 web 的架构分叉（承接 YUJ-2811 教训）：web 有 Tiptap 内联编辑器，文本与图片块
 * 在<em>同一编辑器内</em>逐字符交错暂存，故能做到文本段与图片段任意穿插；Android 的 ContactEditText
 * 是单一纯文本框，无法把图片嵌进文本流中间。因此 Android 的「真实顺序」语义 = <strong>整段文本 +
 * 有序图片序列</strong>（文本作为单个 text block，图片按托盘顺序排在其后）。这与 octo-web#237 对
 * Android 的明确定义一致（"图选完挂缩略图区、可继续打字调序，发送按真实顺序打包"）——托盘可调的是
 * 图片顺序，wire 仍是字节对齐的 type=14 block 数组，不碰 schema（硬约束 #3）。
 *
 * <p>线程约束：仅主线程读写（选图回调 / 拖拽 / 移除 / 发送都在主线程），故无需同步。
 */
public final class WKRichTextComposeModel {

    public static final int MAX_IMAGES = 9;

    /** 托盘中的单个附件项（当前仅静态图片；id 用于 UI diff / 拖拽稳定标识）。 */
    public static final class TrayItem {
        public final long id;
        public final String localPath;

        public TrayItem(long id, String localPath) {
            this.id = id;
            this.localPath = localPath;
        }
    }

    /** 单调递增 id 源：拖拽 / 移除时给每个缩略图一个稳定身份，避免按下标错位。 */
    private long nextId = 1;

    private final List<TrayItem> items = new ArrayList<>();

    /** 当前托盘附件数。 */
    public int size() {
        return items.size();
    }

    /** 托盘是否为空（无任何待发图片）。 */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** 只读快照（按当前顺序）。返回副本，调用方改动不影响内部状态。 */
    @NonNull
    public List<TrayItem> items() {
        return new ArrayList<>(items);
    }

    /**
     * 按当前顺序取出所有图片本地路径（供 {@link WKRichTextSender#send} 串行上传）。
     */
    @NonNull
    public List<String> orderedPaths() {
        List<String> paths = new ArrayList<>(items.size());
        for (TrayItem item : items) {
            paths.add(item.localPath);
        }
        return paths;
    }

    /**
     * 追加一批图片到托盘末尾（按传入顺序）。空 / null 路径被跳过（不占位）。
     *
     * @return 实际新增的条数
     */
    public int addAll(List<String> localPaths) {
        if (localPaths == null) {
            return 0;
        }
        int added = 0;
        for (String path : localPaths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            if (items.size() >= MAX_IMAGES) {
                break;
            }
            items.add(new TrayItem(nextId++, path));
            added++;
        }
        return added;
    }

    /**
     * 按 id 移除一个附件（用户点缩略图上的 ✕）。
     *
     * @return true 表示命中并移除
     */
    public boolean removeById(long id) {
        for (int i = 0, n = items.size(); i < n; i++) {
            if (items.get(i).id == id) {
                items.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 把 {@code fromIndex} 处的附件移动到 {@code toIndex}（拖拽调序）。越界 / 相等为 no-op，
     * 返回 false。用 remove+insert 实现「真移动」语义——而非两端 swap——保证即使 ItemTouchHelper
     * 一次 onMove 跨越多格（from 与 to 不相邻）时，模型顺序也与 RecyclerView 的 notifyItemMoved
     * 展示顺序<strong>逐项一致</strong>（swap 在跨格时会让中间项错位，发送顺序与用户所见分叉）。
     *
     * @return true 表示发生了顺序变化
     */
    public boolean move(int fromIndex, int toIndex) {
        int n = items.size();
        if (fromIndex < 0 || fromIndex >= n || toIndex < 0 || toIndex >= n
                || fromIndex == toIndex) {
            return false;
        }
        TrayItem moving = items.remove(fromIndex);
        items.add(toIndex, moving);
        return true;
    }

    /** 清空托盘（发送成功后、或切换频道时调用）。 */
    public void clear() {
        items.clear();
    }

    /**
     * 构造发送用的有序 block 列表：text 块（若 {@code text} 非空白）在前，图片块按托盘
     * 当前顺序在后。<strong>不上传</strong>——仅做顺序装配，图片块此处用本地路径占位 url、
     * 尺寸为 0（"未知"哨兵），真正的 downloadUrl 与本地尺寸由 {@link WKRichTextSender#send}
     * 在串行上传后回填。本方法的价值是把「文本 + 托盘有序图片 → block 数组」这条穿插顺序
     * 逻辑做成可在纯 JVM 单测下断言的纯函数（与实际网络上传解耦）。
     *
     * <p>注意：这里产出的图片块带的是<em>本地路径</em>而非 downloadUrl，故<strong>不可</strong>
     * 直接发送；它只用于「顺序 / 结构」断言。生产发送路径走 {@link WKRichTextSender#send}
     * （text + orderedPaths()）。
     */
    @NonNull
    public List<WKRichTextContentBlocks> previewBlocks(String text) {
        List<WKRichTextContentBlocks> blocks = new ArrayList<>();
        for (TrayItem item : items) {
            blocks.add(WKRichTextContentBlocks.image(item.localPath));
        }
        if (text != null && !text.trim().isEmpty()) {
            blocks.add(WKRichTextContentBlocks.text(text));
        }
        return blocks;
    }

    /**
     * 轻量 block 投影（仅供 {@link #previewBlocks} 做顺序 / 类型断言用，不参与 wire 序列化）。
     * 刻意不直接复用 {@link com.chat.uikit.chat.msgmodel.WKRichTextContent.RichTextBlock}
     * （Parcelable，构造需 Android framework）以保证本类在纯 JVM 下零 Android 依赖。
     */
    public static final class WKRichTextContentBlocks {
        public static final String TYPE_TEXT = "text";
        public static final String TYPE_IMAGE = "image";

        public final String type;
        public final String text;
        public final String localPath;

        private WKRichTextContentBlocks(String type, String text, String localPath) {
            this.type = type;
            this.text = text;
            this.localPath = localPath;
        }

        static WKRichTextContentBlocks text(String text) {
            return new WKRichTextContentBlocks(TYPE_TEXT, text, null);
        }

        static WKRichTextContentBlocks image(String localPath) {
            return new WKRichTextContentBlocks(TYPE_IMAGE, null, localPath);
        }

        public boolean isText() {
            return TYPE_TEXT.equals(type);
        }

        public boolean isImage() {
            return TYPE_IMAGE.equals(type);
        }
    }
}
