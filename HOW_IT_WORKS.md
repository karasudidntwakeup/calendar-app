# How This App Is Made — Explained Like You're a Kid

Imagine you're building this app as if it were a **LEGO castle**. Every piece
fits together, and if you understand what each piece does, you could build your
own castle too. This guide explains EVERY single thing, in the simplest way I
can.

---

## PART 0: What is an app, really?

A phone app is just a **bunch of instructions** that tell your phone what to do.

- When you tap the calendar, that's an instruction: "show the calendar".
- When you type "buy milk", that's an instruction: "remember this".

The app saves these instructions as **files** on your phone. That's it. No magic.

There are three "languages" you need to know:

| Name | What it is | Kid-friendly analogy |
|------|-----------|----------------------|
| **Kotlin** | The main language you write | The *English* you use to talk to the phone |
| **Jetpack Compose** | How you build screens | The *LEGO bricks* you snap together |
| **Material 3** | The art style | The *rulebook* for colors and shapes |

---

## PART 1: Getting the Tools (like getting your LEGO sets)

Before you can build, you need three things:

1. **JDK** (Java Development Kit)
   - This is the *translator*. It takes the English you write (Kotlin) and
     turns it into "phone language" (something called **bytecode**).
   - Without a translator, the phone can't understand you.

2. **Android SDK** (Software Development Kit)
   - This is the *LEGO catalogs*. It has all the pre-built pieces that the
     Android system gives you — buttons, the calendar, the screen, etc.
   - "SDK" just means "a box of tools built for Android".

3. **Android Studio** (or a terminal like me)
   - This is your *workshop* — where you write code and press "Build".
   - I actually built this app WITHOUT Android Studio, just a text editor and a
     black terminal window. But Android Studio is much friendlier for beginners.

### How to know your tools are working
You type a secret handshake into the terminal:
```
adb devices
```
If your phone shows up, it means the phone and computer are **friends**.
(`adb` = Android Debug Bridge = the invisible cable handshake that lets your
computer remote-control your phone.)

---

## PART 2: The Project (your app's house)

Every app is a **folder** (I called mine `CalendarApp`). Inside are more folders.
Here's what each one does:

```
CalendarApp/
├── build.gradle.kts      ← the RECIPE: what ingredients this app needs
├── settings.gradle.kts   ← the SHOPPING LIST: which recipe files to use
├── gradle.properties     ← special settings (how fast to build, etc.)
├── gradlew               ← the "START" button that runs the build
└── app/                  ← the ACTUAL app lives here
    ├── build.gradle.kts  ← this app's specific recipe
    └── src/main/
        ├── AndroidManifest.xml   ← the APP'S ID CARD
        ├── java/com/karasu/calendarapp/  ← all the Kotlin brain code
        │   ├── MainActivity.kt   ← the MAIN BRAIN (the screen)
        │   ├── TodoStore.kt      ← the NOTEBOOK (saves your todos)
        │   ├── TimerState.kt     ← the STOPWATCH brain
        │   ├── TimerService.kt   ← the BACKGROUND WORKER (countdown)
        │   ├── TimerAlarmReceiver.kt  ← the WAKE-UP PAGER
        │   ├── TimerControlReceiver.kt ← the "STOP" button listener
        │   └── AlarmSoundService.kt   ← the BELL RINGER
        └── res/            ← the ART SUPPLIES (pictures, sounds, colors)
            ├── drawable/   ← pictures and icons
            ├── raw/        ← sound files (like the alarm .ogg)
            ├── values/     ← colors, text, styles
            └── mipmap/     ← the app's LAUNCHER ICON
```

### The "Recipe" files (`build.gradle.kts`)
Just like a cake recipe lists ingredients, this file lists what the app is made
of:
```kotlin
android { compileSdk = 36 }        // build it for Android 16
dependencies {
    implementation("androidx.compose.material3:material3") // the LEGO bricks
}
```
If you forget an ingredient, the build **errors out** and says "I don't know
how to make this". The recipe must be perfect.

---

## PART 3: The ID Card (`AndroidManifest.xml`)

Every app has an ID card that tells Android:

- **Who you are** (`package name` → `com.karasu.calendarapp`)
- **Your picture** (the icon)
- **What permissions you need** (like "can I use the alarm clock?")

Example permissions in our app:
```
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- "Can I show popups?" -->

<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<!-- "Can I set a real alarm?" -->

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- "Can I run a helper in the background?" -->
```

It also lists all the "helpers" (services & receivers) your app has. Think of it
as registering all your workers with the front desk, so Android knows they exist.

---

## PART 4: The Main Brain (`MainActivity.kt`)

This is the biggest file. It draws the **whole screen** using Compose.

