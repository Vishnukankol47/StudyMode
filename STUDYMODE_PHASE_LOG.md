# StudyMode Offline Rebuild — Phase Review Log

## Confirmed scope

- New app name: **StudyMode**
- Target platform: Android
- Start from the supplied XAPK; no editable source project was found.
- Keep non-payment app features and make locally implementable features work offline.
- Remove advertising, payment/subscription flows, and Google sign-in.
- Local AI is acceptable for cloud-dependent AI features; exact model and behavior are not yet selected.
- Use this log to record completed work and obtain review before moving into the next implementation phase.

## Phase 1 — Target and feasibility

**Status: reviewed and approved**

- Target: `studymode.xapk`, SHA-256 `b6d65b2d1e959123be8ff881996bdc21e6b8886d2b5471632912c9b3cd28b26f`.
- The XAPK manifest identifies the included application as **Regain**, package `ai.regainapp`, version `71.3.2051`.
- The workspace contains the XAPK and extracted APK splits, but no editable source project.
- Static JADX inspection succeeded without launching the APK: 27,776 classes and 4,337 resources.
- The user confirmed the artifact is an authorized reference and asked for a new StudyMode app.
- No app implementation files have been created yet.

## Phase 2 — Feature map and offline boundaries

**Status: initial static map prepared; reviewed and approved**

Package/class-name inventory suggests these 32 feature areas:

- AI coach and generated share messages
- App categories, app limits, app usage statistics, distracting-app management
- Focus timer, study goals, productive mode, Zen mode
- Short-video and website blocking
- Control apps, service fixing, permission onboarding
- Onboarding, user sign-in, profile statistics, language and theme
- Creator mode, impact banner, surveys, invite flow, notification batching
- Network logs, launcher integration, subscription, and related C68 features

Evidence is static and package-based. It identifies candidate feature areas, not a complete screen-by-screen product specification or proof of runtime behavior.

### Offline implementation implications

- Local focus sessions, schedules, settings, goals, and statistics can be stored on-device.
- App blocking and usage tracking depend on Android permissions and OS behavior; they need device-level verification and cannot be guaranteed by APK inspection alone.
- Google sign-in, purchases, ads, remote sync, and other service calls should be excluded or replaced with local behavior.
- AI coach functionality needs a selected on-device approach; the APK does not establish that a suitable local model is bundled.
- All required user-facing flows and premium-tier capabilities still need a more detailed screen/behavior inventory before implementation.

### Open items for review

- Confirm the 32-area inventory is the intended scope, with all paid capabilities included as free StudyMode features.
- Decide the local AI approach during architecture planning.
- Exact UI/screens, data formats, and OS permission behavior remain to be mapped before coding.

## Phase 3 — Flow map and implementation architecture

**Status: static flow map and build-readiness review prepared; review pending**

### Observed entry points and screen evidence

- `ai.blox100.HomePageActivity` is the launcher activity in the decoded manifest. Its startup method initializes app content and also contains update, payment-client, and purchase-listener setup that must not be carried into StudyMode.
- `ai.regainapp.showcase.MainActivity` is not the launcher; its `onCreate` opens `CustomBrowserActivity` and finishes.
- APK string-resource keys and class/package names expose flows for onboarding and Android permissions, study goals, focus sessions and schedules, app/website/short-video blocking, usage statistics, Zen/productive modes, settings, AI, account, and subscription/ads.
- Exact navigation order, screen appearance, and every interaction are not fully established by this static evidence. No emulator/device runtime capture or screenshots have been supplied.

### Proposed StudyMode architecture

- Native Android app: Kotlin + Jetpack Compose.
- Local persistence for preferences, goals, schedules, limits, and focus-session history; no account or cloud-sync dependency.
- No Google sign-in, billing/subscriptions, ads, or purchase-gated screens.
- Keep offline-compatible capabilities and make paid-tier capabilities free in StudyMode.
- Use only OS permissions needed for the features the user enables. Usage tracking and blocking require Android access/permission flows and device testing.
- Local AI approach is still a decision: no on-device model was identified in the XAPK.

### Build environment

