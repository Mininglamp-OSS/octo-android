package com.chat.base.utils;

import static android.util.Patterns.DOMAIN_NAME;
import static android.util.Patterns.GOOD_IRI_CHAR;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2019-11-19 17:37
 */
public class StringUtils {

    /**
     * 正则表达式：验证手机号码
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^((13[0-9])|(14[0-9])|(15[^4,\\D])|(16[0-9])|(17[0-9])|" +
            "(18[0-9]))\\d{8}$");
    /**
     * 正则表达式：验证邮箱
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\w+(\\.\\w)*@\\w+(\\.\\w{2,3}){1,3}");

    /**
     * 18位身份证正则
     */
    private static final Pattern ID18_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|" +
            "(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$");
    /**
     * 15位身份证正则
     */
    private static final Pattern ID15_PATTERN = Pattern.compile("^[1-9]\\d{5}\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])" +
            "|10|20|30|31)\\d{2}[0-9Xx]$");


    /**
     * 定义script的正则表达式
     */
    private static final String REGEX_SCRIPT = "<script[^>]*?>[\\s\\S]*?</script>";
    /**
     * 定义style的正则表达式
     */
    private static final String REGEX_STYLE = "<style[^>]*?>[\\s\\S]*?</style>";
    /**
     * 定义HTML标签的正则表达式
     */
    private static final String REGEX_HTML = "<[^>]+>";
    /**
     * 定义空格回车换行符
     */
    private static final String REGEX_SPACE = "\\s*|\t|\r|\n";

    private StringUtils() {
    }


    /**
     * 判断字符串不为空 或者null NULL
     *
     * @param str
     * @return
     */
    public static boolean isStrNotNull(String str) {
        return !TextUtils.isEmpty(str) && !"NULL".equals(str) && !"null".equals(str);
    }

    /**
     * 判断是否有空字符串
     *
     * @param strs
     * @return
     */
    public static boolean isStrsEmpty(String... strs) {
        if (null != strs) {
            for (String str : strs) {
                if (TextUtils.isEmpty(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断字符串是否有不为空的
     *
     * @param strs
     * @return
     */
    public static boolean isStrsNotEmpty(String... strs) {
        if (null != strs) {
            for (String str : strs) {
                if (!TextUtils.isEmpty(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断输入框中是否有内容
     *
     * @param editTexts
     * @return
     */
    public static boolean isEditTextsEmpty(EditText... editTexts) {
        if (null != editTexts) {
            for (EditText editText : editTexts) {
                if (editText.getText().length() == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isTextViewsEmpty(TextView... textViews) {
        if (null != textViews) {
            for (TextView textview : textViews) {
                if (textview.getText().length() == 0) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 在textview前面添加一个固定颜色的头文本
     *
     * @param textView
     * @param head
     * @param color
     * @return
     */
    public static void addHeadStrToTextView(TextView textView, String head, final int color) {
        SpannableString spanText = new SpannableString(head + textView.getText().toString());
        spanText.setSpan(new ClickableSpan() {
            @Override
            public void updateDrawState(@NotNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(color);
                ds.setUnderlineText(false);
            }

            @Override
            public void onClick(@NotNull View view) {
            }
        }, 0, head.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setHighlightColor(Color.TRANSPARENT);
        textView.setText(spanText);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * 是否是手机号
     *
     * @param mobiles
     * @return
     */
    public static boolean isMobile(String mobiles) {
        return PHONE_PATTERN.matcher(mobiles).matches();
    }

    /**
     * 是否是邮箱
     *
     * @param email
     * @return
     */
    public static boolean isEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }


    /**
     * 判断身份证
     *
     * @param idcard
     * @return
     */
    public static boolean isIDNum(String idcard) {
        return ID18_PATTERN.matcher(idcard).matches() || ID15_PATTERN.matcher(idcard).matches();
    }

    /**
     * 拼接字符串
     *
     * @param strs
     * @return
     */
    public static String appendStrs(String... strs) {
        StringBuilder stringBuilder = new StringBuilder();
        if (null != strs) {
            for (String s : strs) {
                stringBuilder.append(s);
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 获取本地软件版本号名称
     */
    public static String getLocalVersionName(Context ctx) {
        String localVersion = "";
        try {
            PackageInfo packageInfo = ctx.getApplicationContext()
                    .getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            localVersion = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return localVersion;
    }

    /**
     * 提供精确减法运算的sub方法
     *
     * @param value1 被减数
     * @param value2 减数
     * @return 两个参数的差
     */
    public static double sub(double value1, double value2) {
        BigDecimal b1 = new BigDecimal(value1);
        BigDecimal b2 = new BigDecimal(value2);
        return b1.subtract(b2).doubleValue();
    }

    /**
     * 提供精确加法计算的add方法
     *
     * @param value1 被加数
     * @param value2 加数
     * @return 两个参数的和
     */
    public static double add(double value1, double value2) {
        BigDecimal b1 = new BigDecimal(value1);
        BigDecimal b2 = new BigDecimal(value2);
        return b1.add(b2).doubleValue();
    }

    /**
     * 提供精确的除法运算方法div
     *
     * @param value1 被除数
     * @param value2 除数
     * @param scale  精确范围
     * @return 两个参数的商
     * @throws IllegalAccessException
     */
    public static double div(double value1, double value2, int scale) throws IllegalAccessException {
        //如果精确范围小于0，抛出异常信息
        if (scale < 0) {
            throw new IllegalAccessException("精确度不能小于0");
        }
        BigDecimal b1 = new BigDecimal(Double.toString(value1));
        BigDecimal b2 = new BigDecimal(Double.toString(value2));
        //默认保留两位会有错误，这里设置保留小数点后4位
        return b1.divide(b2, scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static String getData(long date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date dateUtils = new Date(date);
        return simpleDateFormat.format(dateUtils);
    }

    /**
     * 关键字高亮变色
     *
     * @param color   变化的色值
     * @param text    文字
     * @param keyword 文字中的关键字
     * @return
     */
    public static SpannableString findSearch(int color, String text, String keyword) {
        SpannableString s = new SpannableString(text);
        Pattern p = Pattern.compile(keyword);
        Matcher m = p.matcher(s);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            s.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /**
     * 多个关键字高亮变色
     *
     * @param color   变化的色值
     * @param text    文字
     * @param keyword 文字中的关键字数组
     * @return SpannableString
     */
    public static SpannableString findSearch(int color, String text, String... keyword) {
        SpannableString s = new SpannableString(text);
        for (String value : keyword) {
            Pattern p = Pattern.compile(value);
            Matcher m = p.matcher(s);
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                s.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }

    public static String notNullStr(String str) {
        return str == null ? "" : str;
    }

    public static String delHTMLTag(String htmlStr) {
        // 过滤script标签
        Pattern p_script = Pattern.compile(REGEX_SCRIPT, Pattern.CASE_INSENSITIVE);
        Matcher m_script = p_script.matcher(htmlStr);
        htmlStr = m_script.replaceAll("");
        // 过滤style标签
        Pattern p_style = Pattern.compile(REGEX_STYLE, Pattern.CASE_INSENSITIVE);
        Matcher m_style = p_style.matcher(htmlStr);
        htmlStr = m_style.replaceAll("");
        // 过滤html标签
        Pattern p_html = Pattern.compile(REGEX_HTML, Pattern.CASE_INSENSITIVE);
        Matcher m_html = p_html.matcher(htmlStr);
        htmlStr = m_html.replaceAll("");
        // 过滤空格回车标签
        Pattern p_space = Pattern.compile(REGEX_SPACE, Pattern.CASE_INSENSITIVE);
        Matcher m_space = p_space.matcher(htmlStr);
        htmlStr = m_space.replaceAll("");
        return htmlStr.trim().replaceAll("&nbsp;", ""); // 返回文本字符串
    }


    /**
     * 获取某段字符串中的所有链接
     *
     * @param string
     * @return
     */
    public static List<String> getStrUrls(String string) {
        List<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(string)) return list;
        string = string.replaceAll("[\u4E00-\u9FA5]", "#");

        String[] url = string.split("#");
        //转换为小写
        if (url.length > 0) {
            for (String tempurl : url) {
                if (TextUtils.isEmpty(tempurl)) {
                    continue;
                }
//                tempurl = tempurl.toLowerCase();

                String regex = "((?:(http|https|Http|Https|rtsp|Rtsp):\\/\\/(?:(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
                        + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
                        + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?)?"
                        + "(?:" + DOMAIN_NAME + ")"
                        + "(?:\\:\\d{1,5})?)" // plus option port number
                        + "(\\/(?:(?:[" + GOOD_IRI_CHAR + "\\;\\/\\?\\:\\@\\&\\=\\#\\~"  // plus option query params
                        + "\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])|(?:\\%[a-fA-F0-9]{2}))*)?"
                        + "(?:\\b|$)";

                //String regex2 = "^((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(17[013678])|(18[0,5-9]))\\d{8}$";

                //String regEx1 = "^([a-z0-9A-Z]+[-|\\.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$";

                Pattern p = Pattern.compile(regex);
                Matcher matcher = p.matcher(tempurl);
                if (matcher.find()) {
                    list.add(matcher.group(0));
                }

//                Pattern p1 = Pattern.compile(regEx1);
//                Matcher matcher1 = p1.matcher(tempurl);
//                if (matcher1.find()) {
//                    list.add(matcher1.group(0));
//                }
            }
        }
//        LinkedHashSet<String> hashSet = new LinkedHashSet<>(list);

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i != j && list.get(i).equals(list.get(j))) {
                    list.remove(list.get(j));
                }
            }
        }
        return list;
    }

    public static double pers = 1048576;

    public static String sizeFormatNum2String(long size) {
        String s;
        if (size > 1024 * 1024 * 1024) {
            s = String.format(Locale.CHINA, "%.2f", (double) size / (pers * 1024)) + "G";
        } else if (size > 1024 * 1024)
            s = String.format(Locale.CHINA, "%.2f", (double) size / pers) + "M";
        else
            s = String.format(Locale.CHINA, "%.2f", (double) size / (1024)) + "KB";
        return s;
    }

    /**
     * 格式化时间展示为05’10”
     */
    public static String formatRecordTime(long recTime, long maxRecordTime) {
        int time = (int) ((maxRecordTime - recTime) / 1000);
        int minute = time / 60;
        int second = time % 60;
        //return String.format("%2d’%2d”", minute, second);
        return String.format(Locale.CHINA, "%2d:%2d", minute, second);
    }


    public static String formatTime(int recTime) {
        int minute = recTime / 60;
        int second = recTime % 60;
        return String.format(Locale.CHINA, "%2d’%2d”", minute, second);
    }

    public static boolean isBlank(String string) {
        if (string == null) return true;
        for (int i = 0, len = string.length(); i < len; ++i) {
            if (!Character.isWhitespace(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static int counterChars(String str) {
        // return
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            int tmp = str.charAt(i);
            if (tmp > 0 && tmp < 127) {
                count += 1;
            } else {
                count += 2;
            }
        }
        return count;
    }


    public static String phoneHide(String phone, int size) {
        String sb = phone;

        if (!TextUtils.isEmpty(phone) && size > 6) {
            int start = (size - 4) / 2;
            String str1 = sb.substring(0, start);
            String str2 = "****";
            String str3 = sb.substring(start + 4, size);
            sb = str1 + str2 + str3;
        }
        return sb;
    }

    public static List<String> getEmails(String content) {
        List<String> digitList = new ArrayList<>();
        Pattern p = Pattern.compile("\\w+(\\.\\w)*@\\w+(\\.\\w{2,3}){1,3}");
        Matcher m = p.matcher(content);
        while (m.find()) {
            digitList.add(m.group());
        }
        return digitList;
    }

    public static List<String> getNumbers(String content) {
        List<String> digitList = new ArrayList<>();
        Pattern p = Pattern.compile("((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(18[0,5-9]))\\d{8}");
        Matcher matcher = p.matcher(content);
        while (matcher.find()) {
            digitList.add(matcher.group());
        }
        return digitList;
    }

    public static InputFilter getInputFilter(int mMax) {
        return (source, start, end, dest, dstart, dend) -> {
            int keep = mMax - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                //这里，用来给用户提示,当然可以替换成 更加优雅的形式
//                    if (null != lengthListener) {
//                        lengthListener.pass();
//                    }
                return "";
            } else if (keep >= end - start) {
                return null;
            } else {
                keep += start;
                if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                    --keep;
                    if (keep == start) {
                        return "";
                    }
                }
                return source.subSequence(start, keep);
            }
        };
    }

    public static String removeHtmlTag(String htmlStr) {
        if (TextUtils.isEmpty(htmlStr))
            return htmlStr;
        String script = "<script[^>]*?>[\\s\\S]*?<\\/script>";
        String style = "<style[^>]*?>[\\s\\S]*?<\\/style>";
        String html = "<[^>]+>";
        String space = "(\r?\n(\\s*\r?\n)+)";
        String white = "&nbsp;";
        Pattern pScript = Pattern.compile(script, 2);
        Matcher mScript = pScript.matcher(htmlStr);
        htmlStr = mScript.replaceAll("");
        Pattern pStyle = Pattern.compile(style, 2);
        Matcher mStyle = pStyle.matcher(htmlStr);
        htmlStr = mStyle.replaceAll("");
        Pattern pHtml = Pattern.compile(html, 2);
        Matcher mHtml = pHtml.matcher(htmlStr);
        htmlStr = mHtml.replaceAll("");
        Pattern pSpace = Pattern.compile(space, 2);
        Matcher mSpace = pSpace.matcher(htmlStr);
        htmlStr = mSpace.replaceAll("");
        htmlStr = htmlStr.replaceAll(white, "");
        return htmlStr.trim();
    }

    public static String format(String content, List<String> strings) {
        return MessageFormat.format(content, strings);
    }

    public static String getShowContent(Context context, String contentJson) {
        String content;
        String loginUID = WKConfig.getInstance().getUid();
        try {
            if (TextUtils.isEmpty(contentJson)) {
                return "";
            }
            JSONObject jsonObject = new JSONObject(contentJson);
            String string = jsonObject.optString("content");
            JSONArray list = jsonObject.optJSONArray("extra");
            List<String> names = new ArrayList<>();
            if (list != null && list.length() > 0) {
                for (int i = 0, size = list.length(); i < size; i++) {
                    JSONObject jsonObject1 = list.optJSONObject(i);
                    String name = "";
                    if (jsonObject1 != null) {
                        name = jsonObject1.optString("name");
                        if (jsonObject1.has("uid")) {
                            String uid = jsonObject1.optString("uid");
                            if (!TextUtils.isEmpty(uid) && uid.equals(loginUID)) {
                                name = context.getString(R.string.str_you);
                            }
                        }
                    }
                    names.add(name);
                }
            }
            if (WKReader.isNotEmpty(names))
                content = MessageFormat.format(string, names.toArray());
            else content = string;
        } catch (JSONException e) {
            e.printStackTrace();
            content = context.getString(R.string.base_unknow_msg);
        }
        if (TextUtils.isEmpty(content)) {
            content = context.getString(R.string.base_unknow_msg);
        }
        return content;
    }

    public static String replaceBlank(String str) {
        String dest = "";
        if (str != null) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }
}
