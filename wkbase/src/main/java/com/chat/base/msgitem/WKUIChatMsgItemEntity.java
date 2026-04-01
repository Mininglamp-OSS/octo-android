package com.chat.base.msgitem;


import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.act.WKWebViewActivity;
import com.chat.base.emoji.EmojiManager;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.markdown.WKMarkwonProvider;
import com.chat.base.markdown.WKTableData;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msg.MentionEntityHelper;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.AlignImageSpan;
import com.chat.base.ui.components.NormalClickableContent;
import com.chat.base.ui.components.NormalClickableSpan;
import com.chat.base.utils.StringUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;

import kotlin.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2020-08-05 18:00
 * 消息列表item
 */
public class WKUIChatMsgItemEntity {
    public WKMsg wkMsg; // 本条消息对象
    public boolean showNickName = true; // 是否显示消息昵称
    public boolean isPlaying; // 语音是否在播放
    public boolean isChoose; // 是否选择消息
    public boolean isChecked; // 是否选中消息
    public boolean isShowTips; // 是否显示背景提示
    public WKMsg previousMsg; // 上一条消息
    public WKMsg nextMsg; // 下一条消息
    public boolean isUpdateStatus;
    public boolean isRefreshReaction;
    public boolean isRefreshAvatarAndName;
    public boolean isShowPinnedMessage;
    public int isPinned = 0;
    //=========本地数据========
    public ILinkClick iLinkClick;
    public SpannableStringBuilder displaySpans;
    public List<WKTableData> tableDataList = Collections.emptyList();

    public WKUIChatMsgItemEntity(IConversationContext conversationContext, WKMsg wkMsg, ILinkClick iLinkClick) {
        this.wkMsg = wkMsg;
        this.iLinkClick = iLinkClick;
        if (wkMsg != null) {
            try {
                // 从 mention.entities 补充 SDK 未解析的 mention entity
                MentionEntityHelper.mergeMentionEntities(wkMsg);
                formatSpans(conversationContext, wkMsg);
            } catch (Exception ignored) {
            }
        }

    }

    private String getContent() {
        String showContent = wkMsg.baseContentMsgModel.getDisplayContent();
        if (wkMsg.remoteExtra.contentEditMsgModel != null && !TextUtils.isEmpty(wkMsg.remoteExtra.contentEditMsgModel.getDisplayContent())) {
            showContent = wkMsg.remoteExtra.contentEditMsgModel.getDisplayContent();
        }
        return showContent;
    }

