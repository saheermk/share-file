# SHTTPS — Android File Server Clone

## About the App

A faithful, modern clone of **SHTTPS 3.0.4** for Android. This application allows you to turn your Android device into a local HTTP file server. You can share files over your local Wi-Fi network effortlessly, enabling any device with a web browser to view, download, and upload files to your Android device without needing any cables, Bluetooth, or client software.

Built from the ground up using pure native Android technologies (Jetpack Compose, Kotlin, and raw Java sockets), it delivers maximum performance and unrestricted filesystem access via `MANAGE_EXTERNAL_STORAGE` on Android 11+.

---

| Feature                   | Description                                       |
| ------------------------- | ------------------------------------------------- |
| **Custom Folder Picker**  | In-app filesystem browser — no system picker      |
| **Web Directory Browser** | Navigate folders from any browser on your network |
| **File Download**         | Direct download with size and date info           |
| **File Upload**           | Upload files from the browser to any folder       |
| **Configurable Port**     | Set any port, remembered across restarts          |
| **QR Code Generator**     | Scan to open the server URL instantly             |
| **QR Code Scanner**       | Built-in scanner to connect to other devices      |
| **Keep-Alive Service**    | Server stays running when app is in background    |
| **Activity Log**          | Real-time log of all server requests              |
| **Full Storage Access**   | `MANAGE_EXTERNAL_STORAGE` — share any folder      |
| **Modern Dark Mode**      | Refined neumorphic UI, automatically themed       |

---

## Contributing

If you want to contribute to this project, visit the official repository:
[https://github.com/saheermk/share-file](https://github.com/saheermk/share-file)

---

## How to Use

1. Install the APK on your Android device.
2. Grant **"All Files Access"** when prompted (required to share any folder).
3. Tap **Browse...** to pick a folder, or tap **Internal** to share root storage.
4. Set a port (default: `8080`) and tap **START SERVER**.
5. Open `http://YOUR_IP:PORT` in a browser on any device on the same Wi-Fi.
6. Browse, download, and upload files freely.

---

## Installation

### Option 1: Install Pre-built APK (Easiest)

If you simply want to use the app, install the compiled `.apk` file:

1. Download or transfer the `app-debug.apk` file to your Android phone.
2. Tap the APK file in your phone's file manager to install it.
3. If prompted, allow "Install from unknown sources" in your Android settings.

### Option 2: Install via ADB (For Developers)

If your device is connected to your computer via USB with USB Debugging enabled, run:

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

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

---

## Project Structure

```
android/
├── app/
│   ├── build.gradle                  # Dependencies (ZXing, Compose, Material3)
│   └── src/main/
│       ├── AndroidManifest.xml       # Permissions + Activity
│       └── java/com/saheer/fileshare/
│           ├── MainActivity.kt       # Main UI — server control, QR, logs
│           ├── FolderPicker.kt       # Custom in-app folder browser
│           ├── FileServer.java       # HTTP server engine
│           └── WebInterface.java     # HTML directory listing generator
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
