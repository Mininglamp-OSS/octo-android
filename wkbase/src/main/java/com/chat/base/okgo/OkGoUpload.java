package com.chat.base.okgo;

import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.base.utils.WKTimeUtils;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.convert.StringConvert;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.request.PostRequest;
import com.lzy.okserver.OkUpload;
import com.lzy.okserver.upload.UploadListener;
import com.lzy.okserver.upload.UploadTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

/**
 * 2019-12-15 16:39
 * 文件上传
 */
public class OkGoUpload extends WKBaseModel {

    public static OkGoUpload getInstance() {
        return OkUploadHolder.instance;
    }

    private static class OkUploadHolder {
        private static final OkGoUpload instance = new OkGoUpload();
    }

    private OkGoUpload() {
    }


    //获取聊天上传地址
    public void getChatUploadFileUrl(String channelId, byte channelType, String localPath, final IGetUploadFileUrl iGetUploadFileUrl) {
        getUploadFileUrl(channelId, channelType, localPath, iGetUploadFileUrl);
    }


    private void getUploadFileUrl(String channelID, byte channelType, String localPath, final IGetUploadFileUrl iGetUploadFileUrl) {
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

    public void uploadCommonFile(String uploadUrl, String filePath, Object tag, final ICommonListener iCommonListener) {
        PostRequest<String> postRequest = OkGo.<String>post(uploadUrl)
                .tag(UUID.randomUUID().toString().replaceAll("-", ""))
                .headers("token", WKConfig.getInstance().getToken()).params("file", new File(filePath))
                .converter(new StringConvert());
        UploadTask<String> task = OkUpload.request(tag + "", postRequest)
                .extra1(new UploadOrDownloadTaskTag(tag))
                .save().register(new UploadListener<String>(tag) {
                    @Override
                    public void onStart(Progress progress) {

                    }

                    @Override
                    public void onProgress(Progress progress) {
                        OkGoUploadOrDownloadProgress.getInstance().seekProgress(((UploadOrDownloadTaskTag) progress.extra1).index, progress.fraction);
                    }

                    @Override
                    public void onError(Progress progress) {
                        iCommonListener.onResult(HttpResponseCode.error, "");
                    }

                    @Override
                    public void onFinish(String resultEntity, Progress progress) {
                        int code = HttpResponseCode.success;
                        String msg = "";
                        if (!TextUtils.isEmpty(resultEntity)) {
                            try {
                                JSONObject jsonObject = new JSONObject(resultEntity);
                                if (jsonObject.has("status")) {
                                    code = jsonObject.optInt("status");
                                }
                                if (jsonObject.has("msg"))
                                    msg = jsonObject.optString("msg");
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        iCommonListener.onResult(code, msg);
                    }

                    @Override
                    public void onRemove(Progress progress) {

                    }
                });
        task.start();
    }

    /**
     * 上传聊天文件
     *
     * @param uploadUrl       上传地址
     * @param filePath        文件路径
     * @param tag             本次上传唯一标志
     * @param iUploadListener 返回
     */
    public void uploadFile(String uploadUrl, String filePath, Object tag, final IUploadListener iUploadListener) {
        PostRequest<UploadResultEntity> postRequest = OkGo.<UploadResultEntity>post(uploadUrl)
                .tag(tag + "")
                .headers("token", WKConfig.getInstance().getToken()).params("file", new File(filePath))
                .converter(new UploadResultCovert());
        UploadTask<UploadResultEntity> task = OkUpload.request(tag + "", postRequest)
                .extra1(new UploadOrDownloadTaskTag(tag))
                .save().register(new UploadListener<UploadResultEntity>(tag) {
                    @Override
                    public void onStart(Progress progress) {

                    }

                    @Override
                    public void onProgress(Progress progress) {
                        OkGoUploadOrDownloadProgress.getInstance().seekProgress(((UploadOrDownloadTaskTag) progress.extra1).index, progress.fraction);
                    }

                    @Override
                    public void onError(Progress progress) {
                        iUploadListener.onError();
                    }

                    @Override
                    public void onFinish(UploadResultEntity resultEntity, Progress progress) {
                        if (resultEntity != null && !TextUtils.isEmpty(resultEntity.path))
                            iUploadListener.onUploadSuccess(resultEntity.path);
                        else {
                            iUploadListener.onError();
                        }
                    }

                    @Override
                    public void onRemove(Progress progress) {

                    }
                });
        task.start();
    }

    public interface IUploadListener {

        void onError();

        void onUploadSuccess(String url);
    }

    /**
     * 移除任务
     */
    public void removeTask(String tag) {
        try {
            if (!TextUtils.isEmpty(tag) && OkUpload.getInstance().hasTask(tag)) {
                OkGo.getInstance().cancelTag(tag);
                OkUpload.getInstance().getTask(tag).remove();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
