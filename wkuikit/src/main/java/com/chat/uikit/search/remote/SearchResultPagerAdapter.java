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

package com.chat.uikit.search.remote;

import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 搜索页 5 个分类 tab（全部/聊天记录/联系人/群组/文件）的 ViewPager2 裸壳页面提供者。
 *
 * 模式照搬 {@link com.chat.uikit.chat.adapter.ConversationPagerAdapter}：每页只是一个空的
 * FrameLayout 包一个空 RecyclerView，具体 adapter 由外部（{@link GlobalActivity}）在
 * {@code pager.post{}} 回调里延迟绑定，不用 FragmentStateAdapter。
 */
public class SearchResultPagerAdapter extends RecyclerView.Adapter<SearchResultPagerAdapter.PageViewHolder> {

    private static final int PAGE_COUNT = 5;

    private final RecyclerView[] pages = new RecyclerView[PAGE_COUNT];
    private final FrameLayout[] containers = new FrameLayout[PAGE_COUNT];

    public RecyclerView getPageRecyclerView(int position) {
        return pages[position];
    }

    public FrameLayout getPageContainer(int position) {
        return containers[position];
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        RecyclerView recyclerView = new RecyclerView(parent.getContext());
        recyclerView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(
                parent.getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(15);
        recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_ALWAYS);

        container.addView(recyclerView);
        return new PageViewHolder(container, recyclerView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        pages[position] = holder.recyclerView;
        containers[position] = holder.container;
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;
        final RecyclerView recyclerView;

        PageViewHolder(@NonNull FrameLayout container, @NonNull RecyclerView recyclerView) {
            super(container);
            this.container = container;
            this.recyclerView = recyclerView;
        }
    }
}
