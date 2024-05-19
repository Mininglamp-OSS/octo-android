package com.chat.uikit.search;

import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ActSearchUserLayoutBinding;
import com.chat.uikit.search.service.SearchContract;
import com.chat.uikit.search.service.SearchUserPresenter;
import com.chat.uikit.user.UserDetailActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 2019-11-20 10:57
 * 搜索联系人
 */
public class SearchUserActivity extends WKBaseActivity<ActSearchUserLayoutBinding> implements SearchContract.SearchUserView {

    private SearchUserAdapter searchUserAdapter;
    private SearchUserPresenter presenter;

    @Override
    protected ActSearchUserLayoutBinding getViewBinding() {
        return ActSearchUserLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
    }

    @Override
    protected void initPresenter() {
        presenter = new SearchUserPresenter(this);
    }

    @Override
    protected void initView() {
        wkVBinding.searchBtn.getBackground().setTint(Theme.colorAccount);
        Theme.setColorFilter(this, wkVBinding.backIv, R.color.colorDark);
        Theme.setPressedBackground(wkVBinding.backLayout);
        searchUserAdapter = new SearchUserAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, searchUserAdapter);
        SoftKeyboardUtils.getInstance().showSoftKeyBoard(SearchUserActivity.this, wkVBinding.searchEt);
        String phone = getIntent().getStringExtra("phone");
        if (!TextUtils.isEmpty(phone)) {
            wkVBinding.searchEt.setText(phone);
            wkVBinding.searchEt.setSelection(phone.length());
            wkVBinding.searchBtn.setEnabled(true);
            wkVBinding.searchBtn.setAlpha(1f);
        }
    }

    @Override
    protected void initListener() {
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String content = s.toString();
                if (!TextUtils.isEmpty(content)) {
                    wkVBinding.searchBtn.setEnabled(true);
                    wkVBinding.searchBtn.setAlpha(1f);
                } else {
                    wkVBinding.searchBtn.setEnabled(false);
                    wkVBinding.searchBtn.setAlpha(0.2f);
                }
            }
        });
        searchUserAdapter.addChildClickViewIds(R.id.applyBtn);
        searchUserAdapter.addChildClickViewIds(R.id.avatarIv);
        searchUserAdapter.setOnItemChildClickListener((adapter, view1, position) -> {
            SearchUserEntity searchUserEntity = (SearchUserEntity) adapter.getItem(position);
            if (searchUserEntity != null) {
                if (view1.getId() == R.id.applyBtn) {
                    WKDialogUtils.getInstance().showInputDialog(SearchUserActivity.this, getString(R.string.apply), getString(R.string.input_remark), "", getString(R.string.input_remark), 20, text -> FriendModel.getInstance().applyAddFriend(searchUserEntity.data.uid, searchUserEntity.data.vercode, text, (code, msg) -> {
                        if (code == HttpResponseCode.success) {
                            searchUserEntity.status = 1;
                            adapter.notifyItemChanged(position);
                        } else showToast(msg);
                    }));
                } else if (view1.getId() == R.id.avatarIv) {
                    SoftKeyboardUtils.getInstance().hideSoftKeyboard(SearchUserActivity.this);
                    Intent intent = new Intent(this, UserDetailActivity.class);
                    intent.putExtra("uid", searchUserEntity.data.uid);
                    intent.putExtra("vercode", searchUserEntity.data.vercode);
                    startActivity(intent);
                }
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.searchBtn, v -> presenter.searchUser(Objects.requireNonNull(wkVBinding.searchEt.getText()).toString()));
        wkVBinding.backLayout.setOnClickListener(v -> finish());
    }

    @Override
    public void setSearchUser(SearchUserEntity searchUser) {
        List<SearchUserEntity> list = new ArrayList<>();
        if (searchUser != null && searchUser.exist == 1) {
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(searchUser.data.uid, WKChannelType.PERSONAL);
            //已是好友不显示加好友
            if (channel != null && channel.follow == 1 && channel.isDeleted == 0) {
                searchUser.showApply = false;
            }
            list.add(searchUser);
        } else {
            SearchUserEntity searchUserEntity = new SearchUserEntity();
            searchUserEntity.itemType = 1;
            list.add(searchUserEntity);
        }
        searchUserAdapter.setList(list);
    }

    @Override
    public void showError(String msg) {

    }

    @Override
    public void hideLoading() {

    }

    @Override
    protected void initData() {
        super.initData();
        if (getIntent().hasExtra("searchKey")) {
            String searchKey = getIntent().getStringExtra("searchKey");
            if (!TextUtils.isEmpty(searchKey)) {
                wkVBinding.searchEt.setText(searchKey);
                wkVBinding.searchEt.setSelection(searchKey.length());
                presenter.searchUser(searchKey);
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.searchEt);
    }
}
