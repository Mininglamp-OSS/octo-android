package com.dmwork.im

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chat.base.config.WKConfig
import com.chat.base.net.HttpResponseCode
import com.chat.uikit.R
import com.chat.uikit.user.service.UserModel

/**
 * YUJ-361 (#227) · OCTO 实名认证 `dmwork://verified` 回落处理。
 *
 * CAS 验证完成后 verify-service 会 302 到 `dmwork://verified?...`；系统会按 manifest
 * 注册的 intent-filter 把 intent 投递到这个透明 Activity。我们职责极简：
 *
 * 1. 拉一次 `GET /v1/user/current`，把 `realname_verified / realname /
 *    realname_verified_at` 同步到 [WKConfig] 本地缓存；
 * 2. 弹 toast「实名认证已完成」并 `finish()`，回到任务栈上一层的 SettingActivity/
 *    MyInfoActivity/MyFragment —— 它们的 onResume 会自然按新状态重绘 ✓ 勾/标签。
 *
 * <p>刻意不继承 WKBaseActivity：不需要 title bar，也不需要膨胀任何布局，避免
 * 闪一下白屏。使用 `Theme.Translucent.NoTitleBar` 让它完全透明。
 *
 * <p>未登录兜底：如果 token 为空（少见但可能：用户在浏览器里完成验证时 App 被杀），
 * 直接路由回 [MainActivity] 走登录流程，不发 refresh 请求。
 */
class VerifyLandingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = WKConfig.getInstance().token
        if (token.isNullOrEmpty()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            return
        }
        UserModel.getInstance().refreshCurrentUser { code, _, _ ->
            // Activity 生命周期守卫：noHistory Activity 可能在异步请求飞行中被回收，
            // 在 destroyed context 上 Toast.makeText(this, ...) 在部分 ROM 上会抛 WindowManager token 失效异常。
            if (isFinishing || isDestroyed) return@refreshCurrentUser
            if (code.toInt() == HttpResponseCode.success.toInt()) {
                Toast.makeText(
                    this,
                    getString(R.string.realname_verify_refresh_success),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // 不论成功失败都 finish；失败场景下用户仍能在 SettingActivity 里
            // 看到「去认证」按钮，下次点击时会再拉一次 verify-token 重试。
            finish()
        }
    }
}
