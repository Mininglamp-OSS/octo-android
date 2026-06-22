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

package com.chat.uikit.group.webhook;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActChannelWebhookLayoutBinding;
import com.chat.uikit.group.webhook.adapter.WebhookListAdapter;
import com.chat.uikit.group.webhook.service.IncomingWebhook;
import com.chat.uikit.group.webhook.service.IncomingWebhookManager;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 群消息推送（群入站 Webhook）列表页 — 群信息「群消息推送」入口跳转目标。
 *
 * <p>对全员只读可见；按权限矩阵控制操作（群主 / 管理员管全部，普通成员仅能管自己创建的）。
 * 1:1 对齐 iOS WKChannelWebhookVC：
 * <ul>
 *   <li>列表为空时显示空态视图（链接图标 + 文案 + 居中 CTA）；列表非空显示右上角 + 按钮；</li>
 *   <li>单击 cell 跳转编辑（无权限点击不响应）；</li>
 *   <li>长按弹浮层菜单：测试发送 / 重置 Token / 删除（无权限只显示「复制名称」）；</li>
 *   <li>启停切换由 cell 内 Switch 直接打 PUT，不走二次确认。</li>
 * </ul>
 */
public class ChannelWebhookActivity extends WKBaseActivity<ActChannelWebhookLayoutBinding> {

    public static final String EXTRA_GROUP_NO = "groupNo";
    public static final String EXTRA_IS_MANAGER_OR_CREATOR = "isManagerOrCreator";

    private String groupNo;
    private boolean isManagerOrCreator;
    private WebhookListAdapter adapter;
    // 标记首次 list 请求是否已返回（成功或失败）。
    // onResume 用它判断是「首次进入」还是「从子页 / 弹窗回来」：
    //   - 首次进入：initData() 已发起 loadList(true)，onResume 此时若再 loadList 会重复请求；
    //   - 从子页回来：用户可能在编辑页改了名字 / 头像 / 启停 / 新建，必须刷一次。
    // 旧实现用 `!data.isEmpty()` 代理「是否做过首次加载」，会把首条 webhook 创建后空列表
    // 返回的场景一并挡掉，导致新建后列表不显示（review 反馈 critical）。
    private boolean initialLoaded;
    private TextView addBtn;
    private final List<IncomingWebhook> data = new ArrayList<>();

    @Override
    protected ActChannelWebhookLayoutBinding getViewBinding() {
        return ActChannelWebhookLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.str_group_incoming_webhooks);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        this.addBtn = textView;
        return getString(R.string.str_webhook_create_short);
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra(EXTRA_GROUP_NO);
        isManagerOrCreator = getIntent().getBooleanExtra(EXTRA_IS_MANAGER_OR_CREATOR, false);
        if (TextUtils.isEmpty(groupNo)) {
            finish();
            return;
        }

        // 顶部说明 header（与 web ChannelWebhookPanel description 文案一致）
        TextView headerDesc = new TextView(this);
        int padH = dp(16);
        headerDesc.setPadding(padH, dp(12), padH, dp(12));
        headerDesc.setText(R.string.str_webhook_header_description);
        headerDesc.setTextColor(ContextCompat.getColor(this, R.color.color999));
        headerDesc.setTextSize(13);

        adapter = new WebhookListAdapter(new ArrayList<>());
        adapter.setIsManagerOrCreator(isManagerOrCreator);
        adapter.setCurrentUid(WKConfig.getInstance().getUid());
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wkVBinding.recyclerView.setAdapter(adapter);
        adapter.addHeaderView(headerDesc);

        // 列表非空才显示右上 + 按钮；空态用居中 CTA
        if (addBtn != null) addBtn.setVisibility(View.GONE);
        hideTitleRightView();
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setEnableRefresh(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setOnRefreshListener(refreshLayout -> {
            loadList(false);
            refreshLayout.finishRefresh(800);
        });

        SingleClickUtil.onSingleClick(wkVBinding.emptyAddBtn, v -> openEdit(null));

        adapter.setClickListener((webhook, nextOn) -> toggleWebhook(webhook, nextOn));

        adapter.setOnItemClickListener((a, view, position) -> {
            IncomingWebhook item = adapter.getItem(position);
            if (item == null) return;
            // 单击直接打开编辑 — 让"编辑"成为最主路径；无权限点击无效（保留长按"复制名称"兜底）。
            if (canManageWebhook(item)) {
                openEdit(item);
            }
        });

        adapter.setOnItemLongClickListener((a, view, position) -> {
            IncomingWebhook item = adapter.getItem(position);
            if (item == null) return false;
            showActionMenu(view, item);
            return true;
        });
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        openEdit(null);
    }

