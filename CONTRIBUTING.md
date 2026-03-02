# Contributing to FocusED

Thanks for wanting to contribute. This document covers everything you need: setting up the dev environment, the architecture, code conventions, and what kinds of changes are in scope.

---

## Design principles (read before writing code)

FocusED has strong opinions. Before contributing, understand what this project is and isn't trying to be.

### In scope
- Making enforcement more reliable
- Reducing false positives (blocking the wrong apps, wrong times)
- Improving friction UX (the overlays, the countdowns)
- Performance and battery efficiency
- Accessibility improvements
- Bug fixes

### Out of scope — PRs will be closed
| Category | Examples |
|---|---|
| **Gamification** | Addictive streak counters, badges, rewards, leaderboards |
| **Re-engagement** | Notifications designed to make users open FocusED more |
| **Social** | Sharing, accountability partners, public profiles |
| **Colour** | Any colour accent in the UI — the monochrome palette is intentional |
| **Cloud / accounts** | Any feature that requires data to leave the device |
| **New permissions** | Any new sensitive permission without exceptional justification |

---

## Setting up the project

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (bundled with recent Android Studio)
- Android SDK API 34

### First-time setup

```bash
git clone https://github.com/yourusername/focused.git
cd focused

# Create local.properties (Android Studio may do this automatically on first open)
echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties
```

Open the project in Android Studio. Sync Gradle. You're ready.

### Running on device

FocusED requires three permissions that cannot be granted via ADB and must be done manually:

1. **Overlay** — Settings → Apps → FocusED → Display over other apps → Allow
2. **Accessibility** — Settings → Accessibility → FocusED → Enable
3. **Usage stats** — Settings → Apps → Special app access → Usage access → FocusED → Allow

After granting: open FocusED → tap "Enable FocusED". The foreground service will start.

### Running in emulator

The accessibility service and overlays work in the emulator. Usage stats are limited — the emulator doesn't generate real app sessions. For testing `BudgetEnforcer`, use the debug test overlay in `OverlayManager`.

---

## Architecture overview

```
AccessibilityService
      │
      │  TYPE_WINDOW_STATE_CHANGED (package name only)
      ▼
BudgetEnforcer ──── checks in order:
      │               1. downtime window
      │               2. open count limit
      │               3. short-form limit
      │               4. daily time budget
      │               5. active focus session
      │
      ├── no violation → IntentionGatekeeper (optional prompt)
      │
      └── violation → OverlayManager → WindowManager overlay
                                │
                                ├── FrictionManager (tier 1/2/3)
                                ├── ReflectionManager (post-override)
                                └── GrayscaleManager (tint at 80%)
```

All enforcement logic lives in `manager/`. The service layer (`service/`) only handles lifecycle and event routing. UI layer (`ui/`) is for settings screens only — never put enforcement logic there.

### Database

Room, schema version 6. Migration scripts are in `data/db/migrations/`. If you add a table or column, you must write a migration — `fallbackToDestructiveMigration()` is not set.

### Overlays

Overlays are inflated using `LayoutInflater.from(themedContext)` where `themedContext` is a `ContextThemeWrapper` with `Theme.Focused`. **Never** inflate overlays with `applicationContext` directly — Material components will crash.

### SharedPreferences

Use `commit()` not `apply()` anywhere a pref write must be visible within the same 500ms poll cycle. Use `apply()` everywhere else.

---

## Code conventions

### Kotlin style
- `ktlint` rules apply — run `./gradlew ktlintCheck` before submitting
- No wildcard imports
- `data class` for DAO result types, not separate model files
- Coroutines: `Dispatchers.IO` for all DB/file operations, `Dispatchers.Main` for UI updates

### XML layouts
- All buttons must use `@drawable/bg_btn_*` backgrounds — never `@color/*` on a Button element (Material theme overrides it)
- EditText fields use `@drawable/bg_input`
- No `android:tint` on ImageButton — use `app:tint`
- No hardcoded colours — reference `@color/*` from `colors.xml`

### Naming
```
Overlays:         overlay_[purpose].xml          overlay_intention_gate.xml
Activities:       Activity[Name].kt               FocusActivity.kt
Managers:         [Name]Manager.kt                BudgetEnforcer.kt
DAOs:             [Model]Dao.kt                   AppSessionDao.kt
DB models:        [Name].kt (data class)          AppSession.kt
```

---

## Submitting a PR

1. Fork the repo, create a branch: `git checkout -b fix/overlay-crash-api-34`
2. Make your changes
3. Run `./gradlew lintDebug` — zero new warnings
4. Test on a real device (emulator is acceptable for non-overlay changes)
5. Fill out the PR template completely
6. Submit against `develop`, not `main`

Branch naming:
- `fix/` — bug fixes
- `feature/` — new features  
- `refactor/` — cleanup with no behaviour change
- `docs/` — documentation only

---

## Reporting bugs

Use the bug report issue template. Include:
- Device and Android version
- FocusED version (visible in the app)
- Logcat filtered by `focused` or `FocusED`
- Whether battery optimisation is disabled for the app

The most common false bug reports:
- **Service keeps stopping** → battery optimisation is enabled
- **Overlays not showing** → overlay permission was revoked after an OS update
- **Streak shows wrong number** → expected, streak counts from first session

---

## Questions

Open a Discussion, not an Issue.
