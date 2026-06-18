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

package com.chat.base.emoji;

import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chat.base.R;
import com.chat.base.base.WKBaseFragment;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.databinding.FragEmojiLayoutBinding;
import com.chat.base.foldable.PaneMetrics;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKReader;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-11-13 11:15
 * emoji小表情fragment
 */
public class EmojiFragment extends WKBaseFragment<FragEmojiLayoutBinding> {
    EmojiAdapter emojiAdapter;
    //  · twin of emojiAdapter: the "常用表情" header strip. Kept as a field
    // so onConfigurationChanged can push the new pane width into it as well —
    // same show-at-use one-shot-width bug as the main grid, same fix.
    private EmojiAdapter headerAdapter;
    /**
     * "常用表情" header view. Cached so we can rebuild headerAdapter data on every
     * click instead of re-inflating the header every time. iOS 1:1 对齐：iOS 把
     * recentEmotions 当作 collection view 的第 0 段，点击后会刷新——Android 这边过去
     * 只在 initData() 时构建一次，后续点击只写 prefs 不刷 UI，所以同一会话里看到的永远是
     * 进入聊天瞬间的快照。这里把构建逻辑抽出来，每次点击和 onResume 都重跑一次。
     */
    private View commonHeaderView;
    private IEmojiClick iEmojiClick;
    int width = 0;

    @Override
    protected FragEmojiLayoutBinding getViewBinding() {
        return FragEmojiLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        // : use pane width (Activity-visible bounds) so the emoji grid tracks
        // the current Embedding pane instead of the full device width.
        width = PaneMetrics.widthPx(requireContext()) - (AndroidUtilities.dp(30) * 8);
        Theme.setColorFilter(getContext(), wkVBinding.deleteIv, R.color.popupTextColor);
        List<EmojiEntry> emojiIndexs = new ArrayList<>();
        List<EmojiEntry> customList = EmojiManager.getInstance().getEmojiWithType("custom_");
        List<EmojiEntry> normalList = EmojiManager.getInstance().getEmojiWithType("0_");
        List<EmojiEntry> naturelList = EmojiManager.getInstance().getEmojiWithType("1_");
        List<EmojiEntry> symbolsList = EmojiManager.getInstance().getEmojiWithType("2_");
        emojiIndexs.addAll(customList);
        emojiIndexs.addAll(normalList);
        emojiIndexs.addAll(naturelList);
        emojiIndexs.addAll(symbolsList);
        emojiAdapter = new EmojiAdapter(new ArrayList<>(), width);
        emojiAdapter.setList(emojiIndexs);
        wkVBinding.recyclerView.setLayoutManager(new StaggeredGridLayoutManager(8, StaggeredGridLayoutManager.VERTICAL));
        wkVBinding.recyclerView.setAdapter(emojiAdapter);
        emojiAdapter.addFooterView(getFooterView());
    }

    @Override
    protected void initPresenter() {

    }

    @Override
    protected void initListener() {
        emojiAdapter.setOnItemClickListener((adapter, view, position) -> {
            // 注意：adapter 数据是 EmojiEntry（commit 816793e 加肤色功能时把
            // BaseQuickAdapter<String, …> 换成了 BaseQuickAdapter<EmojiEntry, …>），
            // 旧代码这里直接 (String) cast 导致 ClassCastException → 点击 emoji 永远不写 prefs，
            // "最近发送排序"才不生效。这里走 instanceof 后取 .getText() 取出真正的 emoji tag。
            Object item = adapter.getItem(position);
            if (item instanceof EmojiEntry) {
                emojiClick(((EmojiEntry) item).getText());
            }
        });
        wkVBinding.deleteLayout.setOnClickListener(v -> {
            if (iEmojiClick != null) {
                iEmojiClick.onEmojiClick("");
            }
        });
//        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
//            @Override
//            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
//                outRect.left = 20;
//                outRect.right = 0;
//                outRect.bottom = 20;
//                outRect.top = 10;
//            }
//        });
    }

    @Override
    protected void initData() {
        refreshRecentEmojiHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 进入前台时按 prefs 重排，覆盖"在别处发完表情切回来 header 没刷"的边角场景。
        refreshRecentEmojiHeader();
    }