- Java 21 is available.
- Android SDK platform 34, build-tools 34.0.0, and platform-tools are present.
- Gradle is not on PATH; no source project or Gradle wrapper exists. A project will need a Gradle wrapper and dependencies before it can be built.
- The reference APK targets SDK 36, while the installed SDK platform is 34. No Android emulator is installed and no device is currently connected.

### Remaining questions/limits

- The XAPK alone cannot confirm exact live navigation or pixel-accurate visual behavior. Runtime screen capture or screenshots would improve fidelity.
- A strict no-network app means AI must use a bundled/local model or an explicitly limited local coach; no remote AI calls can remain.
- A build/install verification requires compatible Android build tooling and, for blocking/usage features, a test device or emulator. None is currently connected/installed.

### Proposed next implementation phases after review

1. Create the Kotlin/Compose project shell, offline defaults, local persistence, and onboarding/home navigation.
2. Add focus timer, sessions, schedules, and study goals.
3. Add app selection, permission education, app blocking, and usage statistics.
4. Add remaining offline feature equivalents and local AI behavior.
5. Remove/verify absence of network, ads, Google sign-in, and billing; run build and device tests.

## Phase 4 — Offline app shell

**Status: implemented and build-verified; review pending**

- Created a native Kotlin/Jetpack Compose project with a Gradle 8.6 wrapper.
- Added a three-page first-run welcome flow; completion is saved locally.
- Added a StudyMode home screen with a daily study-goal card and navigation to Home, Focus, Progress, and Settings.
- Focus, Progress, and Settings are clearly marked as placeholders for later phases; timer, schedules, statistics, blockers, and AI are not implemented yet.
- Added a custom StudyMode icon and disabled cloud/device backup for local app data.
- No internet, billing, advertising-ID, or Google-auth permissions are present in the generated APK.

### Verification

- `assembleDebug`: passed.
- `lintDebug`: passed with two dependency-age warnings for the pinned Compose BOM and Activity Compose versions; kept pinned to the installed API 34 build environment.
- APK identity: `com.studymode.app`, label `StudyMode`, target SDK 34, min SDK 24.
- APK output: `app/build/outputs/apk/debug/app-debug.apk`.
- Device/emulator launch testing was not possible because none is connected or installed.

## Phase 5 — Focus, goals, schedules, and local history

**Status: implemented and build-verified; review pending**

- Added configurable focus sessions with 25/45/60-minute presets, a study-topic label, pause/resume, early finish, and automatic completion while the screen is active.
- Active timer state is saved locally and restored after activity/app recreation.
- Added SQLite persistence for focus-session history, a daily study goal (15–600 minutes), and weekly study blocks (day, time, duration, enabled state).
- Added a daily-goal editor, weekly plan editor, and recent-session progress view.
- At the end of Phase 5, the weekly plan only stored and displayed schedules; Phase 7 later added local reminders. App blocking and OS usage tracking were added in Phase 6.
- Session timing is persisted locally; a notification or guaranteed background completion signal is not implemented.

### Verification

- `testDebugUnitTest`: passed (3 timer arithmetic tests, 0 failures).
- `assembleDebug`: passed.
- `lintDebug`: passed with the same two dependency-age warnings listed in Phase 4.
- Current APK SHA-256: `003e1ba80982d3b2535d0a70ad3b8d78909bceefe09c253b582005aeef3077c6`.
- Device/emulator behavior remains unverified because no device/emulator is available.

## Phase 6 — App blocking and local usage

**Status: implemented and build-verified; review pending**

- Added an Apps screen listing launchable user-facing apps and local usage totals.
- Package visibility is restricted to launcher/home intent queries; the APK does not request `QUERY_ALL_PACKAGES`.
- Added a block-list stored in the local database. When explicitly enabled in Android Accessibility settings, the service watches only window-state changes, reads the foreground package name, and returns to Home for a selected blocked app. It does not retrieve window content.
- Added an in-app five-minute pause/resume control. The service can also be disabled at any time through Android Accessibility settings; this is not a device lock.
- Usage statistics are shown only after the user grants Usage Access in Android settings. Usage totals are not uploaded.
- No Internet, billing, advertising-ID, or Google-account permissions were added.

