package com.chat.base.okgo;

import java.io.Serializable;

/**
 * 2020-06-19 11:21
 * 文件上传下载标识
 */
public class UploadOrDownloadTaskTag implements Serializable {
    public Object index;

    UploadOrDownloadTaskTag(Object index) {
        this.index = index;
    }
}
