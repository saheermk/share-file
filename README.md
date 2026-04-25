# Share File

A high-performance Android file server implementation providing seamless local network file sharing via a modern web interface.

---

## Features

- **Integrated File Explorer**: Responsive web interface for browsing and managing files from any browser.
- **Media Gallery**: Optimized media streaming with high-resolution previews and smooth transitions.
- **App Package Management**: Browse installed applications and download APKs directly.
- **Secure Access**: Support for password protection, device approval (QR auth), and HTTPS encryption.
- **Advanced Operations**: Bulk multi-selection, ZIP archive generation, and drag-and-drop uploads.
- **Adaptive Theming**: Neumorphic UI design with automatic light/dark mode synchronization.

---

## User Manual

### Initial Setup

1.  **Installation**: Install the APK on your Android device.
2.  **Permissions**: Grant **"All Files Access"** when prompted. This is required for the server to access and share your requested directories.

### Server Configuration

1.  **Select Directory**: Tap **Browse** to select a specific folder, or **Internal** for the storage root.
2.  **Network Settings**:
    - Set the listening **Port** (default: `8080`).
    - Ensure your device is connected to a local Wi-Fi network or active Hotspot.
3.  **Authentication**: (Optional) In _Security & Settings_, set an access password to restrict browser access.
4.  **Launch**: Tap **Start Server**. The interface will display a QR code and the active URL.

### Connecting to the Server

- **Automatic**: Scan the displayed QR code with another mobile device to open the URL instantly.
- **Manual**: Navigate to the displayed local IP address and port (e.g., `http://192.168.1.5:8080`) in any modern web browser.

---

## Technical Specifications

To maintain performance and a minimal footprint, Share File utilizes a specialized architecture:

1.  **Low-Level HTTP Engine**: Built using raw Java Sockets (`ServerSocket`) instead of heavyweight frameworks. This ensures instant startup and extreme resource efficiency.
2.  **Jetpack Compose UI**: The native Android dashboard utilizes custom Neumorphic modifiers for a consistent, tactile user experience.
3.  **MediaStore Integration**: Interface with the Android Media System for instantaneous loading of large media collections without CPU-intensive disk scanning.
4.  **Stateless Web SPA**: A high-performance Single Page Application written in Vanilla ES6+ Javascript for maximum compatibility and speed.
5.  **Security Sandboxing**: Path resolution is strictly validated using canonical path comparisons to prevent directory traversal vulnerabilities.

---

## Development & Build

### Requirements

- JDK 17 or higher
- Android SDK (API 34)
- Gradle Wrapper (included)

### Build Instructions

```bash
# Build the debug APK
cd android/
./gradlew assembleDebug
```

The output APK is located at: `android/app/build/outputs/apk/debug/app-debug.apk`

---

## License & Credits

Developed by [saheermk](https://saheermk.pages.dev).
For detailed API references and internal architecture, see [DOCS.md](DOCS.md).
