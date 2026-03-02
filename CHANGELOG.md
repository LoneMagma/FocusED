# Changelog

All notable changes to FocusED are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [2.1.0] — 2026-03-02

### Fixed
- **Streak showing 31 on fresh install** — `computeStreak()` counted days even with no session history. Streak is now capped by `firstSessionStart()` from the database. A fresh install correctly shows 0.
- **Heatmap cells invisible** — Empty cells were rendered at alpha 20/255, nearly identical to active cells on a dark background. Empty cells are now solid `#1C1C1C`; active cells scale from `#383838` (faint) to `#E8E8E8` (bright) based on usage intensity.
- **`+` / `−` buttons indistinguishable from card background** — Buttons were `#1C1C1C` on a `#141414` card. Added a `1dp #333333` border so buttons read as tappable.
- **Onboarding CTA button had sharp corners** — Was using `@color/btn_primary_bg` directly instead of `@drawable/bg_btn_primary`. Now matches the rounded style of all other primary buttons.
- **EditText inputs had sharp corners** — Task input and typing challenge input used flat `@color/bg_card`. Now use `@drawable/bg_input` (same fill, 12dp corners).
- **ImageButton tint deprecated** — `overlay_budget_warning.xml` used `android:tint` which silently fails on some Android versions, making the dismiss icon invisible. Changed to `app:tint`.
- **`WeeklyHeatmapActivity` crash** — Activity was not registered in `AndroidManifest.xml` due to a malformed Python insertion script. Registered correctly.
- **Permission setup loop** — `apply()` was used for writing `accessibility_granted` pref, which is asynchronous. The 500ms poll could read before the write committed. Changed to `commit()`. Added `FocusedAccessibilityService.instance != null` as a secondary check.
- **Duplicate resource build error** — `themes.xml` and `styles.xml` both defined `Theme.Focused`. Merged into `styles.xml`, removed `themes.xml`.
- **`stateListAnimator` attribute not found** — Style items were missing the `android:` namespace prefix. Fixed all three occurrences.

### Added
- Heatmap day column labels (Mon, Tue... Today) above the grid
- Empty state message in app breakdown ("No app usage recorded this week")
- `firstSessionStart()` query to `AppSessionDao`

---

## [2.0.0] — 2026-02-28

### Added
- **Real friction delays** — Tier 1 is now a 30-second countdown with no skip. Tier 2 is a typing challenge. Tier 3 is a 2.5-minute mandatory wait.
- **Intention gate** — Optional prompt before opening a monitored app: "What are you here for?"
- **Reels / Shorts budgets** — Separate per-app limit for short-form content, enforced by `ShortFormDetector`.
- **App open limits** — Max opens per day per app, tracked in `AppSessionDao`.
- **Scheduled downtime** — Time-of-day blocks with midnight-wrap support (e.g. 22:00–08:00).
- **Post-override reflection** — After overriding a limit and leaving the app, a bottom sheet asks: "Was that worth it?"
- **Streak counter** — Days since last regret override.
- **Weekly heatmap** — 7-day × 24-hour usage grid in `WeeklyHeatmapActivity`.
- **Focus templates** — Five quick-start templates in the Focus mode screen: Deep work (50m), Study (30m), Writing (45m), Planning (20m), Exercise (45m).
- **Grayscale nudge** — At 80% of daily budget, a 15% grey tint is applied to the screen via `GrayscaleManager`.
- **Breathing room** — Small randomised buffer (±2 min) on budget limits so they don't feel mechanical.
- Database migrated to schema v6.

### Changed
- All button backgrounds moved from `@color/*` to `@drawable/bg_btn_*` to prevent Material theme overrides making buttons white-on-white.
- Theme moved from `themes.xml` to `styles.xml` with proper `Widget.Focused.Button.*` style hierarchy.
- Overlay inflation now uses `themedContext` (ContextThemeWrapper) throughout — prevents Material component crashes in overlays.

### Removed
- Session duration slider from the old per-session approach — replaced by daily budgets.

---

## [1.2.0] — 2026-01-15

### Added
- Activity log screen — timestamped record of every block, override, and focus session
- Per-app toggle to enable/disable monitoring without deleting the budget rule

### Fixed
- AccessibilityService would stop after 20–30 minutes on MIUI devices due to aggressive battery optimisation detection. Added guidance in onboarding.
- Overlay sometimes appeared behind the keyboard. Fixed `WindowManager.LayoutParams` type to `TYPE_APPLICATION_OVERLAY`.

---

## [1.1.0] — 2026-01-03

### Added
- Focus mode — lock all monitored apps for a set duration with a task description
- Onboarding flow — 4-screen introduction before permission setup

### Changed
- Minimum SDK raised from API 21 to API 26 (Android 8.0) — required for `TYPE_APPLICATION_OVERLAY`
- Foreground service notification updated to use `NotificationCompat.Builder` correctly on API 33+

---

## [1.0.0] — 2025-12-20

### Initial release
- Daily time budget per app
- Full-screen block overlay when budget is exceeded
- Single-tap override (no friction)
- Persistent foreground service with accessibility monitoring
- Manual app setup (no automatic detection)
