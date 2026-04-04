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
            ":root {" +
            "  --bg: #f0f4f8;" +
            "  --text: #333333;" +
            "  --accent: #1a73e8;" +
            "  --shadow-light: #ffffff;" +
            "  --shadow-dark: #d1d9e6;" +
            "  --inner-shadow: inset 3px 3px 6px var(--shadow-dark), inset -3px -3px 6px var(--shadow-light);" +
            "  --outer-shadow: 6px 6px 12px var(--shadow-dark), -6px -6px 12px var(--shadow-light);" +
            "}" +
            "body.dark-theme {" +
            "  --bg: #1a1c23;" +
            "  --text: #e1e2e5;" +
            "  --accent: #8ab4f8;" +
            "  --shadow-light: rgba(255, 255, 255, 0.03);" +
            "  --shadow-dark: rgba(0, 0, 0, 0.6);" +
            "}" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { font-family: 'Inter', -apple-system, sans-serif; background: var(--bg); color: var(--text); line-height: 1.5; min-height: 100vh; display: flex; flex-direction: column; transition: background 0.3s; }"
            +
            "header { padding: 24px; text-align: center; position: relative; }" +
            ".theme-toggle { position: absolute; top: 24px; right: 24px; width: 44px; height: 44px; border-radius: 50%; background: var(--bg); box-shadow: var(--outer-shadow); display: flex; align-items: center; justify-content: center; cursor: pointer; border: none; font-size: 18px; color: var(--text); }"
            +
            "header h1 { font-size: 24px; font-weight: 800; color: var(--accent); margin-bottom: 4px; }" +
            ".sticky-header { position: sticky; top: 0; z-index: 100; background: var(--bg); transition: background 0.3s; margin: -20px -20px 20px -20px; padding: 20px 20px 0 20px; box-shadow: 0 10px 20px -10px var(--shadow-dark); }"
            +
            ".container { max-width: 1000px; margin: 0 auto; width: 95%; flex: 1; }" +
            ".plate { background: var(--bg); border-radius: 24px; box-shadow: var(--outer-shadow); padding: 24px; margin-bottom: 32px; border: 1px solid rgba(255,255,255,0.05); }"
            +
            ".toolbar { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; align-items: center; }" +
            ".search-box { flex: 1; min-width: 200px; position: relative; }" +
            ".search-box input { width: 100%; padding: 12px 20px 12px 40px; border-radius: 12px; border: none; background: var(--bg); box-shadow: var(--inner-shadow); color: var(--text); outline: none; }"
            +
            ".search-box i { position: absolute; left: 16px; top: 14px; opacity: 0.5; }" +
            ".view-select, .sort-select { padding: 12px 20px; border-radius: 12px; border: none; background: var(--bg); box-shadow: var(--inner-shadow); color: var(--text); outline: none; font-family: inherit; font-size: 13px; cursor: pointer; }"
            +
            ".ops-bar { display: flex; gap: 10px; margin-bottom: 24px; }" +
            ".gallery { transition: all 0.3s; }" +
            ".gallery.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 20px; user-select: none; -webkit-user-select: none; }"
            +
            ".gallery.list-names, .gallery.list-icons, .gallery.list-detailed { display: flex; flex-direction: column; gap: 10px; user-select: none; -webkit-user-select: none; }"
            +
            ".item-card { background: var(--bg); border-radius: 20px; padding: 20px; text-align: center; box-shadow: var(--outer-shadow); transition: transform 0.2s, box-shadow 0.2s; cursor: pointer; position: relative; display: flex; flex-direction: column; align-items: center; gap: 10px; }"
            +
            ".gallery.list-names .item-card, .gallery.list-icons .item-card, .gallery.list-detailed .item-card { flex-direction: row; padding: 12px 20px; justify-content: space-between; height: auto; text-align: left; }"
            +
            ".item-card:hover { transform: translateY(-2px); box-shadow: 8px 8px 16px var(--shadow-dark), -8px -8px 16px var(--shadow-light); }"
            +
            ".item-card.selected { box-shadow: var(--inner-shadow); border: 2px solid var(--accent); transform: scale(0.98); }"
            +
            ".item-left { display: flex; flex-direction: column; align-items: center; flex: 1; }" +
            ".gallery.list-names .item-left, .gallery.list-icons .item-left, .gallery.list-detailed .item-left { flex-direction: row; align-items: center; overflow: hidden; }"
            +
            ".item-icon { font-size: 40px; margin-bottom: 8px; color: var(--accent); }" +
            ".gallery.list-icons .item-icon, .gallery.list-detailed .item-icon { margin: 0 16px 0 0; font-size: 24px; margin-bottom: 0; }"
            +
            ".gallery.list-names .item-icon, .gallery.list-names .item-info, .gallery.list-names .item-date { display: none; }"
            +
            ".item-name { font-weight: 600; font-size: 14px; word-break: break-all; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; line-height: 1.2; }"
            +
            ".gallery.list-detailed .item-name { white-space: nowrap; text-overflow: ellipsis; margin-right: 16px; }"
            +
            ".item-info, .item-date { font-size: 11px; opacity: 0.6; }" +
            ".gallery.list-icons .item-info, .gallery.list-icons .item-date { display: none; }" +
            ".gallery.grid .item-date { display: none; }" +
            ".gallery.list-detailed .item-date, .gallery.list-detailed .item-info { width: 140px; text-align: right; margin-left: 16px; display: block; font-size: 13px; }"
            +
            ".item-menu-btn { position: absolute; top: 8px; right: 8px; width: 28px; height: 28px; border-radius: 50%; opacity: 1; transition: background 0.2s; display: flex; align-items: center; justify-content: center; background: var(--bg); box-shadow: 2px 2px 5px var(--shadow-dark); color: var(--text); z-index: 2; cursor: pointer; }"
            +
            ".item-menu-btn:active { background: var(--accent); color: white; }" +
            ".dropdown { position: absolute; right: 0; top: 40px; background: var(--bg); border-radius: 12px; box-shadow: var(--outer-shadow); z-index: 100; min-width: 140px; overflow: hidden; display: none; border: 1px solid rgba(255,255,255,0.05); }"
            +
            ".dropdown.show { display: block; }" +
            ".dropdown-item { padding: 10px 16px; font-size: 13px; text-align: left; cursor: pointer; transition: background 0.2s; display: flex; align-items: center; gap: 8px; color: var(--text); }"
            +
            ".dropdown-item:hover { background: rgba(0,0,0,0.05); color: var(--accent); }" +
            "body.dark-theme .dropdown-item:hover { background: rgba(255,255,255,0.05); }" +
            ".btn { padding: 10px 20px; border-radius: 12px; border: none; background: var(--bg); box-shadow: 4px 4px 8px var(--shadow-dark), -4px -4px 8px var(--shadow-light); cursor: pointer; font-weight: 700; color: var(--accent); white-space: nowrap; font-size: 13px; text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }"
            +
            ".upload-section { margin-bottom: 24px; padding: 20px; border-radius: 16px; border: 2px dashed var(--accent); transition: background 0.2s; }"
            +
            "body::after { content: 'Drop files anywhere to upload'; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(26,115,232,0.9); color: white; font-size: 32px; font-weight: bold; display: flex; align-items: center; justify-content: center; z-index: 9999; opacity: 0; pointer-events: none; transition: opacity 0.2s; backdrop-filter: blur(4px); }"
            +
            "body.dragover::after { opacity: 1; pointer-events: all; }"
            +
            ".fab-container { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: var(--bg); padding: 12px 24px; border-radius: 30px; box-shadow: var(--outer-shadow); display: flex; gap: 16px; z-index: 1000; border: 1px solid rgba(255,255,255,0.1); opacity: 0; pointer-events: none; transition: opacity 0.3s; align-items: center; }"
            +
            ".fab-container.show { opacity: 1; pointer-events: auto; }" +
            "footer { padding: 40px 24px; text-align: center; opacity: 0.8; font-size: 14px; }" +
            ".socials { margin-top: 12px; display: flex; justify-content: center; gap: 20px; }" +
            ".social-icon { width: 24px; height: 24px; fill: var(--text); opacity: 0.6; }" +
            "@media (max-width: 600px) { .gallery { grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); } }"
            +
            "</style>";

    private static final String JS = "<script>" +
            "let selectedFiles = new Set();" +
            "let selectMode = false;" +
            "let pressTimer;" +
            "function toggleTheme() { " +
            "  const body = document.body;" +
            "  const isDark = body.classList.toggle('dark-theme');" +
            "  localStorage.setItem('theme', isDark ? 'dark' : 'light');" +
            "  document.getElementById('theme-icon').className = isDark ? 'fa-solid fa-sun' : 'fa-solid fa-moon';" +
            "}" +
            "function initTheme() {" +
            "  const saved = localStorage.getItem('theme');" +
            "  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;" +
            "  const isDark = saved === 'dark' || (!saved && prefersDark);" +
            "  if (isDark) { document.body.classList.add('dark-theme'); }" +
            "  const icon = document.getElementById('theme-icon');" +
            "  if (icon) icon.className = isDark ? 'fa-solid fa-sun' : 'fa-solid fa-moon';" +
            "}" +
            "function filterFiles() {" +
            "  const q = document.getElementById('search').value.toLowerCase();" +
            "  document.querySelectorAll('.item-card').forEach(c => {" +
            "    const name = c.dataset.name.toLowerCase();" +
            "    c.style.display = name.includes(q) ? 'flex' : 'none';" +
            "  });" +
            "}" +
            "function showMenu(e, id) {" +
            "  e.preventDefault(); e.stopPropagation();" +
            "  document.querySelectorAll('.dropdown').forEach(d => { if(d.id !== 'm-'+id) { d.classList.remove('show'); d.closest('.item-card').style.zIndex = '1'; } });"
            +
            "  const menu = document.getElementById('m-'+id);" +
            "  const isShowing = menu.classList.contains('show');" +
            "  if (isShowing) { menu.classList.remove('show'); menu.closest('.item-card').style.zIndex = '1'; }"
            +
            "  else { menu.classList.add('show'); menu.closest('.item-card').style.zIndex = '100'; }"
            +
            "}" +
            "window.onclick = function() { document.querySelectorAll('.dropdown').forEach(d => { d.classList.remove('show'); d.closest('.item-card').style.zIndex = '1'; }); };"
            +
            "async function op(e, action, file, extra='') {" +
            "  if(e) { e.preventDefault(); e.stopPropagation(); document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); }"
            +
            "  let url = '/' + action + '?file=' + encodeURIComponent(file);" +
            "  if (action === 'rename') {" +
            "    const { value: name } = await Swal.fire({ title: 'Rename', input: 'text', inputValue: extra || file.split('/').pop(), showCancelButton: true, icon: 'info' });"
            +
            "    if (!name) return; url += '&new=' + encodeURIComponent(name);" +
            "  } else if (action === 'mkdir') {" +
            "    const { value: name } = await Swal.fire({ title: 'New Folder', input: 'text', showCancelButton: true, icon: 'info' });"
            +
            "    if(!name) return; url = '/mkdir?path=' + encodeURIComponent(file) + '&name=' + encodeURIComponent(name);"
            +
            "  } else if (action === 'delete') {" +
            "    const res = await Swal.fire({ title: 'Delete?', text: 'Are you sure you want to delete this?', icon: 'warning', showCancelButton: true, confirmButtonColor: '#d33' });"
            +
            "    if(!res.isConfirmed) return;" +
            "  } else if (action === 'paste') {" +
            "    url = '/paste?to=' + encodeURIComponent(file);" +
            "  }" +
            "  location.href = url;" +
            "}" +
            "function itemClick(e, el, path, isDir, encodedPath) {" +
            "  e.preventDefault();" +
            "  if(selectMode) { toggleSelect(el, path); return; }" +
            "  location.href = isDir ? '/?path=' + encodedPath : '/download?file=' + encodedPath;" +
            "}" +
            "function toggleSelectMode() {" +
            "  selectMode = !selectMode;" +
            "  if(!selectMode) { document.querySelectorAll('.item-card.selected').forEach(el => el.classList.remove('selected')); selectedFiles.clear(); document.getElementById('fab').classList.remove('show'); }"
            +
            "  else { Swal.fire({toast:true, position:'bottom-end', title:'Selection Mode On', showConfirmButton:false, timer:1500}); }"
            +
            "}" +
            "function toggleSelect(el, path) {" +
            "  selectMode = true;" +
            "  el.classList.toggle('selected');" +
            "  if (selectedFiles.has(path)) selectedFiles.delete(path); else selectedFiles.add(path);" +
            "  if (selectedFiles.size === 0) selectMode = false;" +
            "  document.getElementById('fab').classList.toggle('show', selectedFiles.size > 0);" +
            "  document.getElementById('sel-count').innerText = selectedFiles.size;" +
            "}" +
            "function downloadZip() {" +
            "  const files = Array.from(selectedFiles).map(encodeURIComponent).join(',');" +
            "  location.href = '/zip?files=' + files;" +
            "  clearSelection();" +
            "}" +
            "async function downloadQueue() {" +
            "  const files = Array.from(selectedFiles);" +
            "  if (files.length === 0) return;" +
            "  for (let i = 0; i < files.length; i++) {" +
            "    const link = document.createElement('a');" +
            "    link.href = '/download?file=' + encodeURIComponent(files[i]) + '&dl=1';" +
            "    link.target = '_blank';" +
            "    link.download = '';" +
            "    document.body.appendChild(link);" +
            "    link.click();" +
            "    document.body.removeChild(link);" +
            "    await new Promise(r => setTimeout(r, 500));" +
            "  }" +
            "  clearSelection();" +
            "}" +
            "function clearSelection() {" +
            "  selectedFiles.clear(); selectMode = false;" +
            "  document.querySelectorAll('.item-card.selected').forEach(el => el.classList.remove('selected'));" +
            "  document.getElementById('fab').classList.remove('show');" +
            "}" +
            "document.addEventListener('DOMContentLoaded', () => { " +
            "  initTheme(); initView(); " +
            "  document.body.addEventListener('dragover', e => { e.preventDefault(); document.body.classList.add('dragover'); }); "
            +
            "  document.body.addEventListener('dragleave', e => { if (!e.relatedTarget) document.body.classList.remove('dragover'); }); "
            +
            "  document.body.addEventListener('drop', e => { " +
            "    e.preventDefault(); document.body.classList.remove('dragover'); " +
            "    let files = e.dataTransfer.files; if(files.length > 0) { " +
            "      document.getElementById('fileInput').files = files; " +
            "      if (typeof checkUpload === 'function') checkUpload(); else document.getElementById('uploadForm').submit(); "
            +
            "    } " +
            "  }); " +
            "});" +
            "document.addEventListener('contextmenu', e => { " +
            "  const card = e.target.closest('.item-card');" +
            "  if(card && !selectMode) { e.preventDefault(); toggleSelectMode(); toggleSelect(card, card.dataset.path); }"
            +
            "});" +
            "function changeView(v) { const gal = document.getElementById('gallery'); if(gal) gal.className = 'gallery ' + v; localStorage.setItem('view', v); }"
            +
            "function initView() { const v = localStorage.getItem('view') || 'grid'; const gal = document.getElementById('gallery'); if(gal) gal.className = 'gallery ' + v; const sel = document.querySelector('.view-select'); if(sel) sel.value = v; }"
            +
            "function sortFiles(v) { const gal = document.getElementById('gallery'); if(!gal) return; const items = Array.from(gal.children); items.sort((a,b) => { const aDir = a.dataset.isdir === 'true'; const bDir = b.dataset.isdir === 'true'; if (aDir !== bDir) return aDir ? -1 : 1; if(v === 'name_asc') return a.dataset.name.localeCompare(b.dataset.name); if(v === 'name_desc') return b.dataset.name.localeCompare(a.dataset.name); if(v === 'date_asc') return parseInt(a.dataset.time) - parseInt(b.dataset.time); if(v === 'date_desc') return parseInt(b.dataset.time) - parseInt(a.dataset.time); if(v === 'size_asc') return parseInt(a.dataset.size) - parseInt(b.dataset.size); if(v === 'size_desc') return parseInt(b.dataset.size) - parseInt(a.dataset.size); return 0; }); items.forEach(i => gal.appendChild(i)); }"
            +
            "function openNewTab(e, path) { e.preventDefault(); e.stopPropagation(); document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); window.open('/download?file=' + encodeURIComponent(path), '_blank'); }"
            +
            "function copyToClip(e, path) { e.preventDefault(); e.stopPropagation(); document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); const url = window.location.origin + '/download?file=' + encodeURIComponent(path); navigator.clipboard.writeText(url).then(() => Swal.fire({toast:true, position:'bottom-end', title:'Link copied', showConfirmButton:false, timer:2000})); }"
            +
            "</script>";

    public static String buildDirListing(File dir, File rootDir, String relPath) {
        String displayPath = relPath.isEmpty() ? "/" : "/" + relPath;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&display=swap' rel='stylesheet'>")
                .append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>")
                .append("<script src='https://cdn.jsdelivr.net/npm/sweetalert2@11'></script>")
                .append("<title>Share File — ").append(escapeHtml(displayPath)).append("</title>")
                .append(CSS).append(JS)
                .append("</head><body>")
                .append("<div class='sticky-header'>")
                .append("<header>")
                .append("<button class='theme-toggle' onclick='toggleTheme()'><i id='theme-icon' class='fa-solid fa-moon'></i></button>")
                .append("<h1>Share File</h1>")
                .append("<div class='subtitle'>");

        if (displayPath.equals("/")) {
            sb.append("<a href='/?path=' style='color:var(--accent);text-decoration:none;'>Home</a>");
        } else {
            sb.append("<a href='/?path=' style='color:var(--text);text-decoration:none;opacity:0.7;'>Home</a>");
            String[] parts = relPath.split("/");
            String current = "";
            for (int i = 0; i < parts.length; i++) {
                current += current.isEmpty() ? parts[i] : "/" + parts[i];
                sb.append(" <span style='opacity:0.5'>/</span> ");
                if (i == parts.length - 1) {
                    sb.append("<span style='color:var(--accent); font-weight:600;'>").append(escapeHtml(parts[i]))
                            .append("</span>");
                } else {
                    sb.append("<a href='/?path=").append(urlEncode(current))
                            .append("' style='color:var(--text);text-decoration:none;opacity:0.7;'>")
                            .append(escapeHtml(parts[i])).append("</a>");
                }
            }
        }
        sb.append("</div></header>");

        sb.append("<div class='container'>");

        // Toolbar
        sb.append("<div class='toolbar'>")
                .append("<div class='search-box'><i class='fa-solid fa-search'></i><input type='text' id='search' placeholder='Search files...' oninput='filterFiles()'></div>")
                .append("<select class='view-select' onchange='changeView(this.value)'><option value='grid'>Grid View</option><option value='list-names'>List (Names)</option><option value='list-icons'>List (Icons)</option><option value='list-detailed'>Detailed List</option></select>")
                .append("<select class='sort-select' onchange='sortFiles(this.value)'><option value='name_asc'>Name (A-Z)</option><option value='name_desc'>Name (Z-A)</option><option value='date_desc'>Newest First</option><option value='date_asc'>Oldest First</option><option value='size_desc'>Largest First</option><option value='size_asc'>Smallest First</option></select>")
                .append("<button class='btn' id='selectBtn' onclick='toggleSelectMode()'><i class='fa-solid fa-check-square'></i> Select</button>")
                .append("<button class='btn' onclick=\"op(event, 'mkdir', '").append(escapeHtml(displayPath))
                .append("')\"><i class='fa-solid fa-folder-plus'></i> New Folder</button>")
                .append("<button class='btn' onclick=\"op(event, 'paste', '").append(escapeHtml(displayPath))
                .append("')\"><i class='fa-solid fa-paste'></i> Paste</button>")
                .append("</div>")
                .append("</div></div>"); // Close container & sticky-header

        sb.append("<div class='container'>");
        sb.append("<div class='plate'>");

        // Upload form (TOP - Compact with Drag & Drop)
        String uploadPath = relPath.isEmpty() ? "" : "/" + relPath;
        sb.append(
                "<div class='upload-section'>")
                .append("<form id='uploadForm' class='upload-form' method='POST' action='/upload?path=")
                .append(urlEncode(uploadPath))
                .append("' enctype='multipart/form-data'>")
                .append("<label for='fileInput' style='cursor:pointer; display:flex; flex-direction:column; align-items:center; gap:10px; width:100%; border:none;'>")
                .append("<div style='font-size: 14px; opacity: 0.8; font-weight: 600; text-align:center;'><i class='fa-solid fa-cloud-arrow-up' style='font-size: 24px; margin-bottom: 8px; display:block;'></i> Drag and drop files anywhere on the page, or click here to browse</div>")
                .append("<input type='file' id='fileInput' name='file' multiple required onchange='if(typeof checkUpload === \"function\") checkUpload(); else document.getElementById(\"uploadForm\").submit()' style='display:none;'>")
                .append("</label>")
                .append("</form></div>");

        // Gallery Grid
        File[] children = dir.listFiles();

        sb.append("<script>const existingFiles = [");
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                sb.append("'").append(escapeHtml(children[i].getName().replace("'", "\\'"))).append("'");
                if (i < children.length - 1)
                    sb.append(", ");
            }
        }
        sb.append("];\n");
        sb.append(
                "async function checkUpload() { const input = document.getElementById('fileInput'); const files = input.files; if(files.length === 0) return; for(let i=0; i<files.length; i++) { if(existingFiles.includes(files[i].name)) { const res = await Swal.fire({title: 'File Exists', text: files[i].name + ' already exists. Overwrite?', icon:'warning', showCancelButton: true, confirmButtonText: 'Overwrite'}); if(!res.isConfirmed) { input.value = ''; return; } } } document.getElementById('uploadForm').submit(); }");
        sb.append("</script>");

        if (children == null || children.length == 0) {
            sb.append("<div class='empty'>This folder is empty.</div>");
        } else {
            Arrays.sort(children, Comparator.comparing(File::isFile).thenComparing(f -> f.getName().toLowerCase()));

            sb.append("<div id='gallery' class='gallery grid'>");
            int idCounter = 0;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");

            for (File f : children) {
                String childRel = relPath.isEmpty() ? f.getName() : relPath + "/" + f.getName();
                String encodedPath = urlEncode("/" + childRel);
                String displayRel = "/" + childRel;
                String lastMod = sdf.format(new java.util.Date(f.lastModified()));
                idCounter++;

                sb.append("<div class='item-card' data-name='").append(escapeHtml(f.getName()))
                        .append("' data-path='").append(escapeHtml(displayRel))
                        .append("' data-isdir='").append(f.isDirectory())
                        .append("' data-size='").append(f.length())
                        .append("' data-time='").append(f.lastModified()).append("' ")
                        .append("onclick=\"itemClick(event, this, '").append(escapeHtml(displayRel)).append("', ")
                        .append(f.isDirectory()).append(", '").append(encodedPath).append("')\">")
                        .append("<div class='item-menu-btn' onclick=\"showMenu(event, ").append(idCounter)
                        .append(")\"><i class='fa-solid fa-ellipsis-v'></i></div>")
                        .append("<div class='dropdown' id='m-").append(idCounter).append("'>")
                        .append("<div class='dropdown-item' onclick=\"openNewTab(event, '")
                        .append(escapeHtml(displayRel))
                        .append("')\"><i class='fa-solid fa-arrow-up-right-from-square'></i> Open in New Tab</div>")
                        .append("<div class='dropdown-item' onclick=\"event.preventDefault(); event.stopPropagation(); document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); if(!selectMode) toggleSelectMode(); toggleSelect(this.closest('.item-card'), this.closest('.item-card').dataset.path)\"><i class='fa-solid fa-check-square'></i> Select</div>")
                        .append("<div class='dropdown-item' onclick=\"location.href='/download?file=")
                        .append(encodedPath).append("&dl=1'\"><i class='fa-solid fa-download'></i> Download</div>")
                        .append("<div class='dropdown-item' onclick=\"op(event, 'rename', '")
                        .append(escapeHtml(displayRel))
                        .append(f.isDirectory() ? "" : "', '" + escapeHtml(f.getName()))
                        .append("')\"><i class='fa-solid fa-pen'></i> Rename</div>")
                        .append("<div class='dropdown-item' onclick=\"op(event, 'cut', '")
                        .append(escapeHtml(displayRel))
                        .append("')\"><i class='fa-solid fa-scissors'></i> Cut</div>")
                        .append("<div class='dropdown-item' onclick=\"op(event, 'copy', '")
                        .append(escapeHtml(displayRel))
                        .append("')\"><i class='fa-solid fa-copy'></i> Copy</div>")
                        .append("<div class='dropdown-item' style='color:red;' onclick=\"op(event, 'delete', '")
                        .append(escapeHtml(displayRel)).append("')\"><i class='fa-solid fa-trash'></i> Delete</div>")
                        .append("</div>");

                sb.append("<div class='item-left'>");
                if (f.isDirectory()) {
                    sb.append("<div class='item-icon'><i class='fa-solid fa-folder'></i></div>");
                } else {
                    String ext = f.getName().toLowerCase();
                    if (ext.endsWith(".jpg") || ext.endsWith(".png") || ext.endsWith(".jpeg") || ext.endsWith(".gif")
                            || ext.endsWith(".webp"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-image'></i></div>");
                    else if (ext.endsWith(".mp4") || ext.endsWith(".mov") || ext.endsWith(".avi")
                            || ext.endsWith(".mkv"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-video'></i></div>");
                    else if (ext.endsWith(".mp3") || ext.endsWith(".wav") || ext.endsWith(".flac"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-audio'></i></div>");
                    else if (ext.endsWith(".pdf"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-pdf'></i></div>");
                    else if (ext.endsWith(".zip") || ext.endsWith(".rar") || ext.endsWith(".tar") || ext.endsWith(".gz")
                            || ext.endsWith(".7z"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-zipper'></i></div>");
                    else if (ext.endsWith(".doc") || ext.endsWith(".docx"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-word'></i></div>");
                    else if (ext.endsWith(".txt") || ext.endsWith(".md"))
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file-lines'></i></div>");
                    else if (ext.endsWith(".apk"))
                        sb.append("<div class='item-icon'><i class='fa-brands fa-android'></i></div>");
                    else
                        sb.append("<div class='item-icon'><i class='fa-solid fa-file'></i></div>");
                }
                sb.append("<div class='item-name'>").append(escapeHtml(f.getName())).append("</div>");
                sb.append("</div>"); // end item-left

                sb.append("<div class='item-date'>").append(lastMod).append("</div>")
                        .append("<div class='item-info'>").append(f.isDirectory() ? "Folder" : humanSize(f.length()))
                        .append("</div>")
                        .append("</div>"); // end item-card
            }
            sb.append("</div>");
        }

        sb.append("</div>"); // end plate
        sb.append("</div>"); // end container

        // FAB
        sb.append("<div id='fab' class='fab-container'>")
                .append("<span style='font-size:14px; font-weight:600;'><span id='sel-count'>0</span> Selected</span>")
                .append("<button class='btn' onclick='downloadZip()'><i class='fa-solid fa-file-zipper'></i> ZIP</button>")
                .append("<button class='btn' onclick='downloadQueue()'><i class='fa-solid fa-download'></i> Files</button>")
                .append("<button class='btn' onclick='clearSelection()'><i class='fa-solid fa-xmark'></i> Cancel</button>")
                .append("</div>");

        // Footer
        sb.append("<footer>")
                .append("<div>Developed by <a href='https://saheermk.pages.dev' target='_blank'>saheermk</a></div>")
                .append("<div class='socials'>")
                .append("<a href='https://github.com/saheermk/' target='_blank' title='GitHub'>")
                .append("<svg class='social-icon' viewBox='0 0 24 24'><path d='M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z'/></svg></a>")
                .append("<a href='https://in.linkedin.com/in/saheermk' target='_blank' title='LinkedIn'>")
                .append("<svg class='social-icon' viewBox='0 0 24 24'><path d='M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.79-1.75-1.764s.784-1.764 1.75-1.764 1.75.79 1.75 1.764-.783 1.764-1.75 1.764zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z'/></svg></a>")
                .append("</div>")
                .append("</footer>");

        sb.append("</body></html>");
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

    public static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }
}
