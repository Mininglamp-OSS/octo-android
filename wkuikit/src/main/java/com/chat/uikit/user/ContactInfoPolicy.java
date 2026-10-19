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

package com.chat.uikit.user;

/**  I02a：名片联系方式展示策略。纯函数，无 Android 依赖，可 JVM 单测。 */
public final class ContactInfoPolicy {
    private ContactInfoPolicy() {}

    /** 行渲染模式。 */
    public enum RowMode {
        HIDDEN,       // 整行省略（字段缺失/null/非串，或门控不通过）
        PLACEHOLDER,  // 展示「用户暂未添加该信息」/「未绑定」
        TAP_TO_VIEW,  // 他人手机号未展开
        FULL_VALUE    // 完整值（本人手机号、任意邮箱、已展开的他人手机号）
    }

    public static final int STATE_NORMAL = 0;
    public static final int STATE_COOLING_OFF = 1;
    public static final int STATE_DELETED = 2;

    /**
     * 整个联系方式 section 是否可能展示（对齐 iOS 门控顺序）：
     * 1) 总开关必须开（含本人看自己）
     * 2) robot 一律不展示
     * 3) 非本人且 is_destroy ∈ {1,2} 不展示；本人不受状态约束
     */
    public static boolean sectionEligible(boolean switchOn, boolean isSelf,
                                          boolean isRobot, int isDestroy) {
        if (!switchOn) return false;
        if (isRobot) return false;
        if (!isSelf && (isDestroy == STATE_COOLING_OFF || isDestroy == STATE_DELETED)) return false;
        return true;
    }

    /** 手机号行模式。value 为 null 表示字段缺失/JSON null/非字符串。 */
    public static RowMode phoneRowMode(String phone, boolean isSelf, boolean revealed) {
        if (phone == null) return RowMode.HIDDEN;
        if (phone.isEmpty()) return RowMode.PLACEHOLDER;
        return (isSelf || revealed) ? RowMode.FULL_VALUE : RowMode.TAP_TO_VIEW;
    }

    /** 邮箱行模式（邮箱永不需要点击展开）。 */
    public static RowMode emailRowMode(String email) {
        if (email == null) return RowMode.HIDDEN;
        if (email.isEmpty()) return RowMode.PLACEHOLDER;
        return RowMode.FULL_VALUE;
    }

    /**
     * 区号归一（R9）：优先 phone_country_code，fallback zone；
     * legacy zone 的 "+86" / "0086" 都归一成 "86"（返回纯数字，不带 + 号）。
     * 无可用区号返回 ""。
     */
    public static String normalizeDialCode(String phoneCountryCode, String zone) {
        if (phoneCountryCode != null && !phoneCountryCode.isEmpty()) {
            return stripPlus(phoneCountryCode);
        }
        if (zone == null || zone.isEmpty()) return "";
        String s = stripPlus(zone);
        // 去掉国际前缀 00 以及残余前导 0（"0086" → "86"）
        if (s.startsWith("00")) s = s.substring(2);
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    private static String stripPlus(String s) {
        return s.startsWith("+") ? s.substring(1) : s;
    }

    /** 名片「本人看自己」手机号格式：`+86 13800000000`；无区号时只返回号码。 */
    public static String selfCardPhoneDisplay(String phone, String phoneCountryCode, String zone) {
        String code = normalizeDialCode(phoneCountryCode, zone);
        if (phone == null || phone.isEmpty()) return "";
        return code.isEmpty() ? phone : "+" + code + " " + phone;
    }

    /** 「我的」页手机号格式：`(+86) 13800000000`（iOS 既有差异，Android 照抄）。 */
    public static String meInfoPhoneDisplay(String phone, String zone) {
        String code = normalizeDialCode(null, zone);
        if (phone == null || phone.isEmpty()) return "";
        return code.isEmpty() ? phone : "(+" + code + ") " + phone;
    }
}
