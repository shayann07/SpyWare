# SpyWare

> ⚠ **Repo name vs app name.** The GitHub repo is called `SpyWare`, but the Android app inside has `applicationId = com.shayan.playbackmaster`, launcher label "PlaybackMaster", and **does not perform clandestine surveillance**. It's a scheduled-video-playback kiosk utility. The repo previously had no README at all; this is the first written description of what's actually here.

Android Kotlin app that lets the user pick a local video file, pick a start time + end time, and have the device automatically play that video each day inside that window — using `AlarmManager.setExactAndAllowWhileIdle` plus a `BOOT_COMPLETED` receiver so the schedule survives reboot. ExoPlayer 2.19.1 handles playback. ~12 Kotlin files.

## 🚨 Things to fix before shipping this

### 1. Silent image upload to Firebase Storage

`app/src/main/java/com/shayan/playbackmaster/worker/ImageUploadWorker.kt:19-119` is a `WorkManager` job that:

- Queries `MediaStore` for every image added in the last 30 days
- JPEG-compresses each one at quality 80
- Uploads them to `gs://REDACTED_PROJECT_ID.firebasestorage.app/images/{filename}.jpg`
- Tracks already-uploaded filenames in `SharedPreferences` to dedupe

It is enqueued from `MainActivity.onCreate(...)` (lines 56, 114) the first time `READ_MEDIA_IMAGES` is granted. **There is no consent dialog, no settings toggle, and no progress UI.** Anyone who installs this app and grants the storage permission for the video picker has their recent photos shipped to the upstream project's Firebase Storage bucket without being told.

