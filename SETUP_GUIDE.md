# GateFocus — Samsung Galaxy Tab A9 Study Launcher & Lockdown Guide

**GateFocus** is a custom Android Home Launcher built specifically for GATE exam preparation. It completely replaces your tablet's home screen so you only see your study materials.

---

## What GateFocus Does

1. **Replaces Default Launcher (`android.intent.category.HOME`)**:
   - There is **no app drawer**, no Play Store shortcut, and no browser.
   - The tablet turns on directly to this app.
2. **Video Lecture Hub**:
   - Discovers all downloaded video playlists (from `/sdcard/GATE/` or `/sdcard/Download/`).
   - Displays the `.jpg` thumbnails saved by `download.py`.
   - Auto-tracks progress (e.g. 45% watched, resumes where left off, checkmark `✓` when done).
   - Built-in variable playback speeds (`1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`).
3. **PDF Workbook Solver**:
   - Opens GATE question banks and workbooks.
   - Has a built-in touch/stylus math solver canvas overlay (`Pen`, `Eraser`, `Clear`).
   - Automatically saves handwritten derivations and notes per page.
4. **Unbreakable Focus Lock & Tamper Protection (Inspired by Mindful)**:
   - Locks the tablet into Kiosk mode (`startLockTask()`), blocking notifications, recent apps, and uninstallation.
   - **Tamper Protection**: If you try to open Android Settings to disable Device Admin or Accessibility, GateFocus intercepts the screen and blocks it.
   - **Deactivation Lockdown**: If an admin disable is attempted, `dpm.lockNow()` immediately locks the tablet screen.
   - **Custom Study Whitelist**: Easily choose which apps (e.g. Samsung Notes, Calculator, VLC) are allowed during study, while everything else is blocked.
   - Protected by a Master PIN (Default: `2026`).

---

## Step 1: Building the APK

You have two easy ways to compile the APK:

### Method A: Automated GitHub Actions (Easiest — No Android SDK needed locally)
1. Push this `study_launcher` folder to a GitHub repository (private or public).
2. GitHub Actions (configured in `.github/workflows/build-apk.yml`) will automatically compile the APK in 2 minutes.
3. Go to the **Actions** tab on your GitHub repository, click the latest run, and download **`GateFocus-StudyLauncher-APK`**.

### Method B: Using Android Studio Locally
1. Download & open [Android Studio](https://developer.android.com/studio).
2. Click **Open** and select the `study_launcher` folder.
3. In the top menu, go to **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**.
4. Android Studio will generate `app-debug.apk`.

---

## Step 2: Install on Samsung Galaxy Tab A9

1. Transfer `app-debug.apk` to your tablet via USB cable, Google Drive, or local storage.
2. Open the file on your tablet and tap **Install**.
3. Grant **All files access** when prompted so GateFocus can find your downloaded videos and PDFs.

---

## Step 3: Set GateFocus as Default Launcher

1. On your Samsung Tab A9, go to **Settings** → **Apps** → **Choose default apps**.
2. Tap **Home app**.
3. Select **GateFocus**.
4. Now, pressing the Home button or restarting the tablet will always take you directly to GateFocus. Other distracting apps are completely out of sight.

---

## Step 4: Enforce Anti-Uninstall & Kiosk Mode (Device Owner)

If you struggle with impulsively uninstalling blockers:

### A. Simple Device Admin (Via Tablet Settings):
1. Inside GateFocus, tap **Lock Device**.
2. Tap **Activate** on the Device Administrator prompt.
3. This prevents standard uninstallation through the launcher.

### B. Unbreakable Device Owner (Via ADB from your PC):
To make it completely un-uninstallable even in safe mode:
1. On your Tab A9, go to **Settings** → **Developer options** → turn on **USB debugging**.
2. Connect your tablet to your PC with a USB cable.
3. Open PowerShell on your PC and run:
   ```powershell
   adb shell dpm set-device-owner com.gatefocus.launcher/.DeviceAdmin
   ```
4. Once this is set, Android marks GateFocus as the **Device Owner**. The system will refuse any attempt to uninstall it or bypass kiosk restrictions.

---

## Step 5: Enable the 2nd Line of Defence (App Blocker)

Even if you click a link, open a notification, or trigger Google Assistant:
1. In GateFocus, tap **Enable Blocker** when prompted (or go to **Settings** → **Accessibility** → **Installed apps**).
2. Turn on **GateFocus Focus Guardian**.
3. *What it does*: The instant any unapproved app (File Manager, Chrome, Samsung Internet, Webnovel readers, Social media) opens on the screen, the blocker intercepts it within 50ms and forcibly boots you right back to your study screen with a warning.

---

## Where to Place Your Files (Internal Storage Only — No Micro-SD Needed!)

> **Note on Storage**: Android's Linux subsystem internally labels your tablet's built-in internal memory as `/sdcard` or `/storage/emulated/0`. **You do NOT need a physical micro-SD card.** It uses your Samsung Tab A9's standard built-in internal flash memory.

1. Connect your Samsung Tab A9 to your PC with a USB cable.
2. In Windows File Explorer, open **Galaxy Tab A9** → **Internal Storage**.
3. Create a folder named `GATE`:

```text
Tablet Internal Storage/
 └── GATE/
      ├── Network Theory/
      │    ├── 01 - Basics.mp4
      │    ├── 01 - Basics.jpg      <-- Thumbnail automatically displayed!
      │    ├── 02 - KCL & KVL.mp4
      │    └── 02 - KCL & KVL.jpg
      │
      └── Workbooks/
           ├── Network_Theory_Workbook.pdf
           └── Engineering_Mathematics_PYQs.pdf
```

Whenever you download new playlists using `uv run python download.py`, simply drag and drop the folder directly into `Internal Storage/GATE/`!