### What is Compose, really?
Compose lets you build screens by **describing them**, like giving a drawing
list:
```kotlin
Column {                      // a stack going down
    Text("Today")             // some text
    Button({ /* on click */ }) { Text("Press me") }  // a button
}
```
You don't draw pixels by hand. You say what goes on the screen, and Compose
draws it for you.

### The Three Screens in Our App

**Screen 1: The Month Calendar**
```
┌───────────────────────────┐
│  ⬅  August 2026  ➡        │   ← month navigation buttons
├─┬─┬─┬─┬─┬─┬─┬────────────┤
│M│T│W│T│F│S│S│            │   ← weekday labels
├─┼─┼─┼─┼─┼─┼─┼────────────┤
│1│2│3│4│5│6│7│            │
│8│9│●●15●│...│            │   ← the day cells (15 has a dot = todo)
└─┴─┴─┴─┴─┴─┴─┴────────────┘
```
We make this using a **grid** — a box of boxes. Each day is one box.

**Screen 2: The Todo List (for a picked day)**
```
┌───────────────────────────┐
│ Tuesday, 27 Aug            │   ← the date you picked
│ ☑ Buy milk                 │   ← a done todo (checkbox checked)
│ ☐ Walk the dog             │   ← a not-done todo
│ [ + ]                      │   ← an "add" button that makes a text box
└───────────────────────────┘
```

**Screen 3: The Timer**
```
┌───────────────────────────┐
│       25:00                │   ← the big countdown number
│    [-5min] [+5min]         │   ← adjust buttons
│   [ START ]  [ RESET ]     │   ← control buttons
└───────────────────────────┘
```

### How gestures work (the fun part)
Your finger is tracked. If it moves mostly **UP**, open the todo list.
Mostly **LEFT**, go to next day. Compose has a tool called `pointerInput` that
measures how far your finger moved in each direction — whichever is biggest,
that's the action.

```
finger moved up    →  show todo list
finger moved down  →  go back
finger moved left  →  next day
finger moved right →  previous day
```

---

## PART 5: The Notebook (`TodoStore.kt`)

When you add a todo, the phone must **remember it forever** (even after the app
closes). We use something called **SharedPreferences** — think of it as the
phone's tiny notebook that never forgets.

The data is saved as **JSON** text. JSON is a way to write lists that computers
understand:
```json
{
  "2026-08-27": [
    {"id": 1, "text": "Buy milk", "done": false},
    {"id": 2, "text": "Walk dog", "done": true}
  ]
}
```
- `2026-08-27` = the date (a key in a big dictionary).
- Inside is a **list** of todos, each with an id, text, and done flag.

The two important functions:
```
save(todos)   →  "hey notebook, write this down"
load()        →  "hey notebook, what did I write before?"
```
On Android, todo data lives here:
```
/data/data/com.karasu.calendarapp/shared_prefs/calendar_notes.xml
```

---

## PART 6: The Stopwatch (`TimerState.kt`)

The timer is two things:
- A **number** that counts down (25:00 → 24:59 → ...).
- A **set of rules** for start / pause / reset / finish.

The brain stores the important numbers in memory:
```kotlin
var running        // is it counting right now? (true/false)
var remainingMs    // how many milliseconds are left?
var totalMs        // how long was the whole timer?
var targetTime     // the exact time (in phone-clock) when it hits zero
```

When you press START:
```
targetTime = now + remainingMs    // "I'll finish THIS many ms from now"
```

Every second the app asks:
```
remainingMs = targetTime - now
```
When `remainingMs` gets to 0 → the alarm rings!

---

## PART 7: The Background Worker (`TimerService.kt`)

Problem: If you close the app, does the timer stop? It **should keep running**.
That's where a **foreground service** comes in.

A foreground service is a little worker that Android keeps alive, and it *must*
show something in the notification bar (that's how Android knows it's fair to
keep it alive).

Our worker:
1. Keeps counting down even when the app is closed.
2. Updates the notification every 250ms with the new time.
3. Shows a **Stop** button in the notification so you can cancel it.

The notification bar looks like:
```
┌─────────────────────────────────┐
│ 🔔 Timer running     [Stop]     │
│ ▓▓▓▓▓▓▓▓▓░░░░░░░ 12:34         │  ← progress bar = time left
└─────────────────────────────────┘
```

---

## PART 8: The Wake-Up Pager (`TimerAlarmReceiver.kt`)

Devices love to **sleep** to save battery. If the phone is asleep, it won't run
your app's countdown... unless you use the system **AlarmManager**.

