# Using Material Files as the system document picker (`systemPicker` flavor)

> **Audience: ROM builders / integrators.** This is **not** something you install as an APK. It
> only works when the build is signed with the **platform key** of the ROM it ships in. On a stock,
> pre-built ROM you cannot use it — there is no way to hold the required permission without the
> platform signature.

## What it does

By default Android routes the Storage Access Framework intents (`OPEN_DOCUMENT`,
`OPEN_DOCUMENT_TREE`, `CREATE_DOCUMENT`, `GET_CONTENT`) to **DocumentsUI**. DocumentsUI returns
`content://com.android.externalstorage.documents/…` URIs and, because it holds
`android.permission.MANAGE_DOCUMENTS`, the system lets it grant those URIs to the calling app even
though it does not own the provider.

A normal build of Material Files returns URIs from **its own** providers instead
(`…documents` / `…file_provider`). That works for most apps, but apps that require genuine
`ExternalStorageProvider` URIs — the whole SimpleMobileTools / **Fossify** family, which compares the
returned tree URI for strict equality and then `takePersistableUriPermission()`s it — reject them.

The `systemPicker` flavor makes Material Files behave like DocumentsUI: for a folder/file on **local
storage** it returns the same `com.android.externalstorage.documents` tree/document URI DocumentsUI
would, with the same grant flags. The actual reads/writes are then served by the genuine
`ExternalStorageProvider` (a separate package, `com.android.externalstorage`, which stays present),
not by Material Files. Remote backends (SMB/FTP/SFTP/WebDAV/archives) keep returning Material Files'
own URIs, since `ExternalStorageProvider` cannot serve them.

## Why the platform signature is mandatory

`MANAGE_DOCUMENTS` is declared `protectionLevel="signature"`. It is granted **only** to apps signed
with the same certificate as the framework (`android` package) — i.e. the platform key. It is *not*
covered by the privileged-app allowlist, so `/system/priv-app` + `privapp-permissions.xml` is **not**
enough, and it cannot be granted with `pm grant`, `appops`, or root. This is exactly how the real
DocumentsUI obtains it (`certificate: platform` in its `Android.bp`).

The code is defensively gated: it emits an `ExternalStorageProvider` URI only when
`checkSelfPermission(MANAGE_DOCUMENTS) == GRANTED` at runtime. If the flavor is built but **not**
platform-signed, the permission is not granted, the check fails, and Material Files transparently
falls back to its normal behavior — it does not crash and does not hand back a dead URI.

## Building the flavor

The flavor is **opt-in via a Gradle property** so the standard build is untouched:

```sh
# Standard build (unchanged): no flavor exists, assembleRelease works as usual.
./gradlew assembleRelease

# systemPicker build (adds the MANAGE_DOCUMENTS permission):
./gradlew -PsystemPicker assembleSystemPickerRelease
```

Only `app/src/systemPicker/AndroidManifest.xml` (which adds the `MANAGE_DOCUMENTS`
`uses-permission`) is merged into this variant. Everything else — including the SAF intent-filters —
already lives in the main manifest.

## Integrating into a ROM

1. **Sign with the platform key.** In your ROM tree, add Material Files as a prebuilt and set
   `LOCAL_CERTIFICATE := platform` (Android.mk) / `certificate: "platform"` (Android.bp), or sign the
   `systemPicker` APK with the ROM's platform key. This is the only *required* step for the
   permission.
2. **Place it in the system image** (`/system` or `/system_ext`).
3. **Remove or disable the stock DocumentsUI** (`com.android.documentsui`). It wins SAF intent
   resolution via `android:priority="100"` on its filters (Material Files' filters are priority 0),
   and it is *not* a reassignable `RoleManager` role — so as long as it is present it will be chosen.
   Once it is gone, Material Files is the sole SAF handler.

`ExternalStorageProvider` (`com.android.externalstorage`) must stay enabled — it is a separate
package from DocumentsUI and serves the actual document I/O.

### Not required

- `/system/priv-app` + `privapp-permissions.xml` (`MANAGE_DOCUMENTS` is `signature`, not
  `signatureOrSystem`).
- `sharedUserId="android.uid.system"` (DocumentsUI does not use it).

### To validate on your target ROM

This has been reasoned from AOSP source but should be verified end-to-end on a real platform-signed
build:

- The grant → `takePersistableUriPermission()` chain actually succeeds through the third-party
  `ExternalStorageProvider` and a strict-SAF app (e.g. Fossify) accepts the folder.
- Any `SignaturePermissionAllowlist` your ROM enforces (Android 14+) still grants `MANAGE_DOCUMENTS`
  by platform certificate.
- SELinux (`platform_app` domain) allows enumerating all storage volumes for the picker UI.

## Scope / known limitations

- **`CREATE_DOCUMENT`** keeps Material Files' own `file_provider` URI (it works for generic
  consumers). `ExternalStorageProvider` only serves an *existing* document, and Material Files
  creates the file lazily on first write, so emitting an ESP document URI for a not-yet-created file
  would be a dead URI. Folder grants (`OPEN_DOCUMENT_TREE`) and opening existing files
  (`OPEN_DOCUMENT`/`GET_CONTENT`) are the covered cases.
- Apps like Fossify only accept the **first-level** folder they ask for (e.g. `primary:Music`), not
  a deep sub-folder — this is their own constraint, unchanged by this flavor.
