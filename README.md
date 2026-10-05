# SmallWins

An Android habit app that treats your day like a quest log. Every morning you pick which quests count today, you tick them off as you go, and staying consistent earns XP, levels and a streak.

Your guide is Ember, a small flame with a cheeky voice. Ember nudges, never punishes: there are no XP penalties, and every reminder comes with a one-tap way to deal with it.

> Status: early (v0.1.0). Solo play only, and everything is stored on the device. No account, no network.

## How a day works

1. **Morning call.** At a time you set (07:30 by default) a notification asks you to choose today's quests from your own list. Pick at least 2. If you ignore it, yesterday's picks carry over.
2. **Reminders.** Each picked quest rings at its own time with three actions: **Done**, **Snooze 15** and **Skip**. An unanswered reminder rings once more 15 minutes later.
3. **Streak saver.** At 9 pm, if quests are still open, Ember gives you one last push.
4. **Rollover.** At the end of the day the result is locked in and the streak is updated.

## The rules

| Thing | Rule |
| --- | --- |
| Winning a day | Clear at least half of today's quests |
| Full clear | Clear all of them for a +40 XP bonus |
| Partial progress | Every tick pays XP, so 3 of 8 glasses of water still counts |
| Streak bonus | +2% XP per streak day, capped at +50% |
| Rest tokens | 2 per week, spent automatically to protect a streak on a lost day |
| Earn-back | Lose a streak, then get 2 full clears within 3 days to restore it |
| Comeback | +20 XP for showing up again after a lost day |
| Levels | 100 XP for level 2, then 50 more XP per level |
| Ranks | E, D, C, B, A, S at levels 1, 7, 12, 22, 35, 50 |

All of this lives in one plain Kotlin file, [Rules.kt](app/src/main/java/com/smallwins/app/domain/Rules.kt), and is covered by unit tests in [RulesTest.kt](app/src/test/java/com/smallwins/app/domain/RulesTest.kt).

## Reminders that actually arrive

Reminders are useless if the phone kills them, so the app puts some effort into delivery:

- Exact alarms through `AlarmManager`, re-registered after a reboot, an app update or a clock or time zone change.
- A setup checklist with brand-specific battery steps and a "send a test reminder in 1 minute" button.
- A missed-reminder detector that tells you when the phone swallowed one.
- Reminders that arrive more than 30 minutes late stay silent, so a phone waking from a long freeze doesn't dump a pile of stale alerts on you.

## Tech

- Kotlin, Jetpack Compose and Material 3
- Room for storage (schema exported to [app/schemas](app/schemas))
- Coroutines and a single `ViewModel`, no DI framework
- minSdk 26 (Android 8.0), targetSdk 36

```
app/src/main/java/com/smallwins/app/
  domain/   streak, XP and level rules (pure Kotlin)
  data/     Room database, repository, preferences
  alarms/   alarm scheduling and system event receivers
  notify/   notifications, their actions and Ember's lines
  ui/       Compose screens and theme
```

The look and feel was prototyped first in [design/mock.html](design/mock.html), which opens in any browser.

## Build

You need JDK 17 or newer and the Android SDK with platform 37 installed.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected phone or emulator with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
