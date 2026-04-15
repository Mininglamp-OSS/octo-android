package com.xinbida.wukongim.utils;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

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
            byte[] buffer = new byte[4096];
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

    // 保存文件
    // 文件保存目录按channel区分
    public String saveFile(String oldPath, String channelId, byte channelType, String fileName) {
        if (TextUtils.isEmpty(channelId) || TextUtils.isEmpty(oldPath)) return "";
        File srcFile = new File(oldPath);
        if (!srcFile.exists() || srcFile.length() == 0) return oldPath;

        String tempFileName = srcFile.getName();
        String prefix = tempFileName.substring(tempFileName.lastIndexOf(".") + 1);

        String dirPath = String.format("%s/%s/%s", WKIMApplication.getInstance().getFileCacheDir(), channelType, channelId);
        createFileDir(dirPath);
        String newFilePath = String.format("%s/%s.%s", dirPath, fileName, prefix);

        // 判断源文件是否在 app 私有目录内（缓存/临时文件），只有这类文件才允许移动或删除
        boolean isTempFile = isAppPrivateFile(oldPath);

        // 1. 如果是临时文件，优先尝试 rename（移动），零拷贝
        File destFile = new File(newFilePath);
        if (isTempFile && srcFile.renameTo(destFile)) {
            return newFilePath;
        }

        // 2. 复制文件
        fileCopy(oldPath, newFilePath);

        // 3. 验证复制结果
        if (destFile.exists() && destFile.length() > 0) {
            // 复制成功，仅删除 app 临时文件，不删除用户原始文件
            if (isTempFile) {
                srcFile.delete();
            }
            return newFilePath;
        }

        // 4. 复制失败（Scoped Storage 等），回退使用原始路径
        destFile.delete();
        return oldPath;
    }

    /**
     * 判断文件是否在 app 私有目录内（cache/files），只有这些文件可以安全地移动或删除。
     */
    private boolean isAppPrivateFile(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String appDataDir = WKIMApplication.getInstance().getContext().getDataDir().getAbsolutePath();
        String appCacheDir = WKIMApplication.getInstance().getContext().getCacheDir().getAbsolutePath();
        String appFilesDir = WKIMApplication.getInstance().getContext().getFilesDir().getAbsolutePath();
        // 也包含外部私有目录 Android/data/包名/
        File externalCacheDir = WKIMApplication.getInstance().getContext().getExternalCacheDir();
        File externalFilesDir = WKIMApplication.getInstance().getContext().getExternalFilesDir(null);
        return path.startsWith(appDataDir)
                || path.startsWith(appCacheDir)
                || path.startsWith(appFilesDir)
                || (externalCacheDir != null && path.startsWith(externalCacheDir.getAbsolutePath()))
                || (externalFilesDir != null && path.startsWith(externalFilesDir.getParent()));
    }
}
