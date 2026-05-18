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
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActSpaceSettingsBinding;

public class SpaceSettingsActivity extends WKBaseActivity<ActSpaceSettingsBinding> {

    private String spaceId;
    private SpaceEntity spaceEntity;

    @Override
    protected ActSpaceSettingsBinding getViewBinding() {
        return ActSpaceSettingsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.space_settings);
    }

    @Override
    protected void initPresenter() {
        spaceId = getIntent().getStringExtra("space_id");
    }

    @Override
    protected void initView() {
        loadSpaceDetail();
    }

    @Override
    protected void initListener() {
        wkVBinding.membersLayout.setOnClickListener(v -> {
            if (spaceEntity == null) return;
            Intent intent = new Intent(this, SpaceMembersActivity.class);
            intent.putExtra("space_id", spaceId);
            intent.putExtra("owner_uid", spaceEntity.owner_uid);
            startActivity(intent);
        });

        wkVBinding.inviteLayout.setOnClickListener(v -> {
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
        });

        wkVBinding.leaveBtn.setOnClickListener(v -> {
            WKDialogUtils.getInstance().showDialog(
                    this,
                    getString(R.string.space_leave),
                    getString(R.string.space_leave_tips),
                    true, "", getString(R.string.sure),
                    0, ContextCompat.getColor(this, R.color.red),
                    index -> {
                        if (index == 1) {
                            SpaceModel.getInstance().leaveSpace(spaceId, (code, msg) -> {
                                if (code == 200) {
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    WKToastUtils.getInstance().showToastNormal(msg);
                                }
                            });
                        }
                    });
        });

        wkVBinding.disbandBtn.setOnClickListener(v -> {
            WKDialogUtils.getInstance().showDialog(
                    this,
                    getString(R.string.space_disband),
                    getString(R.string.space_disband_tips),
                    true, "", getString(R.string.sure),
                    0, ContextCompat.getColor(this, R.color.red),
                    index -> {
                        if (index == 1) {
                            SpaceModel.getInstance().disbandSpace(spaceId, (code, msg) -> {
                                if (code == 200) {
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    WKToastUtils.getInstance().showToastNormal(msg);
                                }
                            });
                        }
                    });
        });
    }

    private void loadSpaceDetail() {
        SpaceModel.getInstance().getSpaceDetail(spaceId, new SpaceModel.ISpaceListener() {
            @Override
            public void onResult(SpaceEntity space) {
                spaceEntity = space;
                wkVBinding.nameTv.setText(space.name);
                wkVBinding.descTv.setText(space.description);
                wkVBinding.memberCountTv.setText(String.valueOf(space.member_count));

                String myUid = WKConfig.getInstance().getUid();
                if (myUid != null && myUid.equals(space.owner_uid)) {
                    wkVBinding.disbandBtn.setVisibility(View.VISIBLE);
                    wkVBinding.leaveBtn.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(msg);
            }
        });
    }
}
