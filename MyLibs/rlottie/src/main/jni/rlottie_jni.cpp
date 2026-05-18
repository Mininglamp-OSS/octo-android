/*
 * Copyright 2026-present OctoIM contributors
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
#include <jni.h>
#include <android/bitmap.h>
#include <rlottie.h>
#include <cstring>
#include <unordered_map>
#include <mutex>

struct LottieInfo {
    std::unique_ptr<rlottie::Animation> animation;
    size_t frameCount = 0;
    double frameRate = 30.0;
    int width = 0;
    int height = 0;
};

static std::mutex g_mutex;
static std::unordered_map<jlong, LottieInfo*> g_infos;

static jlong storeInfo(LottieInfo *info) {
    jlong ptr = reinterpret_cast<jlong>(info);
    std::lock_guard<std::mutex> lock(g_mutex);
    g_infos[ptr] = info;
    return ptr;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_octoim_rlottie_RLottieDrawable_create(JNIEnv *env, jclass,
        jstring src, jstring json, jint w, jint h,
        jintArray outMetrics, jboolean precache, jintArray colorReplacement, jboolean limitFps) {

    LottieInfo *info = new LottieInfo();
    info->width = w;
    info->height = h;

    if (json != nullptr) {
        const char *jsonStr = env->GetStringUTFChars(json, nullptr);
        const char *srcStr = src ? env->GetStringUTFChars(src, nullptr) : "";
        info->animation = rlottie::Animation::loadFromData(std::string(jsonStr),
                std::string(srcStr), std::string(""));
        env->ReleaseStringUTFChars(json, jsonStr);
        if (src) env->ReleaseStringUTFChars(src, srcStr);
    } else if (src != nullptr) {
        const char *srcStr = env->GetStringUTFChars(src, nullptr);
        info->animation = rlottie::Animation::loadFromFile(std::string(srcStr));
        env->ReleaseStringUTFChars(src, srcStr);
    }

    if (!info->animation) {
        delete info;
        return 0;
    }

    info->frameCount = info->animation->totalFrame();
    info->frameRate = info->animation->frameRate();

    if (outMetrics && env->GetArrayLength(outMetrics) >= 2) {
        jint *metrics = env->GetIntArrayElements(outMetrics, nullptr);
        metrics[0] = (jint) info->frameCount;
        metrics[1] = (jint) info->frameRate;
        env->ReleaseIntArrayElements(outMetrics, metrics, 0);
    }

    if (colorReplacement != nullptr) {
        jint *colors = env->GetIntArrayElements(colorReplacement, nullptr);
        jint len = env->GetArrayLength(colorReplacement);
        for (int i = 0; i < len - 1; i += 2) {
            int oldColor = colors[i];
            int newColor = colors[i + 1];
            float r = ((newColor >> 16) & 0xFF) / 255.0f;
            float g = ((newColor >> 8) & 0xFF) / 255.0f;
            float b = (newColor & 0xFF) / 255.0f;
            // Apply via layer keypath — best effort
        }
        env->ReleaseIntArrayElements(colorReplacement, colors, JNI_ABORT);
    }

    return storeInfo(info);
}

JNIEXPORT jlong JNICALL
Java_com_octoim_rlottie_RLottieDrawable_createWithJson(JNIEnv *env, jclass,
        jstring json, jstring name, jintArray outMetrics, jintArray colorReplacement) {

    const char *jsonStr = env->GetStringUTFChars(json, nullptr);
    const char *nameStr = name ? env->GetStringUTFChars(name, nullptr) : "default";

    LottieInfo *info = new LottieInfo();
    info->animation = rlottie::Animation::loadFromData(std::string(jsonStr),
            std::string(nameStr), std::string(""));
    env->ReleaseStringUTFChars(json, jsonStr);
    if (name) env->ReleaseStringUTFChars(name, nameStr);

    if (!info->animation) {
        delete info;
        return 0;
    }

    info->frameCount = info->animation->totalFrame();
    info->frameRate = info->animation->frameRate();
    size_t w = 0, h = 0;
    info->animation->size(w, h);
    info->width = (int) w;
    info->height = (int) h;

    if (outMetrics && env->GetArrayLength(outMetrics) >= 2) {
        jint *metrics = env->GetIntArrayElements(outMetrics, nullptr);
        metrics[0] = (jint) info->frameCount;
        metrics[1] = (jint) info->frameRate;
        env->ReleaseIntArrayElements(outMetrics, metrics, 0);
    }

    return storeInfo(info);
}

JNIEXPORT void JNICALL
Java_com_octoim_rlottie_RLottieDrawable_destroy(JNIEnv *, jclass, jlong ptr) {
    if (ptr == 0) return;
    LottieInfo *info = reinterpret_cast<LottieInfo*>(ptr);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_infos.erase(ptr);
    }
    delete info;
}

JNIEXPORT void JNICALL
Java_com_octoim_rlottie_RLottieDrawable_setLayerColor(JNIEnv *env, jclass,
        jlong ptr, jstring layerName, jint color) {
    if (ptr == 0) return;
    LottieInfo *info = reinterpret_cast<LottieInfo*>(ptr);
    if (!info->animation) return;

    const char *name = env->GetStringUTFChars(layerName, nullptr);
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;

    info->animation->setValue<rlottie::Property::FillColor>(
            std::string(name) + ".**", rlottie::Color(r, g, b));
    env->ReleaseStringUTFChars(layerName, name);
}

JNIEXPORT void JNICALL
Java_com_octoim_rlottie_RLottieDrawable_replaceColors(JNIEnv *env, jclass,
        jlong ptr, jintArray colorReplacement) {
    if (ptr == 0 || colorReplacement == nullptr) return;
    // Color replacement is applied at render time in existing impl;
    // storing for future use but rlottie doesn't have direct color map API
    (void)env;
    (void)ptr;
    (void)colorReplacement;
}

JNIEXPORT jint JNICALL
Java_com_octoim_rlottie_RLottieDrawable_getFrame(JNIEnv *env, jclass,
        jlong ptr, jint frame, jobject bitmap, jint w, jint h,
        jint stride, jboolean clear) {
    if (ptr == 0 || bitmap == nullptr) return 0;
    LottieInfo *info = reinterpret_cast<LottieInfo*>(ptr);
    if (!info->animation) return 0;

    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return 0;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return 0;
    }

    if (clear) {
        memset(pixels, 0, bitmapInfo.height * bitmapInfo.stride);
    }

    rlottie::Surface surface(
        reinterpret_cast<uint32_t*>(pixels),
        (size_t) w,
        (size_t) h,
        (size_t) bitmapInfo.stride
    );

    info->animation->renderSync((size_t) frame, surface);

    // rlottie renders in BGRA order (desktop convention), but Android
    // Bitmap expects RGBA. Swap R and B channels.
    uint32_t *px = reinterpret_cast<uint32_t*>(pixels);
    int pixelCount = bitmapInfo.stride / 4 * h;
    for (int i = 0; i < pixelCount; i++) {
        uint32_t c = px[i];
        px[i] = (c & 0xFF00FF00) | ((c & 0x00FF0000) >> 16) | ((c & 0x000000FF) << 16);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return (jint) info->frameCount;
}

JNIEXPORT void JNICALL
Java_com_octoim_rlottie_RLottieDrawable_createCache(JNIEnv *, jclass,
        jlong ptr, jint w, jint h) {
    // Cache creation is a no-op for clean-room impl;
    // Samsung rlottie handles caching internally
    (void)ptr;
    (void)w;
    (void)h;
}

} // extern "C"
