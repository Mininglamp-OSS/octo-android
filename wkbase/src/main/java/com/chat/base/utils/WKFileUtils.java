package com.chat.base.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * 2020-03-10 20:35
 * 文件处理
 */
public class WKFileUtils {

    private WKFileUtils() {
    }

    private static class FileUtilsBinder {
        private final static WKFileUtils utils = new WKFileUtils();
    }

    public static WKFileUtils getInstance() {
        return FileUtilsBinder.utils;
    }

    private final File parentPath = Objects.requireNonNull(WKBaseApplication.getInstance().getContext().getExternalFilesDir(null));
    //     Environment.getExternalStorageDirectory();
    private String storagePath = "";
    private String DST_FOLDER_NAME = "wkIm";

    public String getNormalFileSavePath(String directory) {
        String path = Objects.requireNonNull(WKBaseApplication.getInstance().getContext().getExternalFilesDir(null)).getAbsolutePath() + "/" + directory;
//        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + DST_FOLDER_NAME + "/" + directory;
        createFileDir(path);
        return path;
    }

    private String initPath() {
        if (storagePath.equals("")) {
            storagePath = parentPath.getAbsolutePath() + File.separator + DST_FOLDER_NAME;
            File f = new File(storagePath);
            if (!f.exists()) {
                f.mkdir();
            }
        }
        return storagePath;
    }

