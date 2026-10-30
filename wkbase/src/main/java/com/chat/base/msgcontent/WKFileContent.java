/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.msgcontent;

import android.os.Parcel;
import android.os.Parcelable;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.chat.base.utils.TrustedLocalFileStore;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKLocalAttachmentContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

public class WKFileContent extends WKMediaMessageContent implements Parcelable, WKLocalAttachmentContent {
    public String name;
    public String extension;
    public long size;
    private transient TrustedLocalFileStore.SourceGrant trustedLocalSource;
    private transient java.util.concurrent.atomic.AtomicReference<java.io.File> ownedTemporaryDirectory =
            new java.util.concurrent.atomic.AtomicReference<>();

    public WKFileContent() {
        type = 8;
    }

    public boolean grantTrustedLocalSource(com.chat.base.config.WKConfig.SessionSnapshot session, String serverScope) {
        trustedLocalSource = TrustedLocalFileStore.grant(session, serverScope, localPath);
        return trustedLocalSource != null;
    }

    /** Production factory shared by local-file entry points and the end-to-end send test. */
    public static WKFileContent fromTrustedLocalFile(String path, String displayName, String extension,
            com.chat.base.config.WKConfig.SessionSnapshot session, String serverScope) {
        if (path == null || displayName == null || session == null) return null;
        java.io.File file = new java.io.File(path);
        if (!file.isFile() || !file.canRead() || file.length() <= 0) return null;
        WKFileContent content = new WKFileContent();
        content.localPath = file.getAbsolutePath();
        content.name = displayName;
        content.extension = extension;
        content.size = file.length();
        return content.grantTrustedLocalSource(session, serverScope) ? content : null;
    }

    /** Marks only a producer-created temporary directory as owned by this send task. */
    public void setOwnedTemporaryDirectory(java.io.File directory) {
        if (ownedTemporaryDirectory == null) ownedTemporaryDirectory = new java.util.concurrent.atomic.AtomicReference<>();
        ownedTemporaryDirectory.set(directory);
    }

    /** Deletes a producer-owned temporary source; durable trusted snapshots are never owned here. */
    public void cleanupOwnedTemporarySource() {
        java.io.File directory = ownedTemporaryDirectory == null ? null : ownedTemporaryDirectory.getAndSet(null);
        if (directory != null) deleteTree(directory);
    }

