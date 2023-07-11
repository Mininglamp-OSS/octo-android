package com.chat.base.okgo;

/**
 * 2020-08-03 11:33
 * 下载文件监听
 */
public interface IDownloadFile {
    void onSuccess(Object tag, String filePath);

    void onFail(Object tag);

    void onProgress(Object tag, float progress);
}
