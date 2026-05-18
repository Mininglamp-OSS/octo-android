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

//package com.chat.base.net;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//
//import com.aghajari.rlottie.network.AXrLottieFetchResult;
//import com.aghajari.rlottie.network.AXrLottieNetworkFetcher;
//
//import java.io.IOException;
//import java.io.InputStream;
//
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
//
//public class OkHttpNetworkFetcher extends AXrLottieNetworkFetcher {
//
//    OkHttpClient client = null;
//
//    private OkHttpNetworkFetcher() {
//    }
//
//    public static OkHttpNetworkFetcher create() {
//        return new OkHttpNetworkFetcher();
//    }
//
//    @Override
//    protected void updateClient() {
////        client = new OkHttpClient.Builder()
////                .followRedirects(true).followSslRedirects(true)
////                .connectTimeout(getConnectTimeout(), TimeUnit.MILLISECONDS)
////                .readTimeout(getReadTimeout(), TimeUnit.MILLISECONDS)
////                .build();
//        client = OkHttpUtils.getInstance().getOkHttpClient();
//    }
//
//    @NonNull
//    @Override
//    public AXrLottieFetchResult fetchSync(@NonNull String url) throws IOException {
//        if (client == null) updateClient();
//
//        Request request = new Request.Builder().url(url).build();
//        Response response = client.newCall(request).execute();
//
//        return new OkHttpNetworkFetchResult(response);
//    }
//
//    public static class OkHttpNetworkFetchResult implements AXrLottieFetchResult {
//
//        @NonNull
//        private final Response response;
//
//        public OkHttpNetworkFetchResult(@NonNull Response response) {
//            this.response = response;
//        }
//
//        @Override
//        public boolean isSuccessful() {
//            return response.isSuccessful();
//        }
//
//        @NonNull
//        @Override
//        public InputStream bodyByteStream() throws IOException {
//            return response.body().byteStream();
//        }
//
//        @Nullable
//        @Override
//        public String contentType() {
//            String contentType = null;
//            if (response.body().contentType() != null)
//                contentType = response.body().contentType().toString();
//            return contentType;
//        }
//
//        @Nullable
//        @Override
//        public String error() {
//            return isSuccessful() ? null :
//                    "Unable to fetch " + response.request().url() +
//                            ". Failed with " + response.code() + "\n" +
//                            response.message();
//        }
//
//        @Override
//        public void close() {
//            try {
//                response.body().close();
//                response.close();
//            } catch (Exception ignore) {
//            }
//        }
//
//    }
//}