package com.chat.login.ui

import android.content.Intent
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.chat.base.act.WKWebViewActivity
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.LoginMenu
import com.chat.base.net.HttpResponseCode
import com.chat.login.R
import com.chat.login.databinding.ActThirdLoginLayoutBinding
import com.chat.login.service.LoginModel


class ThirdLoginActivity : WKBaseActivity<ActThirdLoginLayoutBinding>() {
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var authCode: String
    override fun getViewBinding(): ActThirdLoginLayoutBinding {
        return ActThirdLoginLayoutBinding.inflate(layoutInflater)
    }

    override fun initView() {
        wkVBinding.loginTitleTv.text =
            String.format(getString(R.string.login_title), getString(R.string.app_name))
    }

    override fun initListener() {
        wkVBinding.loginTitleTv.setOnLongClickListener {
            val intent = Intent(this@ThirdLoginActivity, WKLoginActivity::class.java)
            startActivity(intent)
            true
        }
        wkVBinding.giteeIV.setOnClickListener {
            getAuthCode("gitee")
        }
        wkVBinding.githubIV.setOnClickListener {
            getAuthCode("github")
        }

        countDownTimer = object : CountDownTimer(1000 * 60 * 10, 1000) {
            override fun onTick(l: Long) {
                getAuthCodeStatus()
            }

            //时间段内最后一次定时任务
            override fun onFinish() {
            }
        }
    }

    private fun getAuthCodeStatus() {
        LoginModel.getInstance().getAuthCodeStatus(
            authCode
        ) { code, _ ->
            if (code == HttpResponseCode.success.toInt()) {
                countDownTimer.cancel()
                runOnUiThread {
                    Handler(Looper.myLooper()!!).postDelayed({
                        val list = EndpointManager.getInstance()
                            .invokes<LoginMenu>(EndpointCategory.loginMenus, null)
                        if (list != null && list.size > 0) {
                            for (menu in list) {
                                if (menu.iMenuClick != null) menu.iMenuClick.onClick()
                            }
                        }
                        finish()
                    }, 200)
                }
            }
        }
    }

    private fun getAuthCode(type: String) {
        loadingPopup.show()
        loadingPopup.setTitle(getString(R.string.logging_in))
        LoginModel.getInstance().getAuthCode { code, msg, authCode ->
            loadingPopup.dismiss()
            if (code == HttpResponseCode.success.toInt()) {
                if (!TextUtils.isEmpty(authCode)) {
                    openWeb(type, authCode!!)
                }
            } else {
                showToast(msg)
            }
        }
    }

    private fun openWeb(type: String, authCode: String) {
        this.authCode = authCode
        val intent = Intent(this, WKWebViewActivity::class.java)
        intent.putExtra("url", WKApiConfig.baseUrl + "user/$type?authcode=$authCode")
        startActivity(intent)
        countDownTimer.start()
    }


}