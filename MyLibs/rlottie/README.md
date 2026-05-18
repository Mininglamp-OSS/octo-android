# RLottie Clean-Room Implementation

This directory contains a clean-room Lottie animation library that replaces
the original Telegram GPL v2 wrapper.

## License

- **Native engine**: [Samsung/rlottie](https://github.com/Samsung/rlottie) — MIT License
- **Java/JNI bridge** (`src/`): Apache License 2.0

## Architecture

```
lottiev1.aar (prebuilt)
├── jni/arm64-v8a/liboctoim_rlottie.so   ← Samsung rlottie (MIT) + rlottie_jni.cpp
├── jni/armeabi-v7a/liboctoim_rlottie.so
└── classes.jar
    └── com/octoim/rlottie/
        ├── RLottieApplication.java   ← Native library loader
        ├── RLottieDrawable.java      ← Core animation drawable
        └── RLottieImageView.java     ← Convenience ImageView
```

## Building from source

Full buildable source: **https://github.com/mzanalyticst/rlottie-android**

```bash
git clone --recursive https://github.com/mzanalyticst/rlottie-android.git
cd rlottie-android
./gradlew :rlottie:assembleRelease
cp rlottie/build/outputs/aar/rlottie-release.aar \
   ../dmwork-android/MyLibs/rlottie/lottiev1.aar
```

Requires: Android NDK 25+, CMake 3.22+, JDK 17.

## Key implementation notes

- BGRA→RGBA pixel channel swap in `rlottie_jni.cpp:getFrame()` — rlottie
  uses desktop BGRA convention, Android Bitmap expects RGBA.
- NEON ASM disabled — uses pure C++ fallback (sufficient for small UI animations).
- Samsung rlottie linked as git submodule at `rlottie/src/main/jni/rlottie/`.
