package com.chat.uikit.chat.search.file;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.chat.base.entity.GlobalMessage;
import com.chat.base.entity.GlobalSearchReq;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.search.GlobalSearchModel;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.views.pinnedsectionitemdecoration.PinnedHeaderItemDecoration;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActSearchMsgFileLayoutBinding;
import com.google.android.material.snackbar.Snackbar;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import com.chat.base.msgcontent.WKFileContent;
import com.chat.base.net.HttpResponseCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchWithFileActivity extends WKBaseActivity<ActSearchMsgFileLayoutBinding> {
    private String channelID;
    private byte channelType;
    private SearchWithFileAdapter adapter;
    private int page = 1;
    private String searchKeyword = "";

    @Override
    protected ActSearchMsgFileLayoutBinding getViewBinding() {
        return ActSearchMsgFileLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.uikit_search_for_file);
    }

    @Override
    protected void initPresenter() {
        channelID = getIntent().getStringExtra("channel_id");
        channelType = getIntent().getByteExtra("channel_type", WKChannelType.PERSONAL);
    }

    @Override
    protected void initView() {
        PinnedHeaderItemDecoration headerDecoration = new PinnedHeaderItemDecoration.Builder(
                SearchFileEntity.TYPE_DATE_HEADER).enableDivider(false).create();
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchWithFileAdapter(new SearchWithFileAdapter.IClick() {
            @Override
            public void onClick(SearchFileEntity entity) {
                showInChat(entity.message);
            }

            @Override
            public void onForward(SearchFileEntity entity) {
                forward(entity);
            }
        });
        wkVBinding.recyclerView.setAdapter(adapter);
        wkVBinding.recyclerView.addItemDecoration(headerDecoration);
    }

    @Override
    protected void initListener() {
        getData();

        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                page++;
                getData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
            }
        });

        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
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
                searchKeyword = editable.toString().trim();
                page = 1;
                adapter.setList(new ArrayList<>());
                getData();
            }
        });
    }

    private void showInChat(GlobalMessage msg) {
        long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(
                msg.getMessage_seq(),
                msg.getChannel().getChannel_id(),
                msg.getChannel().getChannel_type()
        );
        EndpointManager.getInstance().invoke(EndpointSID.chatView,
                new ChatViewMenu(this, channelID, channelType, orderSeq, false));
    }

    private void forward(SearchFileEntity entity) {
        WKMessageContent content = entity.message.getMessageModel();
        if (content == null) return;

        EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView,
                new ChooseChatMenu(new ChatChooseContacts(list -> {
                    if (WKReader.isNotEmpty(list)) {
                        for (WKChannel channel : list) {
                            WKIM.getInstance().getMsgManager().send(content, channel);
                        }
                        ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content).getRootView();
                        Snackbar.make(viewGroup, getString(R.string.is_forward), 1000)
                                .setAction("", v -> {})
                                .show();
                    }
                }), content));
    }

    private void getData() {
        // 本地搜索：按类型查出所有文件消息
        List<SearchFileEntity> localFileEntities = new ArrayList<>();
        Set<Long> seenSeqs = new HashSet<>();

        if (page == 1) {
            List<WKMsg> localMsgs = WKIM.getInstance().getMsgManager()
                    .searchMsgWithChannelAndContentTypes(channelID, channelType, 0, 200, new int[]{WKContentType.WK_FILE});
            if (WKReader.isNotEmpty(localMsgs)) {
                for (WKMsg msg : localMsgs) {
                    if (msg.baseContentMsgModel instanceof WKFileContent fileContent) {
                        // 按文件名过滤
                        if (!TextUtils.isEmpty(searchKeyword) && fileContent.name != null
                                && !fileContent.name.toLowerCase().contains(searchKeyword.toLowerCase())) {
                            continue;
                        }
                        if (!seenSeqs.add((long) msg.messageSeq)) continue;

                        String date = WKTimeUtils.getInstance().time2YearMonth(msg.timestamp * 1000);
                        addDateHeaderIfNeeded(localFileEntities, date);

                        SearchFileEntity entity = new SearchFileEntity();
                        entity.date = date;
                        entity.itemType = SearchFileEntity.TYPE_FILE;
                        entity.fileName = fileContent.name != null ? fileContent.name : "";
                        entity.extension = fileContent.extension != null ? fileContent.extension : "";
                        entity.fileSize = fileContent.size;

                        GlobalMessage gm = new GlobalMessage();
                        gm.setMessage_seq(msg.messageSeq);
                        gm.setFrom_uid(msg.fromUID != null ? msg.fromUID : "");
                        gm.setTimestamp(msg.timestamp);
                        gm.setClient_msg_no(msg.clientMsgNO != null ? msg.clientMsgNO : "");
                        com.chat.base.entity.GlobalChannel gc = new com.chat.base.entity.GlobalChannel();
                        gc.setChannel_id(channelID);
                        gc.setChannel_type(channelType);
                        gm.setChannel(gc);
                        java.util.HashMap<String, Object> payloadMap = new java.util.HashMap<>();
                        payloadMap.put("type", msg.type);
                        payloadMap.put("name", entity.fileName);
                        payloadMap.put("extension", entity.extension);
                        payloadMap.put("size", entity.fileSize);
                        gm.setPayload(payloadMap);
                        entity.message = gm;

                        localFileEntities.add(entity);
                    }
                }
            }
        }

        // API 搜索
        ArrayList<Integer> contentType = new ArrayList<>();
        contentType.add(WKContentType.WK_FILE);
        GlobalSearchReq req = new GlobalSearchReq(1, searchKeyword, channelID, channelType,
                "", "", contentType, page, 20, 0, 0);
        GlobalSearchModel.INSTANCE.search(req, (code, s, globalSearch) -> {
            wkVBinding.refreshLayout.finishLoadMore();
            wkVBinding.refreshLayout.finishRefresh();

            List<SearchFileEntity> merged = new ArrayList<>(localFileEntities);

            if (code == HttpResponseCode.success && globalSearch != null && WKReader.isNotEmpty(globalSearch.messages)) {
                for (GlobalMessage msg : globalSearch.messages) {
                    if (!seenSeqs.add(msg.getMessage_seq())) continue;

                    WKMessageContent content = msg.getMessageModel();
                    if (content == null) continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) msg.getPayload();
                    if (payload == null) continue;

                    String date = WKTimeUtils.getInstance().time2YearMonth(msg.getTimestamp() * 1000);
                    addDateHeaderIfNeeded(merged, date);

                    SearchFileEntity entity = new SearchFileEntity();
                    entity.date = date;
                    entity.itemType = SearchFileEntity.TYPE_FILE;
                    entity.message = msg;
                    entity.fileName = payload.get("name") instanceof String ? (String) payload.get("name") : "";
                    entity.extension = payload.get("extension") instanceof String ? (String) payload.get("extension") : "";
                    Object sizeObj = payload.get("size");
                    entity.fileSize = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : 0;
                    merged.add(entity);
                }
            }

            if (merged.isEmpty()) {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
                if (page == 1) {
                    wkVBinding.refreshLayout.setEnableLoadMore(false);
                    wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                    adapter.setList(new ArrayList<>());
                }
            } else {
                wkVBinding.nodataTv.setVisibility(View.GONE);
                if (page == 1) {
                    adapter.setList(merged);
                } else {
                    adapter.addData(merged);
                }
            }
            return null;
        });
    }

    private void addDateHeaderIfNeeded(List<SearchFileEntity> list, String date) {
        if (WKReader.isNotEmpty(list)) {
            if (!list.get(list.size() - 1).date.equals(date)) {
                SearchFileEntity header = new SearchFileEntity();
                header.date = date;
                header.itemType = SearchFileEntity.TYPE_DATE_HEADER;
                list.add(header);
            }
        } else {
            SearchFileEntity header = new SearchFileEntity();
            header.date = date;
            header.itemType = SearchFileEntity.TYPE_DATE_HEADER;
            list.add(header);
        }
    }
}
