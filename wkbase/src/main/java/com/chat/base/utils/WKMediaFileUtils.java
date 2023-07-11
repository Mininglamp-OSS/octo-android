package com.chat.base.utils;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.util.HashMap;
import java.util.Iterator;

/**
 * 2020-11-10 18:16
 */
public class WKMediaFileUtils {
    private WKMediaFileUtils() {
    }

    private static class MediaFileUtilsBinder {
        final static WKMediaFileUtils fileUtils = new WKMediaFileUtils();
    }

    public static WKMediaFileUtils getInstance() {
        return MediaFileUtilsBinder.fileUtils;
    }

    // Audio file types
    private final int FILE_TYPE_MP3 = 1;
    private final int FILE_TYPE_M4A = 2;
    private final int FILE_TYPE_WAV = 3;
    private final int FILE_TYPE_AMR = 4;
    private final int FILE_TYPE_AWB = 5;
    private final int FILE_TYPE_WMA = 6;
    private final int FILE_TYPE_OGG = 7;
    private final int FIRST_AUDIO_FILE_TYPE = FILE_TYPE_MP3;
    private final int LAST_AUDIO_FILE_TYPE = FILE_TYPE_OGG;

    // MIDI file types
    private final int FILE_TYPE_MID = 11;
    private final int FILE_TYPE_SMF = 12;
    private final int FILE_TYPE_IMY = 13;
    private final int FIRST_MIDI_FILE_TYPE = FILE_TYPE_MID;
    private final int LAST_MIDI_FILE_TYPE = FILE_TYPE_IMY;

    // Video file types
    private final int FILE_TYPE_MP4 = 21;
    private final int FILE_TYPE_M4V = 22;
    private final int FILE_TYPE_3GPP = 23;
    private final int FILE_TYPE_3GPP2 = 24;
    private final int FILE_TYPE_WMV = 25;
    private final int FILE_TYPE_MOV = 26;
    private final int FIRST_VIDEO_FILE_TYPE = FILE_TYPE_MP4;
    private final int LAST_VIDEO_FILE_TYPE = FILE_TYPE_MOV;

    // Image file types
    private final int FILE_TYPE_JPEG = 31;
    private final int FILE_TYPE_GIF = 32;
    private final int FILE_TYPE_PNG = 33;
    private final int FILE_TYPE_BMP = 34;
    private final int FILE_TYPE_WBMP = 35;
    private final int FIRST_IMAGE_FILE_TYPE = FILE_TYPE_JPEG;
    private final int LAST_IMAGE_FILE_TYPE = FILE_TYPE_WBMP;

    // Playlist file types
    private final int FILE_TYPE_M3U = 41;
    private final int FILE_TYPE_PLS = 42;
    private final int FILE_TYPE_WPL = 43;
    private final int FIRST_PLAYLIST_FILE_TYPE = FILE_TYPE_M3U;
    private final int LAST_PLAYLIST_FILE_TYPE = FILE_TYPE_WPL;

    //静态内部类
    static class MediaFileType {

        int fileType;
        String mimeType;

        MediaFileType(int fileType, String mimeType) {
            this.fileType = fileType;
            this.mimeType = mimeType;
        }
    }

    private final HashMap<String, MediaFileType> sFileTypeMap
            = new HashMap<>();
    private final HashMap<String, Integer> sMimeTypeMap
            = new HashMap<>();

    private void addFileType(String extension, int fileType, String mimeType) {
        sFileTypeMap.put(extension, new MediaFileType(fileType, mimeType));
        sMimeTypeMap.put(mimeType, fileType);
    }

