package com.chat.uikit.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.chat.base.config.WKBinder
import com.chat.base.foldable.NarrowTransition
import com.chat.uikit.TabActivity

/**
 * YUJ-298 · 窄屏会话 Activity 复用导航（Fix A · 真正不 recreate 版）。
 *
 * ## 背景
 * PR#195 (YUJ-276) 用 120ms 快过渡（Fix D）缓解了窄屏 ChatActivity 打开/返回
 * 的感知延迟，但根因没解：**窄屏每次点击都在 recreate ChatActivity** —
 * XML inflate ≈ 80-200ms + PanelSwitchHelper.Builder ≈ 50-100ms + DB read
 * ≈ 20-80ms 叠起来仍然 200-400ms。Fix A（onNewIntent 复用）是 YUJ-267 分屏
 * 场景已用过的策略，这里补课把它同时应用到窄屏。
 *
 * ## 策略
 * ### 进入（[launchChat]）
 * 在 Intent 上加 `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP`。
 *
 * - 如果任务栈里已经有一个 [ChatActivity] 实例（被 [goBackToList] 留在那里），
 *   AMS 会先把它 reorder 到栈顶，然后按 singleTop 语义派发 `onNewIntent` —
 *   ChatActivity 里 YUJ-267 的 per-channel detach / persist / reset / attach
 *   流程自然接管，切换成新频道只要 ~50-100ms。
 * - 如果任务栈里没有 ChatActivity（首次打开 / logout 后），AMS 会走常规路径
 *   新建实例，`singleTop` manifest + `onCreate` 里的 `NarrowTransition.applyFastOpen`
 *   和现状一致，**不回退**。
 *
 * ### 返回（[goBackToList]）
 * 不直接调 [Activity.finish]。改为把 [TabActivity] 用 `FLAG_ACTIVITY_REORDER_TO_FRONT`
 * 重新带到栈顶 —— ChatActivity 被压到 [Activity.onStop]，实例保活，per-channel
 * 监听在 onPause / onStop 做的本来就是 pause（listener 还在），下一次 [launchChat]
 * 会直接命中 onNewIntent 热路径。
 *
 * 如果 ChatActivity 自己要真·退出（退群 / 账号切换 / 频道不存在 / logout），
 * 业务侧继续走原有 [Activity.finish] 路径，本类不拦截。
 *
 * ## 取舍
 * - 实例保活期间占用的内存：一个 ChatActivity ≈ 10-30 MB。用户离开聊天超过
 *   系统内存压力阈值时 AMS 会把实例回收，回收后相当于退回冷启路径 —— 这是
 *   系统托管的 soft cache，没有泄漏风险。
 * - 分屏态不受影响：`isNarrow` 严格判定手机窄屏（见 [NarrowTransition.isNarrow]），
 *   平板 / 折叠展开态走 Activity Embedding 副栏 —— Embedding 本身就保证了
 *   onNewIntent 复用，不需要额外 flag。
 */
object ChatReuseNavigator {

    private const val TAG = "YUJ298-reuse"