### Verification

- `testDebugUnitTest`: passed (3 tests, 0 failures).
- `assembleDebug`: passed.
- `lintDebug`: passed with the two dependency-age warnings from Phase 4.
- APK SHA-256: `a959a8a3b1c515e182523f64dbeadc8afeb25dc167aea307eb91d9e6fe88d7ae`.
- APK permissions include `PACKAGE_USAGE_STATS` for the user-granted Usage Access setting; no broad package-list permission is present.
- Accessibility behavior and usage totals still require device testing; no device/emulator is connected/installed.

### Still not implemented

- Guaranteed timer-completion notifications and focus-session-only block rules.
- Remaining feature equivalents and any local AI model.

## Phase 7 — Reminders, app limits, and reports

**Status: implemented and build-verified; review pending**

- Added repeating weekly local study reminders using Android's inexact, battery-aware alarm API. Enabled schedules are restored after device reboot and after clock/time-zone changes; users can opt in to notifications.
- Added per-app daily usage limits (15, 30, 60, 120, or 240 minutes) with local SQLite persistence and a version-2-to-3 database migration.
- The existing opt-in Accessibility service enforces a configured limit when Usage Access and the service are enabled. Enforcement uses Android's UsageStats totals and window-state events; Android can delay usage updates, so this is not guaranteed to trigger at the exact minute.
- Expanded Progress with a seven-day local focus summary, daily totals, session counts, today's goal comparison, and recent session history.
- No server, account, ads, purchases, or Internet permission was added. `POST_NOTIFICATIONS` is requested only when the user chooses to enable reminders; `RECEIVE_BOOT_COMPLETED` is used only to restore saved alarms.

### Verification

