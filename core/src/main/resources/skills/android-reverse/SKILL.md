---
name: android-reverse
description: Analyze APKs, native libraries and Android app behavior for apps the user owns or may examine
---
- APK: manifest (permissions, exported components, intent filters), signing certificates, classes and decompiled methods; check for native libs per ABI and Flutter/React Native markers.
- Native .so: look at JNI_OnLoad and RegisterNatives to map Java natives to functions, then analyze those with the native-binaries skill.
- Resources and strings often name the feature faster than code does.
- Devices: use adb tools only on the user's own device or emulator; list packages, read logs, pull files the user names.
- Runtime instrumentation or traffic capture only on the user's own app and device, and only after saying what will run.
