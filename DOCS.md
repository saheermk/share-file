# Share File — Technical Documentation

---

## Architecture Overview

This is a **pure native Android** app built with Jetpack Compose. It uses raw Java sockets for the HTTP engine to maintain a minimal binary footprint and maximum control.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Android App                                   │
│  ┌──────────────────────┐        ┌──────────────────────────┐           │
│  │     MainActivity     │────────▶│      FolderPicker.kt     │           │
│  │ (Neumorphic Dashboard)│        │   (Compose folder tree)  │           │
│  └──────────┬───────────┘        └──────────────────────────┘           │
│             │                                                           │
│             ▼ starts/controls                                           │
│  ┌──────────────────────┐        ┌──────────────────────────┐           │
│  │  KeepAliveService.kt │────────▶│      FileServer.java     │           │
│  │ (Foreground Service) │        │ (ServerSocket Pool Engine)│           │
│  └──────────────────────┘        └──────────┬───────────────┘           │
│                                             │                           │
│             ┌───────────────────────────────┴───────────────┐           │
│             │                                               │           │
│  ┌──────────▼──────────┐                         ┌──────────▼────────┐  │
│  │   WebInterface.java  │                         │   AppsManager Logic  │  │
│  │ (HTML/JS Generator)  │                         │ (PackageManager API) │  │
│  └──────────┬───────────┘                         └──────────┬────────┘  │
│             │                                               │           │
│  ┌──────────▼───────────────────────────────────────────────▼────────┐  │
│  │                       HTTP API Layer                              │  │
│  │  • /Files      → Explore filesystem with modern UI                │  │
│  │  • /Apps       → Browse installed application APKs                │  │
│  │  • /Security   → Session-based password protection                │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Core Components

### `FileServer.java`

- **Thread model**: Single accept loop on a background thread. Each connection is handled by a worker from a fixed-size thread pool (8 threads).
- **Protocol**: Handcrafted HTTP/1.1 engine using `InputStream.read()` for zero-dependency overhead.
- **Secure Sockets**: Supports `SSLServerSocket` powered by `javax.net.ssl`.
- **Session Cache**: Simple in-memory `Set<String>` for authenticated IP addresses (used when password protection is enabled).

### `WebInterface.java`

- **Architecture**: Stateless static methods generating robust HTML5.
- **Styling**: Inline CSS variables for Neumorphic components and dynamic theme switching.
- **Logic**: Bundled ES6+ Javascript for search, sorting, and stateful selection mode.

### `MainActivity.kt`

- **UI System**: Pure **Jetpack Compose** with custom Neumorphic implementation details (`neumorphicShadow` modifier).
- **Network Discovery**: Iterates over `NetworkInterface.getNetworkInterfaces()` to discover all active local IPs (WLAN, Hotspot, Ethernet, Loopback).
- **QR Ecosystem**:
  - **Generator**: Uses ZXing Core to render the server URL as a bitmap.
  - **Scanner**: Integrated header icon using `ScanContract` to facilitate device-to-device connection.

### `KeepAliveService.kt`

- **Foreground Service**: Holds a persistent notification to prevent the Android system from killing the server process.
- **Wakelocks**:
  - `PowerManager.PARTIAL_WAKE_LOCK`: Keeps the CPU running.
  - `WifiManager.WIFI_MODE_FULL_HIGH_PERF`: Prevents Wi-Fi from entering low-power/sleep mode during transfers.

---

## Automation Logic

### Auto Start on Boot

The `BootReceiver` listens for the `ACTION_BOOT_COMPLETED` broadcast. When received, it checks the `auto_start_boot` preference and, if enabled, starts the `KeepAliveService`, which in turn initializes the HTTP engine.

### Inactivity Shutdown

The `KeepAliveService` runs a periodic check (every 1 minute). It compares the current time with `ServerManager.fileServer.lastActivityTime`. If the difference exceeds the user-defined threshold, the server and service are automatically stopped to conserve device resources.

---

## HTTP API Reference

| Method | Path                   | Description                            |
| ------ | ---------------------- | -------------------------------------- |
| `GET`  | `/`                    | Redirects to Home (Files/Apps)         |
| `GET`  | `/files?path=X`        | Directory listing for path X           |
| `GET`  | `/download?file=X`     | Download file X (binary stream)        |
| `POST` | `/upload?path=X`       | Multipart upload to directory X        |
| `GET`  | `/apps`                | List all user-installed apps           |
| `GET`  | `/download_app?pkg=X`  | Stream APK for package name X          |
| `GET`  | `/zip?files=A,B,C`     | Stream multiple files as a ZIP archive |
| `POST` | `/login`               | Submit password for access             |
| `GET`  | `/mkdir?path=X&name=Y` | Create new directory Y in path X       |
| `GET`  | `/delete?file=X`       | Delete a file or directory             |

---

## Security & Access Control

1. **Password Protection**: When enabled, the server intercepts all requests (except `/login` and assets) and serves a Neumorphic login page if the IP address is not in the authorized session list.
2. **Path Traversal**: All input paths are canonicalized using `File.getCanonicalPath()` and validated to ensure they reside within the user-defined `rootDir`.
3. **Modification Toggle**: Can be disabled to turn the server into a "Read-Only" portal, hiding all upload and file manipulation controls.
4. **Preview Toggle**: Controls whether the web interface renders `<img>` and `<video>` tags directly or just shows generic icons (enhances privacy/performance).
5. **Strict Approval Mode**:
   - Implements an IP-based allowlist for maximum security.
   - When active, the server serves a specialized "Access Request" page with a host-scannable QR code to any IP not in the allowlist.
   - The host uses the Android app's built-in scanner to extract the client IP from the QR code and add it to the persistent `allowed_ips` set.
6. **HTTPS (TLS) Encryption**:
   - The app supports end-to-end encryption using standard TLS.
   - Users must provide a **PKCS12 (.p12/.pfx)** certificate file and its corresponding password.
   - When enabled, the server initializes an `SSLContext` to wrap the standard server socket, ensuring all traffic between the Android device and the browser is encrypted.

---

## Build Configuration

```gradle
compileSdk  = 34
minSdk      = 26      (Android 8.0 Oreo)
targetSdk   = 34
versionName = 3.0.4
```

**Memory Management**:
The system is configured with `org.gradle.jvmargs=-Xmx2048m` in `gradle.properties` to handle complex Compose UI compilation without heap exhaustion.
