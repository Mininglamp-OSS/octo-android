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
import com.chat.uikit.message.MsgModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        int widthPx = (int) (320 * context.getResources().getDisplayMetrics().density);
        popupWindow = new PopupWindow(contentView,
                widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(8f);

        recyclerView = contentView.findViewById(R.id.spaceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new SpaceListAdapter();
        adapter.setCurrentSpaceId(MsgModel.getInstance().getCurrentSpaceId());
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
            dialog.setOnSpaceCreatedListener(space -> {
                // 创建成功后自动切换到新 Space
                if (onSpaceSelectedListener != null) {
                    onSpaceSelectedListener.onSpaceSelected(space);
                }
            });
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
        popupWindow.showAsDropDown(anchorView, 0, 8);
    }

    private RecyclerView recyclerView;

    private void loadSpaces() {
        SpaceModel.getInstance().getMySpaces(new SpaceModel.ISpaceListListener() {
            @Override
            public void onResult(List<SpaceEntity> list) {
                adapter.setList(list);
                constrainRecyclerViewHeight(list == null ? 0 : list.size());
            }

            @Override
            public void onError(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    /**
     * Space 列表超过 MAX_VISIBLE_ITEMS 条时限制高度并启用滚动，
     * 保证底部「创建 / 加入」按钮始终可见。
     */
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final int ITEM_HEIGHT_DP = 48;

    private void constrainRecyclerViewHeight(int itemCount) {
        if (recyclerView == null) return;
        ViewGroup.LayoutParams lp = recyclerView.getLayoutParams();
        if (itemCount > MAX_VISIBLE_ITEMS) {
            float density = context.getResources().getDisplayMetrics().density;
            lp.height = (int) (MAX_VISIBLE_ITEMS * ITEM_HEIGHT_DP * density);
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        recyclerView.setLayoutParams(lp);
    }

    private void showJoinDialog() {
        // 记录加入前已有的 Space ID，用于识别新加入的 Space
        Set<String> existingSpaceIds = new HashSet<>();
        if (adapter != null && adapter.getData() != null) {
            for (SpaceEntity s : adapter.getData()) {
                existingSpaceIds.add(s.space_id);
            }
        }

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
                            // 加入成功后获取最新列表，找到新 Space 并自动切换
                            SpaceModel.getInstance().getMySpaces(new SpaceModel.ISpaceListListener() {
                                @Override
                                public void onResult(List<SpaceEntity> list) {
                                    if (onSpaceSelectedListener != null && list != null) {
                                        for (SpaceEntity space : list) {
                                            if (!existingSpaceIds.contains(space.space_id)) {
                                                onSpaceSelectedListener.onSpaceSelected(space);
                                                return;
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void onError(int code, String errorMsg) {
                                    // 获取列表失败时静默处理，用户可手动切换
                                }
                            });
                        } else {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
                });
    }
}
