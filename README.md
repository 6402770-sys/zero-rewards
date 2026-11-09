<div align="center">

# Zero Rewards

### Hands-free Microsoft Rewards for Android

Zero Rewards taps through your daily Rewards activities in the official Bing app,
for every account you have, while the phone sits on the charger.

[![Latest release](https://img.shields.io/github/v/release/6402770-sys/zero-rewards?label=release)](https://github.com/6402770-sys/zero-rewards/releases/latest)
[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](#build-from-source)

</div>

---

> [!WARNING]
> Zero Rewards is an unofficial project and isn't affiliated with Microsoft.
> Automating Microsoft Rewards goes against its terms of service, and Microsoft
> can void points or suspend accounts it flags. Use it at your own risk.

## How it works

Zero Rewards doesn't talk to Microsoft directly. It uses Android's
**Accessibility** permission to drive the Bing app the same way your finger would:
it opens Bing, switches to each account, plays the activities you turned on, and
moves on to the next account.

Because of that:

- **It never sees your passwords.** You sign in inside the Bing app yourself, and
  Zero Rewards only uses Bing's own account switcher.
- **The phone is busy during a run.** Bing has to be on screen, so the phone
  can't be used for about 10–15 minutes per account. Leave it face down on a
  charger.
- **It depends on Bing's layout.** When Microsoft redesigns a screen, the step
  that uses it can stop working until the app is updated.

## Features

- **Five activities, each one optional per account:** daily streak, searches,
  daily set, more activities and read to earn.
- **Searches that read naturally:** terms come from random Wikipedia articles and
  word lists in the account's language, with a random pause between them.
- **Many accounts in a row:** pick several accounts and they run one after another.
  Starting another run while one is in progress adds it to the queue.
- **Streak reminders:** daily notifications at any times you choose. Tap one,
  answer *Sí*, pick the accounts, and the run starts.
- **Nightly run:** once a day, early in the morning (around 2–4 AM), the app runs
  every account by itself if the phone is online and the battery isn't low.
- **Reminders survive reboots** and changes to the clock or time zone.
- **Appearance:** light, dark or system theme; 8 accent colors or any custom
  color; 16 launcher icons.

## Requirements

- Android 10 or newer.
- The [Bing app](https://play.google.com/store/apps/details?id=com.microsoft.bing)
  (`com.microsoft.bing`), signed in to every account you want to use.
- The Bing app in **Spanish**. That's the language Zero Rewards is developed and
  tested with. Most screens are also recognized in English; other languages may
  not work.

## Install

1. Download the latest `zero-rewards-vX.Y.Z.apk` from
   [Releases](https://github.com/6402770-sys/zero-rewards/releases/latest).
2. Open it on the phone and allow installing from that source if Android asks.

New versions install over the old one and keep your accounts and settings.

<details>
<summary>Check that the APK is genuine</summary>

Every release is signed with the same key. With the Android SDK build tools:

```bash
apksigner verify --print-certs zero-rewards-vX.Y.Z.apk
```

The line `Signer #1 certificate SHA-256 digest` must read:

```
8243b9c9de91d0b91e2fa1862999d57e31c534ad3b56a001f716c2289307d859
```

</details>

## Setup

1. **Sign in to Bing.** In the Bing app, open your profile and add every Microsoft
   account with *Switch account*.
2. **Turn on Accessibility.** Zero Rewards asks for it on first launch. You can
   also go to *Settings → Accessibility → Downloaded apps → Zero Rewards*.
   On Android 13 and newer the switch may be greyed out as a *restricted setting*.
   If so, go to *Settings → Apps → Zero Rewards*, tap **⋮ → Allow restricted
   settings**, and try again.
3. **Allow notifications** so reminders and run progress can appear.
4. **Add your accounts.** For each one, enter:
   - a **label**, just for you;
   - the **email exactly as the Bing app shows it** in its account list (this is
     how the account switcher finds it);
   - the account's **market**, which sets the language of the search terms.

## Using it

| To | Do this |
|---|---|
| Run one account now | Tap **Run now** on its card on the dashboard. |
| Run several accounts | Tap a streak reminder notification, answer *Sí*, and pick the accounts. |
| Stop a run | Tap **Stop** on the dashboard. Queued accounts are cancelled too. |
| Change what an account does | Tap the sliders button on its card, then turn activities on or off. You can also set the number of searches and the pause between them. |
| Get a daily nudge | Use the bell on the dashboard to add reminder times. |
| Change the look | Use the palette on the dashboard to change the theme, accent color and launcher icon. |

**Default account settings:** 20 searches, 8–16 seconds between searches, and
every activity turned on.

> [!NOTE]
> The nightly run has no off switch in the app yet. If you only want to farm by
> hand, keep in mind that the early-morning run still happens.

## Privacy

- Your accounts and settings stay on the phone, and Android backups exclude them.
- Zero Rewards has no analytics and no server of its own. It only goes online to:
  - fetch random Wikipedia article titles for search terms;
  - load the developer's GitHub avatar on the *Credits* screen.
- The Bing app talks to Microsoft as it always does.
- The Accessibility service only acts on the Bing app.

## Troubleshooting

**Nothing happens when a run starts.**
Android sometimes turns Accessibility off after an update or a battery-saving
cleanup. Turn it back on (step 2 of [Setup](#setup)).

**It doesn't switch to the right account.**
The email in Zero Rewards must match the one in Bing's account list. Check for
typos or a different alias.

**Reminders or the nightly run don't fire.**
Set *Settings → Apps → Zero Rewards → Battery* to *Unrestricted*. Some phones
stop background apps aggressively.

**A step stopped working after Bing updated.**
Bing probably changed that screen. [Open an issue](https://github.com/6402770-sys/zero-rewards/issues)
with the step that fails and, if you can, a log captured from a computer:

```bash
adb logcat -s ZeroFarmer
```

## Build from source

You need JDK 17 and Android Studio Ladybug (2024.2) or newer.

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

<details>
<summary>Signed release builds</summary>

`./gradlew :app:assembleRelease` signs with the key described in
`~/.android-keystores/zero-rewards.properties`. Set
`ZERO_REWARDS_SIGNING_PROPS` to use another path:

```properties
storeFile=/path/to/zero-rewards.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file, release builds are signed with the debug key so a fresh clone
still builds. Never commit the keystore or this file.

</details>

### Project layout

```
app/            the Android app: Compose UI, notifications, and the services
                that run the farm (ZeroAccessibilityService, FarmerForegroundService)
farmer-core/    the automation itself, plus the local database
  runner/       one runner per activity, put together by FarmRun
  automation/   gesture helpers and BingPackage, the on-screen text it looks for
  search/       search terms (Wikipedia + word lists)
  market/       per-market language settings
  scheduler/    the nightly run
```

### When Bing changes its UI

Each step finds Bing's screens by their text, and most of those strings live in
[`BingPackage.kt`](farmer-core/src/main/kotlin/com/zero/rewards/core/automation/BingPackage.kt),
so many fixes are a label update there. Before changing how a runner finds
something, capture a fresh dump of that screen's structure first: guessing
Bing's layout wastes far more time than it saves.

## Not planned

- **Running Bing in the background.** Android only lets an Accessibility service
  act on the app that's on screen.
- **Signing in for you.** Zero Rewards doesn't handle passwords and won't.
- **Getting around Microsoft's anti-fraud checks.** If an account gets flagged,
  Zero Rewards can't help. Keep search counts reasonable.

## License

[AGPL-3.0](LICENSE). If you distribute a modified version or run it as a service,
you must publish your changes under the same license.

## Credits

- **[Zero](https://github.com/6402770-sys)**: developer.
- **Claude** by Anthropic: code, UI design and animations.