    public void formatSpans(IConversationContext conversationContext, WKMsg wkMsg) {
        if (wkMsg.type != WKContentType.WK_TEXT
                || wkMsg.baseContentMsgModel == null) {
            return;
        }

        String rawContent = getContent();
        Activity context = conversationContext.getChatActivity();

        // Markwon 渲染：将 Markdown 语法转为 Android Spans，同时提取表格数据
        Pair<Spanned, List<WKTableData>> result = WKMarkwonProvider.toMarkdownWithTables(context, rawContent);
        displaySpans = new SpannableStringBuilder(result.getFirst());
        tableDataList = result.getSecond();

        // 处理 entity spans（link、bot_command，使用文本搜索定位，因为 Markwon 渲染后原始 offset 已失效）
        if (WKReader.isNotEmpty(wkMsg.baseContentMsgModel.entities)) {
            for (WKMsgEntity entity : wkMsg.baseContentMsgModel.entities) {
                if ((entity.offset + entity.length) > rawContent.length() || entity.offset > rawContent.length())
                    continue;

                // 从原始文本提取 entity 内容
                String entityText = rawContent.substring(entity.offset, (entity.offset + entity.length));

                if (entity.type.equals(ChatContentSpanType.getLink())) {
                    // 在渲染后的文本中搜索定位
                    int startIdx = displaySpans.toString().indexOf(entityText);
                    if (startIdx < 0) continue;
                    int endIdx = startIdx + entityText.length();
                    String content = entityText;
                    NormalClickableContent.NormalClickableTypes types;
                    if (StringUtils.isMobile(content) || StringUtils.isEmail(content)) {
                        types = NormalClickableContent.NormalClickableTypes.Other;
                    } else types = NormalClickableContent.NormalClickableTypes.URL;

                    NormalClickableSpan clickableSpan = new NormalClickableSpan(true, ContextCompat.getColor(context, R.color.blue), new NormalClickableContent(types, content), view -> {

                        if (StringUtils.isMobile(content)) {
                            conversationContext.hideSoftKeyboard();
                            List<BottomSheetItem> list = new ArrayList<>();
                            list.add(new
                                            BottomSheetItem(
                                            context.getString(R.string.copy),
                                            R.mipmap.msg_copy, () -> {
                                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                        ClipData mClipData = ClipData.newPlainText("Label", content);
                                        assert cm != null;
                                        cm.setPrimaryClip(mClipData);
                                        WKToastUtils.getInstance().showToastNormal(context.getString(R.string.copyed));
                                    })
                            );
                            list.add(new BottomSheetItem(context.getString(R.string.call), R.mipmap.msg_calls, () -> {
                                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + content));
                                context.startActivity(intent);
                            }));
                            list.add(new BottomSheetItem(context.getString(R.string.add_to_phone_book), R.mipmap.msg_contacts, () -> {
                                Intent addIntent = new Intent(Intent.ACTION_INSERT, Uri.withAppendedPath(Uri.parse("content://com.android.contacts"), "contacts"));
                                addIntent.setType("vnd.android.cursor.dir/person");
                                addIntent.setType("vnd.android.cursor.dir/contact");
                                addIntent.setType("vnd.android.cursor.dir/raw_contact");
                                addIntent.putExtra(ContactsContract.Intents.Insert.NAME, "");
                                addIntent.putExtra(ContactsContract.Intents.Insert.PHONE, content);
                                context.startActivity(addIntent);
                            }));
                            list.add(new BottomSheetItem(context.getString(R.string.str_search), R.mipmap.ic_ab_search, () -> {
                                if (iLinkClick != null)
                                    iLinkClick.onShowSearchUser(content);
                            }));

                            SpannableStringBuilder displaySpans = new SpannableStringBuilder();
                            displaySpans.append(content);
                            displaySpans.setSpan(new
                                            StyleSpan(Typeface.BOLD), 0,
                                    content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            displaySpans.setSpan(new
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.blue)), 0,
                                    content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            WKDialogUtils.getInstance().showBottomSheet(context, displaySpans, false, list);
                            return;
                        }
                        if (StringUtils.isEmail(content)) {
                            conversationContext.hideSoftKeyboard();
                            List<BottomSheetItem> list = new ArrayList<>();
                            list.add(new BottomSheetItem(context.getString(R.string.copy), R.mipmap.msg_copy, () -> {

                                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData mClipData = ClipData.newPlainText("Label", content);
                                assert cm != null;
                                cm.setPrimaryClip(mClipData);
                                WKToastUtils.getInstance().showToastNormal(context.getString(R.string.copyed));

                            }));
                            list.add(new BottomSheetItem(context.getString(R.string.send_email), R.mipmap.msg2_email, () -> {

                                Uri uri = Uri.parse("mailto:" + content);
                                String[] email = {content};
                                Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
                                intent.putExtra(Intent.EXTRA_CC, email); // 抄送人
                                intent.putExtra(Intent.EXTRA_SUBJECT, ""); // 主题
                                intent.putExtra(Intent.EXTRA_TEXT, ""); // 正文
                                context.startActivity(Intent.createChooser(intent, ""));

                            }));
                            list.add(new BottomSheetItem(context.getString(R.string.str_search), R.mipmap.ic_ab_search, () -> {
                                if (iLinkClick != null)
                                    iLinkClick.onShowSearchUser(content);
                            }));
                            SpannableStringBuilder displaySpans = new SpannableStringBuilder();
                            displaySpans.append(content);
                            displaySpans.setSpan(new
                                            StyleSpan(Typeface.BOLD), 0,
                                    content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            displaySpans.setSpan(new
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.blue)), 0,
                                    content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                            WKDialogUtils.getInstance().showBottomSheet(context, displaySpans, false, list);
                            return;
                        }
                        Intent intent = new Intent(conversationContext.getChatActivity(), WKWebViewActivity.class);
                        intent.putExtra("url", content);
                        conversationContext.getChatActivity().startActivity(intent);
                    });
                    displaySpans.setSpan(new StyleSpan(Typeface.BOLD), startIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    displaySpans.setSpan(clickableSpan, startIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity.type.equals(ChatContentSpanType.getBotCommand())) {
                    int startIdx = displaySpans.toString().indexOf(entityText);
                    if (startIdx < 0) continue;
                    int endIdx = startIdx + entityText.length();
                    displaySpans.setSpan(new UnderlineSpan(), startIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        // 检查是否有 mention entity（由 MentionEntityHelper 从 mention.entities 合并而来）
        boolean hasMentionEntities = false;
        if (WKReader.isNotEmpty(wkMsg.baseContentMsgModel.entities)) {
            for (WKMsgEntity entity : wkMsg.baseContentMsgModel.entities) {
                if (entity.type.equals(ChatContentSpanType.getMention())) {
                    hasMentionEntities = true;
                    break;
                }
            }
        }
        // legacy mentionInfo.uids 高亮：仅在无 mention entity 时作为兼容旧消息的回退路径。
        // 有 mention entity 时由后续 entity-based 处理（使用 offset/length 精确定位，不依赖本地备注名匹配）。
        if (!hasMentionEntities
                && wkMsg.baseContentMsgModel.mentionInfo != null
                && WKReader.isNotEmpty(wkMsg.baseContentMsgModel.mentionInfo.uids)) {
            for (String uid : wkMsg.baseContentMsgModel.mentionInfo.uids) {
                String showName = "";
                WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(
                        conversationContext.getChatChannelInfo().channelID,
                        conversationContext.getChatChannelInfo().channelType, uid);
                if (member != null) {
                    showName = member.remark;
                    if (TextUtils.isEmpty(showName))
                        showName = TextUtils.isEmpty(member.memberRemark) ? member.memberName : member.memberRemark;
                }
                if (!TextUtils.isEmpty(showName)) {
                    showName = "@" + showName;
                    int index = displaySpans.toString().indexOf(showName);
                    if (index >= 0) {
                        String groupNo = "";
                        if (wkMsg.channelType == WKChannelType.GROUP) {
                            groupNo = wkMsg.channelID;
                        }
                        showName = showName + " ";
                        SpannableStringBuilder nameSpan = new SpannableStringBuilder();
                        nameSpan.append(showName);
                        nameSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, showName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        String finalGroupNo = groupNo;
                        String content = uid;
                        if (!TextUtils.isEmpty(groupNo)) content = content + "|" + groupNo;
                        nameSpan.setSpan(new NormalClickableSpan(false, Theme.colorAccount, new NormalClickableContent(NormalClickableContent.NormalClickableTypes.Remind, content), view -> {
                            if (iLinkClick != null) {
                                iLinkClick.onShowUserDetail(uid, finalGroupNo);
                            }
                        }), 0, showName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        displaySpans.replace(index, (index + showName.length()), nameSpan);
                    }
                }
            }
        }
        // URL 高亮（始终执行）
        {
            String displayText = displaySpans.toString();
            List<String> urls = StringUtils.getStrUrls(displayText);
            for (String url : urls) {
                int fromIndex = 0;
                while (fromIndex >= 0) {
                    fromIndex = displayText.indexOf(url, fromIndex);
                    if (fromIndex >= 0) {
                        NormalClickableSpan span = new NormalClickableSpan(false, ContextCompat.getColor(context, R.color.blue), new NormalClickableContent(NormalClickableContent.NormalClickableTypes.URL, url), view -> {
                            Intent intent = new Intent(conversationContext.getChatActivity(), WKWebViewActivity.class);
                            intent.putExtra("url", url);
                            conversationContext.getChatActivity().startActivity(intent);
                        });
                        displaySpans.setSpan(new StyleSpan(Typeface.BOLD), fromIndex, (fromIndex + url.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        displaySpans.setSpan(span, fromIndex, (fromIndex + url.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        fromIndex += url.length();
                    }
                }
            }
        }
        // @所有人 高亮（始终执行）
        if (wkMsg.baseContentMsgModel.mentionAll == 1) {
            String mentionAll = "@All";
            String mentionAll1 = "@所有人";
            String currentText = displaySpans.toString();
            int index = currentText.indexOf(mentionAll);
            if (index >= 0) {
                displaySpans.setSpan(new ForegroundColorSpan(Theme.colorAccount), index, (index + mentionAll.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                displaySpans.setSpan(new StyleSpan(Typeface.BOLD), index, (index + mentionAll.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int index1 = currentText.indexOf(mentionAll1);
            if (index1 >= 0) {
                displaySpans.setSpan(new ForegroundColorSpan(Theme.colorAccount), index1, (index1 + mentionAll1.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                displaySpans.setSpan(new StyleSpan(Typeface.BOLD), index1, (index1 + mentionAll1.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        // emoji：在渲染后文本中匹配
        String renderedText = displaySpans.toString();
        Matcher matcher = EmojiManager.getInstance().getPattern().matcher(renderedText);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String emoji = renderedText.substring(start, end);
            Drawable d = MoonUtil.getEmotDrawable(context, emoji, MoonUtil.DEF_SCALE);
            if (d != null) {
                AlignImageSpan span = new AlignImageSpan(d, AlignImageSpan.ALIGN_CENTER) {
                    @Override
                    public void onClick(View view) {

                    }
                };
                displaySpans.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        // 单独处理 @mention entity（使用文本搜索定位）
        if (WKReader.isNotEmpty(wkMsg.baseContentMsgModel.entities)) {
            for (WKMsgEntity entity : wkMsg.baseContentMsgModel.entities) {
                if (entity.type.equals(ChatContentSpanType.getMention())) {
                    String uid = entity.value;
                    String showName = "";

                    String displayContent = rawContent;
                    if (entity.offset > displayContent.length() || (entity.offset + entity.length) > displayContent.length()) {
                        continue;
                    }
                    String oldName = displayContent.substring(entity.offset, (entity.offset + entity.length));

                    // 在渲染后文本中搜索 oldName 的位置
                    int start = displaySpans.toString().indexOf(oldName);
                    if (start < 0) continue;
                    int end = start + oldName.length();

                    if (uid.equals("-1")) {
                        showName = displaySpans.subSequence(start, end).toString();
                    }
                    WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
                    if (channel != null) {
                        showName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
                    }
                    boolean isUserDetail = !TextUtils.isEmpty(uid) && !uid.equals("-1");
                    if (!TextUtils.isEmpty(showName)) {
                        if (!showName.startsWith("@"))
                            showName = "@" + showName;
                    } else {
                        showName = displaySpans.subSequence(start, end).toString();
                    }
                    showName = showName + " ";
                    SpannableStringBuilder nameSpan = new SpannableStringBuilder();
                    nameSpan.append(showName);
                    nameSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, showName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (isUserDetail && iLinkClick != null) {
                        String groupNo = "";
                        if (wkMsg.channelType == WKChannelType.GROUP) {
                            groupNo = wkMsg.channelID;
                        }
                        String content = entity.value;
                        if (!TextUtils.isEmpty(groupNo)) content = content + "|" + groupNo;
                        String finalGroupNo = groupNo;
                        nameSpan.setSpan(new NormalClickableSpan(false, Theme.colorAccount, new NormalClickableContent(NormalClickableContent.NormalClickableTypes.Remind, content), view -> iLinkClick.onShowUserDetail(entity.value, finalGroupNo)), 0, showName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        nameSpan.setSpan(new ForegroundColorSpan(Theme.colorAccount), 0, showName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    displaySpans.replace(start, end, nameSpan);
                }
            }
        }
        // 自动检测 @mention：对没有 entity 标记的 @xxx 文本，匹配群成员/联系人（与 iOS detectMentionsInText 一致）
        if (wkMsg.channelType == WKChannelType.GROUP && iLinkClick != null) {
            detectAndApplyMentions(conversationContext, wkMsg);
        }
    }

    /**
     * 自动检测文本中的 @mention，匹配群成员或联系人，添加可点击 span。
     * 仅处理没有被 entity mention 覆盖的 @xxx 文本。
     */
    private void detectAndApplyMentions(IConversationContext conversationContext, WKMsg wkMsg) {
        String text = displaySpans.toString();
        Pattern pattern = Pattern.compile("@(\\S+)");
        Matcher m = pattern.matcher(text);
        List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager()
                .getMembers(wkMsg.channelID, wkMsg.channelType);

        // 收集已有 mention span 的范围，避免重复
        NormalClickableSpan[] existingSpans = displaySpans.getSpans(0, displaySpans.length(), NormalClickableSpan.class);

        while (m.find()) {
            int start = m.start();
            int end = m.end();

            // 检查此范围是否已有 clickable span（被 entity mention 覆盖）
            boolean overlaps = false;
            for (NormalClickableSpan span : existingSpans) {
                int spanStart = displaySpans.getSpanStart(span);
                int spanEnd = displaySpans.getSpanEnd(span);
                if (start < spanEnd && spanStart < end) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) continue;

            String mentionName = m.group(1);
            String matchedUID = null;

            // 在群成员中匹配
            if (WKReader.isNotEmpty(members)) {
                for (WKChannelMember member : members) {
                    if (mentionName.equals(member.memberName) || mentionName.equals(member.memberRemark) || mentionName.equals(member.memberUID)) {
                        matchedUID = member.memberUID;
                        break;
                    }
                    // 尝试匹配联系人备注
                    WKChannel ch = WKIM.getInstance().getChannelManager().getChannel(member.memberUID, WKChannelType.PERSONAL);
                    if (ch != null && (mentionName.equals(ch.channelName) || mentionName.equals(ch.channelRemark))) {
                        matchedUID = member.memberUID;
                        break;
                    }
                }
            }

            if (matchedUID != null) {
                String groupNo = wkMsg.channelID;
                String finalUID = matchedUID;
                displaySpans.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                String clickContent = matchedUID + "|" + groupNo;
                displaySpans.setSpan(new NormalClickableSpan(false, Theme.colorAccount,
                        new NormalClickableContent(NormalClickableContent.NormalClickableTypes.Remind, clickContent),
                        view -> iLinkClick.onShowUserDetail(finalUID, groupNo)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public interface ILinkClick {
        void onShowUserDetail(String uid, String groupNo);

        void onShowSearchUser(String phone);
    }
}