    public String saveBitmap(String dir, Bitmap b) {
        if (!TextUtils.isEmpty(dir))
            DST_FOLDER_NAME = dir;
        String path = initPath();
        long dataTake = System.currentTimeMillis();
        String jpegName = path + File.separator + "picture_" + dataTake + ".jpg";
        try {
            FileOutputStream fout = new FileOutputStream(jpegName);
            BufferedOutputStream bos = new BufferedOutputStream(fout);
            b.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            return jpegName;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean deleteFile(String url) {
        boolean result = false;
        File file = new File(url);
        if (file.exists()) {
            result = file.delete();
        }
        return result;
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    private boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
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


    private void createPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                // 获取父文件
                File parent = file.getParentFile();
                assert parent != null;
                if (!parent.exists()) {
                    parent.mkdirs();  //创建所有父文件夹
                }
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getSDPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public void createFileDir(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                //按照指定的路径创建文件夹
                file.mkdirs();
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        }
    }

    public void createFileDir(File path) {
        if (null != path && !path.exists()) {
            try {
                //按照指定的路径创建文件夹
                path.mkdirs();
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        }
    }

    public void createFile(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            try {
                //在指定的文件夹中创建文件
                dir.createNewFile();
            } catch (Exception ignored) {
            }
        }

    }

    /**
     * Delete file or folder.
     *
     * @param file file.
     * @return is succeed.
     */
    public static boolean delFileOrFolder(File file) {
        if (file == null || !file.exists()) {
            // do nothing
        } else if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File sonFile : files) {
                    delFileOrFolder(sonFile);
                }
            }
            file.delete();
        }
        return true;
    }

    /**
     * 保存录音文件
     *
     * @param oldPath
     * @return
     */
    public String saveAudio(String oldPath) {
        if (TextUtils.isEmpty(oldPath)) return "";
        String audioPath = getSDPath() + "/wukong/audio";
        createFileDir(audioPath);//创建文件夹
        String newAudioPath = audioPath + "/" + UUID.randomUUID().toString().replaceAll("-", "") + ".WK_amr";
        createFile(newAudioPath);//创建文件
        fileCopy(oldPath, newAudioPath);//复制文件
        return newAudioPath;
    }

    public String getChooseFileResultPath(Context context, Uri uri) {
        String chooseFilePath = null;
        if ("file".equalsIgnoreCase(uri.getScheme())) {//使用第三方应用打开
            chooseFilePath = uri.getPath();
            return chooseFilePath;
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {//4.4以后
            chooseFilePath = getPath(context, uri);
        } else {//4.4以下下系统调用方法
            chooseFilePath = getRealPathFromURI(context, uri);
        }
        return chooseFilePath;
    }

    private String getRealPathFromURI(Context context, Uri contentUri) {
        String res = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        if (null != cursor && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
            cursor.close();
        }
        return res;
    }

    /**
     * 专为Android4.4设计的从Uri获取文件绝对路径，以前的方法已不好使
     */
    @SuppressLint("NewApi")
    private String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];

                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }
                if (id.startsWith("msf:")) {
                    return id.replaceFirst("msf:", "");
                }
                final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

                } else {
                    return uri.getPath();
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);

            }

        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);

        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();

        }
        return null;
    }

    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public void openFileByPath(Context context, String path) {
        Intent intent = new Intent();
        // 这是比较流氓的方法，绕过7.0的文件权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        File file = new File(path);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);//设置标记
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setAction(Intent.ACTION_VIEW);//动作，查看
        intent.setDataAndType(Uri.fromFile(file), getMIMEType(file));//设置类型
        context.startActivity(intent);

    }

    private String getMIMEType(File file) {

        String type = "*/*";
        String fName = file.getName();
        //获取后缀名前的分隔符"."在fName中的位置。
        int dotIndex = fName.lastIndexOf(".");
        if (dotIndex < 0)
            return type;
        /* 获取文件的后缀名 */
        String fileType = fName.substring(dotIndex).toLowerCase();
        if ("".equals(fileType))
            return type;
        //在MIME和文件类型的匹配表中找到对应的MIME类型。
        for (String[] strings : MATCH_ARRAY) {
            if (fileType.equals(strings[0]))
                type = strings[1];
        }
        return type;
    }

    private final String[][] MATCH_ARRAY = {
            //{后缀名，    文件类型}
            {".3gp", "video/3gpp"},
            {".apk", "application/vnd.android.package-archive"},
            {".asf", "video/x-ms-asf"},
            {".avi", "video/x-msvideo"},
            {".bin", "application/octet-stream"},
            {".bmp", "image/bmp"},
            {".c", "text/plain"},
            {".class", "application/octet-stream"},
            {".conf", "text/plain"},
            {".cpp", "text/plain"},
            {".doc", "application/msword"},
            {".exe", "application/octet-stream"},
            {".gif", "image/gif"},
            {".gtar", "application/x-gtar"},
            {".gz", "application/x-gzip"},
            {".h", "text/plain"},
            {".htm", "text/html"},
            {".html", "text/html"},
            {".jar", "application/java-archive"},
            {".java", "text/plain"},
            {".jpeg", "image/jpeg"},
            {".jpg", "image/jpeg"},
            {".js", "application/x-javascript"},
            {".log", "text/plain"},
            {".m3u", "audio/x-mpegurl"},
            {".m4a", "audio/mp4a-latm"},
            {".m4b", "audio/mp4a-latm"},
            {".m4p", "audio/mp4a-latm"},
            {".m4u", "video/vnd.mpegurl"},
            {".m4v", "video/x-m4v"},
            {".mov", "video/quicktime"},
            {".mp2", "audio/x-mpeg"},
            {".mp3", "audio/x-mpeg"},
            {".mp4", "video/mp4"},
            {".mpc", "application/vnd.mpohun.certificate"},
            {".mpe", "video/mpeg"},
            {".mpeg", "video/mpeg"},
            {".mpg", "video/mpeg"},
            {".mpg4", "video/mp4"},
            {".mpga", "audio/mpeg"},
            {".msg", "application/vnd.ms-outlook"},
            {".ogg", "audio/ogg"},
            {".pdf", "application/pdf"},
            {".png", "image/png"},
            {".pps", "application/vnd.ms-powerpoint"},
            {".ppt", "application/vnd.ms-powerpoint"},
            {".prop", "text/plain"},
            {".rar", "application/x-rar-compressed"},
            {".rc", "text/plain"},
            {".rmvb", "audio/x-pn-realaudio"},
            {".rtf", "application/rtf"},
            {".sh", "text/plain"},
            {".tar", "application/x-tar"},
            {".tgz", "application/x-compressed"},
            {".txt", "text/plain"},
            {".wav", "audio/x-wav"},
            {".wma", "audio/x-ms-wma"},
            {".wmv", "audio/x-ms-wmv"},
            {".wps", "application/vnd.ms-works"},
            {".xml", "text/plain"},
            {".z", "application/x-compress"},
            {".zip", "application/zip"},
            {"", "*/*"}
    };

    /***
     * 判断文件是否超出app承受大小。
     * @param context
     * @param filePath
     * @return
     */
    public boolean isFileOverSize(Context context, String filePath) {
        Long fileSize = WKFileUtils.getInstance().getFileSize(filePath);
        if (fileSize > 500 * 1024 * 1024) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.max_file_size));
            return true;
        }
        if (fileSize == 0) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.min_file_size));
            return true;
        }
        return false;
    }

    public long getFileSize(String filePath) {
        File file = new File(filePath);
        long blockSize = 0;
        try {
            blockSize = getFileSize(file);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return blockSize;
    }

    public long getFileSize(File file) {
        long size = 0;
        try {

            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                size = fis.available();
            }
        } catch (IOException ignored) {

        }
        return size;
    }

    public synchronized byte[] file2byte(File tradeFile) {
        byte[] buffer = null;
        try {
            if (tradeFile == null || !tradeFile.exists()) {
                return null;
            }
            FileInputStream fis = new FileInputStream(tradeFile);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    //解压
    public synchronized String uncompressSticker(byte[] uncompressSource, String stickerPath) {
        if (uncompressSource == null || uncompressSource.length == 0) {
            return null;
        }
        ByteArrayOutputStream outs = new ByteArrayOutputStream();
        ByteArrayInputStream ins = new ByteArrayInputStream(uncompressSource);
        GZIPInputStream ungzip;
        byte[] uncompressRes;
        try {
            ungzip = new GZIPInputStream(ins);
            byte[] buff = new byte[1024];
            int n;
            while ((n = ungzip.read(buff)) >= 0) {
                outs.write(buff, 0, n);
            }
            ungzip.close();
            uncompressRes = outs.toByteArray();
            ins.close();
            outs.close();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
        String fileDir = Objects.requireNonNull(WKBaseApplication.getInstance().getContext().getExternalFilesDir("wkStickers")).getAbsolutePath() + "/";
        String path = fileDir + stickerPath.replaceAll("/", "_").replaceAll(".wk", "");
        boolean result = saveFile(path, uncompressRes);
        if (result)
            return path;
        else return "";
        // return uncompressRes;
    }

    public boolean saveFile(String path, byte[] bytes) {
        if (TextUtils.isEmpty(path) || bytes == null || bytes.length == 0) return false;
        try {
            File file = new File(path);
            if (!file.exists()) {
                file.createNewFile();
            }
            OutputStream out = new FileOutputStream(file);
            InputStream is = new ByteArrayInputStream(bytes);
            byte[] buff = new byte[1024];
            int len = 0;
            while ((len = is.read(buff)) != -1) {
                out.write(buff, 0, len);
            }
            is.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    // 将字符串写入到文本文件中
    public void writeTxtToFile(String content, String savePath, final IWriteText iWriteText) {
        try {
            File file = new File(savePath);
            if (!file.exists()) {
                boolean result = file.createNewFile();
                if (!result) {
                    iWriteText.onFail();
                    return;
                }
            }
            RandomAccessFile mRandomAccessFile = new RandomAccessFile(file, "rwd");
            mRandomAccessFile.seek(file.length());
            mRandomAccessFile.write(content.getBytes());
            mRandomAccessFile.close();
            iWriteText.onSuccess();
        } catch (IOException e) {
            WKLogUtils.e("写入备份聊天数据错误");
            iWriteText.onFail();
        }
    }

    public interface IWriteText {
        void onSuccess();

        void onFail();
    }

    public boolean isGif(String localPath) {
        try {
            FileInputStream inputStream = new FileInputStream(localPath);
            int[] flags = new int[5];
            flags[0] = inputStream.read();
            flags[1] = inputStream.read();
            flags[2] = inputStream.read();
            flags[3] = inputStream.read();
            inputStream.skip(inputStream.available() - 1);
            flags[4] = inputStream.read();
            inputStream.close();
            return flags[0] == 71 && flags[1] == 73 && flags[2] == 70 && flags[3] == 56 && flags[4] == 0x3B;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean saveVideoToAlbum(Context context, String videoFile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return saveVideoToAlbumBeforeQ(context, videoFile);
        } else {
            return saveVideoToAlbumAfterQ(context, videoFile);
        }


    }


    private boolean saveVideoToAlbumAfterQ(Context context, String videoFile) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            File tempFile = new File(videoFile);
            ContentValues contentValues = getVideoContentValues(context, tempFile, System.currentTimeMillis());
            Uri uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
            copyFileAfterQ(context, contentResolver, tempFile, uri);
            contentValues.clear();
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, contentValues, null, null);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean saveVideoToAlbumBeforeQ(Context context, String videoFile) {
        File picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File tempFile = new File(videoFile);
        File destFile = new File(picDir, context.getPackageName() + File.separator + tempFile.getName());
        FileInputStream ins = null;
        BufferedOutputStream ous = null;
        try {
            ins = new FileInputStream(tempFile);
            ous = new BufferedOutputStream(new FileOutputStream(destFile));
            long nread = 0L;
            byte[] buf = new byte[1024];
            int n;
            while ((n = ins.read(buf)) > 0) {
                ous.write(buf, 0, n);
                nread += n;
            }
            MediaScannerConnection.scanFile(
                    context,
                    new String[]{destFile.getAbsolutePath()},
                    new String[]{"video/*"},
                    (path, uri) -> {
                        // Scan Completed
                    });
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (ins != null) {
                    ins.close();
                }
                if (ous != null) {
                    ous.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void copyFileAfterQ(Context context, ContentResolver localContentResolver, File tempFile, Uri localUri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.Q) {
            //拷贝文件到相册的uri,android10及以上得这么干，否则不会显示。可以参考ScreenMediaRecorder的save方法
            OutputStream os = localContentResolver.openOutputStream(localUri);
            Files.copy(tempFile.toPath(), os);
            os.close();
            //  tempFile.delete();
        }
    }


    /**
     * 获取视频的contentValue
     */
    public ContentValues getVideoContentValues(Context context, File paramFile, long timestamp) {
        ContentValues localContentValues = new ContentValues();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            localContentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM
                    + File.separator + context.getPackageName());
        }
        localContentValues.put(MediaStore.Video.Media.TITLE, paramFile.getName());
        localContentValues.put(MediaStore.Video.Media.DISPLAY_NAME, paramFile.getName());
        localContentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        localContentValues.put(MediaStore.Video.Media.DATE_TAKEN, timestamp);
        localContentValues.put(MediaStore.Video.Media.DATE_MODIFIED, timestamp);
        localContentValues.put(MediaStore.Video.Media.DATE_ADDED, timestamp);
        localContentValues.put(MediaStore.Video.Media.SIZE, paramFile.length());
        return localContentValues;
    }

    public boolean copyFileToExternalUri(Context context, String filePath, Uri externalUri) {
        ContentResolver contentResolver = context.getContentResolver();
        InputStream inputStream = null;
        OutputStream outputStream = null;
        boolean ret = false;
        try {
            outputStream = contentResolver.openOutputStream(externalUri);
            File sandFile = new File(filePath);
            if (sandFile.exists() && outputStream != null) {
                inputStream = new FileInputStream(sandFile);

                int readCount = 0;
                byte[] buffer = new byte[1024];
                while ((readCount = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readCount);
                    outputStream.flush();
                }
            }
            ret = true;
        } catch (Exception e) {
            Log.e("fileUtils", "copy SandFile To ExternalUri. e = " + e.toString());
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                Log.d("fileUtils", " input stream and output stream close successful.");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("fileUtils", " input stream and output stream close fail. e = " + e.toString());
            }
        }
        return ret;
    }

}
