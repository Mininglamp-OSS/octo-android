package com.chat.base.okgo;

import androidx.annotation.NonNull;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.request.GetRequest;
import com.lzy.okserver.OkDownload;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 2020-06-17 16:12
 * 文件下载
 */
public class OkGoDownload {
    private OkGoDownload() {

    }

    private static class OkGoDownloadBinder {
        final static OkGoDownload okgoDownload = new OkGoDownload();
    }

    public static OkGoDownload getInstance() {
        return OkGoDownloadBinder.okgoDownload;
    }

    private static class DownloadInfo {
        public String url;
        public Object tag;
        public String filePath;
        public IDownloadFile iDownloadFile;

        DownloadInfo(String url, Object tag, String filePath, IDownloadFile iDownloadFile) {
            this.url = url;
            this.tag = tag;
            this.filePath = filePath;
            this.iDownloadFile = iDownloadFile;
        }
    }

    private final HashMap<String, DownloadInfo> downloadList = new HashMap<>();

    public synchronized void downloadFileOneByOne(String url, Object tag, @NonNull String filePath, final IDownloadFile iDownloadFile) {
        DownloadInfo info = new DownloadInfo(url, tag, filePath, iDownloadFile);
        if (downloadList.containsKey(String.valueOf(tag)))
            return;
        downloadList.put(String.valueOf(tag), info);
        if (downloadList.size() == 1) {
            download(info);
        }
    }

    private void download(DownloadInfo info) {
        GetRequest<File> request = OkGo.<File>get(info.url)
                .headers("token", WKConfig.getInstance().getToken());
        OkDownload.request(info.url, request)
                .priority(new Random().nextInt(100))
                .extra1(new UploadOrDownloadTaskTag(info.tag))
//                .fileName(UUID.randomUUID().toString().replaceAll("-", "") + "." + format)
                .save().folder(WKBaseApplication.getInstance().getOkGoDownloadDir())
                .register(new DownloadProgress(info.filePath, new IDownloadFile() {
                    @Override
                    public void onSuccess(Object tag, String filePath) {
                        info.iDownloadFile.onSuccess(tag, filePath);
                        if (tag != null && downloadList.get(String.valueOf(tag)) != null) {
                            downloadList.remove(String.valueOf(tag));
                        }
                        for (Map.Entry<String, DownloadInfo> m : downloadList.entrySet()) {
                            download(m.getValue());
                            break;
                        }
                    }

                    @Override
                    public void onFail(Object tag) {
                        info.iDownloadFile.onFail(tag);
                        if (tag != null && downloadList.get(String.valueOf(tag)) != null) {
                            downloadList.remove(String.valueOf(tag));
                        }
                        for (Map.Entry<String, DownloadInfo> m : downloadList.entrySet()) {
                            download(m.getValue());
                            break;
                        }
                    }

                    @Override
                    public void onProgress(Object tag, float progress) {
                        info.iDownloadFile.onProgress(tag, progress);
                    }
                })).save().start();
    }

    public synchronized void downloadFile(String url, Object tag, String filePath, final IDownloadFile iDownloadFile) {
        GetRequest<File> request = OkGo.<File>get(url)
                .headers("token", WKConfig.getInstance().getToken());
        OkDownload.request(url, request)
                .priority(new Random().nextInt(100))
                .extra1(new UploadOrDownloadTaskTag(tag))
                .save()
                .register(new DownloadProgress(filePath, iDownloadFile))
                .start();
    }
}
