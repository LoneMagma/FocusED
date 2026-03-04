## What does this PR do?

<!-- One sentence summary. -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / cleanup
- [ ] Documentation
- [ ] Build / CI

## Related issue

Closes #

## Changes

<!-- List the files changed and why. -->

-
-
-

## Testing

<!-- How did you test this? What device/Android version? -->

- Device: 
- Android version: 
- Tested: 

## Design checklist

Before submitting, confirm your change does not:

- [ ] Add gamification (badges, rewards, addictive streaks)
- [ ] Add re-engagement notifications
- [ ] Introduce colour accents to the UI
- [ ] Add a new permission without strong justification
- [ ] Send any data off-device

## Code checklist

- [ ] No new lint warnings introduced
- [ ] No `apply()` used for SharedPrefs that need immediate reads — use `commit()`
- [ ] Overlays use `themedContext` inflation, not `applicationContext`
- [ ] Any new DB query tested against empty state (fresh install)
- [ ] `local.properties` is not included in this PR

## Screenshots / recording

<!-- If UI changed, attach before/after screenshots. -->
