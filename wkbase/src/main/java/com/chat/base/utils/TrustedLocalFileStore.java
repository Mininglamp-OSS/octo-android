package com.chat.base.utils;

import android.content.Context;
import android.text.TextUtils;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKApiConfig;
import com.xinbida.wukongim.WKIMApplication;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.UUID;

/** Persists complete, account-scoped snapshots of files created or selected locally. */
public final class TrustedLocalFileStore {
    private static final String DIRECTORY = "trusted-local-files";
    private static final String INDEX = "index.json";
    private static final Object LOCK = new Object();

    private TrustedLocalFileStore() { }

    public static final class SourceGrant {
        private final String uid;
        private final long sessionVersion;
        private final String sessionToken;
        private final String server;
        private final String sourcePath;
        private final WKIMApplication.SessionContext sdkSession;
        private SourceGrant(WKConfig.SessionSnapshot session, String server, String sourcePath,
                            WKIMApplication.SessionContext sdkSession) {
            this.uid = session.uid; this.sessionVersion = session.version; this.sessionToken = session.token;
            this.server = server; this.sourcePath = sourcePath; this.sdkSession = sdkSession;
        }
        public boolean matches(String value) {
            WKConfig.SessionSnapshot current = WKConfig.getInstance().getSessionSnapshot();
            return uid.equals(value) && sessionVersion == current.version && uid.equals(current.uid)
                    && java.util.Objects.equals(sessionToken, current.token)
                    && java.util.Objects.equals(server, WKApiConfig.baseUrl)
                    && WKIMApplication.getInstance().isSessionCurrent(sdkSession);
        }
    }

    public static SourceGrant grant(WKConfig.SessionSnapshot session, String server, String sourcePath) {
        if (session == null || TextUtils.isEmpty(session.uid) || TextUtils.isEmpty(server) || TextUtils.isEmpty(sourcePath)) return null;
        File source = new File(sourcePath);
        if (!source.isFile() || !source.canRead() || source.length() <= 0) return null;
        WKIMApplication.SessionContext sdkSession = WKIMApplication.getInstance().captureSessionContext("", 0);
        if (sdkSession == null || !session.uid.equals(sdkSession.uid)) return null;
        return new SourceGrant(session, server, source.getAbsolutePath(), sdkSession);
    }

