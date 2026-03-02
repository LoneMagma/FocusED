<div align="center">

<br/>

```
███████╗ ██████╗  ██████╗██╗   ██╗███████╗███████╗██████╗
██╔════╝██╔═══██╗██╔════╝██║   ██║██╔════╝██╔════╝██╔══██╗
█████╗  ██║   ██║██║     ██║   ██║███████╗█████╗  ██║  ██║
██╔══╝  ██║   ██║██║     ██║   ██║╚════██║██╔══╝  ██║  ██║
██║     ╚██████╔╝╚██████╗╚██████╔╝███████║███████╗██████╔╝
╚═╝      ╚═════╝  ╚═════╝ ╚═════╝ ╚══════╝╚══════╝╚═════╝
```

**Reclaim your attention. No streaks. No dopamine loops. Just honest friction.**

<br/>

[![Version](https://img.shields.io/badge/version-2.1.0--stable-white?style=flat-square&labelColor=0A0A0A)](https://github.com/yourusername/focused/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-white?style=flat-square&labelColor=0A0A0A&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-white?style=flat-square&labelColor=0A0A0A&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-white?style=flat-square&labelColor=0A0A0A)](LICENSE)
[![Open Source](https://img.shields.io/badge/open_source-yes-white?style=flat-square&labelColor=0A0A0A)](https://github.com/yourusername/focused)

<br/>

> *"The goal isn't to use your phone less. It's to use it on purpose."*

<br/>

</div>

---

## What is FocusED?

FocusED is a screen time app that works differently. Instead of showing you a number and hoping guilt does the work, it puts **deliberate friction** between you and reflexive phone use.

When you've hit your Instagram limit, FocusED shows a 30-second countdown you can't skip — not a popup you dismiss in half a second. During those 30 seconds: *"Take three breaths."* → *"You can still put the phone down."* → *"Almost there..."*

Most of the time, that's enough.

The app is black and white. No colour psychology. No notification badges trying to pull you back. No gamified streaks designed to keep you opening the app. Its only job is to **stay out of your way when you're using your phone intentionally, and slow you down when you're not**.

---

## Features

### ⏱ Enforcement that actually works

| Feature | What it does |
|---|---|
| **Daily time budgets** | Per-app limits (5–120 min). Enforced the moment you exceed them. |
| **Reels / Shorts limits** | Separate budget for short-form content within an app. Instagram gets 30 min — Reels gets 5. |
| **Max opens per day** | Catches reflexive checking that never hits duration limits. *Open Instagram at most 5 times today.* |
| **Scheduled downtime** | Time-of-day blocks with midnight-wrap support. *No social apps 10 pm – 8 am.* |

### 🧱 Friction tiers

FocusED uses a three-tier system when you try to override your limits:

```
Tier 1 — 30-second countdown, no skip button
         Rotating prompts: "Take three breaths" → "Put the phone down" → "Almost there..."

Tier 2 — Typing challenge
         Type a specific phrase to prove intent, not impatience.

Tier 3 — 2.5-minute mandatory wait
         No button. Put the phone down while you wait.
```

### 🎯 Focus mode

Lock all monitored apps while you work. Set a task description and a duration — FocusED blocks everything until the timer ends. Five quick-start templates: Deep work, Study, Writing, Planning, Exercise.

### 📊 Stats & reflection

- **Weekly heatmap** — 7-day × 24-hour usage grid. See exactly when you're most distracted.
- **Override reflection** — After you override a limit and exit the app, FocusED asks: *"Was that worth it?"* Your answer is stored privately and never shown back to you as guilt.
- **Streak** — Days since your last regret override. Accurate: a fresh install shows 0, not 31.

### 🌑 Grayscale nudge

At 80% of your daily budget, FocusED adds a 15% grey tint to the screen. Subtle. Non-blocking. Just enough to make the feed feel less appealing.

---

## Design philosophy

> FocusED is designed on a single premise: **time friction beats mental friction**.
>
> A mandatory 30-second pause with no skip button is more effective than any warning dialog, any guilt trip, any colourful dashboard.
>
> The app does not want you to keep opening it. It does not send re-engagement notifications. It has no social features. It will never show you an ad.

**The colour scheme is black and white on purpose.** Colour is used by apps to attract attention. FocusED does the opposite.

---

## Screenshots

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│                 │  │                 │  │                 │
│   F o c u s E D │  │  What are you   │  │  Manage apps    │
│                 │  │  working on?    │  │                 │
│   Active        │  │                 │  │  Instagram  ──○ │
│                 │  │  [  task...  ]  │  │  30 min/day     │
│  monitoring     │  │                 │  │  5 opens/day    │
│  3 apps         │  │  DEEP  STUDY    │  │                 │
│                 │  │  WORK  WRITING  │  │  YouTube    ──○ │
│  [START FOCUS]  │  │                 │  │  20 min/day     │
│                 │  │  [START FOCUS]  │  │  10pm–8am off   │
└─────────────────┘  └─────────────────┘  └─────────────────┘
     Dashboard           Focus mode          Manage apps
```

---

## Tech stack

```
Language        Kotlin 1.9
Min SDK         API 26 (Android 8.0)
Target SDK      API 34 (Android 14)
Architecture    Single-module, service-based
Database        Room (SQLite), schema v6
Background      AccessibilityService + ForegroundService
DI              Manual (no Dagger/Hilt)
UI              ViewBinding, no Compose
Build           Gradle 8.2, KSP for Room codegen
```

---

## How it works

FocusED runs two persistent components:

**`FocusedAccessibilityService`** listens for `TYPE_WINDOW_STATE_CHANGED` events — the same signal Android uses to track which app is in the foreground. It cannot read the content of other apps (messages, photos, feeds). It only sees the package name.

**`FocusedForegroundService`** keeps a persistent notification alive so Android doesn't kill the process. This is standard practice for any app that needs to do anything in the background.

When an app comes to the foreground, `BudgetEnforcer` checks in order:
1. Is this app in a downtime window?
2. Has the open count limit been hit?
3. Has the short-form content limit been hit?
4. Has the daily time budget been hit?
5. Is a focus session active?

If any check fails, a full-screen overlay is shown via `WindowManager` with `TYPE_APPLICATION_OVERLAY`.

---

## Permissions

| Permission | Why it's needed |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw overlays on top of other apps |
| `BIND_ACCESSIBILITY_SERVICE` | Detect which app is in the foreground |
| `PACKAGE_USAGE_STATS` | Query per-app usage duration for budget enforcement |
| `FOREGROUND_SERVICE` | Keep the monitoring service alive |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `POST_NOTIFICATIONS` | Show the persistent foreground notification |

FocusED does **not** request: camera, microphone, contacts, location, storage, or network access.

---

## Building from source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK API 34
- JDK 17

### Setup

```bash
# 1. Clone the repo
git clone https://github.com/yourusername/focused.git
cd focused

# 2. Create local.properties (Android Studio may do this automatically)
echo "sdk.dir=/path/to/your/Android/sdk" > local.properties

# Windows example:
# sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# 3. Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Finding your SDK path

Open Android Studio → **File → Project Structure → SDK Location**.

Common locations:

| OS | Default path |
|---|---|
| Windows | `C:\Users\<name>\AppData\Local\Android\Sdk` |
| macOS | `/Users/<name>/Library/Android/sdk` |
| Linux | `/home/<name>/Android/Sdk` |

> **Note:** `local.properties` is listed in `.gitignore` and should never be committed. It contains machine-specific paths.

---

## Database schema (v6)

```sql
-- Core tables
app_sessions       -- One row per foreground session (packageName, dayKey, durationMs, shortFormMs)
budget_rules       -- Per-app config (maxSessionDurationMs, maxOpensPerDay, shortFormLimitMs,
                   --   downtimeStartMin, downtimeEndMin, intentionGateEnabled)
activity_log       -- Human-readable event log
focus_sessions     -- Focus mode sessions (task, durationMs, startedAt, endedAt)
intention_options  -- Per-app intention list for the intention gate
friction_attempts  -- Log of each friction event
onboarding_state   -- Tracks onboarding completion + compact mode
reflection_records -- Post-override reflection answers (wasWorthIt nullable bool)
```

---

## Project structure

```
app/src/main/java/com/focused/app/
├── data/
│   ├── dao/          AppSessionDao, BudgetRuleDao, ReflectionRecordDao ...
│   ├── db/           FocusedDatabase (Room, v6)
│   └── model/        AppSession, BudgetRule, ReflectionRecord ...
├── manager/
│   ├── BudgetEnforcer.kt       Core enforcement logic
│   ├── FrictionManager.kt      Tier 1/2/3 friction overlays
│   ├── IntentionGatekeeper.kt  Pre-session intention prompt
│   ├── ReflectionManager.kt    Post-override reflection card
│   ├── GrayscaleManager.kt     80% budget grey tint
│   └── OverlayManager.kt       WindowManager wrapper
├── service/
│   ├── FocusedAccessibilityService.kt
│   └── FocusedForegroundService.kt
├── ui/
│   ├── MainActivity.kt
│   ├── FocusActivity.kt
│   ├── WeeklyHeatmapActivity.kt  (+ HeatmapView custom view)
│   ├── ActivityLogActivity.kt
│   ├── setup/AppSetupActivity.kt
│   └── onboarding/
└── util/
    ├── DateUtil.kt
    ├── PermissionHelper.kt
    └── ShortFormDetector.kt
```

---

## Roadmap

- [ ] Emergency pause — 24-hour self-lockout with accountability message
- [ ] Widget — current usage at a glance on home screen
- [ ] Export data — CSV export of usage history
- [ ] Scheduled focus blocks — recurring calendar-style focus sessions
- [ ] Per-app intention history — see what you said you'd do vs how long you stayed

---

## Why open source?

Screen time apps are a category where trust matters more than almost anywhere else. This app runs with accessibility permissions and is always watching the foreground. You should be able to read every line of code that does that.

No backend. No analytics. No accounts. No data leaves the device.

---

## Contributing

Issues and PRs welcome. A few principles to keep in mind:

- **No gamification.** No streaks designed to be addictive, no badges, no leaderboards.
- **No re-engagement.** No feature should make the user want to open FocusED more.
- **No colour.** The monochrome design is intentional. PRs adding colour accents will be declined.
- **Minimal permissions.** Any new feature that requires a new permission needs a very strong justification.

---

## License

```
MIT License

Copyright (c) 2026 FocusED Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<div align="center">

<br/>

*Built with the belief that your attention is worth protecting.*

<br/>

**[⬆ back to top](#)**

</div>
