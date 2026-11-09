<div align="center">

# Zero Rewards

### Hands-free Microsoft Rewards farmer for Android

Drives the official Bing Android app through an `AccessibilityService`.
No WebView. No scraper. No hosted bot. No passwords leave your phone.

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Platform: Android 10+](https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](#tech-stack)
[![UI: Compose Material 3](https://img.shields.io/badge/UI-Compose%20Material%203-4285F4)](#tech-stack)

</div>

---

## What it is

Zero Rewards opens the Bing app on your phone, switches to each account you
registered, and plays the Rewards activities you'd normally tap through
yourself — searches, daily set, more activities, read-to-earn — stopping
when the day's points are capped.

It does this on-device, driving the real UI the same way your finger would.
There is no cloud service, no proxy, no scraped endpoint, no stored password.
Microsoft sees a normal Bing app being used.

## Features

- **Multi-account, single-tap** — pick accounts from a batch dialog, Zero
  Rewards queues them and runs each one end-to-end.
- **Five farm phases, per-account togglable**:
  - Daily streak panel tap
  - Searches (configurable count, random delays)
  - Daily set (3 tiles)
  - More activities (bonus tiles, handles 1 to 10+)
  - Read-to-earn (news feed, skips ads and videos, 30-point cap)
- **Streak reminders** — schedule any number of daily notifications. Tap the
  notification and an in-app Sí/No prompt opens; choose Sí to pick which
  accounts to farm right there, including a one-tap *all accounts*.
- **Batch queue** — if a run is already in flight, extra accounts land on a
  sequential queue; the service drains it one by one.
- **Survives reboots** — `BootReceiver` re-registers every reminder on boot,
  time change and timezone change.
- **Theming** — Material 3 dynamic color, light/dark, 17 interchangeable
  launcher icons.
- **No internet permission needed for farming** — the app itself never
  contacts Microsoft. Bing does, as Bing always does.

## How it works

1. **You** sign in to each Microsoft account **inside the Bing app** once
   (through Bing's own login flow). Zero Rewards never sees your passwords.
2. **You** add each account to Zero Rewards: a label, the account email, a
   few per-account knobs (search count, which phases to farm).
3. You tap ▶ on the dashboard, or schedule a reminder, or wait for an
   external trigger.
4. Zero Rewards foregrounds the Bing app, drives its account switcher, and
   plays through the enabled phases.
5. During a run the screen stays on and the phone is unusable for ~10-15 min
   per account. Leave it face-down on a charger.

## Screens

| | |
|---|---|
| **Dashboard** | account grid with live phase progress, play button, stop, open settings |
| **Add account** | label + Bing email + searches/day; the email must match what Bing's account switcher shows |
| **Account settings** | per-account phase toggles, search count, min/max delay between searches |
| **Streak reminders** | add/remove notifications at any hour and minute |
| **Appearance** | palette + launcher icon picker |

## Requirements

- Android 10 (API 29) or newer
- Bing Android app (`com.microsoft.bing`) installed and signed in to every
  account you plan to farm
- Zero Rewards granted Accessibility permission:
  `Settings → Accessibility → Downloaded apps → Zero Rewards → Use`

## Build

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android Studio Koala Feature Drop or newer.

## Tech stack

- **Kotlin** 2.0, coroutines + `Flow`
- **Jetpack Compose** + Material 3, Navigation Compose, Material motion easings
- **Room** persistence (accounts, streak reminders, run history)
- **AlarmManager** (`setAndAllowWhileIdle`) for reminder scheduling — no
  `SCHEDULE_EXACT_ALARM` runtime permission required
- **AccessibilityService** as the automation host — gesture dispatch, no
  `performClick`, no root
- **Foreground Service** with an `ArrayDeque` batch queue; one run at a time,
  queue drains sequentially, `ACTION_STOP` cancels everything

## Design notes worth knowing

A handful of lessons from building this that saved real hours:

- Accessibility `performClick` is unreliable on Bing's custom tiles. Zero
  Rewards dispatches a real gesture (≥110 ms with a `lineTo(+2, +2)` so the
  touch system doesn't throw it out as a jittery tap).
- Every runner **anchors on a visible header** (`Intervalos diarios`,
  `Conjunto diario`, `Más actividades`) and selects tiles from that band
  only — otherwise the search bar gets tapped, or the daily-set tiles get
  mistaken for bonus activities.
- `More activities` doesn't re-anchor its header after each tap. Bing
  restores scroll. Trying to re-scroll undoes the user's progress and
  deadlocks when there are 7+ bonus tiles.
- The `0/N` badge next to activity headers is **points**, not tile count.
- Read-to-earn deduplicates articles by the normalized title prefix, not the
  raw accessibility description — Bing adds source/timestamp suffixes
  inconsistently between cards.

All of that lives in `HANDOFF.md` with examples.

## Multi-account

Zero Rewards doesn't manage credentials. It drives Bing's own account
switcher. If the Bing app has multiple MSAs signed in (profile menu →
Switch account → add), Zero Rewards can farm them sequentially from a single
dashboard tap.

## When Bing changes its UI

Bing ships UI updates a few times a year. When tiles stop getting tapped,
the culprit is almost always a renamed label or a changed layout bucket.

The repo contains `HANDOFF.md` with a **Regla Cero**: before editing any UI
selector without a recent structure dump, dump first. Every runner has a
dump helper for its section — it prints the accessibility tree with bounds,
clickability, text, desc, and X/Y-bucketed tile candidates. Writing a new
selector against that dump takes minutes instead of an afternoon.

Localized text hints live in
[`BingPackage.kt`](farmer-core/src/main/kotlin/com/zero/rewards/core/automation/BingPackage.kt).
Most fixes are a label update there.

## Not on the roadmap

- **Running the Bing app in the background** — Android forbids this for an
  `AccessibilityService`. The target app must be foregrounded.
- **Bypassing Microsoft's anti-fraud** — if MS flags an account, Zero
  Rewards doesn't rescue it. Use reasonable search counts.
- **Auto sign-in** — you sign in through Bing's own UI. Zero Rewards does
  not handle passwords and will not.

## Project layout

```
farmer-core/              pure-Kotlin automation + persistence
  account/                Account domain + AccountStore
  automation/             AutomationHost interface + Bing helpers
    AutomationHost
    AutomationNode
    BingPackage           Localized text hints for Bing UI landmarks
    BingPrimitives        ensureBingForeground, scroll, idle, tapCoord
  db/                     Room entities + DAOs
  market/                 MarketConfig + search wordlists
  reminder/               StreakReminder domain + StreakReminderStore
  runner/
    TaskResult
    FarmRun               orchestrates phases per account
    BingAccountSwitcher
    BingIntervalosRunner  streak panel
    BingSearchRunner
    BingDailySetRunner
    BingMoreActivitiesRunner
    BingReadToEarnRunner
    BingRewardsPanel
  scheduler/              WorkManager daily schedule
  search/                 TermPool + sources
  log/

app/                      Android app, Compose UI, services, notifications
  ZeroApp.kt
  AppContainer.kt
  MainActivity.kt         accessibility gate + streak-prompt intent handling
  ui/
    DashboardScreen
    AddAccountScreen
    AccountSettingsScreen
    StreakRemindersScreen
    StreakPromptDialog    Sí/No confirm + account picker with select-all
    AppearanceScreen
    CreditsScreen
  notifications/
    StreakReminderScheduler
    StreakReminderReceiver
    BootReceiver
    StreakPromptBus
  service/
    FarmerForegroundService   single-run + batch-queue host
    ZeroAccessibilityService  AutomationHost implementation
```

## License

[AGPL-3.0](LICENSE). If you host a modified version as a service, you must
publish your changes under the same license.

## Credits

Built with `C.C.` — the tsundere workshop partner who refuses to write a
selector without a dump first.
