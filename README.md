# 🏔️ StudyMode

> **Offline Digital Wellbeing, Focus & Anti-Doomscrolling for Students.**  
> Break mindless doomscrolling on YouTube Shorts & Instagram Reels, lock in with Zen Mode, and reclaim your attention — 100% offline, zero ads, zero tracking.

---

## 🌟 Why StudyMode?

Most screen-time apps are either easily bypassed or filled with ads and subscriptions. **StudyMode** was engineered specifically for students preparing for competitive exams or anyone wanting to break digital dopamine loops:

1. **🎬 Floating Shorts & Reels Counter (Real-Time HUD):** Shows a sleek, unobtrusive pill overlay (`▶ 3 Shorts • 1m 45s`) directly on YouTube Shorts and Instagram Reels. Every time you swipe, the count and timer update live, giving you instant conscious awareness of doomscrolling.
2. **🧘 Zen Mode (Deep Exam Lockout):** Need to study for 2 hours with zero phone distractions? Zen Mode creates an un-dismissible full-screen focus space with breathing pulse rings. Unapproved apps are blocked instantly, while keeping emergency phone calls accessible.
3. **🛡️ Mindful Pre-Open Friction:** Instead of harsh, jarring blocks, StudyMode introduces an intentional pause (e.g., 2 deep breaths or a 10s reflection pause) before opening distracting apps, breaking reflexive habit loops.
4. **📊 Daily Unlock & Screen Time Analytics:** Visual 24-hour pickups breakdown, weekly category distribution (Distracting vs. Productive), streak tracking, and customizable daily unlock goals.
5. **🔒 100% Offline & UPI/Banking Safe:** All processing happens strictly on-device. Banking and UPI payment apps (BHIM, Google Pay, PhonePe, Paytm, etc.) are strictly isolated and never intercepted or inspected.

---

## ✨ Features at a Glance

| Feature | Description |
|---|---|
| **Shorts & Reels Micro-Blocker** | Detects vertical short-form video feeds without blocking regular educational videos or tutorials. |
| **Pomodoro & Focus Sessions** | 25m, 45m, and customizable deep-focus blocks with ambient soundscape support and short/long breaks. |
| **Zen Lockout** | Un-bypassable lockdown mode with 60-second hold-to-abort protection and dialer pass-through. |
| **App Limits & Notifications** | Set custom daily caps (e.g. 15m/day) with threshold warnings at 50% and 75% usage. |
| **Strict Unblock Delay** | Enforces a 15–60s cooldown timer before allowing any unblocking or pause actions. |
| **Alpine Serenity Theme** | Clean, minimalist mountain lake aesthetic designed for calm, focused concentration. |

---

## 📲 Download & Installation

### Option 1: Direct APK Download (Recommended)
1. Head to the **[Releases](../../releases/latest)** tab on this repository.
2. Download `StudyMode-release.apk`.
3. Open the file on your Android device (Android 8.0+ / API 26+) and follow the on-screen setup guide.

---

## 🛠️ Building from Source

### Prerequisites
* **Android Studio** (Hedgehog / Iguana or later)
* **JDK 17**
* **Android SDK 34** (Android 14)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/<YOUR_USERNAME>/StudyMode.git
cd StudyMode

# Build debug APK
./gradlew assembleDebug

# Build signed release APK
./gradlew assembleRelease
```
The output APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 🏗️ Architecture & Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Architecture:** Layered Clean Architecture (Compose UI $\rightarrow$ LocalStudyStore SQLite $\rightarrow$ Background Foreground Services)
* **Micro-Blocking Engine:** Android Accessibility Service with engagement-hash digest tracking for resilient, language-agnostic swipe detection.
* **Usage Engine:** Android `UsageStatsManager` with real-time midnight-to-now foreground session mapping.
* **Privacy Isolation:** Hardcoded exclusions for all NPCI UPI and financial banking packages to guarantee zero permission conflicts.

---

## 🛡️ Permissions & Privacy

StudyMode requires the following permissions purely for on-device functionality:
* **Accessibility Service:** Used solely to detect when YouTube Shorts or Instagram Reels are open and to show the overlay HUD / block shield.
* **Usage Access (`PACKAGE_USAGE_STATS`):** Calculates screen time and daily app limits.
* **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):** Displays the floating counter HUD and mindful pause sheets.
* **Do Not Disturb Access:** Automatically silences notification chimes during active focus sessions.

> **Privacy Guarantee:** StudyMode makes **zero network requests** to any external server. No analytics, no ads, no telemetry. Your personal usage data stays 100% on your device.

---

## 📄 License
This project is licensed under the [Apache License 2.0](LICENSE).
