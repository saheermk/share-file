# Technical Architecture & Implementation Details

This document outlines the internal architecture, engineering decisions, and technological stack of the Share File ecosystem.

---

## System Architecture

Share File is a full-stack Android application that integrate a native background service layer with an embedded web server and a modern Single Page Application (SPA) frontend.

### 1. Embedded HTTP Engine (Java Sockets)

The core server logic utilizes raw `java.net.ServerSocket` implementation rather than standard third-party frameworks.

- **Rationale**: To maintain a minimal binary footprint and achieve near-zero latency by removing framework overhead.
- **Concurrency**: Implements a `FixedThreadPool` (8 threads) to manage simultaneous browser connections efficiently.
- **Media Streaming**: Supports `HTTP 206 Partial Content` for native browser seeking and large file byte-range requests.
- **Protocol Compliance**: Handcrafted HTTP/1.1 response headers ensuring compatibility across all modern web browsers.

### 2. Frontend Architecture (Vanilla JS SPA)

The web interface is a stateless Single Page Application designed for speed and reliability.

- **Implementation**: Injected dynamically via `WebInterface.java` using a centralized state-reactive rendering model.
- **Performance**: Utilizes pure ES6+ JavaScript. The absence of heavy frameworks (like React or Vue) ensures consistent performance on low-bandwidth local connections.
- **Dependency Management**: Minimal third-party reliance, using only **SweetAlert2** for modal interactions and **FontAwesome 6** for system iconography.

### 3. Native Android Layer (Jetpack Compose)

The Android management console is built using the contemporary Jetpack Compose toolkit.

- **Design System**: Custom Neumorphic UI framework built using pure Compose modifiers for shadow calculation and elevation.
- **Resource Management**: Implements Android `ForegroundService` with `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_HIGH_PERF` to ensure uninterrupted transfers when the screen is off.
- **Auto-Start Logic**: Utilizes `BroadcastReceiver` to handle device boot events for optional server persistence.

### 4. High-Performance Media Pipeline

Gallery operations are optimized through direct integration with the Android MediaStore API.

- **Indexing**: Queries the system media database for instant collection loading, bypassing slow recursive storage scanning.
- **Caching & Previews**: Uses a custom memory-aware thumbnail generator powered by `ThumbnailUtils` and `BitmapFactory` optimization flags.

---

## Security Framework

1.  **Canonical Path Validation**: Every input path is resolved and compared against the defined storage root to prevent directory traversal and symlink attacks.
2.  **Device-to-Device Authentication**: A custom security layer requiring the host to scan a client-specific QR code to authorize IP-based access.
3.  **Encrypted Transport (TLS)**: Full support for HTTPS utilizing `SSLServerSocketFactory`. Users can import PKCS12 certificates for end-to-end encryption.

---

## API Documentation (Core)

| Path           | Method | Functionality                                                              |
| :------------- | :----- | :------------------------------------------------------------------------- |
| `/api/status`  | `GET`  | Server heartbeat, permissions status, and clipboard diagnostics.           |
| `/api/files`   | `GET`  | Hierarchical directory listing with size and timestamp metadata.           |
| `/api/gallery` | `GET`  | Aggregated media stream filtered for images and video formats.             |
| `/api/apps`    | `GET`  | Exhaustive retrieval of installed application packages via PackageManager. |
| `/download`    | `GET`  | Buffered binary streaming with support for resume and range headers.       |
| `/upload`      | `POST` | Multipart ingestion with user-defined conflict resolution strategies.      |
