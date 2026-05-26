package com.chat.base.net.ud;

import java.net.URLConnection;

public class MimeTypeUtils {

    public static String mimeTypeForExtension(String extWithDot) {
        String ext = extWithDot;
        if (ext != null && ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (ext == null || ext.isEmpty()) {
            return "application/octet-stream";
        }
        ext = ext.toLowerCase();

        switch (ext) {
            // image
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "heic": return "image/heic";
            case "bmp": return "image/bmp";
            case "svg": return "image/svg+xml";
            // video
            case "mp4": return "video/mp4";
            case "mov": return "video/quicktime";
            case "m4v": return "video/x-m4v";
            case "avi": return "video/x-msvideo";
            case "mkv": return "video/x-matroska";
            case "webm": return "video/webm";
            // audio
            case "amr": return "audio/amr";
            case "m4a": return "audio/mp4";
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            case "ogg": return "audio/ogg";
            // office
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            // document
            case "pdf": return "application/pdf";
            case "rtf": return "application/rtf";
            case "epub": return "application/epub+zip";
            // text
            case "txt": case "log": return "text/plain";
            case "md": case "markdown": return "text/markdown";
            case "csv": return "text/csv";
            case "html": case "htm": return "text/html";
            case "xml": return "application/xml";
            case "json": return "application/json";
            // archive
            case "zip": return "application/zip";
            case "rar": return "application/vnd.rar";
            case "7z": return "application/x-7z-compressed";
            case "tar": return "application/x-tar";
            case "gz": case "gzip": return "application/gzip";
            case "bz2": return "application/x-bzip2";
            // apk
            case "apk": return "application/vnd.android.package-archive";
            default:
                break;
        }

        String guessed = URLConnection.guessContentTypeFromName("file." + ext);
        if (guessed != null && !guessed.isEmpty()) {
            return guessed;
        }
        return "application/octet-stream";
    }
}
