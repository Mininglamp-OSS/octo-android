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

package com.chat.uikit.chat.adapter;

import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConversationPagerAdapter extends RecyclerView.Adapter<ConversationPagerAdapter.PageViewHolder> {

    private static final int PAGE_COUNT = 2;

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