- `testDebugUnitTest`: passed (5 tests, 0 failures), including next-week schedule timing.
- `assembleDebug`: passed.
- `lintDebug`: passed with the same two pre-existing dependency-age warnings (Compose BOM and Activity Compose).
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `4c80ef8e6aa3232848078718b5c0d54076f41cfcbd495596e5a8c316f351689d`.
- APK permissions were checked: `PACKAGE_USAGE_STATS`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED`; no `INTERNET`, billing, ads-ID, or Google-auth permission.
- No Android device or emulator is available, so notification delivery, boot restoration, SQLite migration on-device, and live app-limit enforcement remain unverified at runtime.

### Still not implemented

- Guaranteed timer-completion notifications and focus-session-only block rules.
- Remaining offline feature equivalents, including web/short-video blocking and local AI; the AI model is still unselected.

## Phase 8 — Local website filtering

**Status: implemented; build-verified; device review pending**

- Added an optional Android VPN DNS filter with an explicit Android consent flow and a user-managed hostname list. No domains are preloaded.
- Matching blocks the selected hostname and its subdomains. Allowed lookups use the current network resolver; StudyMode does not log queries or contact a StudyMode server.
- The settings screen explains the one-VPN limit, Android 9 restriction, and DNS/IPv4/encrypted-DNS bypass limitations. Hostname filtering cannot distinguish YouTube Shorts from YouTube or filter URL paths.
- The filter is an online network feature, not a fully offline capability: allowed DNS lookups need the device's current network resolver. Other StudyMode features and their data remain local.
- `DnsFilterTest` covers hostname normalization, matching, DNS question/response handling, and IPv4 packet construction. Live VPN behavior remains unverified without an Android device/emulator.

## Phase 9 — Focus completion and blocking controls

**Status: implemented and build/test-verified; device review pending**

- Focus sessions now schedule a local Android alarm. When a running session expires, the alarm saves the completed session and posts an optional completion notification; a missing notification permission does not prevent saving.
- Timer alarms are restored after reboot/time changes and are rescheduled if Android delivers one before the saved timer is due. Pausing or manually ending a session cancels its alarm.
- Added an opt-in setting to apply manually selected app blocks only while a focus timer is actively running. Daily app limits retain their prior behavior. Blocking still requires Android Usage Access/Accessibility setup.
- Added unit tests for focus-only blocking behavior, including paused and expired timers.
- Verification: `:app:testDebugUnitTest` passed (13 tests, 0 failures); `:app:assembleDebug` passed; `:app:lintDebug` passed with 0 errors and 2 dependency-age warnings. APK SHA-256: `81f88d33c0e8c221235a68158c31a61f8eb1e410dd14ab398090605b23523bb3`. Android device/emulator runtime testing remains unavailable.

## Phase 10 — Home screen visual refresh

**Status: implemented and build/test-verified; device visual review pending**

- Refreshed the StudyMode landing screen to follow the supplied reference's information hierarchy: greeting, focus-session hero, daily summary, upcoming plan cards, and quick links.
- Kept the StudyMode name and linked each section to existing focus, schedule, app-management, goal, and progress screens; did not add nonfunctional Community/Study Groups actions.
- The timer card reflects running or paused sessions and updates when a session starts, pauses, resumes, or completes.
- The app-time summary uses Usage Access data for launchable apps and explains that optional permission when unavailable.
- Verification: `:app:testDebugUnitTest` passed (13 tests, 0 failures); `:app:assembleDebug` passed; `:app:lintDebug` passed with 0 errors and 2 dependency-age warnings. APK SHA-256: `a12a2384888cbe56ff8e8c35721bd561af0af6403701a76045d2fda28e9b6f9d`.
- No Android device/emulator is available for visual comparison or runtime testing.

## Phase 11 — Profile and focus achievements

**Status: implemented and build/test-verified; device review pending**

- Added a local Profile screen reachable from the Home avatar, with editable display name/email, a locally selected profile picture, settings navigation, and a persisted "Focusing since" date. This project has no authentication/backend, so blank email is shown as a private local profile rather than inventing an account.
- Profile statistics use completed focus sessions only: accumulated focus time, current consecutive completion-day streak, and completed session count. Added a weekly report availability date based on the local profile start date, then weekly focus time, completed sessions, daily average, and weekly-goal progress.
- Added exactly 16 cumulative focus-time milestones from 1 to 1000 hours. Milestone unlocks are calculated from completed sessions, stored once in SQLite, and synchronized when a completed session is saved. Early-ended sessions do not contribute.
- Added Achievement navigation, latest achievement summary, centered final grid badge, locked progress indicators, and a help dialog. All milestone/overview book marks use the same custom green open-book glyph; no hourglass symbols are used.
- Added SQLite v6 migration to persist session completion timestamps and achievement unlock records, preserving existing session history.
- Verification: `:app:testDebugUnitTest` passed (16 tests, 0 failures); `:app:assembleDebug` passed; `:app:lintDebug` passed with 0 errors and 2 dependency-age warnings. APK SHA-256: `d566eca1ec6ceb96d52d7a267e4082d736c9747ed11061bdffda21bc20caad60`.
- No Android device/emulator is connected, so image-picker persistence, database migration on-device, visual layout, and runtime navigation remain unverified on hardware.

## Phase 12 — Recurring study schedules

**Status: implemented and build/test-verified; device review pending**

- Replaced the Plan tab's basic schedule flow with a recurring schedule list, selected-date view, create/edit form, weekday selection, start/end time pickers, reminders, break-plan configuration, installed-app selection, and weekly progress reporting.
- Schedules persist selected weekdays, overnight end times, reminder and break settings, selected package names, and creation/update timestamps. SQLite v7 migration retains existing schedule days, start times, durations, and enabled state.
- Schedule reminders use inexact local alarms and are restored after reboot and clock/time-zone changes. Reminder notifications open the Focus screen with that schedule's title and configured focus-interval duration prefilled; the user starts the timer explicitly.
- The Accessibility service adds selected schedule apps to the existing global block list while the schedule is active. Overnight intervals use the previous selected weekday after midnight; overlapping active schedules union their selected packages.
- Added duplicate-name/time-overlap validation and unit coverage for multi-day reminder timing and overnight schedule activity.
- Verification: `:app:testDebugUnitTest` passed (18 tests, 0 failures); `:app:assembleDebug` passed; `:app:lintDebug` passed. APK SHA-256: `F2EB075F54E97F5344D9E50E3956EB5606F248E82D21B73D73CCFFFDF0DFD200`.
- Break durations and interval settings are stored and editable, but the existing focus timer does not yet run automatic focus/break cycles. No Android device/emulator is connected, so notification delivery, migration, app selection, Accessibility enforcement, and responsive runtime layout remain unverified on hardware.

## Phase 13 — Blocks screen and local restrictions

**Status: implemented and build/test-verified; device review pending**

- Renamed the existing Apps navigation tab to **Blocks** and replaced its app-usage list with sections for daily limits, manually blocked apps, Shorts/Reels fallbacks, website rules, permission status, and strict-mode availability.
- Added a searchable installed-app picker with app icons, selection count, confirm/cancel behavior, multi-app limits, blocklist selection, and per-app edit/disable/remove actions.
- App limits now persist daily duration, optional 50/75/90% warning threshold, and whether reaching the limit blocks the app. Usage is read from the local-day UsageStats range; notification warnings are deduplicated per app and day. Enforcement still requires Usage Access and the user-enabled Accessibility service, and Android may delay its usage data.
- Added SQLite v8 migration for app-limit warning/block settings and individually enabled website rules. SQLite v9 adds weekday/time schedules to website rules, including overnight intervals; existing rules remain enabled and unscheduled after migration.
- Website rules can be added, edited, removed, enabled/disabled, and scheduled by weekdays and local time. Filtering continues to use the existing optional local VPN. The UI explains DNS bypass limitations and that Android permits only one active VPN.
- Shorts/Reels are explicitly implemented as whole-app blocking fallbacks because Android does not provide reliable feature-level detection. The UI warns that normal YouTube videos or Instagram content will be restricted too.
- Reused the real focus-only blocking preference. Uninstall prevention, global split-screen disabling, and control of other apps' floating windows are shown as unavailable rather than represented by nonfunctional switches. No Pro, premium, payment, or upgrade UI was added.
- Verification: `:app:testDebugUnitTest` passed (24 tests, 0 failures); `:app:lintDebug` and `:app:assembleDebug` passed. Lint reports 0 errors and 2 existing dependency-age warnings. APK SHA-256: `A3130EA1ED22B804F92FC6271FDA53D793A3465C70F1470F2CBC9A821D19DF44`.
- USB test: installed the debug APK with `adb install -r` on the Samsung SM-M045F (`R9ZW7085N3X`), opened Blocks and the installed-app picker, and verified the app process stayed alive with no current-process fatal/runtime errors.
- Android 14 initially restricted Accessibility because the APK was installed with ADB and had no installer package. Using the normal Settings > Apps > StudyMode app-info route, the user allowed restricted settings and then manually enabled “StudyMode app blocker”; ADB confirmed the service appears in `enabled_accessibility_services`. Usage Access was also confirmed allowed.
- Controlled block test: the user selected Reddit (`com.reddit.frontpage`) as a temporary target. With its block enabled, launching Reddit returned the foreground activity to the Samsung launcher. The temporary block was then removed, and the Blocks screen showed “No apps selected.”
- The app picker and block enforcement were exercised on-device. App-limit threshold timing, notifications, website VPN behavior, and database upgrades from an older on-device database remain unverified; notification permission remains denied. The installed debug APK SHA-256 is `A3130EA1ED22B804F92FC6271FDA53D793A3465C70F1470F2CBC9A821D19DF44`.
- Remaining platform limitation: app-limit notifications and enforcement run when the Accessibility service receives app-window events, so they are not guaranteed to fire at an exact minute. VPN/Usage Access/Accessibility behavior and the v8/v9 database migrations still need on-device validation.

### Remaining limitations before calling the product fully validated

- A model-based local AI coach has not been added. No model was selected or bundled; the app does not send data to a remote AI service.
- Real-device validation is still needed for notifications, reboot alarm restoration, app blocking/usage access, and VPN DNS behavior.
- The debug APK is build-installable for testing, but a release-signed APK requires the owner's signing key and release/distribution setup.
- The reconstruction implements the agreed offline workflows, not a pixel-accurate or complete reproduction of every feature inferred from the reference binary.
