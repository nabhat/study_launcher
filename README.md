# GateFocus — Distraction-Free GATE Study Launcher

**GateFocus** (`com.gatefocus.launcher`) is a custom Android Home Launcher, Focus Kiosk, Video Lecture Hub, and PDF Workbook Solver built for focused GATE exam preparation on Android tablets (tested on **Samsung Galaxy Tab A9 / One UI 6**, Android `minSdk 26` / `targetSdk 34`).

---

## Key Features

### 1. Low-Friction Video Lecture Hub (`MainActivity`, `Adapters`, `TitleFormatter`)
- **Zero Duration Anxiety:** Video length numbers (`min / min`, `exo_position`, `exo_duration`) are hidden on both list cards and the video player seek bar so long lectures don't create mental friction—while keeping visual progress bars and draggable seeking intact.
- **Clean Display Titles:** [TitleFormatter.kt](app/src/main/java/com/gatefocus/launcher/data/TitleFormatter.kt) automatically strips trailing bracketed video IDs (`[...]`) and pipe-delimited metadata suffixes (`｜｜`, `｜`, `||`, ` | `) in the UI without renaming files on disk and while preserving math notation such as `P(A | B)`.
- **Up Next / Continue Card:** Surfaces your most recent unfinished lecture (or the next unwatched lecture in sequence) in one tap.
- **Hide Done & Long-Press Completion:** Toggle the **Hide Done** filter chip to clear finished lectures out of view, or long-press any video card to manually mark/unmark it as completed.
- **Subject-Organized Workbooks & Videos:** Scans `/sdcard/GATE/` (plus `Download/` and `Documents/`), automatically grouping videos and workbooks under their parent subject folder (`<Subject>/` or `<Subject>/workbooks/`) with natural alphanumeric sorting and a dual-pane 2-column tablet layout in landscape.

### 2. Offline Video Player (`VideoPlayerActivity`)
- **Immersive Fullscreen + Material You Controls:** System bars auto-hide; interactive full-width `DefaultTimeBar` seek bar with no time numbers.
- **VLC-Style Gestures:** Vertical swipe on the left half adjusts screen brightness; vertical swipe on the right half adjusts media volume with animated HUD indicators.
- **Speed Control & Hold-for-2x:** Persistent playback speeds (`1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`) across lectures, plus press-and-hold anywhere on the video for temporary `2.0x` fast-forward.
- **Touch Lock, Fit/Zoom, Rotate & Stacked Seeking:** Lock screen touches while holding the tablet bezel, toggle `Fit` / `Fill (Zoom)`, toggle landscape/portrait, double-tap left/right for stacked `±10s` seeks, and jump straight to the next lecture from the **Lecture Completed** overlay.
- **Bluetooth Media Buttons & In-App Pairing:** Supports headphone Play/Pause, Headset Hook, and Next/Prev hardware controls via `MediaSession`, auto-pauses on headphone disconnect (`AUDIO_BECOMING_NOISY`), and lets you scan/connect headphones inside the app via [BluetoothDialogHelper.kt](app/src/main/java/com/gatefocus/launcher/BluetoothDialogHelper.kt) without leaving the launcher to open Android Settings.

### 3. PDF Workbook Solver (`PdfViewerActivity`, `DrawingView`, `ZoomableFrameLayout`)
- Native `PdfRenderer` workbook viewer with pinch-to-zoom, page navigation, and a touch/stylus annotation canvas saving drawings and last-read page per PDF.

### 4. Focus Shield, App Blocker & OS-Level Device Owner (`DeviceAdmin`, `FocusGuardianService`)
- **Focus Session Timer:** Lock into distraction-free study sessions (`15m`, `25m`, `45m`, `60m`, `90m`, `120m`) with configurable allowed study apps (e.g., Samsung Notes, Calculator, VLC) and easy launcher switching when unlocked.
- **Accessibility App Blocker (`FocusGuardianService`):** Intercepts Android Settings and unapproved apps during active focus sessions.
- **Device Admin & Device Owner (`KioskManager`):** Locks screen on admin-deactivation attempts during a focus timer and, when provisioned as Device Owner, enforces OS-level `DISALLOW_UNINSTALL_APPS`, `packageUninstallBlocked`, `DISALLOW_FACTORY_RESET`, `DISALLOW_SAFE_BOOT`, and `DISALLOW_APPS_CONTROL`.

