# SafeBuild — Native Android application

Created and branded by **RC Empire**.

This repository includes a native Android Studio project written in Kotlin and Jetpack Compose. It is not a website or a WebView wrapper.

## Build
1. Install Android Studio with JDK 17 and Android SDK 35.
2. Open this repository's root directory in Android Studio.
3. Allow Gradle sync to finish.
4. Run the `:app` configuration on an Android device/emulator, or choose **Build > Build APK(s)**.

## Current functionality
- Native Compose dashboard and Day/Night mode toggle.
- First-launch animated onboarding/tutorial with progress, glass-style panel, Continue and Skip; completion is saved locally so it does not repeat on every launch.
- Repeating cyan pulse animation while local scan work is in progress.
- Android Storage Access Framework file picker.
- Local heuristic text scanning of supported source files and ZIP archives.
- Shareable text security report.
- About & Support links for RC Empire.

## Current limitations
This is an initial native implementation, not a completed production security auditor. Live URL scanning is currently a placeholder. Automatic AST rewriting, sandbox server startup, re-test loop, hosting-aware secure ZIP packaging, and image/EXIF binary inspection are not yet implemented. Static heuristics can miss vulnerabilities and may produce false positives. Do not treat a clean scan as proof of security. Review and test fixes before production deployment.

## Branding & support
- App: SafeBuild v1.0 — Local Security Auditor
- Enterprise: RC Empire
- Support: support.rcempire@gmail.com
- YouTube: https://youtube.com/@RCRAJAGAMER2.0

The app does not upload source files. The user can choose to open the external YouTube link or compose a support email.
