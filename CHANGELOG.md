# Changelog

## 🚀 v1.8.1 (2026-08-29)

APK installation working again, a first-launch screen that no longer locks you out on Android TV or ejects you halfway through, every reader listed under Default apps, and no keyboard in the way when saving a file.

### 🔄 Changed
- **No keyboard when another app hands you a file to save:** the name arrives prefilled and is usually kept as is. Tap the field to edit it.
- **The signing config is read from `keystore.properties`** instead of `signing.properties`. Building from source only.

### 🐛 Fixed
- **Installing an APK works again:** v1.8.0 shipped without the install permission, dropped along with the in-app updater that also used it, so Android refused every install without a word.
- **The first-launch screen can be completed on Android TV:** it asked for "All files access", which has no settings page on TV before Android 13, so Continue stayed greyed out for good.
- **The first-launch screen is readable:** its text used the Material 3 type scale, which the default theme does not define, so every label came out at the platform's smallest size.
- **The first-launch screen waits for you** instead of dropping you into the file list the moment storage is granted.
- **It says VFiles, not Material Files**, in all 32 languages.
- **Every reader shows up under Default apps:** candidates were queried in a way that only matched apps declaring no file type of their own, so a newly installed PDF reader never appeared.
- **No Notifications card below Android 13**, where the permission does not exist and it showed as already granted.

## 🚀 v1.8.0 (2026-08-03)

The first public release of the fork: Android TV support, removable storage that appears on its own and can be renamed, a default app per file type, an opt-in edge-to-edge display, VFiles usable as the system file picker for every backend it supports, and Firebase gone entirely.

### ➕ Added
- **Android TV support:** full D-pad navigation, with the sidebar, toolbar and file lists reachable and correctly highlighted from a remote, and a first-launch flow that works on TV.
- **A first-launch permission flow**, so what the app needs is granted up front rather than mid-use.
- **Removable storage, reworked:** SD cards and USB drives appear automatically under "Internal storage" and refresh when you plug one in, with no manual "Add storage" SAF dance. Rename a volume and choose its icon, USB or SD detected automatically with a manual override. A multi-partition drive is grouped under a single "USB drive" / "SD card" header with its partitions nested beneath it. Eject shortcut included.
- **A default app per file category:** Settings › Default apps, for images, audio, video, PDF and text, so those files open straight away instead of asking every time.
- **File operation progress inside the app:** a progress button appears in the toolbar during copies, moves and archive work, opening a popover with live per-file detail instead of only a notification.
- **A DocumentsProvider:** VFiles can serve as a source in the system file picker for **every backend it supports**, not just local storage. SMB, FTP, SFTP and WebDAV shares and the contents of archives all appear as ordinary folders in any app's Open or Save dialog. Picked files and folders return real document and tree URIs with persistable permissions, so the receiving app keeps its access across a restart.
- **An opt-in edge-to-edge display** (Settings › Interface): content scrolls behind the status and navigation bars instead of stopping above them, with the header collapsing away completely rather than leaving a strip pinned at the top. Off by default.
- **Per-server SMB encryption.**
- **An opt-in `systemPicker` build flavor for ROM integrators:** signed with a ROM's platform key, VFiles hands back the exact storage URIs DocumentsUI would, so even apps that specifically require them work against it. No product flavor is registered unless the build asks for it, leaving a standard build untouched.

### 🔄 Changed
- **Renamed to VFiles** (`fr.vroot.vfiles`), so it installs alongside the Play or F-Droid build rather than conflicting with it.
- **Navigation drawer redesigned and themed for Material You**, with dark-mode colour and switch-thumb fixes, a coloured header, a themed action button and the selected item in the theme colour.
- **Status bar icons tint themselves to the header or drawer colour**, light or dark, instead of following only the light/dark mode, with a smooth crossfade when the drawer opens. The navigation bar colour follows the app bar.
- **Dark app bar in night mode** instead of the light primary tint, with Material 3 dark-mode fixes for vivid colours and switch consistency.
- **Icons shown in the ⋮ overflow menus**, and in the view and sort menus, with a themed popup in dark mode.
- **The "wallpaper" theme colour comes from the wallpaper itself** rather than the system accent, so it matches on OEM skins whose accent does not follow the wallpaper.
- **Long names wrap** instead of being cut off with an ellipsis, and long toolbar titles marquee-scroll.
- **Internal storage renamed and given a phone icon**, bookmarked folders marked with a real ribbon, and a refreshed FTP server icon.
- **Back exits at the root** instead of opening the navigation drawer.
- **Picker mode is clearly signalled**, with a close icon and the requesting app's name in the title.
- **Free space in the drawer refreshes** when the drawer opens and after file operations, instead of only after a restart.
- **README rewritten in English and French** with a language switcher, and around 55 fork-specific strings translated into 31 languages.
- **The assembled APK is named `VFiles-v<version>.apk`**, so the file carries its version.
- **Debug builds are signed with the release key when a keystore is configured**, so a debug build installs over the published one without uninstalling first.
- **Java 11 and a pass over the compiler warnings**, marking deliberate deprecated-API use and migrating the rest off it, rather than changing behaviour to silence them.
- **dav4jvm is bundled in an offline Maven repository** instead of being fetched from JitPack, which no longer serves an artifact for the pinned commit.

### 🗑️ Removed
- **Firebase (Analytics and Crashlytics) and every dependency on Google services.** Nothing about your usage or your crashes is sent anywhere, and the app starts noticeably faster without WebView/Chromium loading and telemetry initialising at launch.
- **The outdated upstream Chinese README.**

### 🐛 Fixed
- **A crash on tablets in landscape.**
- **Getting stuck in an empty folder on Android TV** (focus now falls back to the toolbar), and a focus jump when toggling the FTP server.
- **Unreadable white-on-white status bar icons** with the drawer open, and the tint now fades symmetrically on opening and closing.
- **A folder opened from another app through an ExternalStorageProvider URI** (LocalSend's "open folder" after receiving files, for one) could not be browsed: it resolved to a single stream rather than a directory. Such a URI is now resolved to the real local path.
- **Documents reached through a persisted tree grant could not be read or written**, including files the app created in sub-folders.
- **A picked URI is now granted to the calling app explicitly**, so its access outlives the picker screen.
- **A clear message when a folder cannot be opened**, instead of a silent failure.

### 🛡️ Security
- **Android cloud backup and data extraction are disabled**, so the system never ships app data off-device.

---

Based on Material Files by Hai Zhang, under the GPL-3.0 licence.
