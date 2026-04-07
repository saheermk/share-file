# Share File — Android File Server

## About the App

A faithful, modern implementation of the **Share File** server for Android. This application allows you to turn your Android device into a local HTTP file server. You can share files over your local Wi-Fi network effortlessly, enabling any device with a web browser to view, download, and upload files without needing any cables, Bluetooth, or client software.

Built from the ground up using pure native Android technologies (**Jetpack Compose**, **Kotlin**, and raw Java sockets), it delivers maximum performance and unrestricted filesystem access via `MANAGE_EXTERNAL_STORAGE` on Android 11+.

---

| Feature                    | Description                                       |
| -------------------------- | ------------------------------------------------- |
| **Custom Folder Picker**   | In-app filesystem browser — no system picker      |
| **Installed Apps Manager** | Browse and download system app APKs directly      |
| **Web Directory Browser**  | Modern file explorer with search and sorting      |
| **Advanced File Ops**      | Multi-select, ZIP downloads, and drag-and-drop    |
| **Password Protection**    | Secure your server with a custom access password  |
| **Keep-Alive Service**     | Server stays running when app is in background    |
| **QR Code Ecosystem**      | Built-in generator (for sharing) and scanner      |
| **Network Discovery**      | Automatically lists all active network interfaces |
| **Auto Start on Boot**     | Automatically start server when device restarts   |
| **Auto Shutdown**          | stop server after a period of inactivity          |
| **Strict Approval Mode**   | Authorize new devices by scanning their QR code   |
| **HTTPS Encryption**       | Secure TLS support with custom PKCS12 certs       |
| **Modern UI/UX**           | Premium Neumorphic design with dynamic theming    |

---

## Modern UI & Experience

The app features a **rich, skeuomorphic (Neumorphic) interface** that feels physical and interactive.

- **Dynamic Theme**: Supports both Light and Dark modes with a manual toggle in the header.
- **Glassmorphism**: Subtle blur and transparency effects in the web interface.
- **Micro-animations**: Smooth transitions and hover effects for a premium feel.
- **Header Tools**: Quick access to the QR scanner and theme switcher in the Android dashboard.

---

## How to Use

1. **Setup**: Install the APK and grant **"All Files Access"** when prompted.
2. **Select Folder**: Tap **Browse** to pick a folder, or **Internal** for root storage.
3. **Configure**:
   - Set your preferred **Port** (default: `8080`).
   - Choose a **Network Interface** (useful if you have multiple active connections).
   - Enable **Password Protection** in "Security & Settings" if needed.
4. **Start**: Tap **START SERVER**.
5. **Connect**:
   - Scan the generated **QR Code** on another device to open the URL.
   - Or open `http://YOUR_IP:PORT` manually in any web browser.
6. **Web Interface**:
   - Use the **Search** bar to filter files instantly.
   - Toggle between **Grid** and **List** views.
   - **Right-click** or **Long-press** a file to enter Selection Mode.
   - **Drag and Drop** files anywhere on the page to upload.
7. **Strict Access (Optional)**:
   - Enable **Strict Approval Mode** in Security settings.
   - New devices will see a QR code.
   - Scan their screen with your app to grant permanent access.
8. **Secure Connection (Optional)**:
   - Enable **HTTPS** in Advanced Settings.
   - Import a `.p12` or `.pfx` certificate file.
   - Enter your certificate password and restart the server.

---

## Installation

### Option 1: Install Pre-built APK (Easiest)

If you simply want to use the app, download the latest release from the [GitHub Releases](https://github.com/saheermk/share-file/releases) page and install the `.apk` file.

### Option 2: Install via ADB (For Developers)

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Build Instructions

### Requirements

| Tool           | Version                |
| -------------- | ---------------------- |
| JDK            | 17+                    |
| Android SDK    | API 34                 |
| Gradle Wrapper | `./gradlew` (included) |

### Build from Terminal

```bash
# Set Android SDK path
export ANDROID_HOME=/home/<user>/Android/Sdk

# Build debug APK
cd android/
./gradlew assembleDebug
```

---

## HTTPS & Certificates

To use HTTPS, you need a **PKCS12 (.p12)** certificate file. You can generate a self-signed one for testing using OpenSSL:

```bash
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365 -nodes
openssl pkcs12 -export -out server_cert.p12 -inkey key.pem -in cert.pem
```

1. Transfer `server_cert.p12` to your Android device.
2. In the app, go to **Security & Settings** -> **HTTPS section**.
3. Tap **Import** (Upload Icon) and select your file.
4. Enter the password you set during the export step.
5. Enable **HTTPS Toggle** and restart the server.

---

## Project Structure

```
android/
├── app/
│   ├── build.gradle                  # Dependencies (ZXing, Compose, Material3)
│   └── src/main/
│       ├── AndroidManifest.xml       # Permissions + Activity + Service
│       └── java/com/saheermk/sharefile/
│           ├── MainActivity.kt       # Main UI — Neumorphic dashboard
│           ├── KeepAliveService.kt   # Foreground server service
│           ├── FolderPicker.kt       # Custom in-app folder picker
│           ├── FileServer.java       # HTTP server engine (Java Sockets)
│           └── WebInterface.java     # Modern HTML/JS generator
├── gradle.properties                 # JVM heap settings
└── gradle/wrapper/                   # Gradle wrapper
```

---

## Permissions Used

| Permission                | Purpose                           |
| ------------------------- | --------------------------------- |
| `INTERNET`                | Run the HTTP server               |
| `MANAGE_EXTERNAL_STORAGE` | Browse all folders (Android 11+)  |
| `READ_EXTERNAL_STORAGE`   | Read files (Android ≤10)          |
| `WRITE_EXTERNAL_STORAGE`  | Accept file uploads (Android ≤10) |
| `ACCESS_WIFI_STATE`       | Detect local IP address           |
| `FOREGROUND_SERVICE`      | Keep server running in background |
| `CAMERA`                  | For the built-in QR scanner       |
