# PlaybackMaster (Android Kiosk Scheduler & Media Processing Research)

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)]()
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Schedules a local video to play full-screen during a chosen daily time window, and resumes automatically after a reboot â€” built as a single-purpose kiosk app.

---

## 📖 Overview

Schedules a local video to play full-screen during a chosen daily time window, and resumes automatically after a reboot â€” built as a single-purpose kiosk app.

---

## ✨ Key Features

- **Precision RTC Playback Scheduling**: Utilizes Android's `AlarmManager.setExactAndAllowWhileIdle` to automatically trigger full-screen video playback at scheduled daily start times.
- **ExoPlayer Video Engine**: Fullscreen landscape media player utilizing Google ExoPlayer 2.19.1 with automated wake-lock management and lock-screen bypass.
- **Reboot Persistence**: `BootReceiver` listens for `android.intent.action.BOOT_COMPLETED` to re-arm scheduled alarms seamlessly following device restarts.
- **Background Media Synchronization**: `CoroutineWorker` pipeline leveraging AndroidX WorkManager and Bumptech Glide for batch image compression and cloud sync.
- **Modern Jetpack Architecture**: Built with Single-Activity Navigation (`NavHostFragment`), ViewBinding, and clean separation of concerns.

---

---

## 🛠️ Technology Stack

| Layer | Technology / Library | Version |
|---|---|---|
| **Language** | Kotlin | 2.0.21 |
| **Build System** | Android Gradle Plugin (AGP) / Gradle | 8.8.0 / 8.10.2 |
| **Target / Compile SDK** | Android SDK 35 (Vanilla Ice Cream) | 35 |
| **Minimum SDK** | Android SDK 21 (Lollipop) | 21 |
| **Video Playback** | Google ExoPlayer | 2.19.1 |
| **Background Processing** | AndroidX WorkManager Kotlin Extensions | 2.10.0 |
| **Image Processing** | Bumptech Glide | 4.15.1 |
| **Cloud Storage** | Firebase Storage / BoM | 21.0.1 / 33.8.0 |
| **Navigation** | Jetpack Navigation Component | 2.8.5 |

---

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17 / 21
- Android SDK 34 / 35

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/shayann07/SpyWare.git
   cd SpyWare
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle dependencies and run on an emulator or physical device.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 [shayann07](https://github.com/shayann07).
