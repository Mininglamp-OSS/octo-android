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

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActChannelWebhookEditLayoutBinding;
import com.chat.uikit.group.webhook.service.IncomingWebhook;
import com.chat.uikit.group.webhook.service.IncomingWebhookManager;

/**
 * 新建 / 编辑 Webhook 页 — 1:1 对齐 iOS WKChannelWebhookEditVC：
 * <ul>
 *   <li>名称（最长 64）：成员留空时服务端自动命名 `Webhook-&lt;id 后缀&gt;`，且强制加 `Webhook-` 前缀；</li>
 *   <li>头像 URL（最长 255）：仅群主/管理员可见可设；成员带 avatar 服务端 400；</li>
 *   <li>右上角「完成」按钮：无变化或 saving 时置灰；</li>
 *   <li>新建成功 → 拉起一次性 URL 弹窗（含 token / curl 示例）。</li>
 * </ul>
 */
public class ChannelWebhookEditActivity extends WKBaseActivity<ActChannelWebhookEditLayoutBinding> {

    public static final String EXTRA_GROUP_NO = "groupNo";
    public static final String EXTRA_IS_MANAGER_OR_CREATOR = "isManagerOrCreator";
    public static final String EXTRA_EDITING_ID = "editingId";
    public static final String EXTRA_EDITING_NAME = "editingName";
    public static final String EXTRA_EDITING_AVATAR = "editingAvatar";

    private static final int NAME_MAX = 64;
    private static final int AVATAR_MAX = 255;

    private String groupNo;
    private boolean isManagerOrCreator;
    private boolean isEditing;
    private String editingId;
    private String origName = "";
    private String origAvatar = "";
    private boolean saving;
    private TextView saveBtn;

