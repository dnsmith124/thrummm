# Thrummm

Longer, stronger-feeling notification vibrations for the Sidephone SP-01 (and other
de-Googled Android 12+ phones).

The SP-01's vibration motor has no amplitude control and needs time to spin up. The stock
notification pattern (100 ms on, 150 off, 100 on) ends before the motor gets going, so
notifications feel faint. Thrummm fixes this with **longer pulses, not stronger ones**: it
listens for notifications from the apps you choose and plays your own vibration pattern.

## Features

- **Pattern presets:** Long (500/200/500), Very long (800/250/800), Triple, or Custom
  (on ms / off ms / pulses). The stock pattern is included as "Original" so you can compare,
  and Test vibration plays any of them.
- **Per-app choice:** pick which apps get the custom pattern, optionally with a different
  pattern per app.
- **No double buzz in vibrate mode:** the system's own short buzz is blocked while your
  pattern plays.
- **Incoming calls (optional):** loops a pattern while the phone rings, optionally different
  from the notification pattern. It follows the system's rules for when calls vibrate.
- **Respects quiet time:** skips Do Not Disturb, silent mode, ongoing and foreground-service
  notifications, group summaries, and repeat posts within 2 seconds.
- **Side-suite look:** Inter type, a time-of-day sky theme, and Light/Dark options. It can
  also match SideHome's theme, via the wallpaper SideHome's wallpaper sync keeps painted.

No Google dependencies, no AndroidX, no network access, no data collection.

## Build

Requires JDK 17 and the Android SDK (platform 34; Android Studio not needed).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` should point at your SDK, e.g. `sdk.dir=/Users/you/Library/Android/sdk`.

## Setup on the phone

1. Open Thrummm → **Apps** → tap **Notification access** and allow it.
2. Tick the apps that should use your pattern.
3. Optional: **Calls** → **Loop while ringing** (asks for the Phone permission).
4. Optional: in ring mode, if a ticked app's notification channel still vibrates, turn that
   off. Long-press the app in Thrummm to open its notification settings.

## Requirements

Android 12 (API 31) or newer. Tested on a Sidephone SP-01.