    /**
     *  · 折叠屏 phone→unfold 右侧自适应修复：
     * EmojiFragment 在正显示时（panel 已打开）unfold 到展开态，原先 {@link #initView()}
     * 里的 {@code width = PaneMetrics.widthPx(ctx) - dp(30)*8} 是 show-at-use 一次性计算，
     * pane 宽度变化后不会重算，导致 emoji 间距与新 pane 不匹配。
     *
     * <p>FragmentActivity 会把 {@code onConfigurationChanged} 派发给所有添加的 Fragment
     * （host Activity 的 {@code configChanges} manifest 属性 + super.onConfigurationChanged
     * 由 PR#175 / PR#177 覆盖），这里重新从 PaneMetrics 读一次当前 pane 宽度，推到
     * {@link EmojiAdapter} 并触发重新绑定，margin 即跟随 pane 变化。
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (emojiAdapter == null || !isAdded() || getContext() == null) return;
        int newWidth =
                PaneMetrics.widthPx(requireContext()) - (AndroidUtilities.dp(30) * 8);
        if (newWidth == width) return;
        width = newWidth;
        emojiAdapter.setWidth(newWidth);
        emojiAdapter.notifyDataSetChanged();
        //  · twin path: the common-emoji header strip shares the same
        // one-shot width; refresh it alongside the main grid so margins match
        // the new pane after unfold/fold. null-guarded: header only exists
        // when the user has recently-used emoji.
        if (headerAdapter != null) {
            headerAdapter.setWidth(newWidth);
            headerAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void setTitle(TextView titleTv) {

    }

    public void setOnEmojiClick(IEmojiClick iEmojiClick) {
        this.iEmojiClick = iEmojiClick;
    }

    private void getCommonEmoji() {
        refreshRecentEmojiHeader();
    }

    /**
     * Rebuild the "常用表情" header strip from {@code common_used_emojis} prefs.
     * Idempotent — safe to call on init, after each click, on resume. Three branches:
     *  - prefs 空 / 全无效条目 → 移除 header（如果之前有）
     *  - 首次有最近表情 → inflate 一次 header view，attach 到主 adapter
     *  - 已有 header → 仅 setList + notify，避免每次点击都重建 view 树
     *
     * 同时把 prefs 规范化（去掉空串），避免 split(",") 残留 ""。
     */
    private void refreshRecentEmojiHeader() {
        if (emojiAdapter == null) return;
        String ids = WKSharedPreferencesUtil.getInstance().getSPWithUID("common_used_emojis");
        List<EmojiEntry> list = new ArrayList<>();
        StringBuilder canonicalIds = new StringBuilder();
        if (!TextUtils.isEmpty(ids)) {
            String[] tokens = ids.contains(",") ? ids.split(",") : new String[]{ids};
            for (String token : tokens) {
                if (TextUtils.isEmpty(token)) continue;
                if (list.size() >= 32) break;
                EmojiEntry entry = EmojiManager.getInstance().getEmojiEntry(token);
                if (entry != null) {
                    list.add(entry);
                }
                if (canonicalIds.length() == 0) {
                    canonicalIds.append(token);
                } else {
                    canonicalIds.append(',').append(token);
                }
            }
        }
        WKSharedPreferencesUtil.getInstance().putSPWithUID(
                "common_used_emojis", canonicalIds.toString());

        if (WKReader.isEmpty(list)) {
            if (commonHeaderView != null) {
                emojiAdapter.removeHeaderView(commonHeaderView);
                commonHeaderView = null;
                headerAdapter = null;
            }
            return;
        }

        if (commonHeaderView == null || headerAdapter == null) {
            emojiAdapter.removeAllHeaderView();
            commonHeaderView = LayoutInflater.from(getContext())
                    .inflate(R.layout.common_used_emoji_header_layout, null);
            RecyclerView recyclerView = commonHeaderView.findViewById(R.id.recyclerView);
            headerAdapter = new EmojiAdapter(new ArrayList<>(), width);
            headerAdapter.setList(list);
            recyclerView.setLayoutManager(
                    new StaggeredGridLayoutManager(8, StaggeredGridLayoutManager.VERTICAL));
            recyclerView.setAdapter(headerAdapter);
            emojiAdapter.addHeaderView(commonHeaderView);
            headerAdapter.setOnItemClickListener((adapter, view, position) -> {
                Object item = adapter.getItem(position);
                if (item instanceof EmojiEntry) {
                    emojiClick(((EmojiEntry) item).getText());
                }
            });
        } else {
            headerAdapter.setList(list);
        }
    }

    private void emojiClick(String name) {
        if (!TextUtils.isEmpty(name)) {
            if (iEmojiClick != null) {
                iEmojiClick.onEmojiClick(name);
            }
            String usedIndexs = WKSharedPreferencesUtil.getInstance().getSPWithUID("common_used_emojis");
            String tempIndexs = "";
            if (!TextUtils.isEmpty(usedIndexs)) {
                if (usedIndexs.contains(",")) {
                    String[] strings = usedIndexs.split(",");
                    for (String string : strings) {
                        if (!string.equals(name)) {
                            if (TextUtils.isEmpty(tempIndexs)) {
                                tempIndexs = string;
                            } else {
                                tempIndexs = tempIndexs + "," + string;
                            }
                        }
                    }
                }
            }
            tempIndexs = name + "," + tempIndexs;
            WKSharedPreferencesUtil.getInstance().putSPWithUID("common_used_emojis", tempIndexs);
            // 同步刷新 header strip：iOS 是即时排序，Android 之前漏了这一步导致同一会话内
            // 排序看不到变化。每次写完 prefs 就回补 UI。
            refreshRecentEmojiHeader();
        }
    }

    private View getFooterView() {
        return LayoutInflater.from(getContext()).inflate(R.layout.common_used_emoji_footer_layout, null);
    }

    public interface IEmojiClick {
        void onEmojiClick(String emojiName);
    }
}
