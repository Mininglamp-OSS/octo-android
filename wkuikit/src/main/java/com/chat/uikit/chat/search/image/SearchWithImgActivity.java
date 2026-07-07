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

package com.chat.uikit.chat.search.image;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.chat.base.entity.GlobalChannel;
import com.chat.base.entity.GlobalMessage;
import com.chat.base.entity.ImagePopupBottomSheetItem;
import com.chat.base.foldable.PaneMetrics;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.search.channel.ChannelSearchModel;
import com.chat.base.search.channel.Rfc3339;
import com.chat.base.search.channel.dto.ChannelSearchReq;
import com.chat.base.search.channel.dto.MediaHit;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.views.CustomImageViewerPopup;
import com.chat.base.views.FullyGridLayoutManager;
import com.chat.base.views.pinnedsectionitemdecoration.PinnedHeaderItemDecoration;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActSearchMsgImgLayoutBinding;
import com.google.android.material.snackbar.Snackbar;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 3/23/21 10:07 AM
 * 搜索聊天图片
 */
public class SearchWithImgActivity extends WKBaseActivity<ActSearchMsgImgLayoutBinding> {
    private String channelID;
    private byte channelType;
    private SearchWithImgAdapter adapter;
    private String nextCursor = null;
    private boolean hasMore = true;
    private boolean loading = false;

