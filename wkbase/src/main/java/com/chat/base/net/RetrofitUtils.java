package com.chat.base.net;

import com.chat.base.config.WKApiConfig;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;

/**
 * 2020-07-17 14:52
 * Retrofit管理
 */
public class RetrofitUtils {
    private RetrofitUtils() {
    }

    private static class RetrofitUtilsBinder {
        final static RetrofitUtils retrofit = new RetrofitUtils();
    }

    public static RetrofitUtils getInstance() {
        return RetrofitUtilsBinder.retrofit;
    }

    private Retrofit retrofit;

    public Retrofit getRetrofit() {
        if (retrofit == null) {
            synchronized (RetrofitUtils.class) {
                retrofit = new Retrofit.Builder()
                        .baseUrl(WKApiConfig.baseUrl)
                        .client(OkHttpUtils.getInstance().getOkHttpClient())
                        .addConverterFactory(FastJsonConverterFactory.Companion.create())
                        .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build();
            }
            // GsonConverterFactory.create(new GsonBuilder().setLenient().create())
        }
        return retrofit;
    }

    public void resetRetrofit() {
        retrofit = null;
    }


    public <T> T createService(Class<T> service) {
        return getRetrofit().create(service);
    }

}
