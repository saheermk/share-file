# SHTTPS Clone — Technical Documentation

---

## Architecture Overview

This is a **pure native Android** app. No services, no NDK, no third-party HTTP libraries.

```
┌─────────────────────────────────────────────────────┐
│                   Android App                       │
│  ┌─────────────────┐   ┌──────────────────────────┐ │
│  │   MainActivity  │   │   FolderPicker.kt        │ │
│  │  (Jetpack       │──▶│   (Compose folder tree)  │ │
│  │   Compose UI)   │   └──────────────────────────┘ │
│  └────────┬────────┘                                 │
│           │ starts/stops                             │
│  ┌────────▼────────────────────────────────────────┐ │
│  │           FileServer.java                        │ │
│  │   ServerSocket on port N                         │ │
│  │   • GET /            → WebInterface directory    │ │
│  │   • GET /?path=X     → Sub-folder listing        │ │
│  │   • GET /download?file=X → File streaming        │ │
│  │   • POST /upload?path=X  → Multipart save        │ │
│  └────────┬────────────────────────────────────────┘ │
│           │ generates HTML                           │
│  ┌────────▼────────────────────────────────────────┐ │
│  │           WebInterface.java                      │ │
│  │   Builds SHTTPS-style HTML:                      │ │
│  │   • Breadcrumb navigation                        │ │
│  │   • Sortable file/folder table                   │ │
│  │   • Upload form                                  │ │
│  └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## Core Components

### `FileServer.java`

- **Thread model**: Main accept loop on a background thread. Each connection handled by a worker from a fixed-size thread pool (8 threads).
- **Protocol**: HTTP/1.1 — handcrafted request parsing with `InputStream.read()`.
- **Directory listing**: Delegates to `WebInterface.buildDirListing()`.
- **File streaming**: `FileInputStream` → raw `OutputStream` with 64KB buffer.
- **File upload**: Parses `multipart/form-data` by byte-array boundary detection. Saves raw bytes to disk preserving binary integrity.
- **Security**: All paths are canonicalized and checked against `rootDir` to prevent path traversal.

### `WebInterface.java`

- Stateless static methods — no templating engine.
- Generates valid HTML5 with inline responsive CSS.
- Breadcrumb built by splitting `relPath` and accumulating cumulative URL params.
- Files sorted: directories first, then files, both case-insensitive alphabetical.

### `FolderPicker.kt`

- Pure Compose dialog — no `Intent`-based system picker.
- Reads `java.io.File` directly (requires `MANAGE_EXTERNAL_STORAGE`).
- Shows only directories (hides hidden folders).
- Supports free navigation up/down the tree.
- Returns the selected `File` to the caller via a lambda.

### `MainActivity.kt`

- Single Activity, single Composable (`ShttsApp`).
- Permission check on every `onResume` via `Environment.isExternalStorageManager()`.
- QR code generated from URL using **ZXing Core** (no ZXing UI dependency).
- **QR Code Scanner**: Integrated with `com.journeyapps:zxing-android-embedded` to allow connecting to other network servers via URL detection.
- Port and root path persisted to `SharedPreferences`.

### `KeepAliveService.kt`

- **Foreground Service**: Ensures the HTTP server remains active even when the app is backgrounded or the screen is off.
- **Wakelocks**: Holds a `PowerManager.PARTIAL_WAKE_LOCK` and `WifiManager.WIFI_MODE_FULL_HIGH_PERF` to prevent the system from suspending the CPU and network stack during file transfers.
- **Lifecycle**: Automatically started and bound to the "Start Server" toggle in `MainActivity.kt`.

---

## HTTP API Reference

| Method | Path                          | Description               |
| ------ | ----------------------------- | ------------------------- |
| `GET`  | `/`                           | Root folder listing       |
| `GET`  | `/?path=/Downloads`           | Sub-folder listing        |
| `GET`  | `/download?file=/foo/bar.txt` | Download a file           |
| `POST` | `/upload?path=/foo`           | Upload a file (multipart) |

---

## Key Dependencies

| Library                                             | Version      | Purpose                   |
| --------------------------------------------------- | ------------ | ------------------------- |
| `androidx.compose:compose-bom`                      | `2023.08.00` | Compose UI                |
| `androidx.compose.material3:material3`              | BOM          | Material Design 3         |
| `androidx.compose.material:material-icons-extended` | BOM          | All Material icons        |
| `com.google.zxing:core`                             | `3.5.2`      | QR code bitmap generation |
| `com.journeyapps:zxing-android-embedded`            | `4.3.0`      | QR code native scanning   |
| `androidx.documentfile:documentfile`                | `1.0.1`      | Optional file abstraction |

---

## Build Configuration

```
compileSdk  = 34
minSdk      = 26      (Android 8.0+)
targetSdk   = 34
versionName = 3.0.4   (matches SHTTPS)
```

**`gradle.properties`:**

```
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

This prevents Gradle daemon from running out of heap when compiling Compose.

---

## Security Notes

- **Path traversal protection**: Every file path is checked with `File.getCanonicalPath()` to ensure it stays inside `rootDir`.
- **No authentication**: This is a local network server — same as SHTTPS. Only use on trusted networks.
- **No HTTPS**: Plain HTTP, same as SHTTPS. For encrypted sharing, set up a reverse proxy.
