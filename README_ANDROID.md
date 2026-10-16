# SafeBuild — Native Android application

Created and branded by **RC Empire**.

This repository includes a native Android Studio project written in Kotlin and Jetpack Compose. It is not a website or a WebView wrapper.

## Minimum Android version

- Configured minimum: **Android 8.0 Oreo (API 26)** (minSdk = 26).
- Compile SDK: Android 35.
- JDK/JVM target: 17.
- Android 7.x and older are unsupported by this configuration.
- API configuration is not the same as verified device support: test Android 8, 9, 10 and newer on real devices/emulators before production release.

## Build APK

1. Install Android Studio with JDK 17 and Android SDK 35.
2. Open this repository's root directory in Android Studio.
3. Allow Gradle sync to finish.
4. Run the :app configuration on an Android device/emulator, or choose **Build > Build APK(s)**.
5. Debug APK output: app/build/outputs/apk/debug/.

The GitHub Actions workflow attempts to build a debug APK on pushes and pull requests to main. Download the SafeBuild-debug-apk artifact from a successful run: https://github.com/imrcraja/SafeBuild/actions

## Current functionality

- Native Compose dashboard and Day/Night mode toggle.
- First-launch animated onboarding/tutorial with progress, glass-style panel, Continue and Skip.
- OpenGL ES animated 3D shield scene integrated into the home screen.
- Repeating cyan pulse animation while local scan work is in progress.
- Android Storage Access Framework file picker.
- Local heuristic text scanning of supported source files and ZIP archives.
- Shareable text security report.
- About & Support links for RC Empire.

## Current limitations

This is an early native implementation, not a completed production security auditor. Live URL scanning, automatic AST rewriting, a full sandbox server startup, re-test loop, hosting-aware secure ZIP packaging, and image/EXIF binary inspection are not fully implemented in the native client. Static heuristics can miss vulnerabilities and may produce false positives. Do not treat a clean scan as proof of security. Review and test fixes before production deployment.

## About & Support

- App: SafeBuild v1.0 — Local Security Auditor
- Developer / maintainer: RC Empire
- Support: support.rcempire@gmail.com
- YouTube: https://youtube.com/@RCRAJAGAMER2.0

The app does not upload source files during local scans. The user can choose to open the external YouTube link or compose a support email.
