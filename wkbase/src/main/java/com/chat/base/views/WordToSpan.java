package com.chat.base.views;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2020-09-16 15:30
 * 高光链接识别
 */
public class WordToSpan {
    private int colorTAG = Color.BLUE;
    private int colorMENTION = Color.BLUE;
    private int colorURL = Color.BLUE;
    private int colorMAIL = Color.BLUE;
    private int colorPHONE = Color.BLUE;
    private int colorCUSTOM = Color.BLUE;
    private int colorHIGHLIGHT = Color.WHITE;
    private int backgroundHIGHLIGHT = Color.BLUE;
    private String regexCUSTOM = null;
    private boolean underlineTAG = false;
    private boolean underlineMENTION = false;
    private boolean underlineURL = false;
    private boolean underlineMAIL = false;
    private boolean underlinePHONE = false;
    private boolean underlineCUSTOM = false;
    private ClickListener clickListener;
    private TextView tv;
    private Spannable ws;
    private String key = null;

    // custom
    public WordToSpan setRegexCUSTOM(String regexCUSTOM) {
        this.regexCUSTOM = regexCUSTOM;
        return this;
    }

    // colors
    public WordToSpan setColorTAG(int colorTAG) {
        this.colorTAG = colorTAG;
        return this;
    }

    public WordToSpan setColorMENTION(int colorMENTION) {
        this.colorMENTION = colorMENTION;
        return this;
    }

    public WordToSpan setColorURL(int colorURL) {
        this.colorURL = colorURL;
        return this;
    }

    public WordToSpan setColorMAIL(int colorMAIL) {
        this.colorMAIL = colorMAIL;
        return this;
    }

    public WordToSpan setColorPHONE(int colorPHONE) {
        this.colorPHONE = colorPHONE;
        return this;
    }

    public WordToSpan setColorCUSTOM(int colorCUSTOM) {
        this.colorCUSTOM = colorCUSTOM;
        return this;
    }

    public WordToSpan setColorHIGHLIGHT(int colorHIGHLIGHT) {
        this.colorHIGHLIGHT = colorHIGHLIGHT;
        return this;
    }

    // background
    public WordToSpan setBackgroundHIGHLIGHT(int backgroundHIGHLIGHT) {
        this.backgroundHIGHLIGHT = backgroundHIGHLIGHT;
        return this;
    }


    // underline
    public WordToSpan setUnderlineTAG(boolean underlineTAG) {
        this.underlineTAG = underlineTAG;
        return this;
    }

    public WordToSpan setUnderlineMENTION(boolean underlineMENTION) {
        this.underlineMENTION = underlineMENTION;
        return this;
    }

    public WordToSpan setUnderlineURL(boolean underlineURL) {
        this.underlineURL = underlineURL;
        return this;
    }

    public WordToSpan setUnderlineMAIL(boolean underlineMAIL) {
        this.underlineMAIL = underlineMAIL;
        return this;
    }

    public WordToSpan setUnderlinePHONE(boolean underlinePHONE) {
        this.underlinePHONE = underlinePHONE;
        return this;
    }

    public WordToSpan setUnderlineCUSTOM(boolean underlineCUSTOM) {
        this.underlineCUSTOM = underlineCUSTOM;
        return this;
    }

    // create link
    public WordToSpan setLink(String txt) {
        this.ws = new SpannableString(txt);

        Matcher matcherTAG = Pattern.compile("(^|\\s+)#(\\w+)").matcher(txt);
        while (matcherTAG.find()) {
            int st = matcherTAG.start();
            int en = st + matcherTAG.group(0).length();
            this.ws.setSpan(new myClickableSpan(colorTAG, underlineTAG, "tag"), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher matcherMENTION = Pattern.compile("(^|\\s+)@(\\w+)").matcher(txt);
        while (matcherMENTION.find()) {
            int st = matcherMENTION.start();
            int en = st + matcherMENTION.group(0).length();
            this.ws.setSpan(new myClickableSpan(colorMENTION, underlineMENTION, "mention"), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher matcherURL = Pattern.compile("(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]").matcher(txt);
        while (matcherURL.find()) {
            int st = matcherURL.start();
            int en = st + matcherURL.group(0).length();
            this.ws.setSpan(new myClickableSpan(colorURL, underlineURL, "url"), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher matcherMAIL = Pattern.compile("([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+\\.[a-zA-Z0-9._-]+)").matcher(txt);
        while (matcherMAIL.find()) {
            int st = matcherMAIL.start();
            int en = st + matcherMAIL.group(0).length();
            this.ws.setSpan(new myClickableSpan(colorMAIL, underlineMAIL, "mail"), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher matcherPHONE = Pattern.compile("\\d{13}|\\d{12}|\\d{11}|\\d{10}|(?:\\d{3}-){2}\\d{4}|\\(\\d{3}\\)\\d{3}-?\\d{4}").matcher(txt);
        while (matcherPHONE.find()) {
            int st = matcherPHONE.start();
            int en = st + matcherPHONE.group(0).length();
            this.ws.setSpan(new myClickableSpan(colorPHONE, underlinePHONE, "phone"), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (regexCUSTOM != null && !regexCUSTOM.isEmpty()) {
            Matcher matcherCUSTOM = Pattern.compile(regexCUSTOM).matcher(txt);
            while (matcherCUSTOM.find()) {
                int st = matcherCUSTOM.start();
                int en = st + matcherCUSTOM.group(0).length();
                this.ws.setSpan(new myClickableSpan(colorCUSTOM, underlineCUSTOM, "custom"), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return this;
    }

    // listener
    public void setClickListener(ClickListener clickListener) {
        this.clickListener = clickListener;
    }

    // create highlight
    public WordToSpan setHighlight(String txt, String ky) {
        this.key = ky;
        this.ws = new SpannableString(txt);

        if (!key.isEmpty()) {
            Matcher matcherONE = Pattern.compile("(?i)" + key.trim()).matcher(txt);
            while (matcherONE.find()) {
                int st = matcherONE.start();
                int en = st + matcherONE.group(0).length();
                this.ws.setSpan(new ForegroundColorSpan(colorHIGHLIGHT), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                this.ws.setSpan(new BackgroundColorSpan(backgroundHIGHLIGHT), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            for (String retval : key.split(" ")) {
                Matcher matcherALL = Pattern.compile("(?i)" + retval.trim()).matcher(txt);
                while (matcherALL.find()) {
                    int st = matcherALL.start();
                    int en = st + matcherALL.group(0).length();
                    this.ws.setSpan(new ForegroundColorSpan(colorHIGHLIGHT), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    this.ws.setSpan(new BackgroundColorSpan(backgroundHIGHLIGHT), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        return this;
    }

    public WordToSpan into(View textView) {
        tv = (TextView) textView;
        tv.setText(ws);
        if (key == null) {
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setHighlightColor(Color.TRANSPARENT);
        }
        return this;
    }

    // interface
    public interface ClickListener {
        void onClick(String type, String text);
    }

    // click
    private class myClickableSpan extends ClickableSpan {
        int color;
        String type;
        boolean underline;

        myClickableSpan(int color, boolean underline, String type) {
            this.color = color;
            this.underline = underline;
            this.type = type;
        }

        @Override
        public void onClick(View textView) {
            Spanned s = (Spanned) tv.getText();
            int start = s.getSpanStart(this);
            int end = s.getSpanEnd(this);
            clickListener.onClick(type, s.subSequence(start, end).toString().trim());
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(color);
            ds.setUnderlineText(underline);
        }
    }
}
