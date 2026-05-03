package com.chat.base.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.chat.base.WKBaseApplication;
import com.chat.base.utils.AppExecutors;


/**
 * 2019-11-13 10:30
 * 临时缓存数据
 *
 * <p>YUJ-284 (P-01) — 所有 {@code putXxx} 写入路径从 {@link SharedPreferences.Editor#commit()}
 * 迁至 {@link SharedPreferences.Editor#apply()}：{@code commit()} 同步写盘，叠加
 * {@link EncryptedSharedPreferences} 的 AES-GCM + KeyStore 每次写入 50-150ms，
 * 冷启动 / 前后台切换 / 退出聊天等热路径主线程多次命中。全仓无调用点消费
 * {@code commit()} 的返回值，改 {@code apply()} 零语义差异。
 *
 * <p>YUJ-284 冷启预热：首次访问 {@link EncryptedSharedPreferences} 需要同步
 * 完成 Android KeyStore AES256-GCM MasterKey 握手，耗时 50-150ms 且阻塞
 * 首个调用线程。{@link #prewarm()} 在 {@code WKBaseApplication.init} 早期
 * 投递到 {@link com.chat.base.utils.AppExecutors#io() AppExecutors.io()}
 * 强制构造单例并预读主线程冷启路径上的高频 Key，
 * 将 KeyStore 初始化与首批磁盘读取都搬出主线程。
 *
 * <p>YUJ-294 merge-time hotfix：原实现 {@code new Thread(...).start()} 与
 * YUJ-283 P-11 的 {@code scripts/check-no-new-thread.sh} guard 冲突，现统一到
 * {@code AppExecutors.io()} 池（2×CPU、daemon、app-io-N 命名、priority NORM-1）。
 */
public class WKSharedPreferencesUtil {

    // 创建一个写入器
    private final SharedPreferences mPreferences;
    private final SharedPreferences.Editor mEditor;

    @SuppressLint("CommitPrefEdits")
    private WKSharedPreferencesUtil(Context context) {
        String mTAG = "wkSharedPreferences";
        SharedPreferences prefs;
        SharedPreferences.Editor editor;
        try {
            // Use EncryptedSharedPreferences to prevent plaintext storage of tokens and passwords
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context,
                    mTAG,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback to regular SharedPreferences to avoid crashes, but still keep data scoped to the app
            prefs = context.getSharedPreferences(mTAG, Context.MODE_PRIVATE);
        }
        mPreferences = prefs;
        editor = mPreferences.edit();
        mEditor = editor;
    }

//    private static class SharedPreferencesUtilBinder {
//        private static final WKSharedPreferencesUtil sharedPreferencesUtil = new WKSharedPreferencesUtil(WKBaseApplication.getInstance().getContext());
//    }

    enum SingletonEnum {
        INSTANCE;
        private final WKSharedPreferencesUtil util;

        SingletonEnum() {
            util = new WKSharedPreferencesUtil(WKBaseApplication.getInstance().application);
        }

        public WKSharedPreferencesUtil getInstance() {
            return util;
        }
    }


    public static WKSharedPreferencesUtil getInstance() {
        return SingletonEnum.INSTANCE.getInstance();
    }

    /**
     * YUJ-284 P-01 · 冷启预热。
     *
     * <p>在后台线程上强制 {@link SingletonEnum} 初始化，触发
     * {@link EncryptedSharedPreferences#create} → MasterKey AES256-GCM →
     * Android KeyStore 握手（首次 50-150ms）。随后预读冷启动主线程必定命中的
     * 几个高频 Key（{@code show_agreement_dialog} 等），让 in-process 缓存
     * 提前成型，首次 {@code getBoolean / getSP} 调用直接命中内存。
     *
     * <p>线程安全：Java enum 单例初始化由 JVM 保证，再多次调用
     * {@code prewarm()} 也是幂等的 —— 仅第一次真正工作，后续立即返回。
     * 若主线程抢先触达 {@code getInstance()}，主线程自己承担 KeyStore 握手，
     * 语义等价于原来的懒加载，没有回归风险。
     *
     * <p>调用时机：必须在 {@link WKBaseApplication#init(String,
     * android.app.Application) WKBaseApplication.init} 内把 {@code application}
     * 赋值之后调用（{@link SingletonEnum} 构造需要它）。
     */
    public static void prewarm() {
        // YUJ-294 · merge-time hotfix：PR#201 (YUJ-284 P-01) 的 `new Thread()` 与
        // PR#202 (YUJ-283 P-11) 在 develop 上撞车。prewarm 语义 = EncryptedSP 单例
        // 构造 + KeyStore 握手 + 首批磁盘预读，是典型 I/O，直接走 AppExecutors.io()
        // （2×CPU 池、app-io-N 命名、priority NORM-1），与其它 I/O 预热同调度。
        AppExecutors.io().execute(() -> {
            try {
                // 强制触发 SingletonEnum 初始化：EncryptedSharedPreferences +
                // MasterKey.Builder + KeyStore 握手全部搬到此后台线程。
                WKSharedPreferencesUtil util = SingletonEnum.INSTANCE.getInstance();
                SharedPreferences p = util.mPreferences;
                // 冷启动主线程已知的高频 Key — 预读把底层文件映射到内存，
                // 避免首次 getBoolean / getString 触发 disk page-in。
                p.getBoolean("show_agreement_dialog", false);
                p.getString("wk_uid", "");
                p.getString("wk_token", "");
                p.getString("wk_theme_pref", null);
                p.getFloat("font_scale", 1f);
                p.getInt("save_language", 0);
            } catch (Throwable ignored) {
                // 防御：prewarm 失败绝不 crash —— 主线程首次访问时会走
                // 原有懒加载 + KeyStore fallback 路径，行为与改动前一致。
            }
        });
    }

