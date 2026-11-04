# 🏔️ StudyMode

> **Mindful Shorts & Reels Counter, Deep Exam Focus & Anti-Doomscrolling for Android.**  
> Break mindless doomscrolling on YouTube Shorts & Instagram Reels, lock in with Zen Mode, and reclaim your attention — 100% on-device, zero ads, zero tracking.

[![Release](https://img.shields.io/badge/Release-v1.0.1-22c55e?style=for-the-badge&logo=android)](https://github.com/Vishnukankol47/StudyMode/releases/latest)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device-emerald?style=for-the-badge)](README.md#-permissions--privacy-transparency)
[![UPI Safe](https://img.shields.io/badge/Banking-100%25%20UPI%20Safe-teal?style=for-the-badge)](#-strict-banking--upi-isolation)

---

## 🎯 The Core Idea: Awareness Over Hard Blocking

Most productivity apps aggressively slam apps shut the second you open them. This creates immediate friction, annoys you, and usually leads to uninstalling the app within 48 hours.

**A live number over Shorts is much easier to live with than a harsh block.**

Instead of fighting your impulse cold-turkey, **StudyMode floats a sleek, non-intrusive HUD directly over YouTube Shorts and Instagram Reels**:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                       Floating HUD Experience                           │
│                                                                         │
│   YouTube Shorts:                   Instagram Reels:                    │
│   ┌───────────────────────┐         ┌───────────────────────┐           │
│   │  ▶ 1 Short • 15s      │         │  🎬 2 Reels • 48s     │           │
│   └───────────────────────┘         └───────────────────────┘           │
│                                                                         │
│   Dynamic Awareness Glow:                                               │
│   🟢 Green (1–5 Videos)   ──>  🟡 Amber (6–15 Videos)  ──>  🔴 Red (16+)│
└─────────────────────────────────────────────────────────────────────────┘
```

* **Live Video Swipe Counter:** Counts every reel and short you swipe through in real-time (`1 Short`, `2 Shorts`, `3 Shorts`...).
* **Continuous Active Timer:** Tracks elapsed session time (`15s`, `1m 20s`, `3m 45s`).
* **Dynamic Three-Tier Glow:** The pill border transitions from calm **Fresh Green** to **Warning Amber**, and finally **Crimson Alert Red** as you slip into doomscrolling.
* **Self-Set Session Limits:** Option to set custom scroll limits (e.g. 5, 10, or 15 Reels) that gently close the feed only after you reach your chosen cap.
* **Fully Draggable:** Smoothly drag the pill anywhere on your screen so it never blocks video captions or channel links.

---

## 🛡️ Permissions & Privacy Transparency

We know that installing an `.apk` from GitHub and granting system permissions can feel suspicious. **StudyMode is open-source and built on strict privacy principles.** 

Here is plain-English transparency into exactly what each permission does, what it **cannot** do, and why it is safe:

| Permission | What It Does In StudyMode | What It CANNOT Do |
| :--- | :--- | :--- |
| **Accessibility Service** | Detects when you enter the Shorts or Reels tab and tracks swipe transitions to update the live counter pill. | **Never records keystrokes, passwords, or personal chats.** Hardcoded to strictly ignore and disconnect from all banking and UPI apps. |
| **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)** | Draws the compact, draggable counter badge and mindful pause sheets over distracting video feeds. | Cannot draw over secure system windows (PIN locks, biometric authentications, or payment dialogs). |
| **Usage Access (`PACKAGE_USAGE_STATS`)** | Reads daily screen time, app open counts, and enforces your optional daily limits. | All data is stored strictly in your phone's local SQLite database. |
| **Do Not Disturb (`ACCESS_NOTIFICATION_POLICY`)** | Mutes distracting notification chimes and vibrations during active Pomodoro & Zen study blocks. | Never reads or intercepts notification content or messages. |

---

### 🏦 Strict Banking & UPI Isolation

To prevent conflicts with Indian banking and payment security scanners, **StudyMode includes a hardcoded safe-list of 94+ financial and payment packages**, including:
* **Google Pay** (`com.google.android.apps.nbu.paisa.user`)
* **PhonePe** (`com.phonepe.app`)
* **Paytm** (`net.one97.paytm`)
* **BHIM UPI** (`in.org.npci.upiapp`)
* **SBI YONO, HDFC, ICICI, Axis Bank, and all major banking apps**

> **Zero Inspection Guarantee:** The moment any banking or payment app opens, StudyMode's accessibility engine terminates inspection immediately. No views, nodes, or windows in banking apps are ever accessed.

---

### 🔒 100% On-Device & Zero Telemetry
* **Zero Analytics SDKs:** No Google Analytics, no Firebase Analytics, no Mixpanel, no Facebook SDK.
* **Zero Advertising:** 100% ad-free, forever.
* **Zero Remote Servers:** StudyMode does not own, operate, or ping any remote user tracking servers. Your personal study habits and screen time stay on your device.

---

## 🌟 Other Key Features

### 🧘 Zen Mode (Deep Exam Lockout)
Need to study for 2 hours with zero phone distractions? Zen Mode creates an un-dismissible full-screen focus space with breathing pulse rings:
* Blocks distracting apps instantly.
* Keeps the phone dialer and emergency calls accessible.
* Enforces a strict 60-second hold-to-abort protection to prevent reflexive quitting.

### 🛡️ Mindful Pre-Open Friction
Instead of jarring, abrupt blocks, StudyMode introduces an intentional pause (e.g. 2 deep breaths or a 10s reflection pause) before opening distracting apps, breaking reflexive habit loops.

### 📊 Daily Unlock & Screen Time Analytics
* Visual 24-hour pickups breakdown.
* Categorized screen time distribution (Productive vs. Distracting).
* Daily streak counter and customizable unlock goals.

---

## 📲 Download & Installation

### Option 1: Direct APK Download (Recommended)
1. Download the latest release binary:
   👉 **[Download StudyMode-v1.0.1.apk](https://github.com/Vishnukankol47/StudyMode/releases/latest/download/StudyMode-v1.0.1.apk)**
2. Tap the downloaded file on your Android device (Android 8.0+ / API 26+) and tap **Install**.
3. Launch StudyMode and follow the clean 3-step setup guide.

#### Verify APK Integrity (SHA-256)
```text
852BAEEC325017C61E88D337AE271DB1DCE206FB0D2FECB497E9C523FDDA9D79
```

---

## 🛠️ Building From Source

If you prefer building the APK yourself from source:

```bash
# Clone the repository
git clone https://github.com/Vishnukankol47/StudyMode.git
cd StudyMode

# Build debug APK
./gradlew assembleDebug

# Build optimized release APK
./gradlew assembleRelease
```

The compiled release APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 🏗️ Architecture & Tech Stack

* **Language:** Kotlin 1.9
* **UI Framework:** Jetpack Compose (Material 3)
* **Architecture:** Clean Architecture (Compose UI $\rightarrow$ LocalStudyStore SQLite $\rightarrow$ Background Foreground Services)
* **Micro-Blocking Engine:** Android Accessibility Service with engagement-hash digest tracking for resilient, language-agnostic swipe detection.
* **Usage Engine:** Android `UsageStatsManager` with real-time midnight-to-now foreground session mapping.
* **Privacy Isolation:** Hardcoded exclusions for all NPCI UPI and financial banking packages to guarantee zero permission conflicts.

---

## 📄 License

StudyMode is open-source software licensed under the **[Apache License 2.0](LICENSE)**.