The AlarmManager is the phone's own alarm clock. You tell it:
```
"At exactly 2:30pm, tap this receiver on the shoulder."
```
A **receiver** (BroadcastReceiver) is a tiny listener that wakes up, receives
the message, and then starts the bell ringer. Even if your app is dead, the
AlarmManager will still deliver its message. That's why we use it — it's
reliable.

```
timer hits zero
   ↓
AlarmManager says: "WAKE UP!"
   ↓
TimerAlarmReceiver wakes up
   ↓
it starts AlarmSoundService (the bell ringer)
```

---

## PART 9: The Bell Ringer (`AlarmSoundService.kt`)

This is another background worker, but its job is to **make noise and buzz**.
It:
1. Plays a sound file (an `.ogg` file — a music format) using **MediaPlayer**.
2. Vibrates the phone (**Vibration**).
3. Keeps ringing until you stop it.

It's also a foreground service, so it shows a notification like "ALARM!" while
ringing.

We put a big `try/catch` around the sound playing:
```kotlin
try {
    mediaPlayer.play()            // try to play the sound
} catch (e) {
    beepWithToneGenerator()       // if that failed, beep a backup way
}
```
A `try/catch` is a safety net: "Try this. If it breaks, do this instead so we
don't crash." Crash-proofing is really important for a real app.

---

## PART 10: The Stop Button (`TimerControlReceiver.kt`)

Remember the "Stop" button in the notification? That button sends a message to
this receiver, which simply tells the timer to cancel:
```kotlin
if (intent.action == "STOP_COUNTDOWN") {
    TimerState.cancel(context)   // stop the timer
}
```
This is like the little servant who runs over and hits the big STOP button.

---

## PART 11: Making it PRETTY (Material 3 Expressive)

This is the newest Android look (for Android 16+). It's a full art style:

- **Cookie-shaped buttons** — `MaterialShapes.Cookie9Sided`. Not perfect squares,
  more like a cookie (rounded on most corners).
- **Dynamic color** — the whole app changes colors to match your **wallpaper**.
  Pick a purple wallpaper → purple buttons.
- **Expressive motion** — things *bounce* and *spring* when you tap them, instead
  of just appearing.
- **Wavy progress bar** — the loading bar isn't straight, it's wiggly/zigzag.

These are all just pre-made choices you turn on — you don't design them by hand.

---

## PART 12: Permissions (asking nicely)

Some things the app wants to do need **permission** from the user:
- **Notifications** → "Can I show popups and progress?"
- **Exact alarms** → "Can I actually wake the phone at a set time?"
- **Vibration** → "Can I buzz?"

On newer Android, the user must say YES in Settings. Without permission:
- No notification popup.
- The alarm only works when the phone is awake.

That's why the app can't just ring — it has to *ask* first.

---

## PART 13: Putting it ALL together (the build)

When you press the build button, this happens in order:

1. **Compile** — the translator turns your Kotlin into phone language.
2. **Package** — all the files get zipped together into one `.apk` file.
3. **Sign** — the app gets a digital signature (like a wax seal) saying "I made
   this, don't tamper with me".
4. **Install** — `adb install` copies the APK onto your phone.
5. **Launch** — `am start` opens your app.

The final product — the `.apk` file — is everything your phone needs to run it.

---

## PART 14: From beginner to builder (your path)

You don't build a castle in one day. Build one brick at a time:

| Step | What you build | What you learn |
|------|---------------|----------------|
| 1 | A screen that says "Hello" | How to run a Compose app |
| 2 | A button that counts taps | Variables + click handlers |
| 3 | A todo list (add/tick/delete) | Lists + saving |
| 4 | Save todos after closing | SharedPreferences + JSON |
| 5 | A calendar grid | Grids + picking dates |
| 6 | Connect calendar → todos | "X shows Y when you tap" |
| 7 | A countdown timer | Math: target - now |
| 8 | An alarm that rings | Services + permissions |
| 9 | Swipe gestures | pointerInput |
| 10 | Make it pretty | Material 3 Expressive |

My first version of this app was just a plain list where you could check boxes.
EVERYTHING else came later, one tiny piece at a time.

---

## The Golden Rules of Making Apps

1. **Start tiny.** One small feature, test it, then move on.
2. **Break things on purpose.** Try to crash your app. Fixing crashes teaches you the most.
3. **Google everything.** Even pros search "how do I..." every single day.
4. **Saving data is the hardest part** — master SharedPreferences early.
5. **Background work is sneaky** — apps close, phones sleep. That's why services exist.
6. **Permission is the difference** between "works" and "works when I want it to."
7. **There is no one big secret** — it's just lots of little pieces snapped together.

That's the whole secret: **an app is just a thousand tiny LEGO bricks, and you
put them together one at a time.**
