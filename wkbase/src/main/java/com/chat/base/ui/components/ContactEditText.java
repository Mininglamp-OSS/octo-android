package com.chat.base.ui.components;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.core.content.ContextCompat;

import com.chat.base.R;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.utils.StringUtils;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ContactEditText extends AppCompatAutoCompleteTextView {
    private int itemPadding;

    public ContactEditText(Context context) {
        super(context);
        init();
    }


    public ContactEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ContactEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        itemPadding = dip2px(getContext(), 3);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        MyImageSpan[] spans = getText().getSpans(0, getText().length(), MyImageSpan.class);
        for (MyImageSpan myImageSpan : spans) {
            if (getText().getSpanEnd(myImageSpan) - 1 == selStart) {
                selStart = selStart + 1;
                setSelection(selStart);
                break;
            }
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    private void flushSpans() {
        Editable editText = getText();
        Spannable spannableString = new SpannableString(editText);
        MyImageSpan[] spans = spannableString.getSpans(0, editText.length(), MyImageSpan.class);
        List<UnSpanText> texts = getAllTexts(spans, editText);
        for (UnSpanText unSpanText : texts) {
            if (!TextUtils.isEmpty(unSpanText.showText.toString().trim())) {
                generateOneSpan(spannableString, unSpanText);
            }
        }
        setText(spannableString);
        setSelection(spannableString.length());
    }

    private List<UnSpanText> getAllTexts(MyImageSpan[] spans, Editable edittext) {
        List<UnSpanText> texts = new ArrayList<>();
        int start;
        int end;
        CharSequence text;
        List<Integer> sortStartEnds = new ArrayList<>();
        sortStartEnds.add(0);
        for (MyImageSpan myImageSpan : spans) {
            sortStartEnds.add(edittext.getSpanStart(myImageSpan));
            sortStartEnds.add(edittext.getSpanEnd(myImageSpan));
        }
        sortStartEnds.add(edittext.length());
        Collections.sort(sortStartEnds);
        for (int i = 0; i < sortStartEnds.size(); i = i + 2) {
            start = sortStartEnds.get(i);
            end = sortStartEnds.get(i + 1);
            text = edittext.subSequence(start, end);
            if (!TextUtils.isEmpty(text)) {
                texts.add(new UnSpanText(start, end, text, spans[i].uid));
            }
        }

        return texts;
    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
//            flushSpans();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }

    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        super.performFiltering(text, keyCode);
    }

    //添加一个Span
    public void addSpan(String showText, String uid) {
        int index = getSelectionStart();
        getText().insert(index, showText);
        SpannableString spannableString = new SpannableString(getText());
        generateOneSpan(spannableString, new UnSpanText(index, index + showText.length(), showText, uid));
        setText(spannableString);
        setSelection(index + showText.length());
    }

    private void generateOneSpan(Spannable spannableString, UnSpanText unSpanText) {
        View spanView = getSpanView(getContext(), unSpanText.showText.toString(), getMeasuredWidth());
        BitmapDrawable bitmapDrawable = (BitmapDrawable) convertViewToDrawable(spanView);
        //   Bitmap bmp = getBitmap(getContext(),28f, unSpanText.showText.toString());
        bitmapDrawable.setBounds(0, 0, bitmapDrawable.getIntrinsicWidth(), bitmapDrawable.getIntrinsicHeight());
//        MyImageSpan what = new MyImageSpan(getContext(),bmp, unSpanText.showText.toString(), unSpanText.uid);
        MyImageSpan what = new MyImageSpan(bitmapDrawable, unSpanText.showText.toString(), unSpanText.uid);
        final int start = unSpanText.start;
        final int end = unSpanText.end;
        spannableString.setSpan(what, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public Drawable convertViewToDrawable(View view) {
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(spec, spec);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap b = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate(-view.getScrollX(), -view.getScrollY());
        view.draw(c);
        view.setDrawingCacheEnabled(true);
        Bitmap cacheBmp = view.getDrawingCache();
        Bitmap viewBmp = cacheBmp.copy(Bitmap.Config.ARGB_8888, true);
        cacheBmp.recycle();
        view.destroyDrawingCache();
        return new BitmapDrawable(viewBmp);
    }

    private Bitmap getBitmap(Context context, float size, String name) {
        Paint paint = new Paint();
        paint.setColor(context.getResources().getColor(R.color.black));
        paint.setAntiAlias(true);
        paint.setTextSize(size);
        // paint.setTextAlign(Paint.Align.CENTER);
        // LogUtil.d("5454",""+editText.getTextSize());
        Rect rect = new Rect();
        paint.getTextBounds(name, 0, name.length(), rect);
        //获得字符串在屏幕上的长度
        int width = (int) (paint.measureText(name));
        int height = (int) (paint.getFontSpacing());
        Paint.FontMetricsInt fontMetrics = paint.getFontMetricsInt();
        float fontTotalHeight = fontMetrics.bottom - fontMetrics.top;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        //(行高-字体高度)/2+字体高度-
//     canvas.drawText(name, rect.left, rect.height() - rect.bottom, paint);
        float offY = fontTotalHeight / 2;
        float newY = rect.height() / 2 + offY;
        //LogUtil.e("atren", "fontMetrics.bottom=" + fontMetrics.bottom + ",fontMetrics.top=" + fontMetrics.top + ",rect.height()=" + rect.height() + ",rect.left=" + rect.left + ",rect.bottom=" + rect.bottom + ",bmp.height=" + bmp.getHeight());
        //canvas.drawColor(context.getColor(R.color.fuschiaColor));
        canvas.drawText(name, 0, newY - rect.bottom - 1, paint);
        return bmp;
    }

    public View getSpanView(Context context, String text, int maxWidth) {
        TextView view = new TextView(context);
        view.setMaxWidth(maxWidth);
        view.setText(text);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setSingleLine(true);
        //设置文字框背景色
        view.setBackgroundResource(R.drawable.shape_corner_rectangle);
        view.setTextSize(getTextSize());
        //设置文字颜色
        setGravity(Gravity.CENTER_VERTICAL);
        //view.setTextColor(getCurrentTextColor());
        view.setTextColor(ContextCompat.getColor(context, R.color.colorDark));
        FrameLayout frameLayout = new FrameLayout(context);
        // frameLayout.setPadding(0, itemPadding, 0, itemPadding);
        frameLayout.addView(view);
        return frameLayout;
    }

    private static class UnSpanText {
        int start;
        int end;
        CharSequence showText;
        String uid;

        UnSpanText(int start, int end, CharSequence showText, String uid) {
            this.start = start;
            this.end = end;
            this.showText = showText;
            this.uid = uid;
        }
    }

    private static class MyImageSpan extends ImageSpan {
        private final String showText;
        private final String uid;

        public MyImageSpan(Drawable d, String showText, String uid) {
            super(d);
            this.showText = showText;
            this.uid = uid;
        }

        //        public MyImageSpan(Context c, Bitmap drawable, String showText, String uid) {
//            super(c, drawable);
//            this.showText = showText;
//            this.uid = uid;
//        }
//
//
//        @Override
//        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
//                         @NonNull Paint paint) {
//
//            Drawable b = getDrawable();
//            canvas.save();
//            int transY;
//            //要显示的文本高度-图片高度除2等居中位置+top(换行情况)
//            transY = ((bottom - top) - b.getBounds().bottom) / 2 + top;
//            //偏移画布后开始绘制
//            canvas.translate(x, transY);
//            b.draw(canvas);
//            canvas.restore();
//        }
        public String getUid() {
            return uid;
        }

        public String getShowText() {
            return showText;
        }
    }

    /**
     * dip转换px
     */
    public static int dip2px(Context context, int dip) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dip * scale + 0.5f);
    }

    public List<String> getAllUIDs() {
        List<String> uids = new ArrayList<>();
        Editable editText = getText();
        Spannable spannableString = new SpannableString(editText);
        MyImageSpan[] spans = spannableString.getSpans(0, editText.length(), MyImageSpan.class);
        for (MyImageSpan span : spans) {
            uids.add(span.getUid());
        }
        return uids;
    }


    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(CLIPBOARD_SERVICE);
            assert cm != null;
            ClipData data = cm.getPrimaryClip();
            assert data != null;
            ClipData.Item item = data.getItemAt(0);
            String editContent = "";
            if (item.getText() != null)
                editContent = item.getText().toString();
            //调用剪贴板
            ClipboardManager clip = (ClipboardManager) getContext().getSystemService(CLIPBOARD_SERVICE);
            //改变剪贴板中Content
            if (clip != null) {
                int curPosition = getSelectionStart();
                StringBuilder sb = new StringBuilder(Objects.requireNonNull(getText()).toString());
                sb.insert(curPosition, editContent);
//                this.setText(sb.toString());
                this.setText(MoonUtil.getEmotionContent(getContext(), this, sb.toString()));
                // 将光标设置到新增完表情的右侧
                this.setSelection(curPosition + editContent.length() );
                return true;
            }
            // clip.setText("改变剪贴板中Content" + clip.getText());
        }
        return super.onTextContextMenuItem(id);
    }

    public List<WKMsgEntity> getAllEntity() {
        List<WKMsgEntity> list = new ArrayList<>();
        Spannable spannableString = new SpannableString(getEditableText());
        String spannedString = Objects.requireNonNull(getText()).toString();
        int next;
        for (int i = 0; i < spannableString.length(); i = next) {
            next = spannableString.nextSpanTransition(i, spannedString.length(), CharacterStyle.class);
            MyImageSpan[] mentionSpans = spannableString.getSpans(i, next, MyImageSpan.class);
            for (MyImageSpan mentionSpan : mentionSpans) {
                WKMsgEntity entity = new WKMsgEntity();
                entity.type = ChatContentSpanType.getMention();
                entity.offset = i;
                entity.length = next - i;
                entity.value = mentionSpan.uid;
                list.add(entity);
            }
        }

        List<String> urls = StringUtils.getStrUrls(spannedString);
        for (String url : urls) {
            int fromIndex = 0;
            while (fromIndex >= 0) {
                fromIndex = spannedString.indexOf(url, fromIndex);
                if (fromIndex >= 0) {
                    WKMsgEntity entity = new WKMsgEntity();
                    entity.type = ChatContentSpanType.getLink();
                    entity.offset = fromIndex;
                    entity.length = url.length();
                    entity.value = url;
                    list.add(entity);
                    fromIndex += url.length();
                }
            }
        }

        List<String> emails = StringUtils.getEmails(spannedString);
        for (String email : emails) {
            int fromIndex = 0;
            while (fromIndex >= 0) {
                fromIndex = spannedString.indexOf(email, fromIndex);
                if (fromIndex >= 0) {
                    WKMsgEntity entity = new WKMsgEntity();
                    entity.type = ChatContentSpanType.getLink();
                    entity.offset = fromIndex;
                    entity.length = email.length();
                    entity.value = email;
                    list.add(entity);
                    fromIndex += email.length();
                }
            }
        }


        List<String> phones = StringUtils.getNumbers(spannedString);
        for (String phone : phones) {
            int fromIndex = 0;
            while (fromIndex >= 0) {
                fromIndex = spannedString.indexOf(phone, fromIndex);
                if (fromIndex >= 0) {
                    WKMsgEntity entity = new WKMsgEntity();
                    entity.type = ChatContentSpanType.getLink();
                    entity.offset = fromIndex;
                    entity.length = phone.length();
                    entity.value = phone;
                    list.add(entity);
                    fromIndex += phone.length();
                }
            }
        }

        return list;
    }
}
