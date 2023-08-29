package com.chat.uikit.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKSystemAccount;
import com.chat.base.glide.GlideUtils;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.ImageUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.msgmodel.WKCardContent;
import com.lxj.xpopup.core.CenterPopupView;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * 2020-09-25 17:58
 * 聊天确认弹框
 */
@SuppressLint("ViewConstructor")
public class ChatConfirmDialogView extends CenterPopupView {
    private final List<WKChannel> list;
    List<WKMessageContent> messageContentList;
    private final Context context;
    private final IChatConfirmListener iChatConfirmListener;

    public ChatConfirmDialogView(@NonNull Context context, @NonNull List<WKChannel> list, @NonNull List<WKMessageContent> messageContent, @NonNull IChatConfirmListener iChatConfirmListener) {
        super(context);
        this.context = context;
        this.list = list;
        this.messageContentList = messageContent;
        this.iChatConfirmListener = iChatConfirmListener;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        AvatarView avatarView = findViewById(R.id.avatarView);
        TextView nameTv = findViewById(R.id.nameTv);
        ImageView imageView = findViewById(R.id.imageView);
        TextView contentTv = findViewById(R.id.contentTv);
        if (list.size() == 1) {
            avatarView.showAvatar(list.get(0));
            String showName = list.get(0).channelRemark;
            if (TextUtils.isEmpty(showName)) showName = list.get(0).channelName;
            if (list.get(0).channelID.equals(WKSystemAccount.system_file_helper)) {
                showName = context.getString(R.string.wk_file_helper);
            }
            if (list.get(0).channelID.equals(WKSystemAccount.system_team)) {
                showName = context.getString(R.string.wk_system_notice);
            }
            nameTv.setText(showName);
            recyclerView.setVisibility(GONE);
            avatarView.setVisibility(VISIBLE);
            nameTv.setVisibility(VISIBLE);
        } else {
            ChatConfirmAdapter adapter = new ChatConfirmAdapter(list);
            recyclerView.setLayoutManager(new GridLayoutManager(context, 5));
            recyclerView.setAdapter(adapter);
            nameTv.setVisibility(GONE);
            avatarView.setVisibility(GONE);
            contentTv.setVisibility(GONE);
        }

        if (messageContentList.size() == 1) {
            WKMessageContent messageContent = messageContentList.get(0);
            if (messageContent.type == WKContentType.WK_IMAGE) {
                WKImageContent imgMsgModel = (WKImageContent) messageContent;
                ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                int[] ints = ImageUtils.getInstance().getImageWidthAndHeightToTalk(imgMsgModel.width, imgMsgModel.height);
                layoutParams.height = ints[1];
                layoutParams.width = ints[0];
                imageView.setLayoutParams(layoutParams);
                String showUrl;
                if (!TextUtils.isEmpty(imgMsgModel.localPath)) {
                    showUrl = imgMsgModel.localPath;
                    File file = new File(showUrl);
                    if (!file.exists()) {
                        //如果本地文件被删除就显示网络图片
                        showUrl = WKApiConfig.getShowUrl(imgMsgModel.url);
                    }
                } else {
                    showUrl = WKApiConfig.getShowUrl(imgMsgModel.url);
                }
                GlideUtils.getInstance().showImg(context, showUrl, ints[0], ints[1], imageView);
                imageView.setVisibility(VISIBLE);
                contentTv.setVisibility(GONE);
            }
//            else if (messageContent.type == WKContentType.WK_EMOJI_STICKER || messageContent.type == WKContentType.WK_VECTOR_STICKER) {
//                contentTv.setVisibility(GONE);
//                imageView.setVisibility(GONE);
//                JSONObject jsonObject = messageContent.encodeMsg();
//                if (jsonObject != null) {
//                    String url = jsonObject.optString("url");
//                    String placeholder = jsonObject.optString("placeholder");
//                    stickerView.showSticker(url, placeholder, messageContent.type == WKContentType.WK_EMOJI_STICKER ? AndroidUtilities.dp(100) : AndroidUtilities.dp(180), messageContent.type == WKContentType.WK_EMOJI_STICKER ? AndroidUtilities.dp(100) : AndroidUtilities.dp(180), true);
//                }
//            }
            else {
                String content = messageContent.getDisplayContent();
                if (messageContent.type == WKContentType.WK_CARD) {
                    WKCardContent WKCardContent = (WKCardContent) messageContent;
                    content = content + WKCardContent.name;
                }
                contentTv.setText(content);
                imageView.setVisibility(GONE);
                contentTv.setVisibility(VISIBLE);
            }
        } else {
            imageView.setVisibility(GONE);
            contentTv.setVisibility(VISIBLE);
            contentTv.setText(String.format(context.getString(R.string.item_forward_count), messageContentList.size()));
        }
        findViewById(R.id.cancelTv).setOnClickListener(view -> dismiss());
        findViewById(R.id.sureTv).setOnClickListener(view -> {
            dismiss();
            iChatConfirmListener.onClick(list, messageContentList);
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.chat_confirm_dialog_view;
    }

    private static class ChatConfirmAdapter extends BaseQuickAdapter<WKChannel, BaseViewHolder> {

        ChatConfirmAdapter(@Nullable List<WKChannel> data) {
            super(R.layout.item_all_members_layout, data);
        }

        @Override
        protected void convert(@NotNull BaseViewHolder baseViewHolder, WKChannel channel) {
            AvatarView avatarView = baseViewHolder.getView(R.id.avatarView);
            avatarView.showAvatar(channel);
        }
    }

    public interface IChatConfirmListener {
        void onClick(List<WKChannel> list, List<WKMessageContent> messageContentList);
    }

}
