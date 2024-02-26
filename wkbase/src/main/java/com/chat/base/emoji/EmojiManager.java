package com.chat.base.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Xml;

import androidx.collection.LruCache;

import com.chat.base.WKBaseApplication;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class EmojiManager {

    private final String EMOT_DIR = "emoji/";

    // max cache size
    private final int CACHE_MAX_SIZE = 1024;

    private Pattern pattern;

    // default entries
    private final List<Entry> defaultEntries = new ArrayList<>();
    // text to entry
    private final Map<String, Entry> text2entry = new HashMap<>();
    // asset bitmap cache, key: asset path
    private LruCache<String, Bitmap> drawableCache;

    private String patternStr = "";

    private EmojiManager() {

    }

    private static class EmojiManagerBinder {
        final static EmojiManager emoji = new EmojiManager();
    }

    public static EmojiManager getInstance() {
        return EmojiManagerBinder.emoji;
    }

    public void init() {

        Context context = WKBaseApplication.getInstance().getContext();

        load(context, EMOT_DIR + "emoji.xml");
        pattern = makePattern();
        drawableCache = new LruCache<String, Bitmap>(CACHE_MAX_SIZE) {
            @Override
            protected void entryRemoved(boolean evicted, @NotNull String key, @NotNull Bitmap oldValue, Bitmap newValue) {
                if (oldValue != newValue)
                    oldValue.recycle();
            }
        };


    }

    private static class Entry {
        String text;
        String assetPath;
        String id;

        Entry(String id, String text, String assetPath) {
            this.text = text;
            this.id = id;
            this.assetPath = assetPath;
        }
    }

    public int getDisplayCount() {
        return defaultEntries.size();
    }

    public Drawable getDisplayDrawable(Context context, int index) {
        String text = (index >= 0 && index < defaultEntries.size() ?
                defaultEntries.get(index).text : null);
        return text == null ? null : getDrawable(context, text);
    }

    public String getDisplayText(int index) {
        return index >= 0 && index < defaultEntries.size() ? defaultEntries
                .get(index).text : null;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public Drawable getDrawable(Context context, String text) {
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }

        Bitmap cache = drawableCache.get(entry.assetPath);
        if (cache == null) {
            cache = loadAssetBitmap(context, entry.assetPath);
        }
        return new BitmapDrawable(context.getResources(), cache);
    }

    //
    // internal
    //

    private Pattern makePattern() {
        return Pattern.compile(patternOfDefault());
    }

    private String patternOfDefault() {
//        return "\\[[^\\[]{1,10}\\]";
        if (TextUtils.isEmpty(patternStr)) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0, size = defaultEntries.size(); i < size; i++) {
                if (!sb.toString().endsWith("(")) {
                    sb.append("|");
                }
                sb.append(defaultEntries.get(i).text);
            }
            sb.append(")");
            patternStr = sb.toString();
        }
        return patternStr;
        // return "[^\\u0000-\\uFFFF]";
    }

    private Bitmap loadAssetBitmap(Context context, String assetPath) {
        InputStream is = null;
        try {
            Resources resources = context.getResources();
            Options options = new Options();
            options.inDensity = DisplayMetrics.DENSITY_HIGH;
            options.inScreenDensity = resources.getDisplayMetrics().densityDpi;
            options.inTargetDensity = resources.getDisplayMetrics().densityDpi;
            is = context.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(is, new Rect(), options);
            if (bitmap != null) {
                drawableCache.put(assetPath, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void load(Context context, String xmlPath) {
        new EntryLoader().load(context, xmlPath);
    }

    //
    // load emoticons from asset
    //
    private class EntryLoader extends DefaultHandler {
        private String catalog = "";

        void load(Context context, String assetPath) {
            InputStream is = null;
            try {
                is = context.getAssets().open(assetPath);
                Xml.parse(is, Xml.Encoding.UTF_8, this);
            } catch (IOException | SAXException e) {
                e.printStackTrace();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("Catalog")) {
                catalog = attributes.getValue(uri, "Title");
            } else if (localName.equals("Emoticon")) {
                String tag = attributes.getValue(uri, "Tag");
                String id = attributes.getValue(uri, "ID");
                String fileName = attributes.getValue(uri, "File");
                Entry entry = new Entry(id, tag, EMOT_DIR + catalog + "/" + fileName);
                text2entry.put(entry.text, entry);
                if (catalog.equals("default")) {
                    defaultEntries.add(entry);
                }
            }
        }
    }

    public boolean isHeart(String tag) {
        if (!text2entry.containsKey(tag)) return false;
        return Objects.requireNonNull(text2entry.get(tag)).id.equals("2_0")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_1")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_2")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_3")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_4")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_5")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_6")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_7")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_8");
    }


    public List<String> getEmojiWithType(String type) {
        List<String> list = new ArrayList<>();
        for (int i = 0, size = defaultEntries.size(); i < size; i++) {
            boolean isAdd = true;
            for (String str : list){
                if (str.equals(defaultEntries.get(i).text)){
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                if (defaultEntries.get(i).id.startsWith(type)) {
                    list.add(defaultEntries.get(i).text);
                }
            }
        }
        return list;
    }
}
