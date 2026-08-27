# CalendarApp

Android port of the quickshell clock popup: Material You calendar with
per-date todos and a countdown timer that fires an alarm notification.

Styled with **Material 3 Expressive** (the Android 16+ design language):
`MaterialExpressiveTheme`, springy `MotionScheme.expressive()` motion, a
cookie-shaped morph on selected days (`MaterialShapes.Cookie9Sided`), a
wavy progress indicator in the timer, and big rounded shape tokens.
Dynamic color still follows the wallpaper (matugen-style), falling back to
`expressiveLightColorScheme()` / `expressiveDarkColorScheme()` pre-Android 12.

## Features (matching the desktop popup)
- Month grid: today highlight, selected-day highlight, note dots, month nav
- Per-date todo list: add / edit / done / delete, persisted on-device in the
  same `{"date": [{id,text,done}]}` JSON shape as
  `~/.cache/quickshell/calendar-notes.json`
- Countdown timer: +/-5 min, start/pause/reset, exact alarm + notification +
  vibration when time is up (works while app is backgrounded)
- Material You dynamic color from the system wallpaper (like matugen)

## Build

Requires JDK 17 and the Android SDK.

**Option A — Android Studio:** Open this folder, let it sync, then Run.

**Option B — command line:**
```sh
# one-time setup (Debian/Arch package names vary):
#   jdk17-openjdk + Android cmdline-tools, then sdkmanager:
#     sdkmanager "platforms;android-35" "build-tools;35.0.0"
#     yes | sdkmanager --licenses

export ANDROID_HOME=$HOME/Android/Sdk

cd CalendarApp
gradle wrapper        # generates ./gradlew (needs gradle once)  -- or --
# use Android Studio's wrapper: copy a known-good gradle-wrapper/

./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Release build: create `app/keystore.jks` and add signing config to
`app/build.gradle.kts`, then `./gradlew assembleRelease`.

## Notes
- On Android 14+, exact alarms need "Alarms & reminders" permission:
  Settings → Apps → Calendar → Alarms & reminders → Allow.
- To sync notes between desktop and phone, pull/push this file:
  `/data/data/com.karasu.calendarapp/shared_prefs/calendar_notes.xml`
