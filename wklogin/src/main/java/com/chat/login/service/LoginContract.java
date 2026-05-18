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
    public interface LoginPresenter extends WKBasePresenter {
        void login(String name, String pwd);

        void sendLoginAuthVerificationCode(String uid);

        void getCountryCode();

        void registerCode(String zone, String phone);

        void forgetPwd(String zone, String phone);

        void registerApp(String code, String zone, String name, String phone, String password,String inviteCode);

        void checkLoginAuth(String uid, String code);

        void resetPwd(String zone, String phone, String code, String pwd);

        void emailLogin(String email, String pwd);

        void emailRegister(String email, String code, String name, String pwd, String inviteCode);

        void emailSendCode(String email, int codeType);

        void emailForgetPwd(String email, String code, String pwd);
    }

    public interface LoginView extends WKBaseView {
        void loginResult(UserInfoEntity userInfoEntity);

        void setCountryCode(List<CountryCodeEntity> list);

        void setRegisterCodeSuccess(int code, String msg, int exist);

        void setLoginFail(int code, String uid, String phone);

        void setSendCodeResult(int code, String msg);

        void setResetPwdResult(int code, String msg);

        void setEmailSendCodeResult(int code, String msg);

        Button getVerificationCodeBtn();

        EditText getNameEt();

        Context getContext();
    }
}
