package com.chat.base.net.ud;


import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.base.net.entity.UploadResultEntity;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKTimeUtils;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class WKUploader extends WKBaseModel {
    private WKUploader() {
    }

    private static class UploadBinder {
        final static WKUploader upload = new WKUploader();
    }

    public static WKUploader getInstance() {
        return UploadBinder.upload;
    }

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
            public void onSuccess(UploadResultEntity result) {
                if (iUploadBack != null ) {
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


    public interface IGetUploadFileUrl {
        void onResult(String url, String fileUrl);
    }

    public interface IUploadBack {
        void onSuccess(String url);

        void onError();
    }
}