    @Override
    protected ActChannelWebhookEditLayoutBinding getViewBinding() {
        return ActChannelWebhookEditLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(isEditingFromIntent()
                ? R.string.str_webhook_title_edit : R.string.str_webhook_title_create);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        this.saveBtn = textView;
        return getString(R.string.str_done);
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra(EXTRA_GROUP_NO);
        isManagerOrCreator = getIntent().getBooleanExtra(EXTRA_IS_MANAGER_OR_CREATOR, false);
        editingId = getIntent().getStringExtra(EXTRA_EDITING_ID);
        isEditing = !TextUtils.isEmpty(editingId);
        origName = nullToEmpty(getIntent().getStringExtra(EXTRA_EDITING_NAME));
        origAvatar = nullToEmpty(getIntent().getStringExtra(EXTRA_EDITING_AVATAR));

        if (TextUtils.isEmpty(groupNo)) {
            finish();
            return;
        }

        wkVBinding.nameEt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NAME_MAX)});
        if (!TextUtils.isEmpty(origName)) wkVBinding.nameEt.setText(origName);

        // 普通成员前缀提示（与 iOS 一致）
        wkVBinding.memberPrefixHint.setVisibility(isManagerOrCreator ? View.GONE : View.VISIBLE);

        // 头像 URL 仅群主/管理员可见可设
        if (isManagerOrCreator) {
            wkVBinding.avatarCard.setVisibility(View.VISIBLE);
            wkVBinding.avatarHint.setVisibility(View.VISIBLE);
            wkVBinding.avatarEt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(AVATAR_MAX)});
            if (!TextUtils.isEmpty(origAvatar)) wkVBinding.avatarEt.setText(origAvatar);
        } else {
            wkVBinding.avatarCard.setVisibility(View.GONE);
            wkVBinding.avatarHint.setVisibility(View.GONE);
        }
    }

    @Override
    protected void initListener() {
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveBtnEnabled();
            }
        };
        wkVBinding.nameEt.addTextChangedListener(tw);
        if (isManagerOrCreator) {
            wkVBinding.avatarEt.addTextChangedListener(tw);
        }
    }

    @Override
    protected void initData() {
        super.initData();
        updateSaveBtnEnabled();
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        onSavePressed();
    }

    private boolean isEditingFromIntent() {
        // setTitle 在 initView 之前会被调用，需要从 intent 直接读取
        return !TextUtils.isEmpty(getIntent().getStringExtra(EXTRA_EDITING_ID));
    }

    private void updateSaveBtnEnabled() {
        if (saveBtn == null) return;
        boolean canSave = hasChange() && !saving;
        saveBtn.setEnabled(canSave);
        saveBtn.setAlpha(canSave ? 1.0f : 0.5f);
    }

    private boolean hasChange() {
        if (!isEditing) return true; // 新建态：留空也允许（服务端自动命名）
        String n = trimmed(wkVBinding.nameEt.getText().toString());
        String a = isManagerOrCreator ? trimmed(wkVBinding.avatarEt.getText().toString()) : origAvatar;
        boolean nameChanged = !TextUtils.isEmpty(n) && !n.equals(origName);
        boolean avatarChanged = isManagerOrCreator && !a.equals(origAvatar);
        return nameChanged || avatarChanged;
    }

    private void onSavePressed() {
        if (saving) return;
        if (!hasChange()) {
            finish();
            return;
        }
        String trimmedName = trimmed(wkVBinding.nameEt.getText().toString());
        String trimmedAvatar = isManagerOrCreator
                ? trimmed(wkVBinding.avatarEt.getText().toString()) : "";

        saving = true;
        updateSaveBtnEnabled();
        showLoadingHud();

        if (isEditing) {
            // 编辑：仅发"有值且与原值不同"的字段
            String nameParam = null;
            if (!TextUtils.isEmpty(trimmedName) && !trimmedName.equals(origName)) {
                nameParam = trimmedName;
            }
            String avatarParam = null;
            if (isManagerOrCreator && !trimmedAvatar.equals(origAvatar)) {
                avatarParam = trimmedAvatar; // 允许空串以清空
            }
            IncomingWebhookManager.getInstance().update(groupNo, editingId,
                    nameParam, avatarParam, null, (code, msg) -> {
                        saving = false;
                        hideLoadingHud();
                        if (code != HttpResponseCode.success) {
                            updateSaveBtnEnabled();
                            String tip = TextUtils.isEmpty(msg)
                                    ? getString(R.string.str_save_failed) : msg;
                            WKToastUtils.getInstance().showToast(tip);
                            return;
                        }
                        WKToastUtils.getInstance().showToast(getString(R.string.str_saved));
                        finish();
                    });
        } else {
            // 新建
            String avatarParam = isManagerOrCreator && !TextUtils.isEmpty(trimmedAvatar)
                    ? trimmedAvatar : null;
            String nameParam = TextUtils.isEmpty(trimmedName) ? null : trimmedName;
            IncomingWebhookManager.getInstance().create(groupNo, nameParam, avatarParam,
                    (code, msg, w) -> {
                        saving = false;
                        hideLoadingHud();
                        if (code != HttpResponseCode.success || w == null) {
                            updateSaveBtnEnabled();
                            String tip = TextUtils.isEmpty(msg)
                                    ? getString(R.string.str_webhook_create_failed) : msg;
                            WKToastUtils.getInstance().showToast(tip);
                            return;
                        }
                        // pop 自己，让上一层 VC 拉起 URL 弹窗 — 这里直接由本页拉起，
                        // 避免 finish 动画期间 URL 页被一起拆掉。延后到 onPause 触发不可控，
                        // 这里改为：先 startActivity 再 finish，URL 页在 List 之上展示。
                        ChannelWebhookUrlActivity.start(this, w);
                        finish();
                    });
        }
    }

    private void showLoadingHud() {
        if (loadingPopup != null) loadingPopup.show();
    }

    private void hideLoadingHud() {
        if (loadingPopup != null) loadingPopup.dismiss();
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
