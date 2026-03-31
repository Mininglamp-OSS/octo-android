package com.xinbida.wukongim.utils;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Objects;

/**
 * 2020-04-01 21:51
 * 文件操作
 */
public class FileUtils {
    private FileUtils() {

    }

    private static class FileUtilsBinder {
        static final FileUtils FILE_UTILS = new FileUtils();
    }

    public static FileUtils getInstance() {
        return FileUtilsBinder.FILE_UTILS;
    }

    public void fileCopy(String oldFilePath, String newFilePath) {
        //如果原文件不存在
        if (!fileExists(oldFilePath)) {
            return;
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(oldFilePath);
            FileOutputStream fileOutputStream = new FileOutputStream(newFilePath);
            byte[] buffer = new byte[1024];
            int byteRead;
            while (-1 != (byteRead = fileInputStream.read(buffer))) {
                fileOutputStream.write(buffer, 0, byteRead);
            }
            fileInputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    private void createFileDir(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                //按照指定的路径创建文件夹
                file.mkdirs();
            } catch (Exception ignored) {
            }
        }
    }

    private void createFile(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            try {
                //在指定的文件夹中创建文件
                dir.createNewFile();
            } catch (Exception ignored) {
            }
        }

    }

    // 保存文件
    // 文件保存目录按channel区分
    public String saveFile(String oldPath, String channelId, byte channelType, String fileName) {
        if (TextUtils.isEmpty(channelId) || TextUtils.isEmpty(oldPath)) return "";
        File f = new File(oldPath);
        String tempFileName = f.getName();
        String prefix = tempFileName.substring(tempFileName.lastIndexOf(".") + 1);

        String filePath = String.format("%s/%s/%s",  WKIMApplication.getInstance().getFileCacheDir(), channelType, channelId);
        createFileDir(filePath);//创建文件夹
        String newFilePath = String.format("%s/%s.%s", filePath, fileName, prefix);
        createFile(newFilePath);//创建文件
        fileCopy(oldPath, newFilePath);//复制文件
        return newFilePath;
    }
}
