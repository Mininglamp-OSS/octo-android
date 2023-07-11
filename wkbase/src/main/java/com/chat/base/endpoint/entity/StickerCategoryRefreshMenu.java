package com.chat.base.endpoint.entity;

/**
 * 1/4/21 3:17 PM
 * 表情菜单分类刷新
 */
public class StickerCategoryRefreshMenu {
    public IRefreshCategory iRefreshCategory;

    public StickerCategoryRefreshMenu(IRefreshCategory iRefreshCategory) {
        this.iRefreshCategory = iRefreshCategory;
    }

    public interface IRefreshCategory {
        //刷新某项
        void onRefresh(String category, boolean isAdd);

        //重置数据
        void onReset();
    }
}
