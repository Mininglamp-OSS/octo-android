package com.chat.base.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class WKFragmentStateAdapter extends FragmentStateAdapter {
    List<Fragment> fragmentList;
    private final List<Long> fragmentIds = new ArrayList<>();
    private final HashSet<Long> createIds = new HashSet<>();

    public WKFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity, List<Fragment> fragmentList) {
        super(fragmentActivity);
        setFragmentList(fragmentList);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long ids = fragmentIds.get(position);
        createIds.add(ids);
        return fragmentList.get(position);
    }

    @Override
    public int getItemCount() {
        return fragmentList == null ? 0 : fragmentList.size();
    }

    public void setFragmentList(List<Fragment> fragmentList) {
        if (this.fragmentList != null) this.fragmentList.clear();
        this.fragmentList = fragmentList;

        fragmentIds.clear();
        for (int i = 0; i < fragmentList.size(); i++) {
            fragmentIds.add((long) fragmentList.get(i).hashCode());
        }

        notifyItemRangeChanged(0, fragmentList.size());
    }


    /**
     * 这两个方法必须重写，作为数据改变刷新检测的工具
     */
    @Override
    public long getItemId(int position) {
        return fragmentIds.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        return createIds.contains(itemId);
    }

}
