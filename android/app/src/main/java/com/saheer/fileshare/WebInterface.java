package com.saheer.fileshare;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Generates SHTTPS-style HTML for directory listing.
 */
public class WebInterface {

    private static final String CSS = "<style>" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { font-family: 'Roboto', Arial, sans-serif; background: #f5f5f5; color: #333; }" +
            "header { background: #1a73e8; color: white; padding: 16px 24px; display: flex; align-items: center; gap: 12px; }"
            +
            "header h1 { font-size: 20px; font-weight: 500; }" +
            "header .subtitle { font-size: 13px; opacity: 0.8; }" +
            ".breadcrumb { background: white; padding: 12px 24px; border-bottom: 1px solid #e0e0e0; font-size: 13px; }"
            +
            ".breadcrumb a { color: #1a73e8; text-decoration: none; }" +
            ".breadcrumb a:hover { text-decoration: underline; }" +
            ".breadcrumb span { color: #777; margin: 0 4px; }" +
            ".container { max-width: 1000px; margin: 0 auto; padding: 24px; }" +
            "table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }"
            +
            "th { background: #fafafa; text-align: left; padding: 12px 16px; font-size: 12px; font-weight: 600; color: #555; border-bottom: 2px solid #e0e0e0; text-transform: uppercase; letter-spacing: 0.5px; }"
            +
            "td { padding: 10px 16px; border-bottom: 1px solid #f0f0f0; font-size: 14px; }" +
            "tr:last-child td { border-bottom: none; }" +
            "tr:hover td { background: #f8f9ff; }" +
            ".icon { width: 20px; margin-right: 8px; vertical-align: middle; }" +
            ".name a { color: #1a73e8; text-decoration: none; font-weight: 500; }" +
            ".name a:hover { text-decoration: underline; }" +
            ".name.dir a { color: #333; }" +
            ".size, .date { color: #777; font-size: 13px; }" +
            ".dl-btn { display: inline-block; padding: 4px 12px; background: #1a73e8; color: white; border-radius: 4px; font-size: 12px; text-decoration: none; }"
            +
            ".dl-btn:hover { background: #1557b0; }" +
            ".upload-section { margin-top: 24px; background: white; border-radius: 8px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }"
            +
            ".upload-section h3 { font-size: 15px; color: #333; margin-bottom: 12px; }" +
            ".upload-form { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }" +
            ".upload-form input[type=file] { border: 1px solid #ddd; padding: 8px; border-radius: 4px; font-size: 13px; }"
            +
            ".upload-form button { padding: 8px 20px; background: #34a853; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 13px; }"
            +
            ".upload-form button:hover { background: #2d8f47; }" +
            ".empty { text-align: center; padding: 48px; color: #999; font-style: italic; }" +
            ".back-link { display: inline-block; margin-bottom: 16px; color: #1a73e8; text-decoration: none; font-size: 14px; }"
            +
            ".back-link:hover { text-decoration: underline; }" +
            "</style>";

    public static String buildDirListing(File dir, File rootDir, String relPath) {
        String displayPath = relPath.isEmpty() ? "/" : "/" + relPath;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<title>Share File — ").append(escapeHtml(displayPath)).append("</title>")
                .append(CSS)
                .append("</head><body>");

        sb.append("<header>")
                .append("<div>")
                .append("<h1>&#128193; Share File Server</h1>")
                .append("<div class='subtitle'>").append(escapeHtml(displayPath)).append("</div>")
                .append("</div>")
                .append("</header>");

        // Breadcrumb
        sb.append("<div class='breadcrumb'>")
                .append("<a href='/'>&#127968; Home</a>");

        if (!relPath.isEmpty()) {
            String[] parts = relPath.split("/");
            StringBuilder cumPath = new StringBuilder();
            for (String part : parts) {
                cumPath.append(part);
                sb.append(" <span>&#8250;</span> ")
                        .append("<a href='/?path=").append(urlEncode("/" + cumPath)).append("'>")
                        .append(escapeHtml(part)).append("</a>");
                cumPath.append("/");
            }
        }
        sb.append("</div>");

        // Content
        sb.append("<div class='container'>");

        // Back link
        if (!relPath.isEmpty()) {
            int lastSlash = relPath.lastIndexOf('/');
            String parentPath = lastSlash > 0 ? relPath.substring(0, lastSlash) : "";
            sb.append("<a class='back-link' href='/?path=").append(urlEncode("/" + parentPath))
                    .append("'>&#8592; Back</a>");
        }

        // File table
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            sb.append("<div class='empty'>This folder is empty.</div>");
        } else {
            // Sort: dirs first, then files, both alphabetical
            Arrays.sort(children, Comparator
                    .comparing(File::isFile)
                    .thenComparing(f -> f.getName().toLowerCase()));

            sb.append("<table><thead><tr>")
                    .append("<th>Name</th><th>Size</th><th>Modified</th><th>Action</th>")
                    .append("</tr></thead><tbody>");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

            for (File f : children) {
                String childRel = relPath.isEmpty() ? f.getName() : relPath + "/" + f.getName();
                String encodedPath = urlEncode("/" + childRel);
                String dateStr = sdf.format(new Date(f.lastModified()));

                sb.append("<tr>");

                if (f.isDirectory()) {
                    sb.append("<td class='name dir'>")
                            .append("&#128193; <a href='/?path=").append(encodedPath).append("'>")
                            .append(escapeHtml(f.getName())).append("/</a></td>")
                            .append("<td class='size'>—</td>")
                            .append("<td class='date'>").append(dateStr).append("</td>")
                            .append("<td>—</td>");
                } else {
                    String encodedFileParam = urlEncode("/" + childRel);
                    sb.append("<td class='name'>")
                            .append("&#128196; ").append(escapeHtml(f.getName())).append("</td>")
                            .append("<td class='size'>").append(humanSize(f.length())).append("</td>")
                            .append("<td class='date'>").append(dateStr).append("</td>")
                            .append("<td><a class='dl-btn' href='/download?file=").append(encodedFileParam)
                            .append("'>Download</a></td>");
                }
                sb.append("</tr>");
            }

            sb.append("</tbody></table>");
        }

        // Upload form
        String uploadPath = relPath.isEmpty() ? "" : "/" + relPath;
        sb.append("<div class='upload-section'>")
                .append("<h3>&#8679; Upload File to ").append(escapeHtml(displayPath)).append("</h3>")
                .append("<form class='upload-form' method='POST' action='/upload?path=").append(urlEncode(uploadPath))
                .append("' enctype='multipart/form-data'>")
                .append("<input type='file' name='file' multiple>")
                .append("<button type='submit'>Upload</button>")
                .append("</form>")
                .append("</div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }
}
