# SafeBuild — RC Empire

Local-first security auditing toolkit by **RC Empire**. The repository contains a native Android app (Kotlin + Jetpack Compose) and a separate Electron desktop application. The desktop app is not the Android app wrapped in a browser; the two clients currently have different feature sets.

> **Development status:** early-stage project. Builds and device/OS compatibility must be verified from CI artifacts and real devices. Scan findings are advisory, not a security certification.

## Downloads and build status

- Android APK: build with Android Studio or download the SafeBuild-debug-apk artifact from a successful Android build workflow run.
- Windows installer: download the SafeBuild-Windows-x64 artifact from a successful Windows desktop installer workflow run.
- Open [Actions](https://github.com/imrcraja/SafeBuild/actions) to see current build status and artifacts. Artifacts are available only after the corresponding workflow succeeds.
- Linux/macOS packaging scripts are configured as starting points; those packages are not currently CI-tested or release-certified.

## Minimum supported operating systems

### Android app

- **Configured minimum: Android 8.0 Oreo (API 26)** — from minSdk = 26.
- Build SDK: Android SDK 35; Java/Kotlin target: JVM 17.
- Android 7.x and earlier are not supported by this configuration.
- Android 8.0 is the declared minimum, not a claim that every feature has already been tested on every device. Test on Android 8, 9, 10 and newer before a production release.

### Desktop app

- **Target platform for the current Windows installer: Windows 10/11, 64-bit.**
- Windows 7/8/8.1 are not supported or verified with the current Electron 38 dependency. Do not assume the installer will run on them.
- Linux and macOS packaging commands are included, but minimum distro/macOS versions and cross-platform compatibility are not yet certified. Build and test on each target OS.
- Windows ARM and 32-bit Windows installers are not configured; current Windows target is x64.

## Features and platform differences

### Native Android
- Kotlin + Jetpack Compose UI; not a website/WebView wrapper.
- Dark/day theme, onboarding tutorial, local source/ZIP heuristic scanning, shareable text report, About & Support section.
- OpenGL ES animated 3D shield scene is included in the Android UI source; verify the latest Android CI build and render on a physical device.
- URL audit and desktop hosting capabilities are not all implemented in the native client.

### Desktop (Electron)
- Responsive dashboard, local project ZIP/source scanning, limited URL response-header audit, hosting-header configuration starters, JSON report export and Electron desktop shell.
- Local service defaults to loopback; do not expose it to untrusted networks.
- Desktop and Android are separate clients; features are not yet identical.

## Build Android APK

1. Install Android Studio, JDK 17 and Android SDK 35.
2. Open this repository in Android Studio and let Gradle sync.
3. Run the :app configuration on an emulator/device, or use **Build → Build APK(s)**.
4. Debug APK output: app/build/outputs/apk/debug/.

The Android workflow builds a debug APK on pushes and pull requests to main and uploads it as an Actions artifact if the build succeeds.

## Build Windows EXE installer

Requirements: Windows or a compatible build environment, Node.js 20+, npm.

    npm install
    npm run build
    npm test
    npm run dist:win

The NSIS installer should be created under dist/. It targets Windows x64 and offers a normal install-directory choice plus Start Menu/Desktop shortcuts. The Windows CI workflow builds and uploads the installer when packaging succeeds.

## Build Linux / macOS packages

    npm install
    npm run dist:linux
    npm run dist:mac

Build on the target OS for reliable packaging. These scripts are initial packaging configuration, not proof of compatibility on every distribution or OS release.

## About & Support

- Product: SafeBuild
- Version: 1.0.0 (development packaging version)
- Developer / maintainer: RC Empire
- Support: support.rcempire@gmail.com
- YouTube: [RC RAJA GAMER 2.0](https://youtube.com/@RCRAJAGAMER2.0)

## Security and privacy notes

- Local scans use heuristics and can miss vulnerabilities or produce false positives.
- URL audit checks selected HTTP response headers; it is not a penetration test. Only audit systems you own or have permission to test.
- Do not treat a clean scan as proof of security. Review fixes and test on staging before production.
- No independent security review or full cross-device compatibility certification has been completed.

## Current limitations

Automatic source-code rewriting, a complete fix/re-test loop, public tunnelling, and equivalent feature parity between Android and desktop are not complete. Build success confirms packaging, not security correctness or real-device UX.