---

## Folder Structure on Tablet Storage

Place your study folders inside **Internal Storage** (`/sdcard/GATE/` or `/storage/emulated/0/GATE/`):

```text
Internal Storage/
└── GATE/
    ├── Engineering Mathematics/
    │   ├── 01 - Introduction.mp4
    │   ├── 01 - Introduction.jpg          # Optional thumbnail
    │   └── workbooks/
    │       ├── Study_Guide.pdf
    │       └── Practice_Guide.pdf
    └── Network Analysis/
        ├── 01 - Basics.mp4
        └── workbooks/
            └── Workbook.pdf
```

*(PDFs placed directly inside `<Subject>/` or inside subfolders such as `workbooks/`, `books/`, `notes/`, or `pdfs/` are automatically linked to `<Subject>`.)*

---

## Project Structure

```text
app/src/
├── main/java/com/gatefocus/launcher/
│   ├── MainActivity.kt               # Launcher home, subject chips, Up Next, stats, timer
│   ├── Adapters.kt                   # Video & PDF RecyclerView adapters
│   ├── VideoPlayerActivity.kt        # Media3 ExoPlayer, gestures, MediaSession, UX controls
│   ├── PdfViewerActivity.kt          # PDF renderer & annotation host
│   ├── DrawingView.kt                # Stylus/touch canvas for solving workbooks
│   ├── ZoomableFrameLayout.kt        # Pinch-zoom container for PDF pages
│   ├── BluetoothDialogHelper.kt      # In-app Classic + BLE Bluetooth headphone manager
│   ├── DeviceAdmin.kt                # DeviceAdminReceiver & KioskManager policies
│   ├── AdminRemovalReceiver.kt       # ADB broadcast receiver to clear Device Owner
│   ├── FocusGuardianService.kt       # Accessibility blocker for non-whitelisted apps
│   ├── BlockedAppActivity.kt         # Distraction-blocked screen
│   ├── LauncherHelper.kt             # System launcher picker helper
│   └── data/
│       ├── Models.kt                 # VideoItem, PdfItem, StudyProgressManager
│       ├── NaturalOrderComparator.kt # Natural numeric sorting (01, 02, 10...)
│       └── TitleFormatter.kt         # Generic UI title suffix/ID cleaner
└── test/java/com/gatefocus/launcher/data/
    └── TitleFormatterTest.kt         # JVM unit tests for TitleFormatter
```

---

## Building, Testing & Installing

Requires **JDK 17–21** (tested with JDK 21) and Android SDK (`compileSdk 34`).

```powershell
# Run JVM unit tests and build debug APK
$env:JAVA_HOME = "C:\Users\nabha\.jdks\jbr-21.0.11"
.\gradlew.bat testDebugUnitTest assembleDebug

# Install APK onto connected tablet via ADB
adb install -r -d -t app\build\outputs\apk\debug\app-debug.apk
```

---

## Device Owner Provisioning & Removal (ADB)

To enable full OS-level anti-uninstall / anti-factory-reset restrictions (device must have no accounts logged in while provisioning; you can re-add accounts afterward):

```powershell
adb shell dpm set-device-owner com.gatefocus.launcher/.DeviceAdmin
```

To cleanly clear all OS-level restrictions and remove Device Owner via ADB when needed:

```powershell
adb shell am broadcast -a com.gatefocus.launcher.CLEAR_DEVICE_OWNER
```

For step-by-step initial tablet setup instructions, see [SETUP_GUIDE.md](SETUP_GUIDE.md).
