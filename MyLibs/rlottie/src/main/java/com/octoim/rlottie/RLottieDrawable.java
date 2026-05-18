/*
 * Copyright 2026-present OctoIM contributors
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.octoim.rlottie;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Animatable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

public class RLottieDrawable extends BitmapDrawable implements Animatable {

    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    private static final ThreadPoolExecutor frameExecutor = new ThreadPoolExecutor(
            2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    protected int width;
    protected int height;
    protected final int[] metaData = new int[3];
    protected int timeBetweenFrames;
    protected int customEndFrame = -1;
    protected boolean playInDirectionOfCustomEndFrame;

    private int[] newReplaceColors;
    private int[] pendingReplaceColors;
    private HashMap<String, Integer> newColorUpdates;
    private volatile HashMap<String, Integer> pendingColorUpdates;

    private WeakReference<Runnable> onFinishCallback;
    private int finishFrame;
    protected int autoRepeat = 1;
    private long lastFrameTime;
    protected volatile boolean nextFrameIsLast;

    protected volatile Bitmap renderingBitmap;
    protected volatile Bitmap nextRenderingBitmap;
    protected volatile Bitmap backgroundBitmap;

    protected volatile Runnable loadFrameTask;
    protected boolean waitingForNextTask;
    protected boolean destroyWhenDone;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean forceFrameRedraw;
    private boolean applyingLayerColors;

    protected int currentFrame;
    private boolean shouldLimitFps;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private boolean applyTransformation;
    private final Rect dstRect = new Rect();

    protected volatile boolean isRunning;
    protected volatile boolean isRecycled;
    protected volatile long nativePtr;

    private boolean invalidateOnProgressSet;
    private boolean isInvalid;

    private ArrayList<WeakReference<View>> parentViews = new ArrayList<>();

    // Constructor: Context + raw resource
    public RLottieDrawable(Context context, int rawRes, String name, int w, int h) {
        this(context, rawRes, name, w, h, false, null);
    }

    // Constructor: Context + raw resource + cache + colors
    public RLottieDrawable(Context context, int rawRes, String name, int w, int h,
                           boolean precache, int[] colorReplacement) {
        this.width = w;
        this.height = h;
        this.shouldLimitFps = false;

        String json = readRes(context, null, rawRes);
        if (json != null) {
            nativePtr = createWithJson(json, name, metaData, colorReplacement);
        }
        int fps = metaData[1] > 0 ? metaData[1] : 30;
        timeBetweenFrames = Math.max(16, (int) (1000.0f / fps));
    }

    // Constructor: Context + JSON string + size + cache + colors
    public RLottieDrawable(Context context, String json, int w, int h,
                           boolean precache, int[] colorReplacement) {
        this.width = w;
        this.height = h;
        this.shouldLimitFps = false;

        if (json != null) {
            nativePtr = createWithJson(json, "default", metaData, colorReplacement);
        }
        int fps = metaData[1] > 0 ? metaData[1] : 30;
        timeBetweenFrames = Math.max(16, (int) (1000.0f / fps));
    }

    // Constructor: File + size + limitFps + precache
    public RLottieDrawable(File file, int w, int h, boolean limitFps, boolean precache) {
        this(file, w, h, limitFps, precache, null);
    }

    // Constructor: File + size + limitFps + precache + colors
    public RLottieDrawable(File file, int w, int h, boolean limitFps, boolean precache,
                           int[] colorReplacement) {
        this.width = w;
        this.height = h;
        this.shouldLimitFps = limitFps;
        this.timeBetweenFrames = limitFps ? 33 : 16;

        nativePtr = create(file.getAbsolutePath(), null, w, h, metaData,
                precache, colorReplacement, shouldLimitFps);
    }

    // Constructor: File + name + size + limitFps + precache + colors
    public RLottieDrawable(File file, String name, int w, int h, boolean limitFps,
                           boolean precache, int[] colorReplacement) {
        this.width = w;
        this.height = h;
        this.shouldLimitFps = limitFps;
        this.timeBetweenFrames = limitFps ? 33 : 16;

        nativePtr = create(file.getAbsolutePath(), name, w, h, metaData,
                precache, colorReplacement, shouldLimitFps);
    }

    // Constructor: InputStream + name + size + precache + colors
    public RLottieDrawable(InputStream stream, String name, int w, int h,
                           boolean precache, int[] colorReplacement) {
        this.width = w;
        this.height = h;
        this.shouldLimitFps = false;

        String json = toString(stream);
        if (json != null) {
            nativePtr = createWithJson(json, name, metaData, colorReplacement);
        }
        int fps = metaData[1] > 0 ? metaData[1] : 30;
        timeBetweenFrames = Math.max(16, (int) (1000.0f / fps));
    }

    // ---- Native methods ----
    public static native long create(String src, String json, int w, int h,
                                     int[] outMetrics, boolean precache, int[] colorReplacement,
                                     boolean limitFps);
    protected static native long createWithJson(String json, String name,
                                                int[] outMetrics, int[] colorReplacement);
    public static native void destroy(long ptr);
    private static native void setLayerColor(long ptr, String layerName, int color);
    private static native void replaceColors(long ptr, int[] colors);
    public static native int getFrame(long ptr, int frame, Bitmap bitmap,
                                      int w, int h, int stride, boolean clear);
    private static native void createCache(long ptr, int w, int h);

    // ---- Public API ----

    public void addParentView(View view) {
        if (view == null) return;
        for (int i = 0; i < parentViews.size(); i++) {
            View v = parentViews.get(i).get();
            if (v == view) return;
            if (v == null) {
                parentViews.remove(i);
                i--;
            }
        }
        parentViews.add(new WeakReference<>(view));
    }

    public void removeParentView(View view) {
        if (view == null) return;
        for (int i = 0; i < parentViews.size(); i++) {
            View v = parentViews.get(i).get();
            if (v == view || v == null) {
                parentViews.remove(i);
                i--;
            }
        }
    }

    protected boolean hasParentView() {
        for (int i = 0; i < parentViews.size(); i++) {
            if (parentViews.get(i).get() != null) return true;
        }
        return false;
    }

    protected void invalidateInternal() {
        for (int i = 0; i < parentViews.size(); i++) {
            View v = parentViews.get(i).get();
            if (v != null) {
                v.invalidate();
            }
        }
        invalidateSelf();
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getCustomEndFrame() {
        return customEndFrame;
    }

    public long getDuration() {
        if (metaData[0] <= 0) return 0;
        int fps = metaData[1] > 0 ? metaData[1] : 30;
        return (long) ((float) metaData[0] / fps * 1000);
    }

    public void setPlayInDirectionOfCustomEndFrame(boolean value) {
        playInDirectionOfCustomEndFrame = value;
    }

    public boolean setCustomEndFrame(int frame) {
        if (frame > metaData[0] || frame < 0) return false;
        customEndFrame = frame;
        return true;
    }

    public int getFramesCount() {
        return metaData[0];
    }

    public void setAllowDecodeSingleFrame(boolean value) {
        decodeSingleFrame = value;
        if (decodeSingleFrame) {
            scheduleNextGetFrame();
        }
    }

    public void recycle() {
        isRecycled = true;
        checkRunningTasks();
    }

    protected void checkRunningTasks() {
        if (loadFrameTask != null) {
            destroyWhenDone = true;
        } else {
            recycleResources();
            if (nativePtr != 0) {
                destroy(nativePtr);
                nativePtr = 0;
            }
        }
    }

    protected void recycleResources() {
        if (renderingBitmap != null) {
            renderingBitmap.recycle();
            renderingBitmap = null;
        }
        if (backgroundBitmap != null) {
            backgroundBitmap.recycle();
            backgroundBitmap = null;
        }
        if (nextRenderingBitmap != null) {
            nextRenderingBitmap.recycle();
            nextRenderingBitmap = null;
        }
    }

    public void setAutoRepeat(int value) {
        autoRepeat = value;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getOpacity() {
        return -3; // TRANSLUCENT
    }

    @Override
    public void start() {
        if (isRunning || isRecycled) return;
        isRunning = true;
        scheduleNextGetFrame();
        invalidateInternal();
    }

    public boolean restart() {
        if (autoRepeat < 1 || isRecycled) return false;
        currentFrame = 0;
        nextFrameIsLast = false;
        if (!isRunning) {
            isRunning = true;
        }
        scheduleNextGetFrame();
        invalidateInternal();
        return true;
    }

    public void beginApplyLayerColors() {
        applyingLayerColors = true;
    }

    public void commitApplyLayerColors() {
        applyingLayerColors = false;
        if (pendingColorUpdates != null && !pendingColorUpdates.isEmpty()) {
            scheduleNextGetFrame();
        }
    }

    public void replaceColors(int[] colors) {
        newReplaceColors = colors;
        requestRedrawColors();
    }

    public void setLayerColor(String layerName, int color) {
        if (newColorUpdates == null) {
            newColorUpdates = new HashMap<>();
        }
        newColorUpdates.put(layerName, color);
        requestRedrawColors();
    }

    private void requestRedrawColors() {
        if (!applyingLayerColors) {
            scheduleNextGetFrame();
        }
    }

    protected boolean scheduleNextGetFrame() {
        if (loadFrameTask != null || nativePtr == 0 || isRecycled || width == 0 || height == 0) {
            return false;
        }

        loadFrameTask = () -> {
            if (isRecycled) return;

            if (backgroundBitmap == null) {
                try {
                    backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                } catch (Throwable e) {
                    return;
                }
            }

            if (pendingColorUpdates != null) {
                for (HashMap.Entry<String, Integer> entry : pendingColorUpdates.entrySet()) {
                    setLayerColor(nativePtr, entry.getKey(), entry.getValue());
                }
                pendingColorUpdates = null;
            }

            if (pendingReplaceColors != null) {
                replaceColors(nativePtr, pendingReplaceColors);
                pendingReplaceColors = null;
            }

            try {
                getFrame(nativePtr, currentFrame, backgroundBitmap, width, height,
                        backgroundBitmap.getRowBytes(), true);
            } catch (Exception e) {
                // ignore
            }

            uiHandler.post(() -> {
                nextRenderingBitmap = backgroundBitmap;
                backgroundBitmap = null;
                loadFrameTask = null;

                if (destroyWhenDone) {
                    checkRunningTasks();
                    return;
                }

                if (isRunning) {
                    if (customEndFrame >= 0) {
                        if (playInDirectionOfCustomEndFrame) {
                            if (currentFrame > customEndFrame) {
                                currentFrame--;
                            } else if (currentFrame < customEndFrame) {
                                currentFrame++;
                            }
                        } else {
                            currentFrame++;
                            if (currentFrame > customEndFrame) {
                                currentFrame = 0;
                            }
                        }
                    } else {
                        currentFrame++;
                        if (currentFrame >= metaData[0]) {
                            if (autoRepeat == 1) {
                                currentFrame = 0;
                            } else if (autoRepeat == 2) {
                                currentFrame = 0;
                                autoRepeat = 0;
                            } else {
                                currentFrame = metaData[0] - 1;
                                nextFrameIsLast = true;
                            }
                        }
                    }

                    if (nextFrameIsLast) {
                        isRunning = false;
                        if (onFinishCallback != null) {
                            Runnable cb = onFinishCallback.get();
                            if (cb != null) cb.run();
                        }
                    }
                }

                if (!isRecycled) {
                    invalidateInternal();
                }

                if (isRunning && !nextFrameIsLast) {
                    uiHandler.postDelayed(() -> {
                        if (isRunning && !isRecycled) {
                            scheduleNextGetFrame();
                        }
                    }, timeBetweenFrames);
                } else if (decodeSingleFrame && !singleFrameDecoded) {
                    singleFrameDecoded = true;
                }
            });
        };

        pendingColorUpdates = newColorUpdates;
        newColorUpdates = null;
        pendingReplaceColors = newReplaceColors;
        newReplaceColors = null;

        frameExecutor.execute(loadFrameTask);
        return true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    public void setCurrentFrame(int frame) {
        setCurrentFrame(frame, true, false);
    }

    public void setCurrentFrame(int frame, boolean force) {
        setCurrentFrame(frame, force, false);
    }

    public void setCurrentFrame(int frame, boolean force, boolean notify) {
        if (frame < 0 || frame >= metaData[0]) return;
        currentFrame = frame;
        nextFrameIsLast = false;
        if (force) {
            singleFrameDecoded = false;
            scheduleNextGetFrame();
        }
        if (notify) {
            invalidateInternal();
        }
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    public void setProgress(float progress, boolean force) {
        if (metaData[0] <= 0) return;
        int frame = (int) (metaData[0] * Math.max(0, Math.min(1, progress)));
        if (frame >= metaData[0]) frame = metaData[0] - 1;
        setCurrentFrame(frame, force);
        if (invalidateOnProgressSet) {
            invalidateInternal();
        }
    }

    public void setProgressMs(long ms) {
        if (metaData[0] <= 0) return;
        int frameCount = metaData[0];
        float progress = ms / (float) getDuration();
        int frame = (int) (frameCount * Math.max(0, Math.min(1, progress)));
        if (frame >= frameCount) frame = frameCount - 1;
        setCurrentFrame(frame, true, true);
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyTransformation = true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (isRecycled) return;

        Bitmap bitmap = getBitmapToDraw();
        if (bitmap == null) return;

        if (applyTransformation) {
            dstRect.set(getBounds());
            scaleX = (float) dstRect.width() / width;
            scaleY = (float) dstRect.height() / height;
            applyTransformation = false;
        }

        canvas.save();
        canvas.translate(dstRect.left, dstRect.top);
        canvas.scale(scaleX, scaleY);
        canvas.drawBitmap(bitmap, 0, 0, getPaint());
        canvas.restore();

        if (isRunning) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastFrameTime;
            if (lastFrameTime == 0 || elapsed >= timeBetweenFrames) {
                lastFrameTime = now;
                if (nextRenderingBitmap != null) {
                    renderingBitmap = nextRenderingBitmap;
                    nextRenderingBitmap = null;
                }
            }
            uiHandler.postDelayed(this::invalidateInternal,
                    Math.max(1, timeBetweenFrames - elapsed));
        }
    }

    @Nullable
    private Bitmap getBitmapToDraw() {
        if (renderingBitmap != null) return renderingBitmap;
        if (nextRenderingBitmap != null) {
            renderingBitmap = nextRenderingBitmap;
            nextRenderingBitmap = null;
            return renderingBitmap;
        }
        return null;
    }

    public void updateCurrentFrame() {
        invalidateInternal();
    }

    @Override
    public int getMinimumHeight() {
        return height;
    }

    @Override
    public int getMinimumWidth() {
        return width;
    }

    public Bitmap getRenderingBitmap() {
        return renderingBitmap;
    }

    public Bitmap getNextRenderingBitmap() {
        return nextRenderingBitmap;
    }

    public Bitmap getBackgroundBitmap() {
        return backgroundBitmap;
    }

    public Bitmap getAnimatedBitmap() {
        if (renderingBitmap != null) return renderingBitmap;
        if (nextRenderingBitmap != null) return nextRenderingBitmap;
        return null;
    }

    public boolean hasBitmap() {
        return renderingBitmap != null || nextRenderingBitmap != null;
    }

    public void setInvalidateOnProgressSet(boolean value) {
        invalidateOnProgressSet = value;
    }

    public void setCurrentParentView(View view) {
        // compatibility
    }

    public void setOnFinishCallback(Runnable callback, int frame) {
        onFinishCallback = callback != null ? new WeakReference<>(callback) : null;
        finishFrame = frame;
    }

    public void setVibrationPattern(HashMap<Integer, Integer> pattern) {
        // compatibility
    }

    public boolean isHeavyDrawable() {
        return true;
    }

    // ---- Utility methods ----

    public static String readRes(Context context, File file, int rawRes) {
        InputStream is = null;
        try {
            if (file != null) {
                is = new java.io.FileInputStream(file);
            } else {
                is = context.getResources().openRawResource(rawRes);
            }
            return toString(is);
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    public static String toString(InputStream is) {
        if (is == null) return null;
        try {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] toByte(InputStream is) {
        if (is == null) return null;
        try {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