    @Override
    protected void initData() {
        super.initData();
        loadList(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 编辑 / 创建 / 弹窗结束后回到列表，强制刷一次：用户可能在编辑页改了名字 / 头像 / 启停。
        // 用 initialLoaded 而不是 `!data.isEmpty()` 判断「是否从子页回来」——后者会漏掉「空列表
        // 创建第一条 webhook 后返回」的场景（首条创建链路 review critical 修复）。
        if (adapter != null && initialLoaded) {
            loadList(false);
        }
    }

    private void loadList(boolean showHud) {
        if (showHud) showLoadingHud();
        IncomingWebhookManager.getInstance().list(groupNo, (code, msg, list) -> {
            if (showHud) hideLoadingHud();
            // 不论成功 / 失败都置位：onResume 后续才会切到「子页返回 → 刷新」语义；
            // 首次失败的情况下，回到本页用户手动下拉刷新仍可重试，不会卡死。
            initialLoaded = true;
            if (code != HttpResponseCode.success) {
                String tip = TextUtils.isEmpty(msg) ? getString(R.string.str_load_failed) : msg;
                WKToastUtils.getInstance().showToast(tip);
                return;
            }
            data.clear();
            if (list != null) data.addAll(list);
            // 解析创建者展示名（自己 / 群成员 / 兜底空串）
            String myUid = WKConfig.getInstance().getUid();
            for (IncomingWebhook h : data) {
                if (TextUtils.isEmpty(h.creatorUid)) continue;
                String name;
                if (h.creatorUid.equals(myUid)) {
                    name = getString(R.string.str_webhook_creator_self);
                } else {
                    WKChannelMember member = WKIM.getInstance().getChannelMembersManager()
                            .getMember(groupNo, WKChannelType.GROUP, h.creatorUid);
                    if (member != null && !TextUtils.isEmpty(member.memberRemark)) {
                        name = member.memberRemark;
                    } else if (member != null && !TextUtils.isEmpty(member.memberName)) {
                        name = member.memberName;
                    } else {
                        name = "";
                    }
                }
                adapter.putCreatorName(h.creatorUid, name);
            }
            adapter.setList(data);
            refreshUIAfterLoad();
            writeBackCountToChannelInfo(data.size());
        });
    }

    private void refreshUIAfterLoad() {
        boolean empty = data.isEmpty();
        wkVBinding.emptyLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        wkVBinding.refreshLayout.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (addBtn != null) addBtn.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) hideTitleRightView();
        else showTitleRightView();
    }

    /** 把 webhook 数量写回 channel.localExtra，给群信息页副标题用。 */
    private void writeBackCountToChannelInfo(int count) {
        WKChannel info = WKIM.getInstance().getChannelManager()
                .getChannel(groupNo, WKChannelType.GROUP);
        if (info == null) return;
        if (info.localExtra == null) info.localExtra = new java.util.HashMap<>();
        Object prev = info.localExtra.get(WebhookConstants.EXTRA_COUNT_KEY);
        if (prev instanceof Integer && (int) prev == count) return;
        info.localExtra.put(WebhookConstants.EXTRA_COUNT_KEY, count);
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(info);
    }

    private boolean canManageWebhook(IncomingWebhook item) {
        if (item == null) return false;
        if (isManagerOrCreator) return true;
        String me = WKConfig.getInstance().getUid();
        return !TextUtils.isEmpty(me) && me.equals(item.creatorUid);
    }

    private void openEdit(IncomingWebhook editing) {
        Intent intent = new Intent(this, ChannelWebhookEditActivity.class);
        intent.putExtra(ChannelWebhookEditActivity.EXTRA_GROUP_NO, groupNo);
        intent.putExtra(ChannelWebhookEditActivity.EXTRA_IS_MANAGER_OR_CREATOR, isManagerOrCreator);
        if (editing != null) {
            intent.putExtra(ChannelWebhookEditActivity.EXTRA_EDITING_ID, editing.webhookId);
            intent.putExtra(ChannelWebhookEditActivity.EXTRA_EDITING_NAME, editing.name == null ? "" : editing.name);
            intent.putExtra(ChannelWebhookEditActivity.EXTRA_EDITING_AVATAR, editing.avatar == null ? "" : editing.avatar);
        }
        startActivity(intent);
    }

    /** 启停切换 — 直接打 PUT，不走二次确认。 */
    private void toggleWebhook(IncomingWebhook webhook, boolean nextOn) {
        if (webhook == null || TextUtils.isEmpty(webhook.webhookId)) return;
        adapter.setToggling(webhook.webhookId, true);
        adapter.notifyDataSetChanged();

        int nextStatus = nextOn ? IncomingWebhook.STATUS_ENABLED : IncomingWebhook.STATUS_DISABLED;
        IncomingWebhookManager.getInstance().update(groupNo, webhook.webhookId,
                null, null, nextStatus, (code, msg) -> {
                    adapter.setToggling(webhook.webhookId, false);
                    if (code != HttpResponseCode.success) {
                        String tip = TextUtils.isEmpty(msg) ? getString(R.string.str_op_failed) : msg;
                        WKToastUtils.getInstance().showToast(tip);
                        // 失败也要刷新一次，把 Switch 拉回原状态
                        adapter.notifyDataSetChanged();
                        return;
                    }
                    // 成功后乐观更新本地数据，避免重新拉列表的闪烁
                    webhook.status = nextStatus;
                    adapter.notifyDataSetChanged();
                });
    }

