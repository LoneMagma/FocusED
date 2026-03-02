# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 2.1.x (stable) | ✅ |
| 2.0.x | ⚠️ Critical fixes only |
| < 2.0 | ❌ |

## Scope

FocusED runs with elevated Android permissions:

- `BIND_ACCESSIBILITY_SERVICE` — reads foreground app package names
- `SYSTEM_ALERT_WINDOW` — draws overlays
- `PACKAGE_USAGE_STATS` — reads per-app usage durations

A vulnerability in this app could potentially expose which apps a user has open, or allow a malicious actor to interfere with overlays. These are the areas we take most seriously.

**In scope:**
- Any issue where FocusED leaks the foreground app name or usage data to another process
- Overlay bypass — a way to dismiss enforcement overlays without going through friction
- Privilege escalation using FocusED's granted permissions
- Data exposure — anything that could make usage history readable by other apps

**Out of scope:**
- The app not blocking apps correctly (this is a bug, not a security issue — file a bug report)
- Issues requiring the attacker to already have physical device access and the screen unlocked
- Theoretical issues with no realistic attack path

## Reporting a vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Email: `security@[yourdomain]` (replace with your actual contact)

Include:
1. Description of the vulnerability
2. Steps to reproduce
3. Affected version
4. Your assessment of impact

You will receive a response within 72 hours. If the issue is confirmed:
- A fix will be prepared privately
- A patched release will be published
- You will be credited in the release notes (unless you prefer to remain anonymous)

FocusED is a small open-source project — we don't have a bug bounty program, but we take security seriously and will handle disclosures promptly.
