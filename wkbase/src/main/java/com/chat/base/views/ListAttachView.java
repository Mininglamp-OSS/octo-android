package com.chat.base.views;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.R;
import com.chat.base.entity.PopupMenuItem;
import com.lxj.xpopup.core.AttachPopupView;

import java.util.List;

/**
 * 2019-11-15 14:35
 * 列表弹框
 */
public class ListAttachView extends AttachPopupView {
    RecyclerView recyclerView;
    List<PopupMenuItem> list;
    Context context;
    IListItemClick iListItemClick;

    public ListAttachView(@NonNull Context context, List<PopupMenuItem> _list, IListItemClick _IListItemClick) {
        super(context);
        this.context = context;
        this.iListItemClick = _IListItemClick;
        this.list = _list;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        recyclerView = findViewById(R.id.recyclerView);
        //这里是为了在聊天长按消息时兼容数量太多超出屏幕范围
//        if (list.size() > 5) {
//            recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
//        } else
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        ListAttachAdapter adapter = new ListAttachAdapter(list);
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener((adapter1, view, position) -> {
            PopupMenuItem item = adapter.getItem(position);
            if (item != null && iListItemClick != null) {
                iListItemClick.onClick(item);
            }
            dismiss();
        });

    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.list_attach_view_layout;
    }

    private static class ListAttachAdapter extends BaseQuickAdapter<PopupMenuItem, BaseViewHolder> {

        public ListAttachAdapter(@Nullable List<PopupMenuItem> data) {
            super(R.layout.item_attach_view, data);
        }

        @Override
        protected void convert(@NonNull BaseViewHolder helper, PopupMenuItem item) {
            helper.setText(R.id.contentTv, item.getText());
            TextView contentTv = helper.getView(R.id.contentTv);
//            if (list.size() < 6) {
//                contentTv.setWidth(WKWindowUtil.getInstance().dip2px(context, 120));
//            }
        }
    }

    public interface IListItemClick {
        void onClick(PopupMenuItem attachItemEntity);
    }

}
