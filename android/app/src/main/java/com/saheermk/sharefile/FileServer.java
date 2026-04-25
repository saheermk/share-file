package com.saheermk.sharefile;

import android.content.Context;
import android.net.Uri;
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
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

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

    // Strict Mode Settings
    private boolean strictMode = false;
    private final Set<String> allowedIps = new HashSet<>();

    // Connection Limiting
    private int maxConnections = 0; // 0 = unlimited
    private final AtomicInteger activeConnections = new AtomicInteger(0);

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

    public void setStrictMode(boolean enable, Set<String> allowedIps) {
        this.strictMode = enable;
        synchronized (this.allowedIps) {
            this.allowedIps.clear();
            this.allowedIps.addAll(allowedIps);
        }
    }

    public void setMaxConnections(int max) {
        this.maxConnections = max;
    }

    public void start() {
        pool = Executors.newFixedThreadPool(8);
        new Thread(() -> {
            int currentPort = port;
            int attempts = 0;
            boolean success = false;

            while (attempts < 10 && !success) {
                try {
                    InetAddress addr = InetAddress.getByName(selectedInterface);
                    serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(addr, currentPort), 50);
                    success = true;
                } catch (java.net.BindException e) {
                    log("Port " + currentPort + " busy, trying next...");
                    currentPort++;
                    attempts++;
                } catch (Exception e) {
                    if (listener != null)
                        listener.onError("Server Init Error: " + e.getMessage());
                    return;
                }
            }

            if (!success) {
                if (listener != null)
                    listener.onError("Cannot bind to any port after 10 attempts.");
                return;
            }

            try {
                running = true;
                if (listener != null)
                    listener.onStarted(selectedInterface.equals("0.0.0.0") ? getLocalIp() : selectedInterface,
                            currentPort);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        pool.execute(() -> handle(client));
                    } catch (IOException e) {
                        if (running && listener != null)
                            listener.onError(e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (listener != null)
                    listener.onError("Runtime Error: " + e.getMessage());
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
        int currentActive = activeConnections.incrementAndGet();
        String clientIp = socket.getInetAddress().getHostAddress();
        try (InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream()) {

            // Connection Limit Check
            if (maxConnections > 0 && currentActive > maxConnections) {
                log("LIMIT: Rejected connection from " + clientIp + " (Limit: " + maxConnections + ")");
                sendError(output, 503, "Server Busy: Maximum connection limit reached.");
                return;
            }

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
                handleGet(rawOut, path, query, clientIp, headers);
            } else if ("POST".equalsIgnoreCase(method)) {
                String ct = headers.getOrDefault("content-type", "");
                String lenStr = headers.getOrDefault("content-length", "0");
                long contentLength = Long.parseLong(lenStr);
                if (path.equals("/telemetry")) {
                    handleTelemetry(rawOut, rawIn, (int) Math.min(contentLength, Integer.MAX_VALUE), clientIp);
                } else {
                    handlePost(rawOut, path, query, ct, rawIn, contentLength, clientIp);
                }
            } else {
                sendError(rawOut, 405, "Method Not Allowed");
            }

        } catch (Exception e) {
            Log.e(TAG, "handle error", e);
        } finally {
            activeConnections.decrementAndGet();
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

    private void handleGet(OutputStream out, String path, String query, String clientIp, Map<String, String> headers)
            throws IOException {
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

        if (path.startsWith("/api/")) {
            handleApi(out, path, query, clientIp);
            return;
        }

        // --- New Routing ---
        if (path.equals("/") || path.equals("/files") || path.equals("/apps") || path.equals("/gallery")) {
            sendHtml(out, WebInterface.buildSpaShell());
            return;
        }

        if (path.equals("/download_app")) {
            handleAppDownload(out, query, headers);
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
        if (path.equals("/download")) {
            String relPath = getQueryParam(query, "path");
            if (relPath == null)
                relPath = getQueryParam(query, "file");
            if (relPath == null)
                relPath = "";
            if (relPath.startsWith("/"))
                relPath = relPath.substring(1);

            File target = relPath.isEmpty() ? rootDir : new File(rootDir, relPath);
            if (!isInsideRoot(target)) {
                sendError(out, 403, "Forbidden");
                return;
            }

            if (target.isFile()) {
                boolean forceDownload = "1".equals(getQueryParam(query, "dl"));
                streamFile(out, target, forceDownload, headers);
            } else {
                sendError(out, 404, "Not Found");
            }
            return;
        }

        if (path.equals("/thumb")) {
            handleThumb(out, query);
            return;
        }

        sendError(out, 404, "Not Found");
    }

    private void handleApi(OutputStream out, String path, String query, String clientIp) throws IOException {
        try {
            if (path.equals("/api/status")) {
                JSONObject status = new JSONObject();
                status.put("running", running);
                status.put("version", "3.0.6");
                status.put("strictMode", strictMode);
                status.put("allowModifications", allowModifications);
                status.put("hasClipboard", Clipboard.sourceFile != null);
                if (Clipboard.sourceFile != null) {
                    status.put("clipboardName", Clipboard.sourceFile.getName());
                    status.put("isCut", Clipboard.isCut);
                }
                JSONObject auth = new JSONObject();
                auth.put("passwordEnabled", passwordEnabled);
                status.put("auth", auth);
                sendJson(out, status);

            } else if (path.equals("/api/files")) {
                String relPath = getQueryParam(query, "path");
                if (relPath == null)
                    relPath = "";
                if (relPath.startsWith("/"))
                    relPath = relPath.substring(1);
                File dir = relPath.isEmpty() ? rootDir : new File(rootDir, relPath);
                if (!isInsideRoot(dir) || !dir.isDirectory()) {
                    sendError(out, 403, "Forbidden");
                    return;
                }
                JSONArray arr = new JSONArray();
                File[] kids = dir.listFiles();
                int limit = Integer.parseInt(getQueryParam(query, "limit", "-1"));
                int offset = Integer.parseInt(getQueryParam(query, "offset", "0"));
                if (kids != null) {
                    // Sort: directories first, then alphabetical
                    Arrays.sort(kids, (a, b) -> {
                        if (a.isDirectory() && !b.isDirectory())
                            return -1;
                        if (!a.isDirectory() && b.isDirectory())
                            return 1;
                        return a.getName().compareToIgnoreCase(b.getName());
                    });
                    int total = kids.length;
                    int start = Math.min(offset, total);
                    int end = limit > 0 ? Math.min(start + limit, total) : total;
                    for (int i = start; i < end; i++) {
                        File f = kids[i];
                        JSONObject obj = new JSONObject();
                        obj.put("name", f.getName());
                        obj.put("isDir", f.isDirectory());
                        obj.put("size", f.length());
                        obj.put("lastModified", f.lastModified());

                        if (!f.isDirectory()) {
                            String n = f.getName().toLowerCase();
                            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif")
                                    || n.endsWith(".webp")) {
                                obj.put("mediaType", "image");
                            } else if (n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv")
                                    || n.endsWith(".avi") || n.endsWith(".mov")) {
                                obj.put("mediaType", "video");
                            }
                        }

                        String fullPath = f.getCanonicalPath();
                        String rootPath = rootDir.getCanonicalPath();
                        String relPathRes = fullPath.length() > rootPath.length()
                                ? fullPath.substring(rootPath.length())
                                : "";
                        if (relPathRes.startsWith("/"))
                            relPathRes = relPathRes.substring(1);
                        obj.put("path", relPathRes);

                        String dirPath = f.getParentFile().getCanonicalPath();
                        String relDirPath = dirPath.length() > rootPath.length()
                                ? dirPath.substring(rootPath.length())
                                : "";
                        if (relDirPath.startsWith("/"))
                            relDirPath = relDirPath.substring(1);
                        obj.put("dir", relDirPath);

                        arr.put(obj);
                    }
                }
                sendJson(out, arr);
            } else if (path.equals("/api/gallery")) {
                int limit = Integer.parseInt(getQueryParam(query, "limit", "-1"));
                int offset = Integer.parseInt(getQueryParam(query, "offset", "0"));
                List<File> allMedia = new ArrayList<>();

                // Try MediaStore first (Instant)
                getGalleryFromMediaStore(allMedia);

                // Fallback to Scan if empty (maybe files not indexed yet or root outside media
                // volume)
                if (allMedia.isEmpty()) {
                    getGalleryFiles(rootDir, allMedia);
                }

                // Sort by lastModified desc
                Collections.sort(allMedia, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                int total = allMedia.size();
                int start = Math.min(offset, total);
                int end = limit > 0 ? Math.min(start + limit, total) : total;
                List<File> subList = allMedia.subList(start, end);

                JSONArray arr = new JSONArray();
                for (File f : subList) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("name", f.getName());
                        obj.put("isDir", false);
                        obj.put("size", f.length());
                        obj.put("lastModified", f.lastModified());

                        String name = f.getName().toLowerCase();
                        String mediaType = "file";
                        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                                || name.endsWith(".gif")
                                || name.endsWith(".webp"))
                            mediaType = "image";
                        else if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mkv")
                                || name.endsWith(".avi") || name.endsWith(".mov"))
                            mediaType = "video";
                        obj.put("mediaType", mediaType);

                        String fullPath = f.getCanonicalPath();
                        String rootPath = rootDir.getCanonicalPath();
                        String relPath = fullPath.length() > rootPath.length() ? fullPath.substring(rootPath.length())
                                : "";
                        if (relPath.startsWith("/"))
                            relPath = relPath.substring(1);
                        obj.put("path", relPath);

                        String dirPath = f.getParentFile().getCanonicalPath();
                        String relDirPath = dirPath.length() > rootPath.length() ? dirPath.substring(rootPath.length())
                                : "";
                        if (relDirPath.startsWith("/"))
                            relDirPath = relDirPath.substring(1);
                        obj.put("dir", relDirPath);

                        arr.put(obj);
                    } catch (Exception ignored) {
                    }
                }
                sendJson(out, arr);
            } else if (path.equals("/api/apps")) {
                JSONArray arr = new JSONArray();
                for (AppItem app : getInstalledApps()) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", app.name);
                    obj.put("packageName", app.packageName);
                    obj.put("size", app.size);
                    arr.put(obj);
                }
                sendJson(out, arr);
            } else {
                sendError(out, 404, "API Not Found");
            }
        } catch (Exception e) {
            sendError(out, 500, "API Error: " + e.getMessage());
        }
    }

    private void sendJson(OutputStream out, Object json) throws IOException {
        byte[] data = json.toString().getBytes("UTF-8");
        out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
        out.write("Content-Type: application/json\r\n".getBytes("UTF-8"));
        out.write(("Content-Length: " + data.length + "\r\n").getBytes("UTF-8"));
        out.write("Access-Control-Allow-Origin: *\r\n".getBytes("UTF-8"));
        out.write("Connection: close\r\n\r\n".getBytes("UTF-8"));
        out.write(data);
        out.flush();
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
            try {
                JSONObject res = new JSONObject();
                res.put("success", true);
                sendJson(out, res);
            } catch (Exception e) {
                sendError(out, 500, e.getMessage());
            }
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
            try {
                JSONObject res = new JSONObject();
                res.put("success", true);
                sendJson(out, res);
            } catch (Exception e) {
                sendError(out, 500, e.getMessage());
            }
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
        try {
            JSONObject res = new JSONObject();
            res.put("success", true);
            sendJson(out, res);
        } catch (Exception e) {
            sendError(out, 500, e.getMessage());
        }
    }

    private void handleRename(OutputStream out, String query) throws IOException {
        String filePath = getQueryParam(query, "file");
        String newName = getQueryParam(query, "new");
        String conflict = getQueryParam(query, "conflict"); // override, auto
        if (filePath == null || newName == null) {
            sendError(out, 400, "Params missing");
            return;
        }
        File f = new File(rootDir, filePath.startsWith("/") ? filePath.substring(1) : filePath);
        File dest = new File(f.getParentFile(), newName);
        if ("auto".equals(conflict)) {
            dest = getUniqueFile(f.getParentFile(), newName);
        }
        if (isInsideRoot(f) && isInsideRoot(dest)) {
            if (dest.exists() && !"override".equals(conflict)) {
                sendError(out, 409, "File exists");
                return;
            }
            if (f.renameTo(dest)) {
                try {
                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    res.put("newName", dest.getName());
                    sendJson(out, res);
                } catch (Exception e) {
                    sendError(out, 500, e.getMessage());
                }
            } else {
                sendError(out, 500, "Rename failed");
            }
        } else {
            sendError(out, 403, "Forbidden");
        }
    }

    private void handleThumb(OutputStream out, String query) throws IOException {
        String filePath = getQueryParam(query, "file");
        if (filePath == null) {
            sendError(out, 400, "Missing file");
            return;
        }
        if (filePath.startsWith("/"))
            filePath = filePath.substring(1);
        File f = new File(rootDir, filePath);
        if (!isInsideRoot(f) || !f.exists()) {
            sendError(out, 404, "Not Found");
            return;
        }

        Bitmap thumb = null;
        String mime = "image/jpeg";
        String n = f.getName().toLowerCase();

        try {
            if (n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".avi")
                    || n.endsWith(".mov")) {
                thumb = ThumbnailUtils.createVideoThumbnail(f.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), options);
                options.inSampleSize = calculateInSampleSize(options, 200, 200);
                options.inJustDecodeBounds = false;
                thumb = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
            }

            if (thumb != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                thumb.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] bytes = baos.toByteArray();
                sendResponse(out, "200 OK", mime, bytes);
                thumb.recycle();
            } else {
                sendError(out, 500, "Failed to create thumbnail");
            }
        } catch (Exception e) {
            sendError(out, 500, e.getMessage());
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
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
            try {
                JSONObject res = new JSONObject();
                res.put("success", true);
                res.put("fileName", f.getName());
                res.put("isCut", Clipboard.isCut);
                sendJson(out, res);
            } catch (Exception e) {
                sendError(out, 500, e.getMessage());
            }
        } else {
            sendError(out, 403, "Forbidden");
        }
    }

    private void handlePaste(OutputStream out, String query) throws IOException {
        String destPath = getQueryParam(query, "path");
        if (destPath == null)
            destPath = getQueryParam(query, "to");
        String conflict = getQueryParam(query, "conflict");
        if (destPath == null)
            destPath = "";
        if (Clipboard.sourceFile == null) {
            sendError(out, 400, "Nothing to paste");
            return;
        }
        File destDir = new File(rootDir, destPath.startsWith("/") ? destPath.substring(1) : destPath);
        File target = new File(destDir, Clipboard.sourceFile.getName());
        if ("auto".equals(conflict)) {
            target = getUniqueFile(destDir, Clipboard.sourceFile.getName());
        }

        if (isInsideRoot(destDir) && isInsideRoot(target)) {
            if (target.exists() && !"override".equals(conflict)) {
                sendError(out, 409, "File exists");
                return;
            }
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
                JSONObject res = new JSONObject();
                res.put("success", true);
                sendJson(out, res);
            } catch (Exception e) {
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
            String contentType, InputStream body, long contentLength, String clientIp) throws IOException {
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
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
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

        // Stream headers first to find filename
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int b;
        int state = 0; // 0: \r, 1: \n, 2: \r, 3: \n
        long bytesRead = 0;
        while (bytesRead < contentLength && (b = body.read()) != -1) {
            headerBuf.write(b);
            bytesRead++;
            if (b == '\r' && (state == 0 || state == 2))
                state++;
            else if (b == '\n' && state == 1)
                state++;
            else if (b == '\n' && state == 3) {
                state++;
                break;
            } else
                state = 0;
        }

        String headerStr = new String(headerBuf.toByteArray(), "ISO-8859-1");

        // Extract filename from Content-Disposition
        String filename = "upload_" + System.currentTimeMillis();
        int fnIdx = headerStr.indexOf("filename=\"");
        if (fnIdx >= 0) {
            int fnEnd = headerStr.indexOf("\"", fnIdx + 10);
            if (fnEnd > fnIdx)
                filename = headerStr.substring(fnIdx + 10, fnEnd);
        }

        String conflict = getQueryParam(query, "conflict");
        File outFile = new File(destDir, filename);
        if ("auto".equals(conflict)) {
            outFile = getUniqueFile(destDir, filename);
        } else if (outFile.exists() && !"override".equals(conflict)) {
            sendError(out, 409, "File exists");
            return;
        }

        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] buf = new byte[65536];
        byte[] searchSeq = ("\r\n--" + boundary).getBytes("ISO-8859-1");
        int seqLen = searchSeq.length;

        byte[] window = new byte[buf.length + seqLen];
        int windowLen = 0;
        long remaining = contentLength - bytesRead;
        boolean done = false;
        long bytesWritten = 0;

        while (remaining > 0 && !done) {
            int toRead = (int) Math.min((long) buf.length, remaining);
            int r = body.read(buf, 0, toRead);
            if (r < 0)
                break;
            remaining -= r;

            System.arraycopy(buf, 0, window, windowLen, r);
            windowLen += r;

            int boundaryIdx = -1;
            for (int i = 0; i <= windowLen - seqLen; i++) {
                boolean match = true;
                for (int j = 0; j < seqLen; j++) {
                    if (window[i + j] != searchSeq[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    boundaryIdx = i;
                    break;
                }
            }

            if (boundaryIdx >= 0) {
                fos.write(window, 0, boundaryIdx);
                bytesWritten += boundaryIdx;
                done = true;
            } else {
                int safeToWrite = windowLen - (seqLen - 1);
                if (safeToWrite > 0) {
                    fos.write(window, 0, safeToWrite);
                    bytesWritten += safeToWrite;
                    System.arraycopy(window, safeToWrite, window, 0, windowLen - safeToWrite);
                    windowLen -= safeToWrite;
                }
            }
        }

        if (!done && windowLen > 0) {
            fos.write(window, 0, windowLen);
            bytesWritten += windowLen;
        }
        fos.close();

        // Read remaining payload if any to avoid connection reset, but we don't really
        // have to.

        if (listener != null)
            listener.onLog("Uploaded: " + filename + " (" + bytesWritten + " bytes)");

        try {
            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("filename", filename);
            sendJson(out, res);
        } catch (Exception e) {
            sendError(out, 500, e.getMessage());
        }
    }

    private void getGalleryFromMediaStore(List<File> results) {
        String rootPath;
        try {
            rootPath = rootDir.getCanonicalPath();
            if (!rootPath.endsWith("/"))
                rootPath += "/";
        } catch (Exception e) {
            rootPath = rootDir.getAbsolutePath();
        }

        String[] projection = { MediaStore.MediaColumns.DATA };
        Uri[] uris = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };

        for (Uri uri : uris) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
                    null)) {
                if (cursor != null) {
                    int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    while (cursor.moveToNext()) {
                        String path = cursor.getString(dataIdx);
                        if (path != null && path.startsWith(rootPath)) {
                            results.add(new File(path));
                        }
                    }
                }
            } catch (Exception e) {
                log("MediaStore query error: " + e.getMessage());
            }
        }
    }

    private void getGalleryFiles(File dir, List<File> results) {
        File[] children = dir.listFiles();
        if (children == null)
            return;
        for (File f : children) {
            if (f.isDirectory()) {
                getGalleryFiles(f, results);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif")
                        || name.endsWith(".webp") || name.endsWith(".mp4") || name.endsWith(".webm")
                        || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")) {
                    results.add(f);
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private File getUniqueFile(File parent, String name) {
        File f = new File(parent, name);
        if (!f.exists())
            return f;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int count = 1;
        while (true) {
            f = new File(parent, base + " (" + count + ")" + ext);
            if (!f.exists())
                return f;
            count++;
            if (count > 100)
                break; // safety
        }
        return f;
    }

    private boolean isInsideRoot(File file) {
        try {
            String cp = file.getCanonicalPath();
            String rp = rootDir.getCanonicalPath();
            if (cp.equals(rp))
                return true;
            if (!rp.endsWith(File.separator))
                rp += File.separator;
            return cp.startsWith(rp);
        } catch (Exception e) {
            return false;
        }
    }

    private String getMimeType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg"))
            return "image/jpeg";
        if (n.endsWith(".png"))
            return "image/png";
        if (n.endsWith(".gif"))
            return "image/gif";
        if (n.endsWith(".webp"))
            return "image/webp";
        if (n.endsWith(".mp4"))
            return "video/mp4";
        if (n.endsWith(".webm"))
            return "video/webm";
        if (n.endsWith(".mkv"))
            return "video/x-matroska";
        if (n.endsWith(".mp3"))
            return "audio/mpeg";
        if (n.endsWith(".pdf"))
            return "application/pdf";
        if (n.endsWith(".zip"))
            return "application/zip";
        String m = URLConnection.guessContentTypeFromName(n);
        return m != null ? m : "application/octet-stream";
    }

    private void streamFile(OutputStream out, File f, boolean forceDownload, Map<String, String> headers)
            throws IOException {
        long fileSize = f.length();
        String rangeHeader = headers.get("range");
        long start = 0;
        long end = fileSize - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] rangeParts = rangeHeader.substring(6).split("-");
            try {
                if (rangeParts.length > 0 && !rangeParts[0].isEmpty()) {
                    start = Long.parseLong(rangeParts[0]);
                }
                if (rangeParts.length > 1 && !rangeParts[1].isEmpty()) {
                    end = Long.parseLong(rangeParts[1]);
                }
            } catch (NumberFormatException e) {
                // Ignore malformed range
            }
        }

        // Validate range
        if (start < 0)
            start = 0;
        if (end >= fileSize)
            end = fileSize - 1;
        if (start > end)
            start = end;

        long contentLength = end - start + 1;
        boolean isPartial = rangeHeader != null;

        String mime = getMimeType(f.getName());

        String disposition = (forceDownload || !allowPreviews) ? "attachment" : "inline";
        StringBuilder sb = new StringBuilder();
        sb.append(isPartial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(mime).append("\r\n")
                .append("Content-Length: ").append(contentLength).append("\r\n")
                .append("Accept-Ranges: bytes\r\n");

        if (isPartial) {
            sb.append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(fileSize)
                    .append("\r\n");
        }

        sb.append("Content-Disposition: ").append(disposition).append("; filename=\"").append(f.getName())
                .append("\"\r\n")
                .append("Connection: close\r\n\r\n");

        out.write(sb.toString().getBytes("UTF-8"));
        log("RESPONSE: " + (isPartial ? "206 Partial" : "200 OK") + " (Streamed " + f.getName() + " range " + start
                + "-"
                + end + ")");

        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(start);
            byte[] buf = new byte[65536];
            long remaining = contentLength;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = raf.read(buf, 0, toRead);
                if (n == -1)
                    break;
                out.write(buf, 0, n);
                remaining -= n;
            }
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

    private String getQueryParam(String query, String key, String defaultValue) {
        String val = getQueryParam(query, key);
        return val != null ? val : defaultValue;
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

    private void handleAppDownload(OutputStream out, String query, Map<String, String> headers) throws IOException {
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
                streamFile(out, apkFile, true, headers);
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
