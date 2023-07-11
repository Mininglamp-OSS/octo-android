package com.chat.base.okgo;

/**
 * 2020-06-19 09:59
 * 上传返回
 */
public class UploadResultEntity {
    public String name;
    public long size;
    public String fid;
    public String url;
    public String path;

    @Override
    public String toString() {
        return "UploadResultEntity{" +
                "name='" + name + '\'' +
                ", size=" + size +
                ", fid='" + fid + '\'' +
                ", url='" + url + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