    public void putSPWithUID(String key, String value) {
        this.putSP(WKConfig.getInstance().getUid() + "_" + key, value);
    }

    public String getSPWithUID(String key) {
        return getSP(WKConfig.getInstance().getUid() + "_" + key);
    }

    // 存入数据
    public void putSP(String key, String value) {
        mEditor.putString(key, value);
        mEditor.apply();
    }

    /**
     * YUJ-310 · 同步落盘写入（仅用于写完立即 {@code Runtime.exit(0)} / 杀进程
     * 的极少数路径，例如登录页隐藏入口切换 API base URL 后的 App 重启）。
     *
     * <p>{@link #putSP(String, String)} 走 {@code apply()} 异步落盘，是 YUJ-284
     * (P-01, PR#201) 的性能优化；但对于「写 SP → 立即 {@code Runtime.exit(0)}」
     * 这条同步 call chain，{@code apply()} 的 QueuedWork 还没 flush 进程已被杀，
     * 冷启动读回旧值，用户表现为切换到正式服后重启仍是测试服（YUJ-310 P0）。
     *
     * <p>该变体用 {@code commit()} 同步写盘，阻塞调用线程（~50-150ms，叠加
     * {@link EncryptedSharedPreferences} AES-GCM 加密），但保证进程被杀前数据已落盘。
     *
     * <p>⚠️ 仅用于重启前落盘场景，不要替换正常写入路径 —— 否则会把 YUJ-284 的
     * 主线程 stall 重新带回来。
     */
    public boolean putSPSync(String key, String value) {
        mEditor.putString(key, value);
        return mEditor.commit();
    }

    // 获取数据
    public String getSP(String key) {
        return mPreferences.getString(key, "");
    }

    public String getSP(String key, String defaultValue) {
        return mPreferences.getString(key, defaultValue);
    }

    public void putBooleanWithUID(String key, boolean value) {
        this.putBoolean(WKConfig.getInstance().getUid() + "_" + key, value);
    }

    public boolean getBooleanWithUID(String key) {
        return this.getBoolean(WKConfig.getInstance().getUid() + "_" + key);
    }

    public void putBoolean(String key, boolean value) {
        mEditor.putBoolean(key, value);
        mEditor.apply();
    }

    // 获取数据
    public boolean getBoolean(String key) {
        return getBoolean(key, true);
    }

    public boolean getBoolean(String key, boolean defValue) {
        return mPreferences.getBoolean(key, defValue);
    }

    public void putIntWithUID(String key, int value) {
        this.putInt(WKConfig.getInstance().getUid() + "_" + key, value);
    }

    public int getIntWithUID(String key) {
        return getInt(WKConfig.getInstance().getUid() + "_" + key);
    }

    public void putInt(String key, int value) {
        mEditor.putInt(key, value);
        mEditor.apply();
    }

    public int getInt(String key) {
        return mPreferences.getInt(key, 0);
    }

    public float getFloat(String key) {
        return mPreferences.getFloat(key, 0.0f);
    }

    public void putFloat(String key, float value) {
        mEditor.putFloat(key, value);
        mEditor.apply();
    }

    public float getFloat(String key, float defValue) {
        return mPreferences.getFloat(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return mPreferences.getInt(key, defValue);
    }

    public void putLongWithUID(String key, long value) {
        this.putLong(WKConfig.getInstance().getUid() + "_" + key, value);
    }

    public long getLongWithUID(String key) {
        return this.getLong(WKConfig.getInstance().getUid() + "_" + key);
    }

    public void putLong(String key, long value) {
        mEditor.putLong(key, value);
        mEditor.apply();
    }

    public long getLong(String key) {
        return mPreferences.getLong(key, 0);
    }
}
