package com.chat.login.ui;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.HanziToPinyin;
import com.chat.base.utils.WKReader;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.ui.Theme;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.sidebar.listener.OnQuickSideBarTouchListener;
import com.chat.login.ChooseCountryCodeAdapter;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.PyUtils;
import com.chat.login.R;
import com.chat.login.databinding.ActChooseAreaCodeLayoutBinding;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2020-08-03 21:24
 * 选择区号
 */
public class ChooseAreaCodeActivity extends WKBaseActivity<ActChooseAreaCodeLayoutBinding> implements LoginContract.LoginView, OnQuickSideBarTouchListener {
    private boolean setResult = false;
    private LoginPresenter loginPresenter;
    private ChooseCountryCodeAdapter adapter;
    private List<CountryCodeEntity> allList;

    @Override
    protected ActChooseAreaCodeLayoutBinding getViewBinding() {
        return ActChooseAreaCodeLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.choose_country);
    }

    @Override
    protected void initPresenter() {
        loginPresenter = new LoginPresenter(this);
    }

    @Override
    protected void initView() {
        setResult = getIntent().getBooleanExtra("set_result", false);
        wkVBinding.quickSideBarTipsView.setBackgroundColor(Theme.colorAccount);
        wkVBinding.quickSideBarView.setTextChooseColor(Theme.colorAccount);
        loginPresenter.getCountryCode();
        adapter = new ChooseCountryCodeAdapter();
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        wkVBinding.quickSideBarView.setOnQuickSideBarTouchListener(this);
        adapter.setOnItemClickListener((adapter1, view1, position) -> SingleClickUtil.determineTriggerSingleClick(view1, view2 -> {
            CountryCodeEntity entity = (CountryCodeEntity) adapter1.getItem(position);
            if (setResult) {
                EndpointManager.getInstance().invoke("set_choose_area_code", entity.code);
                finish();
            } else {
                if (entity != null) {
                    Intent intent = new Intent();
                    intent.putExtra("entity", entity);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        }));


        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchCountry(editable.toString());
            }
        });
    }

    private void searchCountry(String content) {
        if (TextUtils.isEmpty(content)) {
            adapter.setList(allList);
            return;
        }

        List<CountryCodeEntity> tempList = new ArrayList<>();
        for (int i = 0, size = allList.size(); i < size; i++) {
            if ((!TextUtils.isEmpty(allList.get(i).name) && allList.get(i).name.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || (!TextUtils.isEmpty(allList.get(i).pying) && allList.get(i).pying.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))) {
                tempList.add(allList.get(i));
            }
        }
        adapter.setList(tempList);
    }

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {

    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {

        for (int i = 0, size = list.size(); i < size; i++) {
            String showName = list.get(i).name;
            if (!TextUtils.isEmpty(showName)) {
                list.get(i).pying = HanziToPinyin.getInstance().getPY(showName);
            } else list.get(i).pying = "#";

        }
        PyUtils.getInstance().sortListBasic(list);
        allList = list;
        adapter.setList(list);
    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {

    }

    @Override
    public void setLoginFail(int code, String uid, String phone) {

    }

    @Override
    public void setSendCodeResult(int code, String msg) {

    }

    @Override
    public void setResetPwdResult(int code, String msg) {

    }

    @Override
    public Button getVerfiCodeBtn() {
        return null;
    }

    @Override
    public EditText getNameEt() {
        return null;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void showError(String msg) {

    }

    @Override
    public void hideLoading() {

    }

    @Override
    public void onLetterChanged(String letter, int position, float y) {
        wkVBinding.quickSideBarTipsView.setText(letter, position, y);
        //有此key则获取位置并滚动到该位置
        List<CountryCodeEntity> list = adapter.getData();
        if (WKReader.isNotEmpty(list)) {
            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).pying.startsWith(letter)) {
                    wkVBinding.recyclerView.smoothScrollToPosition(i);
                    break;
                }
            }
        }
    }

    @Override
    public void onLetterTouching(boolean touching) {
        wkVBinding.quickSideBarTipsView.setVisibility(touching ? View.VISIBLE : View.INVISIBLE);
    }
}