    private static void deleteTree(java.io.File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) for (java.io.File child : children) deleteTree(child);
        }
        file.delete();
    }

    /** Builds a fresh send content from an older persisted message, trusting only its indexed snapshot. */
    public static WKFileContent forResend(android.content.Context context,
            com.xinbida.wukongim.entity.WKMsg oldMessage,
            com.chat.base.config.WKConfig.SessionSnapshot session, String serverScope) {
        if (context == null || oldMessage == null || !(oldMessage.baseContentMsgModel instanceof WKFileContent)
                || session == null) return null;
        WKFileContent oldContent = (WKFileContent) oldMessage.baseContentMsgModel;
        WKFileContent copy = parcelCopy(oldContent);
        java.io.File trusted = TrustedLocalFileStore.resolve(context, session.uid, serverScope,
                oldMessage.channelType, oldMessage.channelID, oldMessage.clientMsgNO, oldContent.size);
        if (trusted != null) {
            copy.localPath = trusted.getAbsolutePath();
            copy.size = trusted.length();
            if (!copy.grantTrustedLocalSource(session, serverScope)) return null;
        } else {
            // The serialized path is untrusted metadata. Remote URL resend remains valid.
            if (android.text.TextUtils.isEmpty(oldContent.url)) return null;
            copy.localPath = "";
            copy.trustedLocalSource = null;
        }
        return copy;
    }

    /** Makes an independent, task-owned source for each destination of a shared file. */
    public WKFileContent duplicateOwnedSource(android.content.Context context,
            com.chat.base.config.WKConfig.SessionSnapshot session, String serverScope) throws java.io.IOException {
        TrustedLocalFileStore.SourceGrant grant = trustedLocalSource;
        if (context == null || session == null || grant == null || !grant.matches(session.uid)
                || localPath == null || !new java.io.File(localPath).isFile()) return null;
        java.io.File directory = new java.io.File(context.getCacheDir(), "share_file/" + java.util.UUID.randomUUID());
        if (!directory.mkdirs()) throw new java.io.IOException("Cannot create shared file source directory");
        java.io.File target = new java.io.File(directory, name == null ? "shared_file" : new java.io.File(name).getName());
        try (java.io.FileInputStream in = new java.io.FileInputStream(localPath);
             java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.getFD().sync();
        } catch (Exception error) {
            deleteTree(directory);
            if (error instanceof java.io.IOException) throw (java.io.IOException) error;
            throw new java.io.IOException("Cannot duplicate shared file source", error);
        }
        WKFileContent copy = parcelCopy(this);
        copy.localPath = target.getAbsolutePath();
        copy.size = target.length();
        if (!copy.grantTrustedLocalSource(session, serverScope)) { deleteTree(directory); return null; }
        copy.setOwnedTemporaryDirectory(directory);
        return copy;
    }

    private static WKFileContent parcelCopy(WKFileContent content) {
        Parcel parcel = Parcel.obtain();
        try {
            content.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            WKFileContent copy = WKFileContent.CREATOR.createFromParcel(parcel);
            // WKMediaMessageContent has fields but does not parcel them. Keep the remote URL,
            // while callers set localPath only after validating a trusted snapshot.
            copy.url = content.url;
            return copy;
        } finally { parcel.recycle(); }
    }

    /** Copies a selected content URI into a unique cache directory and grants that exact file. */
    public static WKFileContent fromContentUri(android.content.Context context, android.net.Uri uri,
            String displayName, com.chat.base.config.WKConfig.SessionSnapshot session, String serverScope)
            throws java.io.IOException {
        if (context == null || uri == null) throw new java.io.IOException("Missing selected file");
        String name = displayName;
        if (name == null || name.trim().isEmpty()) name = "file_" + System.currentTimeMillis();
        name = new java.io.File(name).getName();
        if (name.trim().isEmpty() || ".".equals(name) || "..".equals(name))
            throw new java.io.IOException("Invalid selected file name");
        java.io.File parent = new java.io.File(context.getCacheDir(), "file_pick/" + java.util.UUID.randomUUID());
        if (!parent.mkdirs()) throw new java.io.IOException("Cannot create file picker cache directory");
        java.io.File destination = new java.io.File(parent, name);
        try (java.io.InputStream input = context.getContentResolver().openInputStream(uri);
             java.io.FileOutputStream output = new java.io.FileOutputStream(destination)) {
            if (input == null) throw new java.io.IOException("Cannot open selected file");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (Exception error) {
            deleteTree(parent);
            if (error instanceof java.io.IOException) throw (java.io.IOException) error;
            throw new java.io.IOException("Cannot copy selected file", error);
        }
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) ext = name.substring(dot + 1).toLowerCase(java.util.Locale.getDefault());
        try {
            WKFileContent result = fromTrustedLocalFile(destination.getAbsolutePath(), name, ext, session, serverScope);
            if (result == null) throw new java.io.IOException("Selected file is empty or unreadable");
            result.setOwnedTemporaryDirectory(parent);
            return result;
        } catch (Exception error) {
            deleteTree(parent);
            if (error instanceof java.io.IOException) throw (java.io.IOException) error;
            throw new java.io.IOException("Could not trust selected file", error);
        }
    }

    @Override public boolean hasTrustedLocalSource() { return trustedLocalSource != null; }
    @Override public long getTrustedLocalAttachmentSize() { return size; }

    @Override public void prepareTrustedLocalAttachment(String uid, String channelId,
            byte channelType, String clientMsgNo, long expectedSize, PrepareCallback callback) {
        TrustedLocalFileStore.SourceGrant grant = trustedLocalSource;
        trustedLocalSource = null;
        // Ownership transfers before scheduling, so caller cleanup cannot delete a queued source.
        java.io.File owned = ownedTemporaryDirectory == null ? null : ownedTemporaryDirectory.getAndSet(null);
        if (grant == null || !grant.matches(uid)) { deleteTree(owned); callback.onPrepared(null, false); return; }
        try { com.chat.base.utils.AppExecutors.io().execute(() -> {
            java.io.File snapshot = null;
            try {
                snapshot = TrustedLocalFileStore.snapshot(
                        WKBaseApplication.getInstance().getContext(), grant, uid,
                        channelType, channelId, clientMsgNo, expectedSize, localPath);
            } catch (Exception ignored) { }
            finally { deleteTree(owned); }
            callback.onPrepared(snapshot == null ? null : snapshot.getAbsolutePath(), snapshot != null);
        }); } catch (RuntimeException error) {
            deleteTree(owned);
            callback.onPrepared(null, false);
        }
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("extension", extension);
            jsonObject.put("size", size);
            jsonObject.put("url", url);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        this.name = jsonObject.optString("name");
        this.extension = jsonObject.optString("extension");
        this.size = jsonObject.optLong("size");
        this.url = jsonObject.optString("url");
        this.localPath = jsonObject.optString("localPath");
        return this;
    }

    protected WKFileContent(Parcel in) {
        super(in);
        name = in.readString();
        extension = in.readString();
        size = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(name);
        dest.writeString(extension);
        dest.writeLong(size);
    }

    public static final Creator<WKFileContent> CREATOR = new Creator<WKFileContent>() {
        @Override
        public WKFileContent createFromParcel(Parcel in) {
            return new WKFileContent(in);
        }

        @Override
        public WKFileContent[] newArray(int size) {
            return new WKFileContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getSearchableWord() {
        String prefix = WKBaseApplication.getInstance().getContext().getString(R.string.last_message_file);
        return name != null ? prefix + name : prefix;
    }

    @Override
    public String getDisplayContent() {
        return WKBaseApplication.getInstance().getContext().getString(R.string.last_message_file);
    }
}
