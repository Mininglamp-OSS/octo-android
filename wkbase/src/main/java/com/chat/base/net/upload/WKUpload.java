package com.chat.base.net.upload;


import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.okgo.UploadResultEntity;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class WKUpload extends WKBaseModel {
    private WKUpload() {
    }

    private static class UploadBinder {
        final static WKUpload upload = new WKUpload();
    }

    public static WKUpload getInstance() {
        return UploadBinder.upload;
    }

    public void upload(String uploadUrl, String filePath, Object tag, final IUploadBack iUploadBack) {
        MediaType mediaType = MediaType.Companion.parse("multipart/form-data");
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.Companion.create(file, mediaType);

        FileRequestBody fileRequestBody = new FileRequestBody(fileBody, tag);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), fileRequestBody);
        request(createService(UploadService.class).upload(uploadUrl, part), new IRequestResultListener<UploadResultEntity>() {
            @Override
            public void onSuccess(UploadResultEntity result) {
                iUploadBack.onSuccess(result.path);
            }

            @Override
            public void onFail(int code, String msg) {
                iUploadBack.onError();
            }
        });
    }

    public interface IUploadBack {
        void onSuccess(String url);

        void onError();
    }
}
