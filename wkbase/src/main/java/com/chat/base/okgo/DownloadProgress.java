package com.chat.base.okgo;

import android.text.TextUtils;

import com.chat.base.utils.WKFileUtils;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.download.DownloadListener;

import java.io.File;

/**
 * 2020-08-03 11:19
 * okgo下载监听
 */
public class DownloadProgress extends DownloadListener {
    private final IDownloadFile iDownloadFile;
    private final String savePath;

    public DownloadProgress(String savePath, final IDownloadFile iDownloadFile) {
        super("LogDownloadListener");
        this.savePath = savePath;
        this.iDownloadFile = iDownloadFile;
    }

    @Override
    public void onStart(Progress progress) {
    }

    @Override
    public void onProgress(Progress progress) {
        if (iDownloadFile != null) {
            iDownloadFile.onProgress(((UploadOrDownloadTaskTag) progress.extra1).index, progress.fraction);
        }
        OkGoUploadOrDownloadProgress.getInstance().seekProgress(((UploadOrDownloadTaskTag) progress.extra1).index, progress.fraction);
    }

    @Override
    public void onError(Progress progress) {
        progress.exception.printStackTrace();
        if (iDownloadFile != null) {
            iDownloadFile.onFail(((UploadOrDownloadTaskTag) progress.extra1).index);
        }
    }

    @Override
    public void onFinish(File file, Progress progress) {
        if (iDownloadFile != null) {
            if (!TextUtils.isEmpty(savePath)) {
                WKFileUtils.getInstance().fileCopy(file.getAbsolutePath(), savePath);
                iDownloadFile.onSuccess(((UploadOrDownloadTaskTag) progress.extra1).index, savePath);
            } else
                iDownloadFile.onSuccess(((UploadOrDownloadTaskTag) progress.extra1).index, file.getAbsolutePath());
        }
    }

    @Override
    public void onRemove(Progress progress) {
    }
}
