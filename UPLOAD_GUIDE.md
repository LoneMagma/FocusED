# Uploading FocusED to GitHub — Step by Step

## Files you need ready

| File | Purpose |
|---|---|
| `README.md` | Replaces the existing one in your project root |
| `LICENSE` | Drop in project root (MIT) |
| `FocusED_v1.4.apk` | Your built APK — attach to the GitHub Release |
| `RELEASE_NOTES_v1.4.md` | Copy-paste this into the Release description on GitHub |

---

## Step 1 — Push the project

```bash
cd /home/lonemagma/Projects/FocusED_v1.4_Stable

# Replace the old README
cp /path/to/new/README.md README.md

# Add LICENSE if not present
cp /path/to/LICENSE LICENSE

# Make sure .gitignore covers build artifacts
# (your existing .gitignore should already handle .gradle/, .idea/, local.properties)

git add README.md LICENSE
git commit -m "docs: polish README for v1.4 stable release"
git push origin main
```

---

## Step 2 — Tag the release

```bash
git tag -a v1.4 -m "FocusED v1.4 — Stable"
git push origin v1.4
```

---

## Step 3 — Create the GitHub Release

1. Go to: `https://github.com/LoneMagma/FocusED/releases/new`
2. **Tag:** select `v1.4` (just pushed above)
3. **Release title:** `FocusED v1.4 — Stable`
4. **Description:** paste the contents of `RELEASE_NOTES_v1.4.md`
5. **Attach binaries:** drag and drop `FocusED_v1.4.apk`
6. Check **Set as the latest release**
7. Click **Publish release**

The APK will now be live at:
`https://github.com/LoneMagma/FocusED/releases/download/v1.4/FocusED_v1.4.apk`

---

## Step 4 — Wire up pacify.site/focused/

Your `focused/index.html` download link already points to:
```
https://github.com/LoneMagma/FocusED/releases
```

If you want a direct APK download button, update the href to:
```
https://github.com/LoneMagma/FocusED/releases/download/v1.4/FocusED_v1.4.apk
```

---

## What GitHub needs vs. what stays local

| Commit to repo | Do NOT commit |
|---|---|
| `app/src/` (all source) | `app/build/` |
| `build.gradle`, `settings.gradle`, `gradle.properties` | `local.properties` |
| `gradle/wrapper/` | `.gradle/` directory |
| `README.md`, `LICENSE` | `.idea/` |
| `.gitignore` | The `.apk` itself (attach to Release instead) |

Your existing `.gitignore` should already exclude most of these — double-check it covers `local.properties` since it contains your SDK path.
