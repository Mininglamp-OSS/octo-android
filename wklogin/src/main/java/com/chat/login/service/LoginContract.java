package com.chat.login.service;


import android.content.Context;
import android.widget.Button;
import android.widget.EditText;

import com.chat.base.base.WKBasePresenter;
import com.chat.base.base.WKBaseView;
import com.chat.base.entity.UserInfoEntity;
import com.chat.login.entity.CountryCodeEntity;

import java.util.List;

/**
 * 2019-11-19 17:44
 * 登录
 */
public class LoginContract {
    public interface LoginPersenter extends WKBasePresenter {
        void login(String name, String pwd);

        void sendLoginAuthVerifCode(String uid);

        void getCountryCode();

        void registerCode(String zone, String phone);

        void forgetPwd(String zone, String phone);

        void registerApp(String code, String zone, String name, String phone, String password,String inviteCode);

        void checkLoginAuth(String uid, String code);

        void resetPwd(String zooe, String phone, String code, String pwd);
    }

    public interface LoginView extends WKBaseView {
        void loginResult(UserInfoEntity userInfoEntity);

        void setCountryCode(List<CountryCodeEntity> list);

        void setRegisterCodeSuccess(int code, String msg, int exist);

        void setLoginFail(int code, String uid, String phone);

        void setSendCodeResult(int code, String msg);

        void setResetPwdResult(int code, String msg);

        Button getVerfiCodeBtn();

        EditText getNameEt();

        Context getContext();
    }
}
