package com.chat.base.utils;

import android.text.TextUtils;

import com.chat.base.config.WKSharedPreferencesUtil;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 2021/7/23 12:25
 * 获取html数据
 */
public class WKJsoupUtils {
    private WKJsoupUtils() {
    }

    private static class JsoupUtilsBinder {
        final static WKJsoupUtils jsoup = new WKJsoupUtils();
    }

    public static WKJsoupUtils getInstance() {
        return JsoupUtilsBinder.jsoup;
    }

    public void getURLContent(String url, String clientMsgNo) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(clientMsgNo)) return;

        Observable.create((ObservableOnSubscribe<WKURLContent>) emitter -> {
            String tempURL = url;
            if (url.startsWith("www") || url.startsWith("WWW")) {
                tempURL = "http://" + url;
            }
            Document document = Jsoup.connect(tempURL).get();
            if (document != null) {
                String title = document.head().getElementsByTag("title").text();
                Elements elements = document.head().getElementsByTag("meta");
                String htmlContent = "";
                String coverURL = "";
                for (Element element : elements) {
                    String name = element.attr("name");
                    String content = element.attr("content");
                    if (name.equals("description")) {
                        htmlContent = content;
                        if (!TextUtils.isEmpty(coverURL))
                            break;
                    }
                    String property = element.attr("property");
                    if (property.equals("og:image")) {
                        coverURL = content;
                        if (!TextUtils.isEmpty(htmlContent)) {
                            break;
                        }
                    }
                }
                WKURLContent wkurlContent = new WKURLContent();
                wkurlContent.content = htmlContent;
                wkurlContent.title = title;
                wkurlContent.url = url;
                wkurlContent.coverURL = coverURL;
                emitter.onNext(wkurlContent);
            }
        }).observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribe(new Observer<WKURLContent>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {

            }

            @Override
            public void onNext(@NonNull WKJsoupUtils.WKURLContent wkUrlContent) {
                if (!TextUtils.isEmpty(wkUrlContent.title) && !TextUtils.isEmpty(wkUrlContent.content)) {
                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put("title", wkUrlContent.title);
                        jsonObject.put("content", wkUrlContent.content);
                        jsonObject.put("coverURL", wkUrlContent.coverURL);
                        jsonObject.put("logo", wkUrlContent.url + "/favicon.ico");
                        jsonObject.put("expirationTime", WKTimeUtils.getInstance().getCurrentSeconds());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    WKMsg wkMsg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
                    if (wkMsg != null) {
//                        if (wkMsg.extraMap == null)
//                            wkMsg.extraMap = new HashMap<String, Object>();
//                        wkMsg.extraMap.put("link_url", url);
//                        wkMsg.extraMap.put("link_title", wkUrlContent.title);
//                        wkMsg.extraMap.put("link_content", wkUrlContent.content);
//                        wkMsg.extraMap.put("link_coverURL", wkUrlContent.coverURL);
//                        wkMsg.extraMap.put("link_logo", wkUrlContent.url + "/favicon.ico");
                        WKIM.getInstance().getMsgManager().setRefreshMsg(wkMsg, true);
                    }
                    WKSharedPreferencesUtil.getInstance().putSP(wkUrlContent.url, jsonObject.toString());
                }

            }

            @Override
            public void onError(@NonNull Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    private static class WKURLContent {
        public String title;
        public String url;
        public String content;
        public String coverURL;
    }
}
