package com.chat.uikit.chat.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ViewPager2 的 Adapter：持有两个 RecyclerView 页面（群聊 / 私聊）。
 * 每个页面独立管理滚动位置，ViewPager2 框架级处理水平/垂直手势冲突。
 */
public class ConversationPagerAdapter extends RecyclerView.Adapter<ConversationPagerAdapter.PageViewHolder> {

    private static final int PAGE_COUNT = 2;

    private final RecyclerView[] pages = new RecyclerView[PAGE_COUNT];

    public RecyclerView getPageRecyclerView(int position) {
        return pages[position];
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView recyclerView = new RecyclerView(parent.getContext());
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(
                parent.getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(15);
        recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_ALWAYS);
        return new PageViewHolder(recyclerView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        pages[position] = holder.recyclerView;
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;

        PageViewHolder(@NonNull RecyclerView itemView) {
            super(itemView);
            this.recyclerView = itemView;
        }
    }
}
