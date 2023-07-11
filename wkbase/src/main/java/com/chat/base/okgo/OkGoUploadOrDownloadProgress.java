package com.chat.base.okgo;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2020-05-06 15:39
 * 文件上传下载进度监听
 */
public class OkGoUploadOrDownloadProgress {
    private OkGoUploadOrDownloadProgress() {
    }

    private static class OkGoUploadOrDownloadProgressBinder {
        static final OkGoUploadOrDownloadProgress okGoUpload = new OkGoUploadOrDownloadProgress();
    }

    public static OkGoUploadOrDownloadProgress getInstance() {
        return OkGoUploadOrDownloadProgressBinder.okGoUpload;
    }

    private ConcurrentHashMap<Object, IProgress> progressList;

    public interface IProgress {
        void onProgress(Object tag, float progress);

        void onSuccess(Object tag, String path);
    }

    public synchronized void addProgress(Object tag, IProgress iProgress) {
        if (progressList == null)
            progressList = new ConcurrentHashMap<>();
        progressList.put(tag, iProgress);
    }

    public void onSuccess(Object tag, String path) {
        if (tag != null && progressList != null && progressList.containsKey(tag)) {
            Objects.requireNonNull(progressList.get(tag)).onSuccess(tag, path);
        }
    }

    public void seekProgress(Object tag, float progress) {
        if (tag != null && progressList != null && progressList.containsKey(tag)) {
            Objects.requireNonNull(progressList.get(tag)).onProgress(tag, progress);
        }
    }

    public void removeProgress(Object tag) {
        if (tag != null && progressList != null) {
            progressList.remove(tag);
        }
    }

}
