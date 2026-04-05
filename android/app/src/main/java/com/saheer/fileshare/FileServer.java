package com.saheer.fileshare;

import android.content.Context;
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

    public interface OnServerListener {
        void onStarted(String ip, int port);

        void onStopped();

        void onError(String msg);

        void onLog(String msg);
    }

    public FileServer(int port, File rootDir, Context context, OnServerListener listener) {
        this.port = port;
        this.rootDir = rootDir;
        this.context = context;
        this.listener = listener;
    }

    public void setRootDir(File dir) {
        this.rootDir = dir;
    }

    public void start() {
        pool = Executors.newFixedThreadPool(8);
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                if (listener != null)
                    listener.onStarted(getLocalIp(), port);
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
        try {
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();

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

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String rawPath = parts[1];

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

            if ("GET".equalsIgnoreCase(method)) {
                handleGet(rawOut, path, query);
            } else if ("POST".equalsIgnoreCase(method)) {
                String ct = headers.getOrDefault("content-type", "");
                String lenStr = headers.getOrDefault("content-length", "0");
                int contentLength = Integer.parseInt(lenStr);
                handlePost(rawOut, path, query, ct, rawIn, contentLength);
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

    // ─── GET Handler ────────────────────────────────────────────────────────

    private void handleGet(OutputStream out, String path, String query) throws IOException {
        if (path.startsWith("/assets/")) {
            serveAsset(out, path.substring(8));
            return;
        }

        // /favicon.ico — ignore
        if (path.equals("/favicon.ico")) {
            sendError(out, 404, "Not Found");
            return;
        }

        if (path.equals("/logs")) {
            handleLogs(out);
            return;
        }

        // File Operations
        if (path.equals("/mkdir")) {
            handleMkdir(out, query);
            return;
        } else if (path.equals("/delete")) {
            handleDelete(out, query);
            return;
        } else if (path.equals("/rename")) {
            handleRename(out, query);
            return;
        } else if (path.equals("/copy") || path.equals("/cut")) {
            handleClipboardAction(out, path, query);
            return;
        } else if (path.equals("/paste")) {
            handlePaste(out, query);
            return;
        } else if (path.equals("/zip")) {
            handleZip(out, query);
            return;
        }

        // Parse ?path= param (sub-directory navigation)
        String relPath = getQueryParam(query, "path");
        if (relPath == null)
            relPath = "";
        // Remove leading slash
        if (relPath.startsWith("/"))
            relPath = relPath.substring(1);

        File target = relPath.isEmpty() ? rootDir : new File(rootDir, relPath);

        // Security: ensure inside rootDir
        if (!isInsideRoot(target)) {
            sendError(out, 403, "Forbidden");
            return;
        }

        // Download file
        String filePath = getQueryParam(query, "file");
        if (filePath != null) {
            if (filePath.startsWith("/"))
                filePath = filePath.substring(1);
            File f = new File(rootDir, filePath);
            if (!isInsideRoot(f) || !f.isFile()) {
                sendError(out, 404, "Not Found");
                return;
            }
            boolean forceDownload = "1".equals(getQueryParam(query, "dl"));
            streamFile(out, f, forceDownload);
            return;
        }

        if (target.isDirectory()) {
            String html = WebInterface.buildDirListing(target, rootDir, relPath);
            sendHtml(out, html);
        } else if (target.isFile()) {
            boolean forceDownload = "1".equals(getQueryParam(query, "dl"));
            streamFile(out, target, forceDownload);
        } else {
            sendError(out, 404, "Not Found");
        }
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
            sendRedirect(out, "/?path=" + WebInterface.urlEncode(parentPath));
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
            sendRedirect(out, "/?path=" + WebInterface.urlEncode(parentRel.isEmpty() ? "/" : parentRel));
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
            sendRedirect(out, "/?path=" + WebInterface.urlEncode(parentRel.isEmpty() ? "/" : parentRel));
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
            sendRedirect(out, "/?path=" + WebInterface.urlEncode(parentRel.isEmpty() ? "/" : parentRel));
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
                sendRedirect(out, "/?path=" + WebInterface.urlEncode(destPath));
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
            String contentType, InputStream body, int contentLength) throws IOException {
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
        String redirect = "/?path=" + (relPath.isEmpty() ? "" : "/" + relPath);
        sendRedirect(out, redirect);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void streamFile(OutputStream out, File f, boolean forceDownload) throws IOException {
        String mime = URLConnection.guessContentTypeFromName(f.getName());
        if (mime == null)
            mime = "application/octet-stream";

        String disposition = forceDownload ? "attachment" : "inline";
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
}
