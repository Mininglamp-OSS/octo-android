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

package com.chat.uikit.group.webhook.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKApiConfig;
import com.chat.base.ui.components.SwitchView;
import com.chat.uikit.R;
import com.chat.uikit.group.webhook.service.IncomingWebhook;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Webhook 列表适配器，1:1 对齐 iOS WKChannelWebhookCell 渲染：
 * 头像 + 名称 + 「已禁用」chip + 启停 Switch + meta 信息行。
 */
public class WebhookListAdapter extends BaseQuickAdapter<IncomingWebhook, BaseViewHolder> {

    public interface IClickListener {
        void onSwitchToggled(IncomingWebhook webhook, boolean nextOn);
    }

    private boolean isManagerOrCreator;
    private String currentUid;
    private IClickListener clickListener;
    /** webhookId -> displayName，VC 解析（订阅缓存 / 自己 / 兜底空串）。 */
    private final java.util.HashMap<String, String> creatorNameMap = new java.util.HashMap<>();
    /** 单条切换启停 in-flight 的 webhookId 集合 —— UI 上 Switch 转 loading 态。 */
    private final Set<String> togglingIds = new HashSet<>();

    private final SimpleDateFormat dateOnlyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat dateTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public WebhookListAdapter(@Nullable List<IncomingWebhook> data) {
        super(R.layout.item_channel_webhook, data);
    }

    public void setClickListener(IClickListener l) { this.clickListener = l; }

    public void setIsManagerOrCreator(boolean v) { this.isManagerOrCreator = v; }

    public void setCurrentUid(String uid) { this.currentUid = uid; }

    public void putCreatorName(String uid, String name) {
        if (TextUtils.isEmpty(uid)) return;
        creatorNameMap.put(uid, name == null ? "" : name);
    }

    public void setToggling(String webhookId, boolean toggling) {
        if (TextUtils.isEmpty(webhookId)) return;
        if (toggling) togglingIds.add(webhookId);
        else togglingIds.remove(webhookId);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, IncomingWebhook item) {
        if (item == null) return;
        AppCompatImageView avatarView = holder.getView(R.id.avatarView);
        if (!TextUtils.isEmpty(item.avatar)) {
            String avatarUrl = item.avatar;
            // 与项目内其它地方一致：相对路径基于 baseUrl 拼接。
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = WKApiConfig.baseUrl + avatarUrl;
            }
            Glide.with(getContext())
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_webhook_default_avatar)
                    .error(R.drawable.ic_webhook_default_avatar)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(avatarView);
        } else {
            Glide.with(getContext()).clear(avatarView);
            avatarView.setImageResource(R.drawable.ic_webhook_default_avatar);
        }

        holder.setText(R.id.nameTv, TextUtils.isEmpty(item.name) ? "Webhook" : item.name);

        boolean disabled = (item.status == IncomingWebhook.STATUS_DISABLED);
        holder.setVisible(R.id.disabledChip, disabled);

        boolean canManage = item.canManageByCurrentUser(isManagerOrCreator, currentUid);
        boolean loading = togglingIds.contains(item.webhookId);

        SwitchView toggleSwitch = holder.getView(R.id.toggleSwitch);
        ProgressBar switchLoading = holder.getView(R.id.switchLoading);

        // Switch & loading 互斥显隐
        toggleSwitch.setVisibility((!canManage || loading) ? View.GONE : View.VISIBLE);
        toggleSwitch.setEnabled(canManage && !loading);
        toggleSwitch.setOnCheckedChangeListener(null);
        toggleSwitch.setChecked(!disabled);
        toggleSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            if (clickListener != null) clickListener.onSwitchToggled(item, isChecked);
        });
        switchLoading.setVisibility(loading ? View.VISIBLE : View.GONE);

        // 整张卡片在禁用态稍降透明度（与 iOS / web 一致）。
        View root = holder.getView(R.id.itemRoot);
        root.setAlpha(disabled ? 0.78f : 1.0f);

        // meta 行
        String createdTime = formatDateOnly(item.createdAt);
        String creatorDisplayName = creatorNameMap.get(item.creatorUid);
        String createdMeta;
        if (!TextUtils.isEmpty(creatorDisplayName)) {
            createdMeta = getContext().getString(R.string.str_webhook_meta_created_by,
                    creatorDisplayName, createdTime);
        } else {
            createdMeta = getContext().getString(R.string.str_webhook_meta_created_at, createdTime);
        }
        holder.setText(R.id.createdMetaTv, createdMeta);

        if (item.callCount > 0) {
            String lastTime = item.lastUsedAt > 0 ? formatDateTime(item.lastUsedAt) : "";
            String usage = getContext().getString(R.string.str_webhook_meta_usage,
                    item.callCount, lastTime);
            holder.setText(R.id.usageMetaTv, usage);
            holder.setVisible(R.id.usageMetaTv, true);
        } else {
            holder.setVisible(R.id.usageMetaTv, false);
        }
    }

    private String formatDateOnly(long sec) {
        if (sec <= 0) return "";
        return dateOnlyFmt.format(new Date(sec * 1000L));
    }

    private String formatDateTime(long sec) {
        if (sec <= 0) return "";
        return dateTimeFmt.format(new Date(sec * 1000L));
    }
}
