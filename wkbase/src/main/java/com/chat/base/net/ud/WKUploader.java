package com.chat.base.net.ud;


import android.net.Uri;
import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKTimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WKUploader extends WKBaseModel {
    private WKUploader() {
    }

    private static class UploadBinder {
        final static WKUploader upload = new WKUploader();
    }

    public static WKUploader getInstance() {
        return UploadBinder.upload;
    }

    private volatile OkHttpClient cosClient;

    private OkHttpClient getCosClient() {
        if (cosClient == null) {
            synchronized (this) {
                if (cosClient == null) {
                    cosClient = new OkHttpClient.Builder()
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(120, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return cosClient;
    }

    public void getUploadCredentials(String channelID, byte channelType, String localPath,
                                     IGetUploadCredentials callback) {
        File f = new File(localPath);
        String fileName = f.getName();
        String ext = "";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex >= 0) {
            ext = fileName.substring(dotIndex);
        }
        String contentType = MimeTypeUtils.mimeTypeForExtension(ext);
        long fileSize = f.length();

        String randomName = UUID.randomUUID().toString().replaceAll("-", "");
        String path = "/" + channelType + "/" + channelID + "/" + randomName + ext;

        Uri.Builder uriBuilder = Uri.parse(WKApiConfig.baseUrl + "file/upload/credentials").buildUpon();
        uriBuilder.appendQueryParameter("path", path);
        uriBuilder.appendQueryParameter("type", "chat");
        uriBuilder.appendQueryParameter("filename", fileName);
        uriBuilder.appendQueryParameter("contentType", contentType);
        uriBuilder.appendQueryParameter("fileSize", String.valueOf(fileSize));
        String url = uriBuilder.build().toString();

        request(createService(ApiService.class).getUploadCredentials(url), new IRequestResultListener<UploadFileUrl>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                callback.onResult(result.uploadUrl, result.downloadUrl,
                        !TextUtils.isEmpty(result.contentType) ? result.contentType : contentType,
                        result.contentDisposition);
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e("WKUploader", "getUploadCredentials fail: " + code + " " + msg);
                callback.onResult(null, null, null, null);
            }
        });
    }

    public void getUploadCredentialsWithMime(String channelID, byte channelType, String localPath,
                                             String overrideExt, String overrideContentType,
                                             IGetUploadCredentials callback) {
        File f = new File(localPath);
        String fileName = f.getName();
        long fileSize = f.length();

        String randomName = UUID.randomUUID().toString().replaceAll("-", "");
        String path = "/" + channelType + "/" + channelID + "/" + randomName + overrideExt;

        Uri.Builder uriBuilder = Uri.parse(WKApiConfig.baseUrl + "file/upload/credentials").buildUpon();
        uriBuilder.appendQueryParameter("path", path);
        uriBuilder.appendQueryParameter("type", "chat");
        uriBuilder.appendQueryParameter("filename", fileName);
        uriBuilder.appendQueryParameter("contentType", overrideContentType);
        uriBuilder.appendQueryParameter("fileSize", String.valueOf(fileSize));
        String url = uriBuilder.build().toString();

        request(createService(ApiService.class).getUploadCredentials(url), new IRequestResultListener<UploadFileUrl>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                callback.onResult(result.uploadUrl, result.downloadUrl,
                        !TextUtils.isEmpty(result.contentType) ? result.contentType : overrideContentType,
                        result.contentDisposition);
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e("WKUploader", "getUploadCredentialsWithMime fail: " + code + " " + msg);
                callback.onResult(null, null, null, null);
            }
        });
    }

    public void putUpload(String uploadUrl, String filePath, String contentType,
                          String contentDisposition, Object tag, IUploadBack callback) {
        File file = new File(filePath);
        MediaType mediaType = MediaType.parse(contentType);
        RequestBody fileBody = RequestBody.create(file, mediaType);
        FileRequestBody progressBody = new FileRequestBody(fileBody, tag);

        Request.Builder reqBuilder = new Request.Builder()
                .url(uploadUrl)
                .put(progressBody)
                .header("Content-Type", contentType);

        if (!TextUtils.isEmpty(contentDisposition)) {
            reqBuilder.header("Content-Disposition", contentDisposition);
        }

        getCosClient().newCall(reqBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.post(() -> {
                    if (callback != null) callback.onError();
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (response.isSuccessful()) {
                    handler.post(() -> {
                        if (callback != null) callback.onSuccess("");
                    });
                } else {
                    WKLogUtils.e("WKUploader", "putUpload non-2xx: " + response.code());
                    handler.post(() -> {
                        if (callback != null) callback.onError();
                    });
                }
                response.close();
            }
        });
    }

    // --- Legacy methods kept for compatibility ---

    public void upload(String uploadUrl, String filePath, final IUploadBack iUploadBack) {
        upload(uploadUrl, filePath, filePath, iUploadBack);
    }

    public void upload(String uploadUrl, String filePath, Object tag, final IUploadBack iUploadBack) {
        MediaType mediaType = MediaType.Companion.parse("multipart/form-data");
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.Companion.create(file, mediaType);
        FileRequestBody fileRequestBody = new FileRequestBody(fileBody, tag);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), fileRequestBody);
        request(createService(UploadService.class).upload(uploadUrl, part), new IRequestResultListener<>() {
            @Override
            public void onSuccess(com.chat.base.net.entity.UploadResultEntity result) {
                if (iUploadBack != null) {
                    iUploadBack.onSuccess(result.path);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iUploadBack != null) {
                    iUploadBack.onError();
                }
            }
        });
    }

    public void getUploadFileUrl(String channelID, byte channelType, String localPath, final IGetUploadFileUrl iGetUploadFileUrl) {
        File f = new File(localPath);
        String tempFileName = f.getName();
        String prefix = tempFileName.substring(tempFileName.lastIndexOf(".") + 1);
        String path = "/" + channelType + "/" + channelID + "/" + WKTimeUtils.getInstance().getCurrentMills() + "." + prefix;
        request(createService(ApiService.class).getUploadFileUrl(WKApiConfig.baseUrl + "file/upload?type=chat&path=" + path), new IRequestResultListener<UploadFileUrl>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                iGetUploadFileUrl.onResult(result.url, path);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetUploadFileUrl.onResult(null, path);
            }
        });
    }

    public interface IGetUploadCredentials {
        void onResult(String uploadUrl, String downloadUrl, String contentType, String contentDisposition);
    }

    public interface IGetUploadFileUrl {
        void onResult(String url, String fileUrl);
    }

    public interface IUploadBack {
        void onSuccess(String url);

        void onError();
    }
}
