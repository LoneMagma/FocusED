# Release signing & Play Protect

## Why Play Protect flags the APK

Play Protect flags sideloaded APKs for two independent reasons:

**1. Not from the Play Store (unfixable for sideloads)**
Every APK installed outside the Play Store triggers a Play Protect warning regardless of what it does. This is by design — Google wants all apps going through their review pipeline. The warning says "App not recognized" or "This app was not installed from the Play Store." Users can tap "Install anyway." There is no way to eliminate this warning for a sideloaded APK.

**2. Accessibility service flags (now fixed)**
The previous build declared `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` and `FLAG_REPORT_VIEW_IDS` in the accessibility service config. These flags grant access to window content and view hierarchies — the same flags used by keyloggers and screen readers. Play Protect treats them as high-risk. FocusED only needs the package name from `TYPE_WINDOW_STATE_CHANGED` events, so both flags have been removed. This reduces the risk rating of the APK significantly.

---

## Permanent fix: Play Store

The only complete fix is publishing to the Play Store. Sideloaded APKs will always show a warning.

Play Store submission requirements for an accessibility service app:
1. The accessibility service description must be specific and honest (already done in `strings.xml`)
2. `canRetrieveWindowContent="false"` must be declared (already done)
3. A privacy policy URL is required — even for free apps
4. A declaration of why the accessibility permission is needed (submitted in Play Console)

---

## Signing a release APK

An unsigned or debug-signed APK triggers additional Play Protect warnings. Release-signed APKs with a stable key fare better.

### Step 1 — Generate a keystore (one time only)

```bash
# Run this in your terminal — save the keystore somewhere safe, NOT in the repo
keytool -genkeypair -v \
  -keystore focused-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias focused \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=FocusED, OU=App, O=YourName, L=City, S=State, C=IN"
```

### Step 2 — Create key.properties in the project root

```
storeFile=focused-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=focused
keyPassword=YOUR_KEY_PASSWORD
```

> ⚠️ `key.properties` and `focused-release.jks` are in `.gitignore`. Never commit them.

### Step 3 — Build signed release APK

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

The release APK is minified, shrunk, and signed. Distribute this instead of the debug APK.

---

## F-Droid

F-Droid is the open-source Android app store. Apps on F-Droid:
- Are not flagged by Play Protect
- Are built from source by F-Droid's own build servers (reproducible builds)
- Are actively looked for by privacy-conscious users

Submit at: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/

This is the recommended distribution path alongside the Play Store.
