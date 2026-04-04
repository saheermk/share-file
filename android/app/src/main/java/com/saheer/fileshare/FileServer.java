package com.saheer.fileshare;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Pure-Java HTTP file server — SHTTPS clone core.
 * Handles directory listing, file download, and file upload.
 */
public class FileServer {
    private static final String TAG = "FileServer";

    private final int port;
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

    public FileServer(int port, File rootDir, OnServerListener listener) {
        this.port = port;
        this.rootDir = rootDir;
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
        // /favicon.ico — ignore
        if (path.equals("/favicon.ico")) {
            sendError(out, 404, "Not Found");
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
            streamFile(out, f);
            return;
        }

        if (target.isDirectory()) {
            String html = WebInterface.buildDirListing(target, rootDir, relPath);
            sendHtml(out, html);
        } else if (target.isFile()) {
            streamFile(out, target);
        } else {
            sendError(out, 404, "Not Found");
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

    private void streamFile(OutputStream out, File f) throws IOException {
        String mime = URLConnection.guessContentTypeFromName(f.getName());
        if (mime == null)
            mime = "application/octet-stream";

        PrintWriter w = new PrintWriter(out);
        w.print("HTTP/1.1 200 OK\r\n");
        w.print("Content-Type: " + mime + "\r\n");
        w.print("Content-Length: " + f.length() + "\r\n");
        w.print("Content-Disposition: attachment; filename=\"" + f.getName() + "\"\r\n");
        w.print("Connection: close\r\n\r\n");
        w.flush();

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
        PrintWriter w = new PrintWriter(out);
        w.print("HTTP/1.1 200 OK\r\n");
        w.print("Content-Type: text/html; charset=UTF-8\r\n");
        w.print("Content-Length: " + bytes.length + "\r\n");
        w.print("Connection: close\r\n\r\n");
        w.flush();
        out.write(bytes);
        out.flush();
    }

    private void sendError(OutputStream out, int code, String msg) {
        try {
            PrintWriter w = new PrintWriter(out);
            w.print("HTTP/1.1 " + code + " " + msg + "\r\n");
            w.print("Content-Type: text/plain\r\nConnection: close\r\n\r\n");
            w.print(msg);
            w.flush();
        } catch (Exception ignored) {
        }
    }

    private void sendRedirect(OutputStream out, String location) {
        try {
            PrintWriter w = new PrintWriter(out);
            w.print("HTTP/1.1 302 Found\r\n");
            w.print("Location: " + location + "\r\n");
            w.print("Connection: close\r\n\r\n");
            w.flush();
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
