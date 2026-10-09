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

package com.chat.base.utils.language;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import com.chat.base.R;
import com.chat.base.config.WKSharedPreferencesUtil;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * 多语言切换的帮助类
 */
public class WKMultiLanguageUtil {

    private static final String TAG = "MultiLanguageUtil";

    private WeakReference<Context> mContext;
    private static final String SAVE_LANGUAGE = "save_language";

    private static class MultiLanguageUtilBinder {
        private final static WKMultiLanguageUtil util = new WKMultiLanguageUtil();
    }

    public static WKMultiLanguageUtil getInstance() {
        return MultiLanguageUtilBinder.util;
    }

    public void init(Context context) {
        mContext = new WeakReference<>(context);
    }

    private WKMultiLanguageUtil() {
    }

    /**
     * 设置语言
     */
    public void setConfiguration() {
        setConfiguration(null);
    }

    /**
     * 以 baseConfig 为基准设置语言。baseConfig 传系统派发的最新 Configuration，
     * 避免读取当前 resources 里尚未更新的旧配置（会把旧 uiMode 等值盖回去）。
     *
     * <p>App 级配置的 uiMode night 位必须强制镜像 {@link Resources#getSystem()} 的真值，
     * 不能携带任何残留：light/dark 锁定是 AppCompat 在每个 Activity 自己的配置上覆写的，
     * 不依赖 app 级配置；而 AppCompat 的"跟随系统"解析和框架的 Activity 重建配置都以
     * app 级配置为基准。若这里把过期的 night 位原样写回，会形成自我固化——系统已深色
     * 而 app 级配置仍停留在上次锁定的浅色，导致切到"跟随系统"后 AppCompat 算出
     * "没有变化"、不重建，静态色值（读 Resources.getSystem() 真值）却已变深，
     * 页面呈现"个别 tag 变深"的持久性错位。
     */
    public void setConfiguration(Configuration baseConfig) {
        if (mContext == null || mContext.get() == null) {
            return;
        }
        // baseConfig 为 null 时也要拷贝一份再改，不能直接改 live 的 getConfiguration()
        // 再整份传给 updateConfiguration ——那样等于把当前 resources 里可能已过时的
        // uiMode 等字段原样写回去，是这个方法本来要避免的问题。
        Configuration configuration = new Configuration(
                baseConfig != null ? baseConfig : mContext.get().getResources().getConfiguration());
        // night 位强制对齐系统真值，切断"锁定残留 → 写回 → 永远残留"的固化环。
        int sysNight = Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (sysNight != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | sysNight;
        }
        com.chat.base.ui.ThemeTrace.log("WKMultiLanguageUtil.setConfiguration",
                "baseFrom=" + (baseConfig != null ? "systemDispatch" : "appRes")
                        + " sysNight=" + com.chat.base.ui.ThemeTrace.nightBitName(sysNight)
                        + " writtenNight=" + com.chat.base.ui.ThemeTrace.nightBitName(
                        configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK));
        Locale targetLocale = getLanguageLocale();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(targetLocale);
        } else {
            configuration.locale = targetLocale;
        }
        Resources resources = mContext.get().getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        resources.updateConfiguration(configuration, dm);//语言更换生效的代码!
    }

    //如果不是英文、简体中文、繁体中文，默认返回简体中文
    private Locale getLanguageLocale() {
        if (mContext == null || mContext.get() == null) return getSysLocale();
        int languageType = WKSharedPreferencesUtil.getInstance().getInt(WKMultiLanguageUtil.SAVE_LANGUAGE, 0);
        if (languageType == WKLanguageType.LANGUAGE_FOLLOW_SYSTEM) {
            return getSysLocale();
        } else if (languageType == WKLanguageType.LANGUAGE_EN) {
            return Locale.ENGLISH;
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED) {
            return Locale.SIMPLIFIED_CHINESE;
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL) {
            return Locale.TRADITIONAL_CHINESE;
        }
        getSystemLanguage(getSysLocale());
        Log.e(TAG, "getLanguageLocale" + languageType + languageType);
        return Locale.SIMPLIFIED_CHINESE;
    }

    private String getSystemLanguage(Locale locale) {
        return locale.getLanguage() + "_" + locale.getCountry();
    }

    //以上获取方式需要特殊处理一下
    public Locale getSysLocale() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                LocaleListCompat listCompat= ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
                locale= listCompat.get(0);
            }catch (Exception e){
                locale=LocaleList.getDefault().get(0);
            }
        } else {
            locale = Locale.getDefault();

        }
        return locale;
    }

    /**
     * 更新语言
     *
     * @param languageType
     */
    public void updateLanguage(int languageType) {
        WKSharedPreferencesUtil.getInstance().putInt(WKMultiLanguageUtil.SAVE_LANGUAGE, languageType);
        WKMultiLanguageUtil.getInstance().setConfiguration();
    }

    public String getLanguageName(Context context) {
        int languageType = WKSharedPreferencesUtil.getInstance().getInt(WKMultiLanguageUtil.SAVE_LANGUAGE, WKLanguageType.LANGUAGE_FOLLOW_SYSTEM);
        if (languageType == WKLanguageType.LANGUAGE_EN) {
            return mContext.get().getString(R.string.setting_language_english);
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED) {
            return mContext.get().getString(R.string.setting_simplified_chinese);
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL) {
            return mContext.get().getString(R.string.setting_traditional_chinese);
        }
        return mContext.get().getString(R.string.setting_language_auto);
    }

    /**
     * 获取到用户保存的语言类型
     *
     * @return
     */
    public int getLanguageType() {
        int languageType = WKSharedPreferencesUtil.getInstance().getInt(WKMultiLanguageUtil.SAVE_LANGUAGE, WKLanguageType.LANGUAGE_FOLLOW_SYSTEM);
        if (languageType == WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED) {
            return WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED;
        } else if (languageType == WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL) {
            return WKLanguageType.LANGUAGE_CHINESE_TRADITIONAL;
        } else if (languageType == WKLanguageType.LANGUAGE_FOLLOW_SYSTEM) {
            return WKLanguageType.LANGUAGE_FOLLOW_SYSTEM;
        }
        Log.e(TAG, "getLanguageType" + languageType);
        return languageType;
    }

    public Context attachBaseContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return createConfigurationResources(context);
        } else {
            WKMultiLanguageUtil.getInstance().setConfiguration();
            return context;
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context createConfigurationResources(Context context) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        Locale locale = getInstance().getLanguageLocale();
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }
}