    private void showActionMenu(View anchor, IncomingWebhook item) {
        boolean canManage = canManageWebhook(item);
        java.util.List<WebhookActionMenu.Item> menu = new java.util.ArrayList<>();

        if (canManage) {
            boolean enabled = item.status == IncomingWebhook.STATUS_ENABLED;
            boolean onCooldown = IncomingWebhookManager.getInstance().isWebhookOnTestCooldown(item.webhookId);
            boolean anyInFlight = IncomingWebhookManager.getInstance().hasTestInFlight();

            // 测试发送（仅 enabled 才出现；冷却中文案变化但仍可见）
            if (enabled) {
                String title = onCooldown
                        ? getString(R.string.str_webhook_action_test_cooldown)
                        : getString(R.string.str_webhook_action_test);
                menu.add(new WebhookActionMenu.Item(title, false, () -> {
                    if (anyInFlight || IncomingWebhookManager.getInstance().isWebhookOnTestCooldown(item.webhookId)) {
                        WKToastUtils.getInstance().showToast(getString(R.string.str_webhook_action_wait));
                        return;
                    }
                    testWebhook(item);
                }));
            }
            // 重置 token —— destructive 二次确认
            menu.add(new WebhookActionMenu.Item(getString(R.string.str_webhook_action_regenerate),
                    false, () -> confirmRegenerate(item)));
            // 删除 —— destructive 二次确认
            menu.add(new WebhookActionMenu.Item(getString(R.string.str_webhook_action_delete),
                    true, () -> confirmDelete(item)));
        } else {
            // 无权限：仅给一个"复制名称"
            menu.add(new WebhookActionMenu.Item(getString(R.string.str_webhook_action_copy_name),
                    false, () -> {
                        copyToClipboard(item.name == null ? "" : item.name);
                        WKToastUtils.getInstance().showToast(getString(R.string.copied));
                    }));
        }
        WebhookActionMenu.show(anchor, menu);
    }

    private void testWebhook(IncomingWebhook item) {
        showLoadingHud();
        IncomingWebhookManager.getInstance().test(groupNo, item.webhookId, (sent, code, msg) -> {
            hideLoadingHud();
            if (!sent) {
                if (code != HttpResponseCode.success && !TextUtils.isEmpty(msg)) {
                    WKToastUtils.getInstance().showToast(msg);
                }
                return;
            }
            if (code != HttpResponseCode.success) {
                String tip = TextUtils.isEmpty(msg) ? getString(R.string.str_webhook_action_test_failed) : msg;
                WKToastUtils.getInstance().showToast(tip);
                return;
            }
            WKToastUtils.getInstance().showToast(getString(R.string.str_webhook_action_test_sent));
        });
    }

    private void confirmRegenerate(IncomingWebhook item) {
        String tip = String.format(getString(R.string.str_webhook_regenerate_confirm),
                item.name == null ? "" : item.name);
        WKDialogUtils.getInstance().showDialog(this,
                getString(R.string.str_webhook_action_regenerate),
                tip, true, "",
                getString(R.string.str_webhook_action_regenerate),
                0, ContextCompat.getColor(this, R.color.red),
                index -> {
                    if (index != 1) return;
                    showLoadingHud();
                    IncomingWebhookManager.getInstance().regenerate(groupNo, item.webhookId, (code, msg, w) -> {
                        hideLoadingHud();
                        if (code != HttpResponseCode.success || w == null) {
                            String t = TextUtils.isEmpty(msg) ? getString(R.string.str_webhook_action_regenerate_failed) : msg;
                            WKToastUtils.getInstance().showToast(t);
                            return;
                        }
                        // 把新地址（token 仅此一次）弹出来给用户复制
                        ChannelWebhookUrlActivity.start(this, w);
                        loadList(false);
                    });
                });
    }

    private void confirmDelete(IncomingWebhook item) {
        String tip = String.format(getString(R.string.str_webhook_delete_confirm),
                item.name == null ? "" : item.name);
        WKDialogUtils.getInstance().showDialog(this,
                getString(R.string.str_webhook_action_delete),
                tip, true, "",
                getString(R.string.str_webhook_action_delete),
                0, ContextCompat.getColor(this, R.color.red),
                index -> {
                    if (index != 1) return;
                    showLoadingHud();
                    IncomingWebhookManager.getInstance().delete(groupNo, item.webhookId, (code, msg) -> {
                        hideLoadingHud();
                        if (code != HttpResponseCode.success) {
                            String t = TextUtils.isEmpty(msg) ? getString(R.string.str_webhook_action_delete_failed) : msg;
                            WKToastUtils.getInstance().showToast(t);
                            return;
                        }
                        WKToastUtils.getInstance().showToast(getString(R.string.str_webhook_action_deleted));
                        loadList(false);
                    });
                });
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("webhook", text == null ? "" : text));
        }
    }

    private void showLoadingHud() {
        if (loadingPopup != null) loadingPopup.show();
    }

    private void hideLoadingHud() {
        if (loadingPopup != null) loadingPopup.dismiss();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
