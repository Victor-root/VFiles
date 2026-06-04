# Material Files — Victor-root fork

**English** · [Français](README.fr.md)

[![Latest release](https://img.shields.io/github/v/release/Victor-root/MaterialFiles)](https://github.com/Victor-root/MaterialFiles/releases) [![License: GPL v3](https://img.shields.io/github/license/Victor-root/MaterialFiles?color=blue)](LICENSE)

A personal, improved fork of **[Material Files](https://github.com/zhanghai/MaterialFiles)** by Hai Zhang — an open-source Material Design file manager for Android.

> **Why this fork?**
> Material Files is already an excellent app — this is **not** a competitor or a criticism of the original. I forked it to build a version tailored to **my own daily use**: mostly small UI-consistency improvements, bug fixes, and first-class **Android TV** support. Everything here sits on top of the original author's work, and all credit for the app itself goes to him.

The original repository remains the reference; the sections below describe **only what this fork adds or changes**. For what the app fundamentally *is*, see [About the original app](#about-the-original-app).

## What this fork changes

### 📺 Android TV support
- Full D-pad navigation — the sidebar, toolbar and file lists are reachable and highlight correctly with a remote.
- The first-launch onboarding works on TV.
- Fixed getting stuck in an empty folder (focus falls back to the toolbar) and a focus jump when toggling the FTP server.

### 🚀 First-launch onboarding
- A welcome flow that requests the permissions the app needs (all-files access, notifications, install-from-APK) up front, so nothing has to be granted mid-use later.

### ⬆️ In-app updates
- Checks this repo's GitHub releases and can download and install a newer APK directly from **About → Check for updates** — no store required.

### 💾 Removable storage (SD card / USB), reworked
- SD cards and USB drives **appear automatically under "Internal storage"** and refresh when you plug one in — no manual "Add storage" SAF dance.
- **Rename** a volume and choose its **icon**; USB vs SD is auto-detected (with a manual override).
- A multi-partition drive is grouped under a single **"USB drive" / "SD card"** header, with its partitions nested as **"Partition 1, 2, …"**.
- **Eject** shortcut, and the free-space figure refreshes live (e.g. right after deleting files) instead of only after a restart.

### 🎯 Default apps per file type
- New **Settings → Default apps**: pick which app opens Images, Audio, Video, PDF and Text — so those files open straight away in your app of choice instead of asking every time.

### 🎨 UI consistency & theming
- Status-bar icons tint themselves to the header/drawer colour (light or dark), fixing unreadable white-on-white and black-on-dark cases, with a smooth crossfade when the drawer opens.
- Dark app bar in night mode instead of the light primary tint.
- Icons shown in the ⋮ overflow menus; themed popup in dark mode.
- Material You (M3) dark-mode colour and switch-thumb fixes.
- Long names wrap instead of being cut off with "…".
- Internal storage renamed with a phone icon; a real ribbon marks bookmarked folders; refreshed FTP-server icon.

### 🐛 Other fixes & polish
- Friendly message when a folder can't be opened, instead of a silent failure.
- Cloud backup / data extraction disabled for privacy.
- All fork-specific strings translated into **31 languages**.
- App ID changed to `fr.vroot.android.files` so it installs alongside the Play/F-Droid build rather than conflicting with it.

## About the original app

Material Files is an open-source Material Design file manager for Android 6.0+:

- Material Design with attention to detail, and breadcrumb navigation.
- Root support; view/extract/create common archives; FTP, SFTP, SMB and WebDAV.
- Customisable colours and night mode (with optional true black).
- Linux-aware — symbolic links, permissions and SELinux context — via real system calls rather than parsing `ls`, built on the Java NIO2 file API with `ViewModel`/`LiveData`.

## Building

Open the project in Android Studio and run it, or from the command line:

```sh
./gradlew assembleRelease
```

The native code is compiled for all ABIs (arm64-v8a, armeabi-v7a, x86, x86_64), producing a single universal APK that runs on any device.

## Credits & license

- **Original app and all of its core features:** [Hai Zhang](https://github.com/zhanghai) and contributors.
- **This fork:** [Victor-root](https://github.com/Victor-root).

Released under the **GNU General Public License v3.0**, the same license as the original. See [LICENSE](LICENSE).

```
Copyright (C) 2018 Hai Zhang
Copyright (C) 2024 Victor-root (fork modifications)

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option)
any later version. It is distributed WITHOUT ANY WARRANTY; see the GNU
General Public License for more details.
```
