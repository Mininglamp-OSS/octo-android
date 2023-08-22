package com.chat.uikit.chat.face;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chat.base.endpoint.entity.ChatFunctionMenu;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.FaceManger;

import java.util.List;


/**
 * 2020-10-19 13:03
 * 功能面板
 */
public class FunctionViewPageAdapter extends RecyclerView.Adapter<FunctionViewPageAdapter.ViewHolder> {
    private final List<List<ChatFunctionMenu>> mData;
    private final LayoutInflater mInflater;
    private final int h;
    private final FaceManger.IFuncListener iFuncListener;

    public FunctionViewPageAdapter(Context context, List<List<ChatFunctionMenu>> data, int h, FaceManger.IFuncListener iFuncListener) {
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
        this.iFuncListener = iFuncListener;
        this.h = h - AndroidUtilities.dp(40);
    }

    @NonNull
    @Override
    public FunctionViewPageAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_function_viewpage_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FunctionViewPageAdapter.ViewHolder holder, int position) {
        List<ChatFunctionMenu> list = mData.get(position);
        FunctionAdapter adapter = new FunctionAdapter(list, h);
        holder.recyclerView.setLayoutManager(new StaggeredGridLayoutManager(4, StaggeredGridLayoutManager.VERTICAL));
        holder.recyclerView.setAdapter(adapter);
        holder.recyclerView.setNestedScrollingEnabled(false);


        adapter.setOnItemClickListener((adapter1, view1, position1) -> {
            SingleClickUtil.determineTriggerSingleClick(view1, view -> {
                ChatFunctionMenu menu = adapter.getItem(position1);
                if (menu != null) {
                    if (iFuncListener != null) {
                        iFuncListener.onFuncLick(menu);
                    }
                }
            });

        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        RecyclerView recyclerView;

        ViewHolder(View itemView) {
            super(itemView);
            recyclerView = itemView.findViewById(R.id.recyclerView);
        }
    }
}
