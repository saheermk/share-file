package com.saheermk.sharefile;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pure-Java HTTP file server — SHTTPS clone core.
 * Handles directory listing, file download, and file upload.
 */
public class FileServer {
    private static final String TAG = "FileServer";
    public static final List<String> serverLogs = new CopyOnWriteArrayList<>();

    public static void log(String msg) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        serverLogs.add("[" + time + "] " + msg);
        if (serverLogs.size() > 2000)
            serverLogs.remove(0);
    }

    private final int port;
    private final Context context;
    private File rootDir;
    private final OnServerListener listener;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private ExecutorService pool;

    // Advanced Settings
    private String password = "";
    private boolean passwordEnabled = false;
    private boolean allowModifications = true;
    private boolean allowPreviews = true;
    private String selectedInterface = "0.0.0.0";
    private final Set<String> sessions = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> blockedIps = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, Boolean> clientModifications = new ConcurrentHashMap<>();
    private final Map<String, Boolean> clientPreviews = new ConcurrentHashMap<>();
    private volatile long lastActivityTime = System.currentTimeMillis();

    // TLS Settings
    private boolean useHttps = false;
    private String certPath = null;
    private String certPassword = null;

    // Strict Mode Settings
    private boolean strictMode = false;
    private final Set<String> allowedIps = new HashSet<>();

    public interface OnServerListener {
        void onStarted(String ip, int port);

        void onStopped();

        void onError(String msg);

        void onLog(String msg);

        void onClientActive(String ip, String userAgent);

        void onTelemetry(String ip, int battery, boolean isCharging, String model, String platform);
    }

    public FileServer(int port, File rootDir, Context context, OnServerListener listener) {
        this.port = port;
        this.rootDir = rootDir;
        this.context = context;
        this.listener = listener;
        updateActivity();
    }

    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void setSecurity(boolean enabled, String pwd) {
        this.passwordEnabled = enabled;
        this.password = pwd;
    }

    public void setToggles(boolean allowMod, boolean allowPrev) {
        this.allowModifications = allowMod;
        this.allowPreviews = allowPrev;
    }

    private boolean isBlocked(String ip) {
        return blockedIps.contains(ip);
    }

    public void setInterface(String ip) {
        this.selectedInterface = ip;
    }

    public void setBlocked(String ip, boolean blocked) {
        if (blocked)
            blockedIps.add(ip);
        else
            blockedIps.remove(ip);
    }

    public void setClientPermission(String ip, String type, Boolean allowed) {
        if (allowed == null) {
            if ("mod".equals(type))
                clientModifications.remove(ip);
            else if ("prev".equals(type))
                clientPreviews.remove(ip);
        } else {
            if ("mod".equals(type))
                clientModifications.put(ip, allowed);
            else if ("prev".equals(type))
                clientPreviews.put(ip, allowed);
        }
    }

    private boolean isModAllowed(String ip) {
        return clientModifications.getOrDefault(ip, allowModifications);
    }

    private boolean isPrevAllowed(String ip) {
        return clientPreviews.getOrDefault(ip, allowPreviews);
    }

    public void setRootDir(File dir) {
        this.rootDir = dir;
    }

    public void setTls(boolean enable, String path, String password) {
        this.useHttps = enable;
        this.certPath = path;
        this.certPassword = password;
    }

    public void setStrictMode(boolean enable, Set<String> allowedIps) {
        this.strictMode = enable;
        synchronized (this.allowedIps) {
            this.allowedIps.clear();
            this.allowedIps.addAll(allowedIps);
        }
    }

    public void start() {
        pool = Executors.newFixedThreadPool(8);
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getByName(selectedInterface);
                if (useHttps && certPath != null && certPassword != null) {
                    try {
                        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
                        java.io.FileInputStream fis = new java.io.FileInputStream(certPath);
                        keyStore.load(fis, certPassword.toCharArray());
                        fis.close();

                        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory
                                .getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
                        kmf.init(keyStore, certPassword.toCharArray());

                        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                        sslContext.init(kmf.getKeyManagers(), null, null);

                        serverSocket = sslContext.getServerSocketFactory().createServerSocket(port, 50, addr);
                    } catch (Exception e) {
                        if (listener != null)
                            listener.onError("TLS Init Error: " + e.getMessage());
                        return;
                    }
                } else {
                    serverSocket = new ServerSocket(port, 50, addr);
                }
                running = true;
                if (listener != null)
                    listener.onStarted(selectedInterface.equals("0.0.0.0") ? getLocalIp() : selectedInterface, port);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        pool.execute(() -> handle(client));
                    } catch (IOException e) {
                        if (running && listener != null)
                            listener.onError(e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (listener != null)
                    listener.onError("Cannot bind to port " + port + ": " + e.getMessage());
            }
        }, "SHTTPS-accept").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException ignored) {
        }
        if (pool != null)
            pool.shutdown();
        if (listener != null)
            listener.onStopped();
    }

    // ─── Request Handler ────────────────────────────────────────────────────

    private void handle(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();
        try (InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream()) {

            // Strict Mode Check
            if (strictMode) {
                boolean isAllowed;
                synchronized (allowedIps) {
                    isAllowed = allowedIps.contains(clientIp);
                }

                if (!isAllowed) {
                    log("STRICT: Blocked unauthorized IP: " + clientIp);
                    // Read request headers first to avoid socket reset
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                    String line = reader.readLine();
                    if (line != null) {
                        String html = WebInterface.buildApprovalPage(clientIp);
                        sendResponse(output, "200 OK", "text/html", html.getBytes());
                    }
                    return;
                }
            }

            if (isBlocked(clientIp)) {
                sendResponse(output, "403 Forbidden", "text/plain", "IP Blocked".getBytes());
                return;
            }

            InputStream rawIn = input;
            OutputStream rawOut = output;

            // Read request line
            String requestLine = readLine(rawIn);
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            // Read headers
            Map<String, String> headers = new LinkedHashMap<>();
            String hLine;
            while (!(hLine = readLine(rawIn)).isEmpty()) {
                int colon = hLine.indexOf(':');
                if (colon > 0) {
                    headers.put(hLine.substring(0, colon).trim().toLowerCase(),
                            hLine.substring(colon + 1).trim());
                }
            }

            String ip = socket.getInetAddress().getHostAddress();
            String ua = headers.get("user-agent");
            if (ua == null)
                ua = "Unknown Device";
            listener.onClientActive(ip, ua);

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String rawPath = parts[1];

            // Auth check
            String cookie = headers.getOrDefault("cookie", "");
            boolean authenticated = !passwordEnabled || isAuthorized(cookie);

            // Split path and query
            String path, query = "";
            int qIdx = rawPath.indexOf('?');
            if (qIdx >= 0) {
                path = URLDecoder.decode(rawPath.substring(0, qIdx), "UTF-8");
                query = rawPath.substring(qIdx + 1);
            } else {
                path = URLDecoder.decode(rawPath, "UTF-8");
            }

            if (listener != null)
                listener.onLog(method + " " + rawPath);
            log("REQUEST: " + method + " " + rawPath);
            updateActivity();

            // Special route: login
            if (path.equals("/login")) {
                if ("GET".equals(method)) {
                    if (authenticated) {
                        sendRedirect(rawOut, "/");
                        return;
                    }
                    sendHtml(rawOut, WebInterface.buildLoginPage(null));
                } else if ("POST".equals(method)) {
                    handleLoginPost(rawOut, headers, rawIn);
                }
                return;
            }

            if (!authenticated) {
                sendRedirect(rawOut, "/login");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(rawOut, path, query, clientIp);
            } else if ("POST".equalsIgnoreCase(method)) {
                String ct = headers.getOrDefault("content-type", "");
                String lenStr = headers.getOrDefault("content-length", "0");
                int contentLength = Integer.parseInt(lenStr);
                if (path.equals("/telemetry")) {
                    handleTelemetry(rawOut, rawIn, contentLength, clientIp);
                } else {
                    handlePost(rawOut, path, query, ct, rawIn, contentLength, clientIp);
                }
            } else {
                sendError(rawOut, 405, "Method Not Allowed");
            }

        } catch (Exception e) {
            Log.e(TAG, "handle error", e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean isAuthorized(String cookie) {
        if (cookie == null || cookie.isEmpty())
            return false;
        for (String c : cookie.split(";")) {
            String[] kv = c.trim().split("=", 2);
            if (kv.length == 2 && "SHTTPS_SESSION".equals(kv[0])) {
                return sessions.contains(kv[1]);
            }
        }
        return false;
    }

    private void handleLoginPost(OutputStream out, Map<String, String> headers, InputStream in) throws IOException {
        int len = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        byte[] body = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(body, read, len - read);
            if (n < 0)
                break;
            read += n;
        }
        String bodyStr = new String(body);
        String passInput = getQueryParam(bodyStr, "password"); // getQueryParam works for body too if urlencoded

        if (password.equals(passInput)) {
            String sessionId = UUID.randomUUID().toString();
            sessions.add(sessionId);
            String resp = "HTTP/1.1 302 Found\r\n" +
                    "Location: /\r\n" +
                    "Set-Cookie: SHTTPS_SESSION=" + sessionId + "; Path=/; HttpOnly\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(resp.getBytes("UTF-8"));
            out.flush();
        } else {
            sendHtml(out, WebInterface.buildLoginPage("Invalid password. Please try again."));
        }
    }

    // ─── GET Handler ────────────────────────────────────────────────────────

    private void handleGet(OutputStream out, String path, String query, String clientIp) throws IOException {
        if (path.startsWith("/assets/")) {
            serveAsset(out, path.substring(8));
            return;
        }

        if (path.equals("/favicon.ico")) {
            sendError(out, 404, "Not Found");
            return;
        }

        if (path.equals("/logs")) {
            handleLogs(out);
            return;
        }

        if (path.equals("/check_auth")) {
            boolean isAllowed;
            synchronized (allowedIps) {
                isAllowed = allowedIps.contains(clientIp);
            }
            String status = isAllowed ? "authorized" : "pending";
            sendResponse(out, "200 OK", "text/plain", status.getBytes("UTF-8"));
            return;
        }

        // --- New Routing ---
        if (path.equals("/")) {
            sendHtml(out, WebInterface.buildLandingPage());
            return;
        }

        if (path.equals("/apps")) {
            sendHtml(out, WebInterface.buildAppsListing(getInstalledApps()));
            return;
        }

        if (path.equals("/download_app")) {
            handleAppDownload(out, query);
            return;
        }

        if (path.equals("/logo.png")) {
            handleAppIcon(out, "pkg=" + context.getPackageName() + "&circular=true");
            return;
        }

        if (path.equals("/app_icon")) {
            handleAppIcon(out, query);
            return;
        }

        // --- File Operations ---
        if (path.equals("/mkdir")) {
            if (!allowModifications) {
                sendError(out, 403, "Modifications disabled");
                return;
            }
            handleMkdir(out, query);
            return;
        } else if (path.equals("/delete")) {
            if (!allowModifications) {
                sendError(out, 403, "Modifications disabled");
                return;
            }
            handleDelete(out, query);
            return;
        } else if (path.equals("/rename")) {
            if (!allowModifications) {
                sendError(out, 403, "Modifications disabled");
                return;
            }
            handleRename(out, query);
            return;
        } else if (path.equals("/copy") || path.equals("/cut")) {
            if (!allowModifications) {
                sendError(out, 403, "Modifications disabled");
                return;
            }
            handleClipboardAction(out, path, query);
            return;
        } else if (path.equals("/paste")) {
            if (!allowModifications) {
                sendError(out, 403, "Modifications disabled");
                return;
            }
            handlePaste(out, query);
            return;
        } else if (path.equals("/delete_multiple")) {
            if (!allowModifications) {
                sendError(out, 403, "Modifications disabled");
                return;
            }
            handleMultipleDelete(out, query);
            return;
        } else if (path.equals("/zip")) {
            handleZip(out, query);
            return;
        }

        // --- File Manager ---
        if (path.equals("/files") || path.equals("/download")) {
            String relPath = getQueryParam(query, "path");
            if (relPath == null)
                relPath = getQueryParam(query, "file"); // fallback for legacy /download
            if (relPath == null)
                relPath = "";
            if (relPath.startsWith("/"))
                relPath = relPath.substring(1);

            File target = relPath.isEmpty() ? rootDir : new File(rootDir, relPath);
            if (!isInsideRoot(target)) {
                sendError(out, 403, "Forbidden");
                return;
            }

            if (target.isDirectory() && path.equals("/files")) {
                sendHtml(out,
                        WebInterface.buildDirListing(target, rootDir, relPath, isModAllowed(clientIp),
                                isPrevAllowed(clientIp)));
            } else if (target.isFile()) {
                boolean forceDownload = "1".equals(getQueryParam(query, "dl"));
                streamFile(out, target, forceDownload);
            } else {
                sendError(out, 404, "Not Found");
            }
            return;
        }

        sendError(out, 404, "Not Found");
    }

    // ─── File Operations ─────────────────────────────────────────────────────

    private static class Clipboard {
        static File sourceFile = null;
        static boolean isCut = false;
    }

    private void handleMkdir(OutputStream out, String query) throws IOException {
        String parentPath = getQueryParam(query, "path");
        String name = getQueryParam(query, "name");
        if (parentPath == null)
            parentPath = "";
        if (name == null || name.isEmpty()) {
            sendError(out, 400, "Name required");
            return;
        }
        File parent = new File(rootDir, parentPath.startsWith("/") ? parentPath.substring(1) : parentPath);
        File newDir = new File(parent, name);
        if (isInsideRoot(newDir) && newDir.mkdirs()) {
            sendRedirect(out, "/files?path=" + WebInterface.urlEncode(parentPath));
        } else {
            sendError(out, 500, "Failed to create directory");
        }
    }

    private void handleDelete(OutputStream out, String query) throws IOException {
        String filePath = getQueryParam(query, "file");
        if (filePath == null) {
            sendError(out, 400, "File required");
            return;
        }
        File f = new File(rootDir, filePath.startsWith("/") ? filePath.substring(1) : filePath);
        if (isInsideRoot(f) && deleteRecursive(f)) {
            File parent = f.getParentFile();
            String parentRel = parent.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
            sendRedirect(out, "/files?path=" + WebInterface.urlEncode(parentRel.isEmpty() ? "/" : parentRel));
        } else {
            sendError(out, 500, "Failed to delete");
        }
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null)
                for (File k : kids)
                    deleteRecursive(k);
        }
        return f.delete();
    }

    private void handleMultipleDelete(OutputStream out, String query) throws IOException {
        String filesParam = getQueryParam(query, "files");
        String parentPath = getQueryParam(query, "path");
        if (filesParam == null || filesParam.isEmpty()) {
            sendError(out, 400, "Files required");
            return;
        }
        if (parentPath == null)
            parentPath = "";

        String[] filePaths = filesParam.split(",");
        for (String p : filePaths) {
            File f = new File(rootDir, p.startsWith("/") ? p.substring(1) : p);
            if (isInsideRoot(f)) {
                deleteRecursive(f);
            }
        }
        sendRedirect(out, "/files?path=" + WebInterface.urlEncode(parentPath));
    }

    private void handleRename(OutputStream out, String query) throws IOException {
        String filePath = getQueryParam(query, "file");
        String newName = getQueryParam(query, "new");
        if (filePath == null || newName == null) {
            sendError(out, 400, "Params missing");
            return;
        }
        File f = new File(rootDir, filePath.startsWith("/") ? filePath.substring(1) : filePath);
        File dest = new File(f.getParentFile(), newName);
        if (isInsideRoot(f) && isInsideRoot(dest) && f.renameTo(dest)) {
            File parent = f.getParentFile();
            String parentRel = parent.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
            sendRedirect(out, "/files?path=" + WebInterface.urlEncode(parentRel.isEmpty() ? "/" : parentRel));
        } else {
            sendError(out, 500, "Rename failed");
        }
    }

    private void handleClipboardAction(OutputStream out, String action, String query) throws IOException {
        String filePath = getQueryParam(query, "file");
        if (filePath == null) {
            sendError(out, 400, "File missing");
            return;
        }
        File f = new File(rootDir, filePath.startsWith("/") ? filePath.substring(1) : filePath);
        if (isInsideRoot(f)) {
            Clipboard.sourceFile = f;
            Clipboard.isCut = action.equals("/cut");
            File parent = f.getParentFile();
            String parentRel = parent.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
            sendRedirect(out, "/files?path=" + WebInterface.urlEncode(parentRel.isEmpty() ? "/" : parentRel));
        } else {
            sendError(out, 403, "Forbidden");
        }
    }

    private void handlePaste(OutputStream out, String query) throws IOException {
        String destPath = getQueryParam(query, "to");
        if (destPath == null)
            destPath = "";
        if (Clipboard.sourceFile == null) {
            sendError(out, 400, "Nothing to paste");
            return;
        }
        File destDir = new File(rootDir, destPath.startsWith("/") ? destPath.substring(1) : destPath);
        File target = new File(destDir, Clipboard.sourceFile.getName());

        if (isInsideRoot(destDir) && isInsideRoot(target)) {
            try {
                if (Clipboard.isCut) {
                    if (Clipboard.sourceFile.renameTo(target)) {
                        Clipboard.sourceFile = null;
                    } else {
                        throw new IOException("Move failed");
                    }
                } else {
                    copyRecursive(Clipboard.sourceFile, target);
                }
                sendRedirect(out, "/files?path=" + WebInterface.urlEncode(destPath));
            } catch (IOException e) {
                sendError(out, 500, "Paste failed: " + e.getMessage());
            }
        } else {
            sendError(out, 403, "Forbidden");
        }
    }

    private void handleZip(OutputStream out, String query) throws IOException {
        String filesParam = getQueryParam(query, "files");
        if (filesParam == null || filesParam.isEmpty()) {
            sendError(out, 400, "Files required");
            return;
        }
        String[] filePaths = filesParam.split(",");

        out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
        out.write("Content-Type: application/zip\r\n".getBytes("UTF-8"));
        out.write("Content-Disposition: attachment; filename=\"download.zip\"\r\n".getBytes("UTF-8"));
        out.write("Connection: close\r\n\r\n".getBytes("UTF-8"));
        out.flush();

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (String p : filePaths) {
                File f = new File(rootDir, p.startsWith("/") ? p.substring(1) : p);
                if (isInsideRoot(f)) {
                    zipRecursive(f, f.getName(), zos);
                }
            }
        }
    }

    private void handleLogs(OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Server Logs</title>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<style>")
                .append("body { font-family: monospace; background: #0d1117; color: #00ff00; padding: 20px; line-height: 1.5; }")
                .append(".btn { padding: 10px 20px; background: #21262d; color: #fff; text-decoration: none; border-radius: 8px; border: 1px solid #30363d; display: inline-block; margin-bottom: 20px; font-family: sans-serif; font-size: 14px; }")
                .append(".log-entry { border-bottom: 1px solid #21262d; padding: 4px 0; word-wrap: break-word; }")
                .append("</style></head><body>")
                .append("<a href='/' class='btn'>&larr; Back to Home</a>")
                .append("<h2><span style='color:#fff'>HTTP Server Logs</span></h2><hr style='border-color:#30363d'>");

        for (int i = serverLogs.size() - 1; i >= 0; i--) {
            sb.append("<div class='log-entry'>").append(WebInterface.escapeHtml(serverLogs.get(i))).append("</div>");
        }
        sb.append("</body></html>");
        sendHtml(out, sb.toString());
    }

    private void zipRecursive(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden())
            return;
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(fileName));
                zos.closeEntry();
            } else {
                zos.putNextEntry(new ZipEntry(fileName + "/"));
                zos.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipRecursive(childFile, fileName + "/" + childFile.getName(), zos);
                }
            }
            return;
        }
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[65536];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
        }
    }

    private void copyRecursive(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.mkdirs())
                throw new IOException("Cannot create dir: " + dest);
            File[] kids = src.listFiles();
            if (kids != null)
                for (File k : kids)
                    copyRecursive(k, new File(dest, k.getName()));
        } else {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1)
                    out.write(buf, 0, n);
            }
        }
    }

    // ─── POST Handler (file upload) ──────────────────────────────────────────

    private void handlePost(OutputStream out, String path, String query,
            String contentType, InputStream body, int contentLength, String clientIp) throws IOException {
        if (!isModAllowed(clientIp)) {
            sendError(out, 403, "Modifications disabled for this client");
            return;
        }
        if (!contentType.startsWith("multipart/form-data")) {
            sendError(out, 400, "Expected multipart");
            return;
        }

        // Parse boundary
        String boundary = null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                boundary = part.substring("boundary=".length()).trim();
                break;
            }
        }
        if (boundary == null) {
            sendError(out, 400, "No boundary");
            return;
        }

        // Destination directory
        if (!allowModifications) {
            sendError(out, 403, "Modifications disabled");
            return;
        }
        String relPath = getQueryParam(query, "path");
        if (relPath == null)
            relPath = "";
        if (relPath.startsWith("/"))
            relPath = relPath.substring(1);
        File destDir = relPath.isEmpty() ? rootDir : new File(rootDir, relPath);
        if (!destDir.isDirectory() || !isInsideRoot(destDir)) {
            sendError(out, 403, "Forbidden");
            return;
        }

        // Read body bytes
        byte[] bodyBytes = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = body.read(bodyBytes, read, contentLength - read);
            if (n < 0)
                break;
            read += n;
        }

        // Parse multipart — find filename and data
        String boundaryLine = "--" + boundary;
        String bodyStr = new String(bodyBytes, "ISO-8859-1");
        // Simple approach: split on boundaries
        int dataStart = bodyStr.indexOf("\r\n\r\n");
        if (dataStart < 0) {
            sendError(out, 400, "Bad multipart");
            return;
        }

        // Extract filename from Content-Disposition
        String filename = "upload_" + System.currentTimeMillis();
        int fnIdx = bodyStr.indexOf("filename=\"");
        if (fnIdx >= 0) {
            int fnEnd = bodyStr.indexOf("\"", fnIdx + 10);
            if (fnEnd > fnIdx)
                filename = bodyStr.substring(fnIdx + 10, fnEnd);
        }

        // Data starts after header block
        int bodyDataStart = dataStart + 4;
        // Data ends before trailing boundary
        String endBoundary = "\r\n--" + boundary;
        int bodyDataEnd = bodyStr.indexOf(endBoundary, bodyDataStart);
        if (bodyDataEnd < 0)
            bodyDataEnd = bodyBytes.length;

        File outFile = new File(destDir, filename);
        FileOutputStream fos = new FileOutputStream(outFile);
        // Write raw bytes to preserve binary data
        fos.write(bodyBytes, bodyDataStart, bodyDataEnd - bodyDataStart);
        fos.close();

        if (listener != null)
            listener.onLog("Uploaded: " + filename + " (" + (bodyDataEnd - bodyDataStart) + " bytes)");

        // Redirect back to folder
        String redirect = "/files?path=" + (relPath.isEmpty() ? "" : "/" + relPath);
        sendRedirect(out, redirect);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void streamFile(OutputStream out, File f, boolean forceDownload) throws IOException {
        String mime = URLConnection.guessContentTypeFromName(f.getName());
        if (mime == null)
            mime = "application/octet-stream";

        String disposition = (forceDownload || !allowPreviews) ? "attachment" : "inline";
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(mime).append("\r\n")
                .append("Content-Length: ").append(f.length()).append("\r\n")
                .append("Content-Disposition: ").append(disposition).append("; filename=\"").append(f.getName())
                .append("\"\r\n")
                .append("Connection: close\r\n\r\n");

        out.write(sb.toString().getBytes("UTF-8"));
        log("RESPONSE: 200 OK (Streamed " + f.getName() + " as " + disposition + ")");

        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = fis.read(buf)) != -1)
                out.write(buf, 0, n);
        }
        out.flush();
    }

    private void sendHtml(OutputStream out, String html) throws IOException {
        byte[] bytes = html.getBytes("UTF-8");
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
        log("RESPONSE: 200 OK (HTML)");
    }

    private void sendError(OutputStream out, int code, String msg) {
        try {
            String resp = "HTTP/1.1 " + code + " " + msg + "\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + msg.length() + "\r\n" +
                    "Connection: close\r\n\r\n" +
                    msg;
            out.write(resp.getBytes("UTF-8"));
            out.flush();
            log("RESPONSE: " + code + " " + msg);
        } catch (Exception ignored) {
        }
    }

    private void sendRedirect(OutputStream out, String location) {
        try {
            String resp = "HTTP/1.1 302 Found\r\n" +
                    "Location: " + location + "\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(resp.getBytes("UTF-8"));
            out.flush();
            log("RESPONSE: 302 Redirect to " + location);
        } catch (Exception ignored) {
        }
    }

    private void sendResponse(OutputStream out, String status, String contentType, byte[] data) throws IOException {
        String header = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(data);
        out.flush();
    }

    private boolean isInsideRoot(File f) {
        try {
            return f.getCanonicalPath().startsWith(rootDir.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private String getQueryParam(String query, String key) {
        if (query == null || query.isEmpty())
            return null;
        for (String p : query.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) {
                try {
                    if (kv[0].equals(key))
                        return URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n')
                break;
            if (b != '\r')
                sb.append((char) b);
        }
        return sb.toString();
    }

    private void serveAsset(OutputStream out, String assetPath) throws IOException {
        String fullPath = "web/" + assetPath;
        try (InputStream is = context.getAssets().open(fullPath)) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int n;
            while ((n = is.read(buffer)) != -1)
                baos.write(buffer, 0, n);
            byte[] bytes = baos.toByteArray();

            String mime = URLConnection.guessContentTypeFromName(assetPath);
            if (mime == null) {
                if (assetPath.endsWith(".css"))
                    mime = "text/css";
                else if (assetPath.endsWith(".js"))
                    mime = "application/javascript";
                else if (assetPath.endsWith(".woff2"))
                    mime = "font/woff2";
                else if (assetPath.endsWith(".ttf"))
                    mime = "font/ttf";
                else
                    mime = "application/octet-stream";
            }

            PrintWriter w = new PrintWriter(out);
            w.print("HTTP/1.1 200 OK\r\n");
            w.print("Content-Type: " + mime + "\r\n");
            w.print("Content-Length: " + bytes.length + "\r\n");
            w.print("Connection: close\r\n\r\n");
            w.flush();
            out.write(bytes);
            out.flush();
            log("ASSET: " + assetPath + " (" + bytes.length + " bytes)");
        } catch (IOException e) {
            sendError(out, 404, "Asset Not Found: " + assetPath);
        }
    }

    public static String getLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface ni = en.nextElement();
                for (Enumeration<InetAddress> addr = ni.getInetAddresses(); addr.hasMoreElements();) {
                    InetAddress ia = addr.nextElement();
                    if (!ia.isLoopbackAddress() && ia instanceof Inet4Address)
                        return ia.getHostAddress();
                }
            }
        } catch (SocketException ignored) {
        }
        return "127.0.0.1";
    }

    public static class AppItem {
        public String name;
        public String packageName;
        public long size;
        public String apkPath;
    }

    private List<AppItem> getInstalledApps() {
        List<AppItem> list = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            // Only show apps that have a launcher intent (user-facing)
            if (pm.getLaunchIntentForPackage(app.packageName) == null)
                continue;
            AppItem item = new AppItem();
            item.name = app.loadLabel(pm).toString();
            item.packageName = app.packageName;
            item.apkPath = app.sourceDir;
            item.size = new File(app.sourceDir).length();
            list.add(item);
        }
        Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return list;
    }

    private void handleAppDownload(OutputStream out, String query) throws IOException {
        String pkg = getQueryParam(query, "pkg");
        if (pkg == null) {
            sendError(out, 400, "Missing package name");
            return;
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
            File apkFile = new File(app.sourceDir);
            if (apkFile.exists()) {
                streamFile(out, apkFile, true);
            } else {
                sendError(out, 404, "APK not found");
            }
        } catch (PackageManager.NameNotFoundException e) {
            sendError(out, 404, "App not found");
        }
    }

    private void handleAppIcon(OutputStream out, String query) throws IOException {
        String pkg = getQueryParam(query, "pkg");
        boolean circular = "true".equals(getQueryParam(query, "circular"));
        if (pkg == null) {
            sendError(out, 400, "Missing package name");
            return;
        }
        try {
            PackageManager pm = context.getPackageManager();
            Drawable icon = pm.getApplicationIcon(pkg);
            Bitmap bitmap;
            if (icon instanceof BitmapDrawable && !circular) {
                bitmap = ((BitmapDrawable) icon).getBitmap();
            } else {
                int w = icon.getIntrinsicWidth() > 0 ? icon.getIntrinsicWidth() : 128;
                int h = icon.getIntrinsicHeight() > 0 ? icon.getIntrinsicHeight() : 128;
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);

                if (circular) {
                    Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvasMask = new Canvas(output);
                    final Paint paint = new Paint();
                    final Rect rect = new Rect(0, 0, w, h);
                    paint.setAntiAlias(true);
                    canvasMask.drawARGB(0, 0, 0, 0);
                    canvasMask.drawCircle(w / 2f, h / 2f, w / 2f, paint);
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                    canvasMask.drawBitmap(bitmap, rect, rect, paint);
                    bitmap = output;
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] bytes = baos.toByteArray();

            PrintWriter w = new PrintWriter(out);
            w.print("HTTP/1.1 200 OK\r\n");
            w.print("Content-Type: image/png\r\n");
            w.print("Content-Length: " + bytes.length + "\r\n");
            w.print("Cache-Control: public, max-age=86400\r\n");
            w.print("Connection: close\r\n\r\n");
            w.flush();
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            sendError(out, 404, "Icon not found");
        }
    }

    private void handleTelemetry(OutputStream out, InputStream in, int len, String ip) throws IOException {
        byte[] bodyData = new byte[len];
        int totalRead = 0;
        while (totalRead < len) {
            int r = in.read(bodyData, totalRead, len - totalRead);
            if (r == -1)
                break;
            totalRead += r;
        }
        String json = new String(bodyData, "UTF-8");
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            int battery = obj.optInt("batteryLevel", -1);
            boolean charging = obj.optBoolean("isCharging", false);
            String model = obj.optString("model", "Unknown");
            String platform = obj.optString("platform", "Unknown");

            if (listener != null) {
                listener.onTelemetry(ip, battery, charging, model, platform);
            }
            out.write("HTTP/1.1 204 No Content\r\n".getBytes("UTF-8"));
            out.write("Connection: close\r\n\r\n".getBytes("UTF-8"));
        } catch (Exception e) {
            sendError(out, 400, "Bad Request");
        }
    }
}
