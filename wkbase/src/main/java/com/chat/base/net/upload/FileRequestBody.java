package com.chat.base.net.upload;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.chat.base.okgo.OkGoUploadOrDownloadProgress;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;

public class FileRequestBody extends RequestBody {
    private final RequestBody requestBody;
    private long mCurrentLength;
    private final Object tag;
    Handler handler = new Handler(Looper.getMainLooper());

    public FileRequestBody(RequestBody requestBody, Object tag) {
        super();
        this.tag = tag;
        this.requestBody = requestBody;
    }

    @Override
    public long contentLength() throws IOException {
        return requestBody.contentLength();
    }

    @Override
    public MediaType contentType() {
        return requestBody.contentType();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        final long contentLength = contentLength();
        ForwardingSink forwardingSink = new ForwardingSink(sink) {
            @Override
            public void write(@NonNull Buffer source, long byteCount) throws IOException {
                // 每次写都会来这里
                mCurrentLength += byteCount;
                float f1 = mCurrentLength / (float) contentLength;
                handler.post(() -> OkGoUploadOrDownloadProgress.getInstance().seekProgress(tag, f1));
                super.write(source, byteCount);
            }
        };
        BufferedSink bufferedSink = Okio.buffer(forwardingSink);
        requestBody.writeTo(bufferedSink);
        bufferedSink.flush();
    }

}