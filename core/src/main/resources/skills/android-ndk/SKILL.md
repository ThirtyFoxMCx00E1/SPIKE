---
name: android-ndk
description: Android native (NDK r30, CMake, JNI) build and debug
---
- Pin `ndkVersion "30.0.16248370"` and CMake 3.22.1 in the app module; set `abiFilters` (arm64-v8a, armeabi-v7a, x86_64).
- JNI names: `Java_<package_with_underscores>_<Class>_<method>`; Kotlin `object` methods receive a `jobject`.
- NDK r28+ aligns .so files to 16 KB pages by default; do not turn that off.
- Link `log` for `__android_log_print`. Prefer C++17, `-fvisibility=hidden`, and exported JNI symbols only.
- Debug crashes with `adb logcat` and `ndk-stack -sym <obj dir>`; check ABI mismatches first.
