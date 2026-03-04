---
name: Bug report
about: Something is broken or behaving unexpectedly
title: "[BUG] "
labels: bug
assignees: ''
---

## What happened?

<!-- A clear description of what went wrong. -->

## What did you expect to happen?

<!-- What should have happened instead? -->

## Steps to reproduce

1. 
2. 
3. 

## Device info

| Field | Value |
|---|---|
| Device | <!-- e.g. Pixel 7, Samsung S23 --> |
| Android version | <!-- e.g. Android 13 (API 33) --> |
| FocusED version | <!-- shown in app footer, e.g. 2.1.0-stable --> |
| Tablet? | <!-- yes / no --> |

## Logcat output

<!-- If you can capture a crash log, paste it here. In Android Studio: Logcat → filter by "focused" or "FocusED". -->

```
paste log here
```

## Additional context

<!-- Screenshots, screen recordings, or anything else that helps. -->

---

> **Note:** FocusED uses an AccessibilityService and WindowManager overlays. If the issue involves overlays not appearing or the service stopping, please confirm:
> - Accessibility permission is still enabled (Settings → Accessibility → FocusED)
> - Battery optimisation is disabled for FocusED (Settings → Battery → FocusED → Unrestricted)