    public static File snapshot(Context context, SourceGrant grant, String uid,
                                byte channelType, String channelId, String clientMsgNo, long expectedSize, String sourcePath) throws IOException {
        if (context == null || grant == null || !grant.matches(uid)
                || !grant.sourcePath.equals(new File(sourcePath).getAbsolutePath())
                || TextUtils.isEmpty(channelId) || TextUtils.isEmpty(clientMsgNo)) return null;
        File source = new File(sourcePath);
        if (!source.isFile() || !source.canRead() || source.length() <= 0
                || (expectedSize > 0 && source.length() != expectedSize)) return null;
        String server = grant.server;
        String key = identityKey(uid, server, channelType, channelId, clientMsgNo);
        File filesDir = canonicalFilesDir(context.getFilesDir());
        File root = new File(filesDir, DIRECTORY + "/" + sha256(uid) + "/" + sha256(server));
        rejectSymlinks(filesDir, root);
        if (!root.exists() && !root.mkdirs()) throw new IOException("Could not create trusted file directory");
        File canonicalRoot = root.getCanonicalFile();
        if (!isWithin(filesDir, canonicalRoot)) throw new IOException("Trusted directory escaped private files");
        File rawTarget = new File(canonicalRoot, key + extension(source.getName()));
        if (isSymlink(rawTarget)) throw new IOException("Trusted file target is a symlink");
        File target = rawTarget.getCanonicalFile();
        if (!target.getParentFile().equals(canonicalRoot)) throw new IOException("Trusted file escaped storage directory");
        File temp = new File(canonicalRoot, "." + key + "." + UUID.randomUUID() + ".tmp");
        long expected = source.length();
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(temp)) {
            FileChannel input = in.getChannel(), output = out.getChannel();
            long offset = 0;
            while (offset < expected) {
                long copied = input.transferTo(offset, expected - offset, output);
                if (copied <= 0) throw new IOException("Incomplete trusted file copy");
                offset += copied;
            }
            out.getFD().sync();
        } catch (IOException error) {
            temp.delete();
            throw error;
        }
        if (temp.length() != expected || !temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Trusted file snapshot verification failed");
        }
        if (!grant.matches(uid)) { target.delete(); throw new IOException("Trusted file session expired"); }
        synchronized (LOCK) {
            if (!grant.matches(uid)) { target.delete(); throw new IOException("Trusted file session expired"); }
            JSONArray rows = readIndex(context);
            JSONObject row = new JSONObject();
            try {
                row.put("identity", key); row.put("uid", uid); row.put("server", server);
                row.put("channelType", channelType); row.put("channelId", channelId);
                row.put("clientMsgNo", clientMsgNo); row.put("size", expected);
                row.put("path", target.getAbsolutePath());
                rows.put(row);
                writeIndex(context, rows);
            } catch (org.json.JSONException error) {
                target.delete();
                throw new IOException("Could not persist trusted file record", error);
            }
        }
        return target;
    }

    public static File resolve(Context context, String uid, String server, byte channelType,
                               String channelId, String clientMsgNo, long expectedSize) {
        if (context == null || TextUtils.isEmpty(uid) || TextUtils.isEmpty(server)
                || TextUtils.isEmpty(channelId) || TextUtils.isEmpty(clientMsgNo)) return null;
        String identity = identityKey(uid, server, channelType, channelId, clientMsgNo);
        synchronized (LOCK) {
            File filesDir;
            File rawRoot;
            try {
                filesDir = canonicalFilesDir(context.getFilesDir());
                rawRoot = new File(filesDir, DIRECTORY + "/" + sha256(uid) + "/" + sha256(server));
                rejectSymlinks(filesDir, rawRoot);
                if (isSymlink(new File(filesDir, DIRECTORY + "/" + INDEX))) return null;
            } catch (IOException error) { return null; }
            JSONArray rows = readIndex(context);
            for (int i = rows.length() - 1; i >= 0; i--) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !identity.equals(row.optString("identity"))) continue;
                if (!uid.equals(row.optString("uid")) || !server.equals(row.optString("server"))
                        || row.optInt("channelType", -1) != channelType
                        || !channelId.equals(row.optString("channelId"))
                        || !clientMsgNo.equals(row.optString("clientMsgNo"))) return null;
                File file = new File(row.optString("path"));
                try {
                    if (isSymlink(file)) return null;
                    File root = rawRoot.getCanonicalFile();
                    File canonical = file.getCanonicalFile();
                    long size = row.optLong("size", -1);
                    if (!canonical.getParentFile().equals(root) || !canonical.isFile() || size <= 0
                            || canonical.length() != size || (expectedSize > 0 && expectedSize != size)) return null;
                    return canonical;
                } catch (IOException error) { return null; }
            }
        }
        return null;
    }

    private static JSONArray readIndex(Context context) {
        File index;
        try {
            File filesDir = canonicalFilesDir(context.getFilesDir());
            index = new File(filesDir, DIRECTORY + "/" + INDEX);
            rejectSymlinks(filesDir, index.getParentFile());
            if (isSymlink(index)) return new JSONArray();
        } catch (IOException error) { return new JSONArray(); }
        if (!index.isFile()) return new JSONArray();
        try {
            try (FileInputStream in = new FileInputStream(index); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                return new JSONArray(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { return new JSONArray(); }
    }

    private static void writeIndex(Context context, JSONArray rows) throws IOException {
        File filesDir = canonicalFilesDir(context.getFilesDir());
        File dir = new File(filesDir, DIRECTORY);
        rejectSymlinks(filesDir, dir);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create trusted index directory");
        File temp = new File(dir, "." + INDEX + "." + UUID.randomUUID() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.getFD().sync();
        }
        File target = new File(dir, INDEX);
        if (isSymlink(target)) { temp.delete(); throw new IOException("Trusted index is a symlink"); }
        if (!temp.renameTo(target)) { temp.delete(); throw new IOException("Could not atomically update trusted index"); }
    }

    static File canonicalFilesDir(File contextFilesDir) throws IOException {
        return contextFilesDir.getCanonicalFile();
    }

    static void rejectSymlinks(File canonicalBase, File target) throws IOException {
        String basePath = canonicalBase.getCanonicalPath();
        String targetPath = target.getAbsolutePath();
        if (targetPath.contains(File.separator + ".." + File.separator) || targetPath.endsWith(File.separator + ".."))
            throw new IOException("Trusted path contains parent traversal");
        if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator))
            throw new IOException("Trusted path escaped private files");
        String relative = targetPath.equals(basePath) ? "" : targetPath.substring(basePath.length() + 1);
        File current = new File(basePath);
        for (String segment : relative.split(java.util.regex.Pattern.quote(File.separator))) {
            if (segment.isEmpty()) continue;
            current = new File(current, segment);
            if (isSymlink(current)) throw new IOException("Trusted directory contains a symlink");
        }
    }

    static boolean isSymlink(File file) throws IOException {
        try {
            return OsConstants.S_ISLNK(Os.lstat(file.getAbsolutePath()).st_mode);
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return false;
            throw new IOException("Could not inspect trusted path", error);
        }
    }

    private static boolean isWithin(File root, File candidate) throws IOException {
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".bin";
        String ext = name.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
        return ext.length() <= 12 ? ext : ".bin";
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static String identityKey(String uid, String server, byte channelType, String channelId, String clientMsgNo) {
        return sha256(uid + "\u0000" + server + "\u0000" + channelType + "\u0000" + channelId + "\u0000" + clientMsgNo);
    }
}