    /**
     * 把所有「点击会话 / 子区卡片 / 搜索结果 → 打开 ChatActivity」的 startActivity
     * 路径统一到这里。调用方只管组装 Intent（channelId / channelType / tipsOrderSeq
     * 等 extras），剩下的 flag 组合 + 动画策略由本方法决定。
     *
     * @param activity 发起方 Activity（可以为 null，此时退化为 [Context.startActivity]
     *                 且不尝试复用，因为 reorder-to-front 依赖同 task stack）。
     * @param intent   目标 ChatActivity Intent，extras 必须已经填好。
     */
    @JvmStatic
    @JvmOverloads
    fun launchChat(context: Context, intent: Intent, activity: Activity? = asActivity(context)) {
        val narrow = activity?.let { NarrowTransition.isNarrow(it) } ?: false
        if (narrow) {
            // YUJ-305 P1-B 防御：上游入口（通知构造 / Bot 跳转 / 外部 deeplink）若带了
            // FLAG_ACTIVITY_CLEAR_TOP，Android 会忽略同一 Intent 上的
            // FLAG_ACTIVITY_REORDER_TO_FRONT（见 Intent javadoc：
            // "This flag is ignored if FLAG_ACTIVITY_CLEAR_TOP is also specified."），
            // 导致 Fix A 复用路径失效，又掉回 recreate。同理 NEW_TASK 会开新 task，
            // ChatActivity 跨 task 无法复用。这里统一剥掉，保证 REORDER_TO_FRONT 生效；
            // 调用方若确实需要清栈 / 开 task 的语义，应走非窄屏路径或独立入口。
            val toClear = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if ((intent.flags and toClear) != 0) {
                intent.flags = intent.flags and toClear.inv()
                if (WKBinder.isDebug) {
                    Log.d(TAG, "launchChat narrow stripped CLEAR_TOP|NEW_TASK to keep REORDER_TO_FRONT semantics")
                }
            }
            // REORDER_TO_FRONT + SINGLE_TOP：任务栈里有实例就 reorder 并走
            // onNewIntent；没有就正常新建。两种情况都是单一 startActivity 调用，
            // 系统负责选路。
            intent.addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            if (WKBinder.isDebug) {
                Log.d(TAG, "launchChat narrow flags=REORDER|SINGLE_TOP channel=" +
                        intent.getStringExtra("channelId"))
            }
        }
        if (WKBinder.isDebug) {
            Log.d(
                "YUJ276-trace",
                "[T_START_ACTIVITY_VIA_NAVIGATOR] ts=" + SystemClock.uptimeMillis() +
                        " narrow=" + narrow +
                        " channel=" + intent.getStringExtra("channelId")
            )
        }
        context.startActivity(intent)
    }

    /**
     * 窄屏下的「回到会话列表」。把 TabActivity reorder 到栈顶，把当前 ChatActivity
     * 压成背景态但不 destroy —— 下一次 [launchChat] 命中 onNewIntent 热路径。
     *
     * 分屏态不需要也不应该走这里：分屏态 ChatActivity 本来就常驻副栏，back 键由
     * Embedding 自己处理。
     *
     * @return true 表示命中窄屏复用路径（调用方应短路 finish）；false 表示未命中，
     *               调用方继续走原有 finish 逻辑。
     */
    @JvmStatic
    fun goBackToList(activity: Activity): Boolean {
        if (!NarrowTransition.isNarrow(activity)) return false
        return try {
            val intent = Intent(activity, TabActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            activity.startActivity(intent)
            // TabActivity 已经 reorder 到栈顶，当前 ChatActivity 被压到 onStop。
            //
            // YUJ-317 · 这条路径在 AMS 眼里是 OPEN，不是 CLOSE —— [applyFastOpen]
            // 预注册的 OVERRIDE_TRANSITION_CLOSE 不会生效。以前这里调
            // [NarrowTransition.applyFastClose]，在 pre-34 靠 overridePendingTransition
            // 兜底、在 API 34+ 直接早返回 no-op，导致 Android 14+ 设备点左上角返回
            // 仍然是默认 PUSH 过渡（新页右滑入 + 旧页左滑出），用户反馈「像又打开了
            // 一个新页面」。改用 applyFastPopViaStartActivity：无论 SDK 版本都显式
            // overridePendingTransition，让 TabActivity 从左滑入 + ChatActivity 向
            // 右滑出，视觉对齐企微 / iOS pop。
            NarrowTransition.applyFastPopViaStartActivity(activity)
            if (WKBinder.isDebug) {
                Log.d(TAG, "goBackToList reorder TabActivity to front, ChatActivity kept alive")
                Log.d(
                    "YUJ276-trace",
                    "[T_REUSE_BACK] ts=" + SystemClock.uptimeMillis() +
                            " kept=ChatActivity"
                )
            }
            true
        } catch (t: Throwable) {
            if (WKBinder.isDebug) Log.w(TAG, "goBackToList failed, fallback to finish: $t")
            false
        }
    }

    private fun asActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is Activity) return c
            val next = c.baseContext
            if (next === c) break
            c = next
        }
        return null
    }
}