Either delete `ImageUploadWorker` (if it's leftover prototyping) or wrap it in an explicit consent screen + an opt-in settings toggle.

### 2. Firebase Storage rules are not in this repo

`google-services.json` ships the project id `REDACTED_PROJECT_ID` and the storage bucket `REDACTED_PROJECT_ID.firebasestorage.app`. There is no `storage.rules` file in the repo. If the bucket is still on default test-mode rules (`allow read, write: if true;`), every photo uploaded by the worker above is **world-readable**. The app does not use Firebase Auth, so even after locking the rules down you'll need to add either Anonymous Auth or a per-device token before per-user paths can work.

### 3. The Firebase Android API key in `app/google-services.json` is committed

```
app/google-services.json:
  project_id   = REDACTED_PROJECT_ID
  api_key      = REDACTED_API_KEY
  package_name = com.shayan.playbackmaster
```

This is normal — Android API keys are not secret by design and are restricted to your package name + signing certificate. Apply API-key restrictions + Firebase App Check in the Cloud Console anyway.

### 4. `local.properties`, `.gradle/`, and `.idea/` are tracked

`app/.gitignore` is six bytes (`/build`). Run:

```bash
git rm --cached local.properties
git rm -r --cached .gradle .idea
```

…and replace `.gitignore` with the standard Android template (`/build`, `local.properties`, `.gradle/`, `.idea/`, `*.iml`). The tracked `local.properties` currently exposes the developer's Windows username via `sdk.dir=REDACTED_SDK_PATH` — minor, but unnecessary.

### 5. The "disable lock screen" switch is misleading

`HomeFragment.kt:163-213` has a switch labelled like it disables the device lock. It can't — Android does not allow ordinary apps to disable the keyguard. The switch only opens **Settings → Security** so the user can disable it manually. Either rename it to "Open lock-screen settings" or remove it.

## What the app actually does

1. **Pick a video.** `HomeFragment` opens the system file picker (`video/*`) and stores the resulting `Uri` in `SharedPreferences` via `PreferencesHelper`.
2. **Pick start + end time.** Two `TimePickerDialog`s capture daily start/end times.
3. **Schedule.** `AlarmUtils.scheduleDailyAlarm(...)` (`utils/AlarmUtils.kt:13-60`) calls `AlarmManager.setExactAndAllowWhileIdle` with a `PendingIntent` that re-launches `MainActivity` with the video URI and times as extras. On Android 12+ it first checks `canScheduleExactAlarms()` and bounces to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` if denied (added in commit `20d81bd`).
4. **Play.** When the alarm fires, `MainActivity` routes to `VideoFragment` which locks orientation to landscape, sets `FLAG_FULLSCREEN | FLAG_KEEP_SCREEN_ON | FLAG_SECURE`, acquires a `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` for at most ten minutes, and plays the video via ExoPlayer until the end-time stop.
5. **Survive reboot.** `BootReceiver` (registered for `BOOT_COMPLETED`) reads the saved video URI + times and re-arms the alarm.
6. **(Side effect) Upload images.** See "Silent image upload" above.

What the app does **not** do (despite the repo name): no SMS / call-log / contacts / location / microphone / camera-stream / keystroke / clipboard / browser-history collection, no `AccessibilityService`, no `DeviceAdminReceiver`, no foreground service with persistent notification, no hidden launcher icon, no disguised app name.

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Why |
| --- | --- |
| `INTERNET` | Firebase Storage upload |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Video picker + image-upload worker |
| `WRITE_EXTERNAL_STORAGE` | legacy; redundant on Android 10+ |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Daily playback alarm |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarm after reboot |
| `WAKE_LOCK` | Turn screen on for playback |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt user to whitelist app |
| `SYSTEM_ALERT_WINDOW` | **unused in code — drop** |
| `DEVICE_POWER` | **unused in code, signature-only — drop** |

## Tech stack

- **AGP** 8.8.0, **Kotlin** 2.0.21, **JVM target** 11.
- **compileSdk / targetSdk** 35, **minSdk** 21.
- **ExoPlayer** 2.19.1 (legacy `com.google.android.exoplayer2` — migrate to `androidx.media3:media3-exoplayer`).
- **WorkManager** 2.10.0, **Navigation** 2.8.5, **Material** 1.12.0, **ConstraintLayout** 2.2.0, **Glide** 4.15.1.
- **Firebase BOM** 33.8.0 + **firebase-storage** 21.0.1 (no Auth, no Firestore, no FCM, no Functions).
- **viewBinding** enabled. `isMinifyEnabled = false` (no R8 / ProGuard).
- No DI (no Hilt). No Room. No Retrofit. Persistence is `SharedPreferences` only.

## Project layout

```
SpyWare/
├── build.gradle.kts                                root
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── gradle.properties
├── gradlew, gradlew.bat
├── local.properties                                🚨 tracked
├── .idea/, .gradle/                                🚨 tracked
└── app/
    ├── .gitignore                                   only `/build`
    ├── build.gradle.kts                             applicationId com.shayan.playbackmaster
    ├── google-services.json                         tracked — Firebase project `REDACTED_PROJECT_ID`
    ├── proguard-rules.pro                           empty
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/shayan/playbackmaster/
        │   ├── data/{models/Video.kt, preferences/PreferencesHelper.kt}
        │   ├── receivers/BootReceiver.kt            (BOOT_COMPLETED → reschedule alarm)
        │   ├── services/PlaybackService.kt
        │   ├── ui/MainActivity.kt
        │   ├── ui/fragments/{HomeFragment, VideoFragment, ExitPlaybackListener}.kt
        │   ├── ui/viewmodel/AppViewModel.kt
        │   ├── utils/{AlarmUtils, BatteryOptimizationHelper, Constants, TimePickerHelper}.kt
        │   └── worker/ImageUploadWorker.kt          ⚠ silent 30-day image upload
        └── res/
            ├── layout/{activity_main, fragment_home, fragment_video, bottom_sheet_instructions}.xml
            ├── navigation/nav_graph.xml
            ├── values/strings.xml                   app_name = "PlaybackMaster"
            └── mipmap-*/ic_launcher{,_round}.webp   visible launcher icon
```

## Setup / run

1. Open in Android Studio (Hedgehog or newer for AGP 8.8.0). Sync Gradle.
2. **Replace `app/google-services.json` with your own Firebase project's file before doing any image-upload testing** — otherwise the images land in the upstream project's bucket.
3. **Lock down Firebase Storage rules** before distributing any APK that runs `ImageUploadWorker` (or delete the worker).
4. `./gradlew :app:installDebug` and run.

## Status

- Working tree clean on `master`. 4 commits total: `82e3264 .`, `ecb90ab .`, `fc7cbd7 .`, `20d81bd Fixed AlarmManager Permission Error`. **No GitPulse pollution.**
- Remote: `https://github.com/shayann07/SpyWare.git`. **No `LICENSE` file.** Treat as "all rights reserved" until one is committed.
