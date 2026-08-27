# Calendar & Timer

A lightweight Android calendar app with per-date todo lists and a countdown
timer that rings an alarm when time is up.

Styled with **Material 3 Expressive** (the Android 16+ design language):
`MaterialExpressiveTheme`, springy `MotionScheme.expressive()` motion, a
cookie-shaped morph on selected days (`MaterialShapes.Cookie9Sided`), and big
rounded shape tokens. Dynamic color follows the system wallpaper, falling back
to `expressiveLightColorScheme()` / `expressiveDarkColorScheme()` pre-Android 12.

## Features
- Month grid: today highlight, selected-day highlight, note dots, month nav
- Per-date todo list: add / edit / done / delete, persisted on-device
- Countdown timer: +/-5 min, start/pause/reset, exact alarm + notification +
  vibration when time is up (works while the app is backgrounded)
- Material You dynamic color from the system wallpaper

## Build

Requires JDK 17 and the Android SDK.

**Option A — Android Studio:** Open this folder, let it sync, then Run.

**Option B — command line:**
```sh
# one-time setup:
#   install JDK 17 + Android cmdline-tools, then sdkmanager:
#     sdkmanager "platforms;android-35" "build-tools;35.0.0"
#     yes | sdkmanager --licenses

export ANDROID_HOME=$HOME/Android/Sdk

cd CalendarApp
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Release build: create a signing config in `app/build.gradle.kts`, then
`./gradlew assembleRelease`.

## Notes
- On Android 14+, exact alarms need "Alarms & reminders" permission:
  Settings → Apps → Calendar & Timer → Alarms & reminders → Allow.
- On Android 13+, grant notification permission so the countdown progress and
  alarm notification are visible.
