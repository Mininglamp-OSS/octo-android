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

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.CheckBox;
import com.chat.base.utils.AndroidUtilities;
import com.chat.uikit.R;
import com.chat.uikit.chat.ChooseChatActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 选择会话适配器 — 支持 section header + 群聊紧凑布局 + 子区 + 子区折叠
 */
public class ChooseChatAdapter extends BaseQuickAdapter<ChooseChatActivity.ChooseChatEntity, BaseViewHolder> {

    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_COMPACT = 1;
    private static final int TYPE_SECTION_HEADER = 2;
    private static final int TYPE_THREAD = 3;
    private static final int TYPE_THREAD_TOGGLE = 4;

    private final Set<String> collapsedSections = new HashSet<>();
    private ISectionToggleListener sectionToggleListener;

    public ChooseChatAdapter(@Nullable List<ChooseChatActivity.ChooseChatEntity> data) {
        super(R.layout.item_choose_chat_layout, data);
    }

    @Override
    protected int getDefItemViewType(int position) {
        ChooseChatActivity.ChooseChatEntity item = getItem(position);
        if (item != null && item.isSectionHeader) return TYPE_SECTION_HEADER;
        if (item != null && item.isThreadToggle) return TYPE_THREAD_TOGGLE;
        if (item != null && item.isThread) return TYPE_THREAD;
        // 来自 conversationMsg 的子区(最近 tab 直接展示) 也走 thread 紧凑布局, 对齐 iOS
        if (item != null && item.uiConveursationMsg != null
                && item.uiConveursationMsg.channelType == WKChannelType.COMMUNITY_TOPIC) {
            return TYPE_THREAD;
        }
        if (item != null && item.uiConveursationMsg != null
                && item.uiConveursationMsg.channelType == WKChannelType.GROUP) {
            return TYPE_COMPACT;
        }
        return TYPE_NORMAL;
    }

