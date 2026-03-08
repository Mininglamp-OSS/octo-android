package com.chat.uikit.space;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;

import java.util.List;

public class SpacePopupWindow {

    public interface OnSpaceSelectedListener {
        void onSpaceSelected(SpaceEntity space);
    }

    private PopupWindow popupWindow;
    private final Context context;
    private SpaceListAdapter adapter;
    private OnSpaceSelectedListener onSpaceSelectedListener;

    public SpacePopupWindow(Context context) {
        this.context = context;
    }

    public void setOnSpaceSelectedListener(OnSpaceSelectedListener listener) {
        this.onSpaceSelectedListener = listener;
    }

    public void show(View anchorView) {
        View contentView = LayoutInflater.from(context).inflate(R.layout.popup_space_list, null);

        popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(8f);

        RecyclerView recyclerView = contentView.findViewById(R.id.spaceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new SpaceListAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener((a, view, position) -> {
            SpaceEntity space = adapter.getItem(position);
            if (space == null) return;
            popupWindow.dismiss();
            if (onSpaceSelectedListener != null) {
                onSpaceSelectedListener.onSpaceSelected(space);
            } else {
                Intent intent = new Intent(context, SpaceSettingsActivity.class);
                intent.putExtra("space_id", space.space_id);
                context.startActivity(intent);
            }
        });

        LinearLayout createLayout = contentView.findViewById(R.id.createSpaceLayout);
        createLayout.setOnClickListener(v -> {
            popupWindow.dismiss();
            SpaceCreateDialog dialog = new SpaceCreateDialog(context);
            dialog.setOnSpaceCreatedListener(space -> loadSpaces());
            dialog.show();
        });

        LinearLayout joinLayout = contentView.findViewById(R.id.joinSpaceLayout);
        joinLayout.setOnClickListener(v -> {
            popupWindow.dismiss();
            showJoinDialog();
        });

        LinearLayout showAllLayout = contentView.findViewById(R.id.showAllLayout);
        showAllLayout.setOnClickListener(v -> {
            popupWindow.dismiss();
            // Reload to show all spaces
            loadSpaces();
        });

        loadSpaces();
        popupWindow.showAtLocation(anchorView, Gravity.TOP | Gravity.START,
                anchorView.getLeft() + 15, anchorView.getBottom() + 20);
    }

    private void loadSpaces() {
        SpaceModel.getInstance().getMySpaces(new SpaceModel.ISpaceListListener() {
            @Override
            public void onResult(List<SpaceEntity> list) {
                adapter.setList(list);
            }

            @Override
            public void onError(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    private void showJoinDialog() {
        WKDialogUtils.getInstance().showInputDialog(
                context,
                context.getString(R.string.space_join_title),
                "",
                "",
                context.getString(R.string.space_join_hint),
                20,
                text -> {
                    if (TextUtils.isEmpty(text)) {
                        WKToastUtils.getInstance().showToastNormal(
                                context.getString(R.string.space_invite_code_empty));
                        return;
                    }
                    SpaceModel.getInstance().joinSpace(text, (status, msg) -> {
                        if (status == 200) {
                            loadSpaces();
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                });
    }
}
