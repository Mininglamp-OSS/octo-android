package com.chat.base.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.chat.base.ui.components.AlignImageSpan;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoonUtil {
    public static final float DEF_SCALE = 0.6f;
    public static final float SMALL_SCALE = 0.40F;

    public static void identifyFaceExpression(Context context,
                                              View textView, String value) {
        identifyFaceExpression(context, textView, value, DEF_SCALE);
    }

    public static void identifyFaceExpressionAndATags(Context context,
                                                      View textView, String value, int align) {
        SpannableString mSpannableString = makeSpannableStringTags(context, value, DEF_SCALE, align);
        viewSetText(textView, mSpannableString);
    }

    /**
     * 具体类型的view设置内容
     *
     * @param textView
     * @param mSpannableString
     */
    private static void viewSetText(View textView, SpannableString mSpannableString) {
        if (textView instanceof TextView) {
            TextView tv = (TextView) textView;
            tv.setText(mSpannableString);
        } else if (textView instanceof EditText) {
            EditText et = (EditText) textView;
            et.setText(mSpannableString);
        }
    }

    public static void identifyFaceExpression(Context context,
                                              View textView, String value, float scale) {
        SpannableString mSpannableString = replaceEmoticons(context, value, scale);
        viewSetText(textView, mSpannableString);
    }

//    public static void identifyRecentVHFaceExpressionAndTags(Context context, View textView,
//                                                             String value, int align, float scale) {
//        SpannableString mSpannableString = makeSpannableStringTags(context, value, scale, align, false);
//        TeamMemberAitHelper.replaceAitForeground(value, mSpannableString);
//        viewSetText(textView, mSpannableString);
//    }

    /**
     * lstmsgviewholder类使用,只需显示a标签对应的文本
     */
    public static void identifyFaceExpressionAndTags(Context context,
                                                     View textView, String value, int align, float scale) {
        SpannableString mSpannableString = makeSpannableStringTags(context, value, scale, align, false);
        viewSetText(textView, mSpannableString);
    }

    private static SpannableString replaceEmoticons(Context context, String value, float scale) {
        if (TextUtils.isEmpty(value)) {
            value = "";
        }
        SpannableString mSpannableString = new SpannableString(value);
        Matcher matcher = EmojiManager.getInstance().getPattern().matcher(value);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            String emot = value.substring(start, end);
            Log.e("找到emoji：",emot+"_"+start+"_"+end+"_"+value.length());
            Drawable d = getEmotDrawable(context, emot, scale);
            if (d != null) {
                AlignImageSpan span = new AlignImageSpan(d, AlignImageSpan.ALIGN_CENTER) {
                    @Override
                    public void onClick(View view) {

                    }
                };
                mSpannableString.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return mSpannableString;
    }

    private static final Pattern mATagPattern = Pattern.compile("<a.*?>.*?</a>");

    public static SpannableString makeSpannableStringTags(Context context, String value, float scale, int align) {
        return makeSpannableStringTags(context, value, DEF_SCALE, align, true);
    }

    public static SpannableString makeSpannableStringTags(Context context, String value, float scale, int align, boolean bTagClickable) {
        ArrayList<ATagSpan> tagSpans = new ArrayList<ATagSpan>();
        if (TextUtils.isEmpty(value)) {
            value = "";
        }
        //a标签需要替换原始文本,放在moonutil类中
        Matcher aTagMatcher = mATagPattern.matcher(value);

        int start = 0;
        int end = 0;
        while (aTagMatcher.find()) {
            start = aTagMatcher.start();
            end = aTagMatcher.end();
            String atagString = value.substring(start, end);
            ATagSpan tagSpan = getTagSpan(atagString);
            value = value.substring(0, start) + tagSpan.getTag() + value.substring(end);
            tagSpan.setRange(start, start + tagSpan.getTag().length());
            tagSpans.add(tagSpan);
            aTagMatcher = mATagPattern.matcher(value);
        }


        SpannableString mSpannableString = new SpannableString(value);
        Matcher matcher = EmojiManager.getInstance().getPattern().matcher(value);
        while (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
            String emot = value.substring(start, end);
            Drawable d = getEmotDrawable(context, emot, scale);
            if (d != null) {
                ImageSpan span = align == -1 ? new AlignImageSpan(d, AlignImageSpan.ALIGN_CENTER) {
                    @Override
                    public void onClick(View view) {

                    }
                } : new ImageSpan(d, align);
                mSpannableString.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (bTagClickable) {
            for (ATagSpan tagSpan : tagSpans) {
                mSpannableString.setSpan(tagSpan, tagSpan.start, tagSpan.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return mSpannableString;
    }

    //添加一个emojiSpan
    public static void addEmojiSpan(EditText editText, String showText, Context context) {
        StringBuilder builder = new StringBuilder();

        builder.append(showText);
        Objects.requireNonNull(editText.getText()).insert(editText.getSelectionStart(), builder.toString());
        SpannableString sps = new SpannableString(editText.getText());
        int start = editText.getSelectionEnd() - builder.toString().length();
        int end = editText.getSelectionEnd();
        Drawable d = getEmotDrawable(context, showText, SMALL_SCALE);
        if (d != null) {
            AlignImageSpan span = new AlignImageSpan(d, AlignImageSpan.ALIGN_CENTER) {
                @Override
                public void onClick(View view) {

                }
            };
            sps.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        editText.setText(sps);
        editText.setSelection(end);
    }

    public static SpannableString getEmotionContent(final Context context, final EditText tv, String source) {
        SpannableString spannableString = new SpannableString(source);

        String regexEmotion = "\\[([\u4e00-\u9fa5\\w])+\\]";
        Pattern patternEmotion = Pattern.compile(regexEmotion);
        Matcher matcherEmotion = patternEmotion.matcher(spannableString);
        //添加表情样式之前，先将@的样式获取保存

        while (matcherEmotion.find()) {
            String key = matcherEmotion.group();
            int start = matcherEmotion.start();
            Drawable d = getEmotDrawable(context, key, SMALL_SCALE);
            if (d != null) {
                AlignImageSpan span = new AlignImageSpan(d, AlignImageSpan.ALIGN_CENTER) {
                    @Override
                    public void onClick(View view) {

                    }
                };
                spannableString.setSpan(span, start, start + key.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

        }
        //重新渲染@成员

        return spannableString;
    }


    public static void replaceEmoticons(Context context, Editable editable, int start, int count) {
        if (count <= 0 || editable.length() < start + count)
            return;
        CharSequence s = editable.subSequence(start, start + count);
        Matcher matcher = EmojiManager.getInstance().getPattern().matcher(s);
        while (matcher.find()) {
            int from = start + matcher.start();
            int to = start + matcher.end();
            String emot = editable.subSequence(from, to).toString();
            Drawable d = getEmotDrawable(context, emot, SMALL_SCALE);
            if (d != null) {
                AlignImageSpan span = new AlignImageSpan(d, AlignImageSpan.ALIGN_BOTTOM) {
                    @Override
                    public void onClick(View view) {

                    }
                };
                editable.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public static Drawable getEmotDrawable(Context context, String text, float scale) {
        Drawable drawable = EmojiManager.getInstance().getDrawable(context, text);

        // scale
        if (drawable != null) {
            int width = (int) (drawable.getIntrinsicWidth() * scale);
            int height = (int) (drawable.getIntrinsicHeight() * scale);
            drawable.setBounds(0, 0, width, height);
        }

        return drawable;
    }

    private static ATagSpan getTagSpan(String text) {
        String href = null;
        String tag = null;
        if (text.toLowerCase().contains("href")) {
            int start = text.indexOf("\"");
            int end = text.indexOf("\"", start + 1);
            if (end > start)
                href = text.substring(start + 1, end);
        }
        int start = text.indexOf(">");
        int end = text.indexOf("<", start);
        if (end > start)
            tag = text.substring(start + 1, end);
        return new ATagSpan(tag, href);

    }

    private static class ATagSpan extends ClickableSpan {
        private int start;
        private int end;
        private String mUrl;
        private final String tag;

        ATagSpan(String tag, String url) {
            this.tag = tag;
            this.mUrl = url;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(true);
        }

        public String getTag() {
            return tag;
        }

        public void setRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void onClick(View widget) {
            try {
                if (TextUtils.isEmpty(mUrl))
                    return;
                Uri uri = Uri.parse(mUrl);
                String scheme = uri.getScheme();
                if (TextUtils.isEmpty(scheme)) {
                    mUrl = "http://" + mUrl;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
