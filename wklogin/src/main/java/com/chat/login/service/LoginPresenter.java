package com.chat.login.service;


import com.chat.base.net.HttpResponseCode;
import com.chat.login.R;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.ResourceObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 2019-11-19 17:47
 * 登录
 */
public class LoginPresenter implements LoginContract.LoginPersenter {
    private final WeakReference<LoginContract.LoginView> loginView;
    private final int totalTime = 60;

    public LoginPresenter(LoginContract.LoginView loginView) {
        this.loginView = new WeakReference<>(loginView);
    }

    @Override
    public void login(String name, String pwd) {
        LoginModel.getInstance().loginApp(name, pwd, (code, msg, userInfoEntity) -> {
            if (code == HttpResponseCode.success) {
                if (loginView.get() != null) loginView.get().loginResult(userInfoEntity);
            } else {
                if (loginView.get() != null) {
                    loginView.get().hideLoading();
                    if (code == 110) {
                        loginView.get().setLoginFail(code, userInfoEntity.uid, userInfoEntity.phone);
                    } else {
                        loginView.get().showError(msg);
                    }
                }
            }

        });
    }

    @Override
    public void sendLoginAuthVerifCode(String uid) {
        LoginModel.getInstance().sendLoginAuthVerifCode(uid, (code, msg) -> {
            if (loginView.get() != null) {
                loginView.get().setSendCodeResult(code, msg);
            }
        });
    }

    @Override
    public void getCountryCode() {
        LoginModel.getInstance().getCountries((code, msg, list) -> {
            if (code == HttpResponseCode.success) {
                if (loginView.get() != null) loginView.get().setCountryCode(list);
            } else {
                if (loginView.get() != null) {
                    loginView.get().hideLoading();
                    loginView.get().showError(msg);
                }
            }
        });
    }

    @Override
    public void registerCode(String zone, String phone) {
        LoginModel.getInstance().registerCode(zone, phone, (code, msg, exist) -> {
            loginView.get().hideLoading();
            if (code == HttpResponseCode.success) {
                if (loginView.get() != null)
                    loginView.get().setRegisterCodeSuccess(code, msg, exist);
            }else {

            }
        });
    }

    @Override
    public void forgetPwd(String zone, String phone) {
        LoginModel.getInstance().forgetPwd(zone, phone, (code, msg, exist) -> {
            loginView.get().hideLoading();
            if (loginView.get() != null)
                loginView.get().setSendCodeResult(code, msg);
        });
    }

    @Override
    public void registerApp(String code, String zone, String name, String phone, String password,String inviteCode) {
        LoginModel.getInstance().registerApp(code, zone, name, phone, password,inviteCode, (code1, errorMsg, userInfoEntity) -> {
            if (code1 == HttpResponseCode.success) {
                if (loginView.get() != null) loginView.get().loginResult(userInfoEntity);
            } else {
                if (loginView.get() != null) {
                    loginView.get().hideLoading();
                    loginView.get().showError(errorMsg);
                }
            }
        });
    }

    @Override
    public void checkLoginAuth(String uid, String code) {
        LoginModel.getInstance().checkLoginAuth(uid, code, (code1, errorMsg, userInfo) -> {
            if (code1 == HttpResponseCode.success) {
                if (loginView.get() != null) loginView.get().loginResult(userInfo);
            } else {
                if (loginView.get() != null) {
                    loginView.get().hideLoading();
                    loginView.get().showError(errorMsg);

                }
            }
        });
    }

    @Override
    public void resetPwd(String zooe, String phone, String code, String pwd) {
        LoginModel.getInstance().resetPwd(zooe, phone, code, pwd, (code1, errorMsg) -> {
            if (code1 == HttpResponseCode.success) {
                if (loginView.get() != null) loginView.get().setResetPwdResult(code1, errorMsg);
            } else {
                if (loginView.get() != null) {
                    loginView.get().hideLoading();
                    loginView.get().showError(errorMsg);

                }
            }
        });
    }

    @Override
    public void showLoading() {

    }

    public void startTimer() {
        Observable.interval(0, 1, TimeUnit.SECONDS)
                .take(totalTime + 1)
                .map(takeValue -> totalTime - takeValue)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ResourceObserver<Long>() {
                    @Override
                    public void onComplete() {
                        if (loginView.get() != null && loginView.get().getVerfiCodeBtn() != null) {
                            loginView.get().getVerfiCodeBtn().setEnabled(true);
                            loginView.get().getVerfiCodeBtn().setAlpha(1f);
                            loginView.get().getVerfiCodeBtn().setText(R.string.get_verf_code);
                            loginView.get().getNameEt().setEnabled(true);
                            loginView.get().getVerfiCodeBtn().setAlpha(1);
                        }
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                    }

                    @Override
                    public void onNext(@NotNull Long value) {
                        if (loginView.get() != null && loginView.get().getVerfiCodeBtn() != null) {
                            loginView.get().getVerfiCodeBtn().setEnabled(false);
                            loginView.get().getVerfiCodeBtn().setAlpha(0.2f);
                            loginView.get().getNameEt().setEnabled(true);
                            loginView.get().getVerfiCodeBtn().setText(String.format("%s%s s", loginView.get().getContext().getString(R.string.recapture), value));
                        }
                    }
                });
    }
}