    {
        addFileType("MP3", FILE_TYPE_MP3, "audio/mpeg");
        addFileType("M4A", FILE_TYPE_M4A, "audio/mp4");
        addFileType("WAV", FILE_TYPE_WAV, "audio/x-wav");
        addFileType("AMR", FILE_TYPE_AMR, "audio/amr");
        addFileType("AWB", FILE_TYPE_AWB, "audio/amr-wb");
        addFileType("WMA", FILE_TYPE_WMA, "audio/x-ms-wma");
        addFileType("OGG", FILE_TYPE_OGG, "application/ogg");

        addFileType("MID", FILE_TYPE_MID, "audio/midi");
        addFileType("XMF", FILE_TYPE_MID, "audio/midi");
        addFileType("RTTTL", FILE_TYPE_MID, "audio/midi");
        addFileType("SMF", FILE_TYPE_SMF, "audio/sp-midi");
        addFileType("IMY", FILE_TYPE_IMY, "audio/imelody");

        addFileType("MP4", FILE_TYPE_MP4, "video/mp4");
        addFileType("M4V", FILE_TYPE_M4V, "video/mp4");
        addFileType("3GP", FILE_TYPE_3GPP, "video/3gpp");
        addFileType("3GPP", FILE_TYPE_3GPP, "video/3gpp");
        addFileType("3G2", FILE_TYPE_3GPP2, "video/3gpp2");
        addFileType("3GPP2", FILE_TYPE_3GPP2, "video/3gpp2");
        addFileType("WMV", FILE_TYPE_WMV, "video/x-ms-wmv");
        addFileType("MOV", FILE_TYPE_MOV, "video/x-ms-mov");

        addFileType("JPG", FILE_TYPE_JPEG, "image/jpeg");
        addFileType("JPEG", FILE_TYPE_JPEG, "image/jpeg");
        addFileType("GIF", FILE_TYPE_GIF, "image/gif");
        addFileType("PNG", FILE_TYPE_PNG, "image/png");
        addFileType("BMP", FILE_TYPE_BMP, "image/x-ms-bmp");
        addFileType("WBMP", FILE_TYPE_WBMP, "image/vnd.wap.wbmp");

        addFileType("M3U", FILE_TYPE_M3U, "audio/x-mpegurl");
        addFileType("PLS", FILE_TYPE_PLS, "audio/x-scpls");
        addFileType("WPL", FILE_TYPE_WPL, "application/vnd.ms-wpl");

        // compute file extensions list for native Media Scanner
        StringBuilder builder = new StringBuilder();
        Iterator<String> iterator = sFileTypeMap.keySet().iterator();

        while (iterator.hasNext()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(iterator.next());
        }
    }


    public boolean isAudioFileType(int fileType) {
        return ((fileType >= FIRST_AUDIO_FILE_TYPE &&
                fileType <= LAST_AUDIO_FILE_TYPE) ||
                (fileType >= FIRST_MIDI_FILE_TYPE &&
                        fileType <= LAST_MIDI_FILE_TYPE));
    }

    public boolean isVideoFileType(int fileType) {
        return (fileType >= FIRST_VIDEO_FILE_TYPE &&
                fileType <= LAST_VIDEO_FILE_TYPE);
    }

    public boolean isImageFileType(int fileType) {
        return (fileType >= FIRST_IMAGE_FILE_TYPE &&
                fileType <= LAST_IMAGE_FILE_TYPE);
    }

    public boolean isPlayListFileType(int fileType) {
        return (fileType >= FIRST_PLAYLIST_FILE_TYPE &&
                fileType <= LAST_PLAYLIST_FILE_TYPE);
    }

    public MediaFileType getFileType(String path) {
        int lastDot = path.lastIndexOf(".");
        if (lastDot < 0)
            return null;
        return sFileTypeMap.get(path.substring(lastDot + 1).toUpperCase());
    }

    //根据视频文件路径判断文件类型
    public boolean isVideoFileType(String path) {  //自己增加
        MediaFileType type = getFileType(path);
        if (null != type) {
            return isVideoFileType(type.fileType);
        }
        return false;
    }

    //根据音频文件路径判断文件类型
    public boolean isAudioFileType(String path) {  //自己增加
        MediaFileType type = getFileType(path);
        if (null != type) {
            return isAudioFileType(type.fileType);
        }
        return false;
    }

    //根据mime类型查看文件类型
    public int getFileTypeForMimeType(String mimeType) {
        Integer value = sMimeTypeMap.get(mimeType);
        return (value == null ? 0 : value.intValue());
    }

    //获取视频封面
    public String getVideoCover(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        Bitmap bitmap = retriever.getFrameAtTime();
        return WKFileUtils.getInstance().saveBitmap(null, bitmap);
    }

    //获取视频时长
    public long getVideoTime(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        return Long.valueOf(duration);
    }

}
