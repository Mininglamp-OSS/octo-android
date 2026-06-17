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

package com.chat.uikit.group;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActTransferGroupOwnerLayoutBinding;
import com.chat.uikit.group.adapter.TransferGroupOwnerAdapter;
import com.chat.uikit.group.service.GroupModel;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 转让群主 - 选择新群主。1:1 对齐 iOS WKConversationSettingVM#presentTransferOwnerPicker。
 *
 * <p>关键约束:
 * <ul>
 *   <li>单选, 同一时刻最多一个 item 选中。</li>
 *   <li>列表隐藏当前登录用户和所有机器人 (与 iOS hiddenUsers 对齐)。</li>
 *   <li>右上角 "确定" 按钮初始隐藏, 选中后显示; 点击后弹二次确认 dialog,
 *   确认后调 {@link GroupModel#transferGroupOwner} 并在成功回调里同步成员/频道信息。</li>
 * </ul>
 */
public class TransferGroupOwnerActivity extends WKBaseActivity<ActTransferGroupOwnerLayoutBinding> {

    private String groupNo;
    private TransferGroupOwnerAdapter adapter;
    private TextView confirmTv;
    private String searchKey = "";
    private int page = 1;
    private String selectedUid;
    private String selectedName;

    @Override
    protected ActTransferGroupOwnerLayoutBinding getViewBinding() {
        return ActTransferGroupOwnerLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.str_select_new_owner);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        this.confirmTv = textView;
        return getString(R.string.sure);
    }

    @Override
    protected void initView() {
        groupNo = getIntent().getStringExtra("groupNo");
        if (TextUtils.isEmpty(groupNo)) {
            // 没有 groupNo 不可能完成转让, 直接结束避免后续 NPE / 空列表困惑用户。
            finish();
            return;
        }
        adapter = new TransferGroupOwnerAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        if (TextUtils.isEmpty(selectedUid)) return;
        showConfirmDialog();
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                page++;
                getData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                page = 1;
                getData();
            }
        });

        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(TransferGroupOwnerActivity.this);
                return true;
            }
            return false;
        });
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchKey = editable.toString();
                page = 1;
                adapter.setSearchKey(searchKey);
                getData();
            }
        });

        adapter.setOnItemClickListener((a, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, v -> {
            GroupMemberEntity entity = adapter.getItem(position);
            if (entity == null || entity.member == null) return;

            // 单选: 先把列表里所有 item 清空 checked, 再把当前 item 置为 checked。
            // 不直接用 notifyItemChanged 比较 prev / curr 索引, 避免 RecyclerView
            // 复用导致旧勾选不刷新; 这里全量 notifyDataSetChanged 数据量小 (单页 50) 没有性能问题。
            for (GroupMemberEntity e : adapter.getData()) {
                e.checked = 0;
            }
            entity.checked = 1;
            adapter.notifyDataSetChanged();
            selectedUid = entity.member.memberUID;
            selectedName = TextUtils.isEmpty(entity.member.memberRemark)
                    ? entity.member.memberName : entity.member.memberRemark;
            if (confirmTv != null) {
                confirmTv.setVisibility(View.VISIBLE);
                showTitleRightView();
            }
        }));
    }

    @Override
    protected void initData() {
        super.initData();
        // 默认隐藏 "确定" 按钮, 选中成员后再显示。注意必须放在 initData() (lifecycle 中
        // 在 initTitleBar() 之后) — getRightTvText 在 initTitleBar 才回调, initView 阶段
        // confirmTv / titleRightLayout 都还是 null, 提前 hide 会失效。
        if (confirmTv != null) {
            confirmTv.setVisibility(View.GONE);
        }
        hideTitleRightView();
        getData();
    }

    private void getData() {
        WKIM.getInstance().getChannelMembersManager().getWithPageOrSearch(
                groupNo, WKChannelType.GROUP, searchKey, page, 50,
                (list, b) -> resortData(list));
    }

    private void resortData(List<WKChannelMember> list) {
        wkVBinding.refreshLayout.finishLoadMore();
        if (WKReader.isEmpty(list)) {
            if (page == 1) adapter.setList(new ArrayList<>());
            else wkVBinding.refreshLayout.setEnableLoadMore(false);
            return;
        }
        wkVBinding.refreshLayout.setEnableLoadMore(true);

        // hidden 规则: 当前登录用户 + 所有机器人 (对齐 iOS hiddenUsers)。
        String loginUid = WKConfig.getInstance().getUid();
        List<GroupMemberEntity> tempList = new ArrayList<>();
        for (WKChannelMember member : list) {
            if (member == null || TextUtils.isEmpty(member.memberUID)) continue;
            if (member.memberUID.equals(loginUid)) continue;
            if (member.robot == 1) continue;
            if (member.isDeleted == 1) continue;
            GroupMemberEntity entity = new GroupMemberEntity(member);
            // 保留之前选中的勾选态: 跨页加载时不能让选中的成员被新 list 覆盖丢勾。
            if (!TextUtils.isEmpty(selectedUid) && selectedUid.equals(member.memberUID)) {
                entity.checked = 1;
            }
            tempList.add(entity);
        }
        if (page == 1) {
            adapter.setList(tempList);
        } else {
            adapter.addData(tempList);
        }
    }

    private void showConfirmDialog() {
        String name = TextUtils.isEmpty(selectedName) ? selectedUid : selectedName;
        String tip = String.format(getString(R.string.str_transfer_owner_confirm_tip), name);
        WKDialogUtils.getInstance().showDialog(
                this,
                getString(R.string.str_transfer_owner),
                tip,
                true,
                "",
                getString(R.string.str_transfer_owner),
                0,
                ContextCompat.getColor(this, R.color.red),
                index -> {
                    if (index == 1) {
                        doTransfer();
                    }
                });
    }

    private void doTransfer() {
        showTitleRightLoading();
        GroupModel.getInstance().transferGroupOwner(groupNo, selectedUid, (code, msg) -> {
            hideTitleRightLoading();
            if (code == HttpResponseCode.success) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.str_transfer_owner_success));
                // 同步成员列表和频道信息, 让设置页 / 详情页角色权限刷新, 与 iOS
                // [WKGroupManager syncMemebers] + [WKSDK fetchChannelInfo] 对齐。
                GroupModel.getInstance().groupMembersSync(groupNo, null);
                setResult(RESULT_OK);
                finish();
            } else {
                WKToastUtils.getInstance().showToast(TextUtils.isEmpty(msg)
                        ? getString(R.string.str_transfer_owner_failed) : msg);
            }
        });
    }
}
