package com.chat.uikit.fragment;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.endpoint.entity.PersonalInfoMenu;
import com.chat.uikit.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 2020-08-12 14:40
 * 个人中心
 */
public class PersonalItemAdapter extends BaseQuickAdapter<PersonalInfoMenu, BaseViewHolder> {
    PersonalItemAdapter(List<PersonalInfoMenu> list) {
        super(R.layout.item_frag_me_layout, list);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, PersonalInfoMenu menu) {
        baseViewHolder.setText(R.id.nameTv, menu.text);
        baseViewHolder.setImageResource(R.id.imageView, menu.imgResourceID);
        baseViewHolder.setGone(R.id.bottomView,!menu.text.equals(getContext().getString(R.string.web_login)));
        if (menu.isNewVersionIv) {
            baseViewHolder.setVisible(R.id.newVersionIv, true);
        } else {
            baseViewHolder.setVisible(R.id.newVersionIv, false);
        }
    }
}
