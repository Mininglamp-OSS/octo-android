cmake_minimum_required(VERSION 3.18)
project(octoim_rlottie)

# Reference source only. For a full buildable project with Samsung rlottie
# submodule, see: https://github.com/mzanalyticst/rlottie-android

# Build Samsung rlottie as static library
set(LOTTIE_MODULE OFF CACHE BOOL "" FORCE)
set(LOTTIE_THREAD OFF CACHE BOOL "" FORCE)
set(LOTTIE_CACHE ON CACHE BOOL "" FORCE)
set(LOTTIE_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(rlottie EXCLUDE_FROM_ALL)

# Disable NEON code path — pixman .S ASM doesn't build with modern NDK clang
target_compile_options(rlottie PRIVATE -U__ARM_NEON__)

# rlottie links with -Wl,--version-script via PUBLIC, which propagates to
# consumers and hides our JNI symbols. Strip all link libraries and re-add
# only the ones we need (pthread).
set_target_properties(rlottie PROPERTIES INTERFACE_LINK_LIBRARIES "")

# JNI bridge — export all JNI symbols
add_library(octoim_rlottie SHARED rlottie_jni.cpp)
target_include_directories(octoim_rlottie PRIVATE rlottie/inc)
target_link_libraries(octoim_rlottie rlottie jnigraphics log)