    @NonNull
    @Override
    protected BaseViewHolder onCreateDefViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes;
        switch (viewType) {
            case TYPE_SECTION_HEADER:
                layoutRes = R.layout.item_chat_section_header;
                break;
            case TYPE_COMPACT:
                layoutRes = R.layout.item_choose_chat_compact_layout;
                break;
            case TYPE_THREAD:
                layoutRes = R.layout.item_choose_chat_thread_layout;
                break;
            case TYPE_THREAD_TOGGLE:
                layoutRes = R.layout.item_choose_chat_thread_toggle;
                break;
            default:
                layoutRes = R.layout.item_choose_chat_layout;
                break;
        }
        return createBaseViewHolder(parent, layoutRes);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, ChooseChatActivity.ChooseChatEntity item, @NonNull List<?> payloads) {
        super.convert(holder, item, payloads);
        int viewType = holder.getItemViewType();
        if (viewType == TYPE_SECTION_HEADER || viewType == TYPE_THREAD_TOGGLE) return;
        ChooseChatActivity.ChooseChatEntity entity = (ChooseChatActivity.ChooseChatEntity) payloads.get(0);
        if (entity != null) {
            CheckBox checkBox = holder.getView(R.id.checkbox);
            // 始终保持 drawBackground=true 让 border 一直可见, 见 setupCheckBox 注释
            checkBox.setDrawBackground(true);
            checkBox.setChecked(item.isCheck, true);
        }
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, ChooseChatActivity.ChooseChatEntity item) {
        switch (helper.getItemViewType()) {
            case TYPE_SECTION_HEADER:
                convertSectionHeader(helper, item);
                return;
            case TYPE_THREAD_TOGGLE:
                convertThreadToggle(helper, item);
                return;
            case TYPE_THREAD:
                convertThread(helper, item);
                return;
            case TYPE_COMPACT:
                convertCompact(helper, item);
                return;
            default:
                convertNormal(helper, item);
        }
    }

    // ── 私聊：带头像 ────────────────────────────────────────────

    private void convertNormal(@NonNull BaseViewHolder helper, ChooseChatActivity.ChooseChatEntity item) {
        CheckBox checkBox = helper.getView(R.id.checkbox);
        setupCheckBox(checkBox, item);

        AvatarView avatarView = helper.getView(R.id.avatarView);
        if (item.uiConveursationMsg.getWkChannel() != null) {
            String showName = item.uiConveursationMsg.getWkChannel().channelRemark;
            if (TextUtils.isEmpty(showName))
                showName = item.uiConveursationMsg.getWkChannel().channelName;
            helper.setText(R.id.nameTv, showName);
            avatarView.showAvatar(item.uiConveursationMsg.getWkChannel());
            helper.setGone(R.id.banTv, !item.isBan);
            if (item.isForbidden || item.isBan) {
                helper.setGone(R.id.checkbox, true);
            } else {
                helper.setGone(R.id.checkbox, false);
            }
            helper.setGone(R.id.fullStaffingTv, !item.isForbidden);
        } else {
            avatarView.showAvatar(item.uiConveursationMsg.channelID, item.uiConveursationMsg.channelType);
            WKIM.getInstance().getChannelManager().fetchChannelInfo(item.uiConveursationMsg.channelID, item.uiConveursationMsg.channelType);
            helper.setGone(R.id.fullStaffingTv, true);
            helper.setGone(R.id.checkbox, false);
        }
    }

    // ── 群聊：紧凑风格 ──────────────────────────────────────────

    private void convertCompact(@NonNull BaseViewHolder helper, ChooseChatActivity.ChooseChatEntity item) {
        CheckBox checkBox = helper.getView(R.id.checkbox);
        setupCheckBox(checkBox, item);

        if (item.uiConveursationMsg.getWkChannel() != null) {
            String showName = item.uiConveursationMsg.getWkChannel().channelRemark;
            if (TextUtils.isEmpty(showName))
                showName = item.uiConveursationMsg.getWkChannel().channelName;
            helper.setText(R.id.nameTv, showName);
            helper.setGone(R.id.banTv, !item.isBan);
            if (item.isForbidden || item.isBan) {
                helper.setGone(R.id.checkbox, true);
            } else {
                helper.setGone(R.id.checkbox, false);
            }
            helper.setGone(R.id.fullStaffingTv, !item.isForbidden);
        } else {
            WKIM.getInstance().getChannelManager().fetchChannelInfo(item.uiConveursationMsg.channelID, item.uiConveursationMsg.channelType);
            helper.setGone(R.id.fullStaffingTv, true);
            helper.setGone(R.id.checkbox, false);
        }
    }

    // ── 子区 ────────────────────────────────────────────────────

    private void convertThread(@NonNull BaseViewHolder helper, ChooseChatActivity.ChooseChatEntity item) {
        CheckBox checkBox = helper.getView(R.id.checkbox);
        setupCheckBox(checkBox, item);
        // 兼容两种数据源: (a) threadEntityCache 里的 isThread 条目用 threadName;
        // (b) 最近 tab 里 conversationMsg 子区用 channelName.
        String name;
        if (item.isThread) {
            name = item.threadName;
        } else if (item.uiConveursationMsg != null && item.uiConveursationMsg.getWkChannel() != null) {
            String n = item.uiConveursationMsg.getWkChannel().channelName;
            name = TextUtils.isEmpty(n) ? "" : n;
        } else {
            name = "";
        }
        helper.setText(R.id.nameTv, name);
    }

    // ── 子区折叠/展开 toggle ────────────────────────────────────

    private void convertThreadToggle(@NonNull BaseViewHolder helper, ChooseChatActivity.ChooseChatEntity item) {
        TextView toggleText = helper.getView(R.id.toggleText);
        ImageView toggleArrow = helper.getView(R.id.toggleArrow);

        if (item.threadExpanded) {
            toggleText.setText(String.format(getContext().getString(R.string.str_collapse_threads), item.threadCount));
            toggleArrow.setRotation(0f);
        } else {
            toggleText.setText(String.format(getContext().getString(R.string.str_expand_threads), item.threadCount));
            toggleArrow.setRotation(-90f);
        }
    }

    private void setupCheckBox(CheckBox checkBox, ChooseChatActivity.ChooseChatEntity item) {
        checkBox.setResId(getContext(), R.mipmap.round_check2);
        // CheckBox.onDraw 在 !drawBackground && progress==0 时整个 return,
        // 该组件原本设计是叠在头像上,未选中态故意空白让 avatar 当背景。
        // 我们把 checkbox 挪到最左 (无 avatar 当背景), 所以未选中态也要画 border ring,
        // 始终 setDrawBackground(true) 让 border 一直可见, 选中态 progress 把填充圆动起来。
        checkBox.setDrawBackground(true);
        checkBox.setHasBorder(true);
        checkBox.setStrokeWidth(AndroidUtilities.dp(1.5f));
        checkBox.setBorderColor(ContextCompat.getColor(getContext(), R.color.color999));
        checkBox.setSize(22);
        checkBox.setColor(Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.white));
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setChecked(item.isCheck, true);
    }

    // ── Section header ────────────────────────────────────────────

    private void convertSectionHeader(@NonNull BaseViewHolder helper, ChooseChatActivity.ChooseChatEntity item) {
        TextView titleTv = helper.getView(R.id.sectionTitle);
        ImageView arrowIv = helper.getView(R.id.sectionArrow);
        View divider = helper.getView(R.id.sectionDivider);

        int position = helper.getAdapterPosition() - getHeaderLayoutCount();
        divider.setVisibility(position > 0 ? View.VISIBLE : View.GONE);

        titleTv.setText(item.sectionTitle);

        boolean collapsed = collapsedSections.contains(item.sectionId);
        arrowIv.setRotation(collapsed ? -90f : 0f);

        helper.itemView.setOnClickListener(v -> {
            boolean nowCollapsed = collapsedSections.contains(item.sectionId);
            if (nowCollapsed) {
                collapsedSections.remove(item.sectionId);
            } else {
                collapsedSections.add(item.sectionId);
            }
            float to = nowCollapsed ? 0f : -90f;
            arrowIv.animate().rotation(to).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();

            if (sectionToggleListener != null) {
                sectionToggleListener.onSectionToggled(item.sectionId, !nowCollapsed);
            }
        });
    }

    public boolean isSectionCollapsed(String sectionId) {
        return collapsedSections.contains(sectionId);
    }

    public void setSectionToggleListener(ISectionToggleListener listener) {
        this.sectionToggleListener = listener;
    }

    public interface ISectionToggleListener {
        void onSectionToggled(String sectionId, boolean collapsed);
    }
}
