package com.xinbida.tsdd.demo

import android.content.Intent
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.chat.base.WKBaseApplication
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.login.ui.PerfectUserInfoActivity
import com.chat.login.ui.ThirdLoginActivity
import com.chat.uikit.TabActivity
import com.xinbida.tsdd.demo.databinding.ActivityMainBinding
import com.xinbida.wukongim.WKIM

class MainActivity : WKBaseActivity<ActivityMainBinding>() {

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        val isShowDialog: Boolean =
            WKSharedPreferencesUtil.getInstance().getBoolean("show_agreement_dialog")
        if (isShowDialog) {
            showDialog()
        } else gotoApp()
    }

    private fun gotoApp() {
        if (!TextUtils.isEmpty(WKConfig.getInstance().token)) {
            if (TextUtils.isEmpty(WKConfig.getInstance().userInfo.name)) {
                startActivity(Intent(this@MainActivity, PerfectUserInfoActivity::class.java))
            } else {
                val publicRSAKey: String =
                    WKIM.getInstance().cmdManager.rsaPublicKey
                if (TextUtils.isEmpty(publicRSAKey)) {
                    val intent = Intent(this@MainActivity, ThirdLoginActivity::class.java)
                    intent.putExtra("from", getIntent().getIntExtra("from", 0))
                    startActivity(intent)
//                    val intent = Intent(this@MainActivity, WKLoginActivity::class.java)
//                    intent.putExtra("from", getIntent().getIntExtra("from", 0))
//                    startActivity(intent)
                } else {
                    startActivity(Intent(this@MainActivity, TabActivity::class.java))
                }
            }
        } else {
//            val intent = Intent(this@MainActivity, WKLoginActivity::class.java)
//            intent.putExtra("from", getIntent().getIntExtra("from", 0))
//            startActivity(intent)
            val intent = Intent(this@MainActivity, ThirdLoginActivity::class.java)
            intent.putExtra("from", getIntent().getIntExtra("from", 0))
            startActivity(intent)
        }
        finish()
    }

    private fun showDialog() {
        val view: View =
            LayoutInflater.from(this).inflate(R.layout.privacy_agreement_dialog_view, null)
        val builder = AlertDialog.Builder(this, R.style.AlertDialog)
        builder.setCancelable(false)
        val alertDialog = builder.create()
        alertDialog.show()
        alertDialog.setContentView(view)
        val window = alertDialog.window
        val param = window!!.attributes
        param.width = AndroidUtilities.getScreenWidth() / 5 * 4
        param.height = AndroidUtilities.getScreenHeight() / 3 * 2
        window.attributes = param
        val contentTv = view.findViewById<TextView>(R.id.contentTv)
        contentTv.movementMethod = LinkMovementMethod.getInstance()
        val content = getString(R.string.dialog_content)
        val linkSpan = SpannableStringBuilder()
        linkSpan.append(content)
        val userAgreementIndex = content.indexOf(getString(R.string.main_user_agreement))
        linkSpan.setSpan(
            NormalClickableSpan(
                true,
                ContextCompat.getColor(this, R.color.blue),
                NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                object : NormalClickableSpan.IClick {
                    override fun onClick(view: View) {
                        showWebView(
                            WKApiConfig.baseWebUrl + "user_agreement.html"
                        )
                    }
                }), userAgreementIndex, userAgreementIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val privacyPolicyIndex = content.indexOf(getString(R.string.main_privacy_policy))
        linkSpan.setSpan(
            NormalClickableSpan(true,
                ContextCompat.getColor(this, R.color.blue),
                NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                object : NormalClickableSpan.IClick {
                    override fun onClick(view: View) {
                        WKApiConfig.baseWebUrl + "privacy_policy.html"
                    }
                }), privacyPolicyIndex, privacyPolicyIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        SingleClickUtil.onSingleClick(view.findViewById(R.id.disagreeBtn)) {
            alertDialog.dismiss()
            finish()
        }
        SingleClickUtil.onSingleClick(view.findViewById(R.id.agreeBtn)) {
            alertDialog.dismiss()
            //checkPermissions();
            WKSharedPreferencesUtil.getInstance().putBoolean("show_agreement_dialog", false)
            WKBaseApplication.getInstance().init(
                WKBaseApplication.getInstance().packageName,
                WKBaseApplication.getInstance().application
            )
            gotoApp()
        }
        contentTv.text = linkSpan
    }
}