    @Override
    protected ActSearchMsgImgLayoutBinding getViewBinding() {
        return ActSearchMsgImgLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.uikit_search_for_image);
    }

    @Override
    protected void initPresenter() {
        channelID = getIntent().getStringExtra("channel_id");
        channelType = getIntent().getByteExtra("channel_type", WKChannelType.PERSONAL);
    }

    @Override
    protected void initView() {
        PinnedHeaderItemDecoration mHeaderItemDecoration = new PinnedHeaderItemDecoration.Builder(1).enableDivider(false).create();
        // : image grid cell sizes against current pane, not full device width.
        int wH = (PaneMetrics.widthPx(this) - AndroidUtilities.dp(6)) / 4;
        FullyGridLayoutManager layoutManager = new FullyGridLayoutManager(this, 4);
        wkVBinding.recyclerView.setLayoutManager(layoutManager);
        adapter = new SearchWithImgAdapter(wH, new SearchWithImgAdapter.ICLick() {
            @Override
            public void onClick(SearchImgEntity entity) {
                showInChat(entity.message);
            }

            @Override
            public void onForward(SearchImgEntity entity) {
                forward(entity);
            }
        });
        wkVBinding.recyclerView.setAdapter(adapter);
        wkVBinding.recyclerView.addItemDecoration(mHeaderItemDecoration);
    }

    @Override
    protected void initListener() {
        getData();

        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                getData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {

            }
        });
        adapter.addChildClickViewIds(R.id.imageView);
        adapter.setOnItemChildClickListener((adapter1, view1, position) -> {
            SearchImgEntity entity = (SearchImgEntity) adapter1.getData().get(position);
            if (entity == null || entity.getItemType() != 0) return;
            // 视频跳过大图浏览器（图片专用），直接跳到聊天页查看原视频；
            // 没有 server 封面时大图也只是空白，体验更差。
            if (entity.originalContent instanceof WKVideoContent) {
                showInChat(entity.message);
            } else {
                showImg(entity.url, (ImageView) view1);
            }
        });
    }

    private void showImg(String uri, ImageView imageView) {
        //查看大图
        List<Object> tempImgList = new ArrayList<>();
        List<Object> urlList = new ArrayList<>();
        List<ImageView> imgList = new ArrayList<>();
        for (int i = 0, size = adapter.getData().size(); i < size; i++) {
            SearchImgEntity item = adapter.getData().get(i);
            // 大图浏览器只承载图片；视频跳过（视频走 jump-to-chat 路径）。
            if (item.getItemType() != 0 || !(item.originalContent instanceof WKImageContent)) continue;
            tempImgList.add(item);
            urlList.add(item.url);
            ImageView imageView1 = (ImageView) adapter.getViewByPosition(i, R.id.imageView);
            imgList.add(imageView1);
        }
        int index = 0;
        for (int i = 0; i < tempImgList.size(); i++) {
            SearchImgEntity entity = (SearchImgEntity) tempImgList.get(i);
            if (entity.url.equals(uri)) {
                index = i;
                break;
            }
        }

        List<ImagePopupBottomSheetItem> bottomEntityArrayList = new ArrayList<>();
        bottomEntityArrayList.add(new ImagePopupBottomSheetItem(getString(R.string.forward), R.mipmap.msg_forward, position -> {
            SearchImgEntity entity = (SearchImgEntity) tempImgList.get(position);
            if (entity == null || entity.originalContent == null) return;
            forward(entity);
        }));
        bottomEntityArrayList.add(new ImagePopupBottomSheetItem(getString(R.string.uikit_go_to_chat_item), R.mipmap.msg_message, position -> {
            SearchImgEntity entity = (SearchImgEntity) tempImgList.get(position);
            showInChat(entity.message);
        }));
        WKDialogUtils.getInstance().showImagePopup(this, urlList, imgList, imageView, index, bottomEntityArrayList, new CustomImageViewerPopup.IImgPopupMenu() {
            @Override
            public void onForward(int position) {
            }

            @Override
            public void onFavorite(int position) {
                SearchImgEntity entity = (SearchImgEntity) tempImgList.get(position);
                if (entity == null) return;
                // 切到 API 后本地 DB 可能没有这条消息（这正是本页换接口的初衷）：
                //  - 先按 server message_id 查本地，命中则走原 WKMsg 路径（带 from channel 完整信息）；
                //  - 未命中时由 MediaHit 重建的 WKImageContent + entity.message 元数据兜底，仅图片支持。
                WKMsg localMsg = WKIM.getInstance().getMsgManager().getWithMessageID(entity.message.message_idstr);
                if (localMsg != null && localMsg.baseContentMsgModel instanceof WKImageContent) {
                    collect(localMsg);
                } else if (entity.originalContent instanceof WKImageContent) {
                    collectFromEntity(entity, (WKImageContent) entity.originalContent);
                }
            }

            @Override
            public void onShowInChat(int position) {
                SearchImgEntity entity = (SearchImgEntity) tempImgList.get(position);
                showInChat(entity.message);
            }
        }, null);

    }

    private void showInChat(GlobalMessage msg) {
        long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(
                msg.getMessage_seq(),
                channelID,
                channelType
        );
        EndpointManager.getInstance().invoke(EndpointSID.chatView, new ChatViewMenu(SearchWithImgActivity.this, channelID, channelType, orderSeq, false));
    }


    private void collect(WKMsg msg) {
        JSONObject jsonObject = new JSONObject();
        WKImageContent msgModel = (WKImageContent) msg.baseContentMsgModel;
        jsonObject.put("content", WKApiConfig.getShowUrl(msgModel.url));
        jsonObject.put("width", msgModel.width);
        jsonObject.put("height", msgModel.height);
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("type", msg.type);
        String unique_key = msg.messageID;
        if (TextUtils.isEmpty(unique_key))
            unique_key = msg.clientMsgNO;
        hashMap.put("unique_key", unique_key);
        if (msg.getFrom() != null) {
            hashMap.put("author_uid", msg.getFrom().channelID);
            hashMap.put("author_name", msg.getFrom().channelName);
        }
        hashMap.put("payload", jsonObject);
        hashMap.put("activity", this);
        EndpointManager.getInstance().invoke("favorite_add", hashMap);
    }

    /** 本地 DB 没这条 msg 时，直接用 MediaHit 重建的 [entity] 凑出 favorite_add 需要的字段。 */
    private void collectFromEntity(SearchImgEntity entity, WKImageContent img) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("content", WKApiConfig.getShowUrl(img.url));
        jsonObject.put("width", img.width);
        jsonObject.put("height", img.height);
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("type", WKContentType.WK_IMAGE);
        String uniqueKey = !TextUtils.isEmpty(entity.message.message_idstr)
                ? entity.message.message_idstr
                : "";
        hashMap.put("unique_key", uniqueKey);
        hashMap.put("author_uid", entity.message.from_uid != null ? entity.message.from_uid : "");
        hashMap.put("author_name", entity.senderName != null ? entity.senderName : "");
        hashMap.put("payload", jsonObject);
        hashMap.put("activity", this);
        EndpointManager.getInstance().invoke("favorite_add", hashMap);
    }

    private void forward(SearchImgEntity entity) {
        WKMessageContent finalWKMessageContent = entity.originalContent;
        if (finalWKMessageContent == null) {
            return;
        }
        // 视频特别处理：MediaHit 只有 thumb_url（封面），没有真视频 URL；如果直接把合成的
        // WKVideoContent（url=封面）转发出去，对方收到的"视频"会无法播放。
        // 兜底：先按 server message_id 查本地 WKMsg，命中就用本地完整内容；查不到提示用户去聊天页操作。
        if (finalWKMessageContent instanceof WKVideoContent) {
            WKMsg localMsg = WKIM.getInstance().getMsgManager().getWithMessageID(entity.message.message_idstr);
            if (localMsg != null && localMsg.baseContentMsgModel instanceof WKVideoContent) {
                finalWKMessageContent = localMsg.baseContentMsgModel;
            } else {
                WKToastUtils.getInstance().showToast(getString(R.string.uikit_video_forward_unavailable));
                return;
            }
        }
        final WKMessageContent contentToSend = finalWKMessageContent;
        EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView, new ChooseChatMenu(new ChatChooseContacts(list1 -> {
            if (WKReader.isNotEmpty(list1)) {
                for (WKChannel channel : list1) {
                    WKIM.getInstance().getMsgManager().send(contentToSend, channel);
                }
                ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content).getRootView();
                Snackbar.make(viewGroup, getString(R.string.is_forward), 1000)
                        .setAction("", v1 -> {
                        })
                        .show();
            }
        }), contentToSend));
    }

    private void getData() {
        if (loading || !hasMore) {
            wkVBinding.refreshLayout.finishLoadMore();
            if (!hasMore) {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
            }
            return;
        }
        loading = true;
        // 频道内媒体走 /_search_media。keyword 必须为空（Model 内部强制），cursor 由服务端签发。
        // 子区直接复用：传入 channelID 即对应子区频道，无需额外逻辑。
        ChannelSearchReq req = new ChannelSearchReq(
                channelType,
                channelID,
                null,
                null,
                ChannelSearchReq.SORT_TIME_DESC,
                ChannelSearchReq.DEFAULT_PAGE_SIZE,
                nextCursor
        );
        final boolean isReset = (nextCursor == null);
        ChannelSearchModel.INSTANCE.searchMedia(req, outcome -> {
            loading = false;
            wkVBinding.refreshLayout.finishLoadMore();
            wkVBinding.refreshLayout.finishRefresh();

            if (!outcome.getOk() || outcome.getData() == null) {
                if (isReset && adapter.getData().isEmpty()) {
                    wkVBinding.refreshLayout.setEnableLoadMore(false);
                    wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                }
                return null;
            }

            List<MediaHit> hits = outcome.getData().getData();
            List<SearchImgEntity> page = new ArrayList<>();
            if (WKReader.isNotEmpty(hits)) {
                for (MediaHit hit : hits) {
                    SearchImgEntity entity = toEntity(hit);
                    if (entity == null) continue;
                    addDateHeaderIfNeeded(page, entity.date);
                    page.add(entity);
                }
            }

            hasMore = outcome.getData().getPagination().getHas_more();
            nextCursor = hasMore ? outcome.getData().getPagination().getNext_cursor() : null;

            if (isReset) {
                if (page.isEmpty()) {
                    wkVBinding.refreshLayout.setEnableLoadMore(false);
                    wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                } else {
                    wkVBinding.nodataTv.setVisibility(View.GONE);
                    adapter.setList(page);
                }
            } else if (!page.isEmpty()) {
                wkVBinding.nodataTv.setVisibility(View.GONE);
                adapter.addData(page);
            }

            if (!hasMore) {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
            }
            return null;
        });
    }

    private SearchImgEntity toEntity(MediaHit hit) {
        String thumb = hit.getThumb_url();
        // 视频可能没有 server 端生成的封面（thumb_url 缺失），此时仍要保留条目：
        // 列表里会显示 play 角标 + 灰底占位，点击跳到聊天页查看原视频，与 4-tab 媒体页对齐。
        String showUrl = !TextUtils.isEmpty(thumb) ? WKApiConfig.getShowUrl(thumb) : "";

        SearchImgEntity entity = new SearchImgEntity();
        entity.url = showUrl;
        entity.date = !TextUtils.isEmpty(hit.getMonth_bucket()) ? hit.getMonth_bucket() : "";
        entity.originalContent = buildContent(hit);
        entity.senderName = hit.getSender_name() != null ? hit.getSender_name() : "";

        GlobalMessage gm = new GlobalMessage();
        gm.setMessage_seq(hit.getMessage_seq());
        gm.setFrom_uid(hit.getSender_id() != null ? hit.getSender_id() : "");
        gm.setTimestamp(Rfc3339.INSTANCE.toEpochSeconds(hit.getSent_at()));
        gm.setClient_msg_no("");
        gm.setMessage_idstr(hit.getMessage_id() != null ? hit.getMessage_id() : "");
        GlobalChannel gc = new GlobalChannel();
        gc.setChannel_id(channelID);
        gc.setChannel_type(channelType);
        gm.setChannel(gc);
        HashMap<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("type", hit.isVideo() ? 5 : 2);
        gm.setPayload(payloadMap);
        entity.message = gm;
        return entity;
    }

    private WKMessageContent buildContent(MediaHit hit) {
        if (hit.isVideo()) {
            WKVideoContent video = new WKVideoContent();
            video.cover = hit.getThumb_url();
            video.width = hit.getWidth();
            video.height = hit.getHeight();
            video.second = hit.getDuration_ms() / 1000;
            // url 字段对视频指向视频文件本身；MediaHit 仅返回封面缩略图，转发时若需要原视频
            // 需调用方进一步通过 messageID 拉取——这里先放封面，保证基础展示不崩。
            video.url = hit.getThumb_url();
            return video;
        }
        WKImageContent img = new WKImageContent();
        img.url = hit.getThumb_url();
        img.width = hit.getWidth();
        img.height = hit.getHeight();
        return img;
    }

    private void addDateHeaderIfNeeded(List<SearchImgEntity> list, String date) {
        if (TextUtils.isEmpty(date)) return;
        String lastDate = null;
        if (WKReader.isNotEmpty(list)) {
            lastDate = list.get(list.size() - 1).date;
        } else if (WKReader.isNotEmpty(adapter.getData())) {
            lastDate = adapter.getData().get(adapter.getData().size() - 1).date;
        }
        if (!date.equals(lastDate)) {
            SearchImgEntity header = new SearchImgEntity();
            header.date = date;
            header.itemType = 1;
            list.add(header);
        }
    }
}
