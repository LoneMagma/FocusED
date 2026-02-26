# FocusED

**Kills Reels and Shorts. Leaves everything else alone.**

[![Version](https://img.shields.io/badge/version-1.4-5a9960?style=flat-square)](https://github.com/LoneMagma/FocusED/releases/tag/v1.4)
[![Platform](https://img.shields.io/badge/platform-Android-3ddc84?style=flat-square&logo=android&logoColor=white)](https://github.com/LoneMagma/FocusED/releases)
[![License](https://img.shields.io/badge/license-MIT-555?style=flat-square)](LICENSE)
[![No Internet](https://img.shields.io/badge/internet%20permission-none-red?style=flat-square)](https://github.com/LoneMagma/FocusED)

---

## What it does

FocusED is an Android accessibility service that intercepts short-form video feeds — Instagram Reels and YouTube Shorts — and closes them immediately. It does not block the apps, break DMs, require an account, or touch the internet. One permission. Zero data collection.

| Surface | Status |
|---|---|
| Instagram — DMs | ✅ Works normally |
| Instagram — Feed | ✅ Works normally |
| Instagram — Reels | ❌ Blocked |
| YouTube — Watch | ✅ Works normally |
| YouTube — Shorts | ❌ Blocked |

---

## How it works

FocusED registers as an Android Accessibility Service and listens for `TYPE_WINDOW_STATE_CHANGED` events. When an activity matching the Reels or Shorts fingerprint appears, it fires a back press immediately — no overlay, no network call, no storage write.

```
UI Event fires → Match pattern → Reels / Shorts? → Back press → Done
```

The APK is under 200 KB. There is no internet permission in the manifest — the app is physically incapable of phoning home.

---

## Installation

> **Requires:** Android 8.0+ · Accessibility Service permission

1. Download `FocusED_v1.4.apk` from [Releases](https://github.com/LoneMagma/FocusED/releases/tag/v1.4)
2. On your device: **Settings → Security → Install unknown apps** → allow your browser or file manager
3. Tap the APK to install
4. Open FocusED → tap **Enable Service** → grant Accessibility permission
5. Done — no further setup required

> You can also get the APK from [pacify.site/focused](https://pacify.site/focused/)

---

## Permissions

| Permission | Why |
|---|---|
| Accessibility Service | Watches for Reels / Shorts UI events and sends a back press |

No `INTERNET`. No `READ_CONTACTS`. No `ACCESS_FINE_LOCATION`. Nothing else.

The full permission list is in [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml).

---

## Build from source

```bash
# Clone
git clone https://github.com/LoneMagma/FocusED.git
cd FocusED

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

Requires Android Studio or the Android SDK command-line tools. Java 11+.

---

## Project structure

```
FocusED/
├── app/
│   └── src/main/
│       ├── java/          # Service logic (~80 lines)
│       └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── README.md
```

---

## Version history

| Version | Notes |
|---|---|
| **v1.4** *(current)* | Improved UI fingerprint matching for latest Instagram and YouTube builds |
| v1.3 | Added YouTube Shorts support |
| v1.2 | Stability fixes for Android 13 |
| v1.0 | Initial release — Instagram Reels only |

---

## Why open source?

The Accessibility Service API can observe everything you do on your phone. The only honest response to that is to make every line of code public and auditable. Read it. The service does exactly what the README says.

---

## License

MIT — see [LICENSE](LICENSE)

---

*Built by [LoneMagma](https://pacify.site) · [pacify.site/focused](https://pacify.site/focused/)*
