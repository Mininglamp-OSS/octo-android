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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActSpaceMembersBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpaceMembersActivity extends WKBaseActivity<ActSpaceMembersBinding> {

    private String spaceId;
    private String ownerUid;
    private SpaceMemberAdapter adapter;

    @Override
    protected ActSpaceMembersBinding getViewBinding() {
        return ActSpaceMembersBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.space_members);
    }

    @Override
    protected void initPresenter() {
        spaceId = getIntent().getStringExtra("space_id");
        ownerUid = getIntent().getStringExtra("owner_uid");
    }

    @Override
    protected String getRightTvText(TextView textView) {
        String myUid = WKConfig.getInstance().getUid();
        if (myUid != null && myUid.equals(ownerUid)) {
            return getString(R.string.space_invite);
        }
        return "";
    }

    @Override
    protected void rightLayoutClick() {
        SpaceModel.getInstance().createInvite(spaceId, new SpaceModel.IInviteListener() {
            @Override
            public void onResult(String inviteCode) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("invite_code", inviteCode);
                clipboard.setPrimaryClip(clip);
                WKToastUtils.getInstance().showToastNormal(getString(R.string.space_invite_code_copied));
            }

            @Override
            public void onError(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }

    @Override
    protected void initView() {
        adapter = new SpaceMemberAdapter();
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wkVBinding.recyclerView.setAdapter(adapter);
        loadMembers();
    }

    @Override
    protected void initListener() {
        String myUid = WKConfig.getInstance().getUid();
        boolean isOwnerOrAdmin = myUid != null && myUid.equals(ownerUid);

        adapter.setOnItemLongClickListener((a, view, position) -> {
            if (!isOwnerOrAdmin) return false;
            SpaceEntity.SpaceMember member = adapter.getItem(position);
            if (member == null || member.uid.equals(myUid)) return false;
            if (member.role == 2) return false; // can't remove owner

            WKDialogUtils.getInstance().showDialog(
                    this,
                    getString(R.string.space_remove_member),
                    String.format(getString(R.string.space_remove_member_tips), member.name),
                    true, "", getString(R.string.sure),
                    0, ContextCompat.getColor(this, R.color.red),
                    index -> {
                        if (index == 1) {
                            List<String> uids = new ArrayList<>();
                            uids.add(member.uid);
                            SpaceModel.getInstance().removeMembers(spaceId, uids, (code, msg) -> {
                                if (code == 200) {
                                    loadMembers();
                                } else {
                                    WKToastUtils.getInstance().showToastNormal(msg);
                                }
                            });
                        }
                    });
            return true;
        });
    }

    private void loadMembers() {
        SpaceModel.getInstance().getMembers(spaceId, new SpaceModel.IMembersListener() {
            @Override
            public void onResult(List<SpaceEntity.SpaceMember> members) {
                // Sort: owner first, then admin, then members
                Collections.sort(members, (a, b) -> b.role - a.role);
                adapter.setList(members);
            }

            @Override
            public void onError(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }
}
