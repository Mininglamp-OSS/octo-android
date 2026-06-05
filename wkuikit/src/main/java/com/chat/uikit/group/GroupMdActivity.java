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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.markdown.WKMarkwonProvider;
import com.chat.base.markdown.WKTableData;
import com.chat.base.markdown.WKTablePlugin;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupMdLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.chat.uikit.thread.service.ThreadModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class GroupMdActivity extends WKBaseActivity<ActGroupMdLayoutBinding> {

    private static final int MAX_BYTES = 10240;
    private String groupNo;
    private String shortId;
    private String channelId;
    private boolean isThread;
    private String originalContent = "";
    private TextView titleRightTv;
    private boolean canEdit;

    @Override
    protected ActGroupMdLayoutBinding getViewBinding() {
        return ActGroupMdLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_md);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        titleRightTv = textView;
        return getString(R.string.save);
    }

    @Override
    protected boolean hideStatusBar() {
        return true;
    }

    @Override
    protected void rightLayoutClick() {
        String content = wkVBinding.contentEt.getText() != null
                ? wkVBinding.contentEt.getText().toString() : "";
        if (content.equals(originalContent)) {
            finish();
            return;
        }
        int byteLen = content.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen > MAX_BYTES) {
            showToast(getString(R.string.group_md_exceed_limit));
            return;
        }
        showTitleRightLoading();
        if (isThread) {
            ThreadModel.getInstance().updateThreadMd(groupNo, shortId, content, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    updateThreadMdExtra(!content.isEmpty());
                    finish();
                } else {
                    hideTitleRightLoading();
                    showToast(msg);
                }
            });
        } else {
            GroupModel.getInstance().updateGroupMd(groupNo, content, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    WKIM.getInstance().getChannelManager().fetchChannelInfo(groupNo, WKChannelType.GROUP);
                    finish();
                } else {
                    hideTitleRightLoading();
                    showToast(msg);
                }
            });
        }
    }

    @Override
    protected void initView() {
        wkVBinding.contentEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateByteCount(s.toString());
            }
        });
    }

    @Override
    protected void initListener() {}

    @Override
    protected void initData() {
        groupNo = getIntent().getStringExtra("groupNo");
        byte channelType = getIntent().getByteExtra("channelType", WKChannelType.GROUP);
        isThread = channelType == WKChannelType.COMMUNITY_TOPIC;

        if (isThread) {
            shortId = getIntent().getStringExtra("shortId");
            channelId = getIntent().getStringExtra("channelId");
            canEdit = getIntent().getBooleanExtra("canEdit", false);
            if (!canEdit) {
                WKChannelMember groupMember = WKIM.getInstance().getChannelMembersManager()
                        .getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
                canEdit = groupMember != null
                        && (groupMember.role == WKChannelMemberRole.admin
                        || groupMember.role == WKChannelMemberRole.manager);
            }
        } else {
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager()
                    .getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
            canEdit = member != null
                    && (member.role == WKChannelMemberRole.admin
                    || member.role == WKChannelMemberRole.manager);
        }

        applyEditMode();

        if (loadingPopup != null) loadingPopup.show();
        GroupModel.IGroupMdListener mdListener = (code, msg, entity) -> {
            if (loadingPopup != null) loadingPopup.dismiss();
            if (code == HttpResponseCode.success && entity != null
                    && !TextUtils.isEmpty(entity.content)) {
                originalContent = entity.content;
                if (canEdit) {
                    wkVBinding.contentEt.setText(entity.content);
                    wkVBinding.contentEt.setSelection(entity.content.length());
                    wkVBinding.contentEt.requestFocus();
                    SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, wkVBinding.contentEt);
                } else {
                    renderMarkdown(entity.content);
                }
                wkVBinding.readonlyHintLayout.setVisibility(View.GONE);
                updateByteCount(entity.content);
            } else if (!canEdit) {
                wkVBinding.renderScrollView.setVisibility(View.GONE);
                wkVBinding.readonlyHintLayout.setVisibility(View.VISIBLE);
            }
        };

        if (isThread) {
            ThreadModel.getInstance().getThreadMd(groupNo, shortId, mdListener);
        } else {
            GroupModel.getInstance().getGroupMd(groupNo, mdListener);
        }
    }

    private void applyEditMode() {
        if (canEdit) {
            if (titleRightTv != null) titleRightTv.setVisibility(View.VISIBLE);
            wkVBinding.editScrollView.setVisibility(View.VISIBLE);
            wkVBinding.renderScrollView.setVisibility(View.GONE);
            wkVBinding.contentEt.setEnabled(true);
            wkVBinding.contentEt.setFocusableInTouchMode(true);
            wkVBinding.byteCountTv.setVisibility(View.VISIBLE);
        } else {
            if (titleRightTv != null) titleRightTv.setVisibility(View.GONE);
            wkVBinding.editScrollView.setVisibility(View.GONE);
            wkVBinding.renderScrollView.setVisibility(View.VISIBLE);
            wkVBinding.byteCountTv.setVisibility(View.GONE);
        }
    }

    private void renderMarkdown(String content) {
        wkVBinding.renderTv.setMovementMethod(LinkMovementMethod.getInstance());

        kotlin.Pair<Spanned, List<WKTableData>> result =
                WKMarkwonProvider.toMarkdownWithTables(this, content);
        Spanned rendered = result.getFirst();
        List<WKTableData> tableDataList = result.getSecond();

        if (tableDataList.isEmpty()) {
            wkVBinding.renderTv.setText(rendered);
            return;
        }

        String fullText = rendered.toString();
        java.util.List<Integer> placeholderPositions = new java.util.ArrayList<>();
        int searchIdx = 0;
        while (searchIdx < fullText.length()) {
            int pos = fullText.indexOf(WKTablePlugin.TABLE_PLACEHOLDER, searchIdx);
            if (pos < 0) break;
            placeholderPositions.add(pos);
            searchIdx = pos + 1;
        }

        if (placeholderPositions.size() != tableDataList.size()) {
            wkVBinding.renderTv.setText(rendered);
            for (WKTableData tableData : tableDataList) {
                wkVBinding.renderLayout.addView(buildTableCardView(tableData));
            }
            return;
        }

        java.util.List<CharSequence> segments = new java.util.ArrayList<>();
        int start = 0;
        for (int pos : placeholderPositions) {
            segments.add(rendered.subSequence(start, pos));
            start = pos + WKTablePlugin.TABLE_PLACEHOLDER.length();
        }
        segments.add(rendered.subSequence(start, rendered.length()));

        CharSequence firstSegment = trimEdgeNewlines(segments.get(0));
        if (isBlank(firstSegment)) {
            wkVBinding.renderTv.setVisibility(View.GONE);
        } else {
            wkVBinding.renderTv.setVisibility(View.VISIBLE);
            wkVBinding.renderTv.setText(firstSegment);
        }

        int textColor = wkVBinding.renderTv.getCurrentTextColor();
        for (int i = 0; i < tableDataList.size(); i++) {
            wkVBinding.renderLayout.addView(buildTableCardView(tableDataList.get(i)));

            if (i + 1 < segments.size()) {
                CharSequence nextSegment = trimEdgeNewlines(segments.get(i + 1));
                if (isBlank(nextSegment)) continue;

                TextView extraTv = new TextView(this);
                extraTv.setText(nextSegment);
                extraTv.setTextColor(textColor);
                extraTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, wkVBinding.renderTv.getTextSize());
                extraTv.setMovementMethod(LinkMovementMethod.getInstance());
                extraTv.setLineSpacing(4f * getResources().getDisplayMetrics().density, 1f);
                extraTv.setTextIsSelectable(true);
                wkVBinding.renderLayout.addView(extraTv);
            }
        }
    }

    private static boolean isBlank(CharSequence cs) {
        if (cs == null) return true;
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return false;
        }
        return true;
    }

    private CharSequence trimEdgeNewlines(CharSequence cs) {
        int s = 0, e = cs.length();
        while (s < e && cs.charAt(s) == '\n') s++;
        while (e > s && cs.charAt(e - 1) == '\n') e--;
        return (s == 0 && e == cs.length()) ? cs : cs.subSequence(s, e);
    }

    private View buildTableCardView(WKTableData tableData) {
        View cardView = LayoutInflater.from(this)
                .inflate(com.chat.base.R.layout.layout_markdown_table_card, wkVBinding.renderLayout, false);

        TableLayout tableContent = cardView.findViewById(com.chat.base.R.id.tableContent);
        HorizontalScrollView tableScrollView = cardView.findViewById(com.chat.base.R.id.tableScrollView);
        ImageView copyBtn = cardView.findViewById(com.chat.base.R.id.tableCopyBtn);

        if (tableData.getHeaders().isEmpty() && tableData.getRows().isEmpty()) {
            tableContent.setStretchAllColumns(false);
            return cardView;
        }

        float dp = getResources().getDisplayMetrics().density;
        int cellPaddingH = (int) (10 * dp);
        int cellPaddingV = (int) (8 * dp);
        float textSize = 13f;
        int headerBgColor = Color.parseColor("#F0F0F0");
        int evenRowBgColor = Color.parseColor("#FAFAFA");
        int headerTextColor = Color.parseColor("#333333");
        int cellTextColor = Color.parseColor("#555555");

        tableContent.setStretchAllColumns(true);

        if (!tableData.getHeaders().isEmpty()) {
            TableRow headerRow = new TableRow(this);
            headerRow.setBackgroundColor(headerBgColor);
            for (int colIdx = 0; colIdx < tableData.getHeaders().size(); colIdx++) {
                headerRow.addView(createCellTextView(
                        tableData.getHeaders().get(colIdx).getText(),
                        textSize, cellPaddingH, cellPaddingV,
                        headerTextColor, true, tableData, colIdx, dp));
            }
            tableContent.addView(headerRow);
        }

        for (int rowIdx = 0; rowIdx < tableData.getRows().size(); rowIdx++) {
            List<com.chat.base.markdown.WKTableCell> row = tableData.getRows().get(rowIdx);
            TableRow tableRow = new TableRow(this);
            if (rowIdx % 2 == 1) tableRow.setBackgroundColor(evenRowBgColor);
            for (int colIdx = 0; colIdx < row.size(); colIdx++) {
                tableRow.addView(createCellTextView(
                        row.get(colIdx).getText(),
                        textSize, cellPaddingH, cellPaddingV,
                        cellTextColor, false, tableData, colIdx, dp));
            }
            tableContent.addView(tableRow);
        }

        copyBtn.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            if (!tableData.getHeaders().isEmpty()) {
                for (int i = 0; i < tableData.getHeaders().size(); i++) {
                    if (i > 0) sb.append("\t");
                    sb.append(tableData.getHeaders().get(i).getText());
                }
                sb.append("\n");
            }
            for (List<com.chat.base.markdown.WKTableCell> row : tableData.getRows()) {
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) sb.append("\t");
                    sb.append(row.get(i).getText());
                }
                sb.append("\n");
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("table", sb.toString().trim()));
            WKToastUtils.getInstance().showToastNormal(getString(com.chat.base.R.string.str_table_copied));
        });

        return cardView;
    }

    private TextView createCellTextView(String text, float textSize, int paddingH, int paddingV,
                                         int textColor, boolean isBold, WKTableData tableData,
                                         int colIdx, float dp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        tv.setTextColor(textColor);
        tv.setPadding(paddingH, paddingV, paddingH, paddingV);
        if (isBold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        tv.setMinWidth((int) (60 * dp));
        tv.setMaxWidth((int) (200 * dp));

        if (colIdx < tableData.getAlignments().size()) {
            org.commonmark.ext.gfm.tables.TableCell.Alignment alignment = tableData.getAlignments().get(colIdx);
            if (alignment == org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER) {
                tv.setGravity(android.view.Gravity.CENTER);
            } else if (alignment == org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT) {
                tv.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            } else {
                tv.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            }
        }

        if (colIdx > 0) {
            TableRow.LayoutParams lp = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int) (0.5 * dp), 0, 0, 0);
            tv.setLayoutParams(lp);
        }
        return tv;
    }

    private void updateThreadMdExtra(boolean hasContent) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, WKChannelType.COMMUNITY_TOPIC);
        if (channel != null) {
            if (channel.remoteExtraMap == null) {
                channel.remoteExtraMap = new java.util.HashMap<>();
            }
            channel.remoteExtraMap.put("has_thread_md", hasContent);
            Object vObj = channel.remoteExtraMap.get("thread_md_version");
            int version = vObj instanceof Number ? ((Number) vObj).intValue() : 0;
            if (hasContent) version++;
            channel.remoteExtraMap.put("thread_md_version", version);
            WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
        }
    }

    private void updateByteCount(String text) {
        int byteLen = text.getBytes(StandardCharsets.UTF_8).length;
        wkVBinding.byteCountTv.setText(String.format(getString(R.string.group_md_byte_count), byteLen, MAX_BYTES));
        wkVBinding.byteCountTv.setTextColor(byteLen > MAX_BYTES
                ? ContextCompat.getColor(this, R.color.reminderColor)
                : 0xFF999999);
    }

    @Override
    public void finish() {
        super.finish();
        SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
    }
}
