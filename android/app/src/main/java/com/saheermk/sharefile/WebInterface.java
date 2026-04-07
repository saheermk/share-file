package com.saheermk.sharefile;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates SHTTPS-style HTML for directory listing.
 */
public class WebInterface {

    private static final String APP_LOGO = "data:image/png;base64,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";

    private static final String TELEMETRY_JS = "<script>" +
            "async function sendTelemetry() {" +
            "  try {" +
            "    let batteryInfo = { level: null, charging: null };" +
            "    if (navigator.getBattery) {" +
            "      const battery = await navigator.getBattery();" +
            "      batteryInfo = { level: Math.round(battery.level * 100), charging: battery.charging };" +
            "    }" +
            "    let model = 'Unknown';" +
            "    let platform = 'Unknown';" +
            "    if (navigator.userAgentData) {" +
            "       const highEntropy = await navigator.userAgentData.getHighEntropyValues(['model', 'platform']);" +
            "       model = highEntropy.model || 'Unknown';" +
            "       platform = highEntropy.platform || 'Unknown';" +
            "    }" +
            "    fetch('/telemetry', {" +
            "      method: 'POST'," +
            "      headers: { 'Content-Type': 'application/json' }," +
            "      body: JSON.stringify({" +
            "        batteryLevel: batteryInfo.level," +
            "        isCharging: batteryInfo.charging," +
            "        model: model," +
            "        platform: platform" +
            "      })" +
            "    });" +
            "  } catch (e) { console.log('Telemetry error', e); }" +
            "}" +
            "setTimeout(sendTelemetry, 1000);" +
            "</script>";

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
            "  --shadow-light: transparent;" +
            "  --shadow-dark: rgba(0, 0, 0, 0.4);" +
            "  --outer-shadow: 4px 4px 8px var(--shadow-dark), -4px -4px 8px var(--shadow-light);" +
            "  --inner-shadow: inset 2px 2px 4px var(--shadow-dark), inset -2px -2px 4px var(--shadow-light);" +
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
            ".back-btn { position: absolute; top: 24px; left: 24px; width: 44px; height: 44px; border-radius: 50%; background: var(--bg); box-shadow: var(--outer-shadow); display: flex; align-items: center; justify-content: center; cursor: pointer; border: none; font-size: 18px; color: var(--accent); z-index: 10; text-decoration: none; }"
            +
            ".back-btn:active { box-shadow: var(--inner-shadow); transform: scale(0.95); }"
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
            "  location.href = isDir ? '/files?path=' + encodedPath : '/download?file=' + encodedPath;" +
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
            "async function deleteSelected() {" +
            "  if (selectedFiles.size === 0) return;" +
            "  const res = await Swal.fire({ title: 'Delete ' + selectedFiles.size + ' items?', text: 'This action cannot be undone.', icon: 'warning', showCancelButton: true, confirmButtonColor: '#d33' });"
            +
            "  if (!res.isConfirmed) return;" +
            "  const files = Array.from(selectedFiles).map(encodeURIComponent).join(',');" +
            "  const path = new URLSearchParams(window.location.search).get('path') || '';" +
            "  location.href = '/delete_multiple?files=' + files + '&path=' + encodeURIComponent(path);" +
            "  clearSelection();" +
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
            "function goBack() { "
            + "  const urlParams = new URLSearchParams(window.location.search); "
            + "  const path = urlParams.get('path'); "
            + "  if (!path || path === '') { location.href = '/'; return; } "
            + "  const lastIndex = path.lastIndexOf('/'); "
            + "  if (lastIndex <= 0) { location.href = '/files?path='; } "
            + "  else { location.href = '/files?path=' + encodeURIComponent(path.substring(0, lastIndex)); } "
            + "}"
            +
            "</script>";

    public static String buildLogPage(String logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>");
        sb.append("<title>Server Logs</title>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append(CSS);
        sb.append("</head><body><div class='container'>");
        sb.append(
                "<div class='header'><a href='/' class='btn' style='font-size:12px; padding:6px 12px;'>&larr; Back</a><h1>System Logs</h1></div>");
        sb.append(
                "<div class='card' style='font-family:monospace; background:#000; color:#0f0; padding:15px; font-size:12px; line-height:1.5; white-space:pre-wrap;'>");
        sb.append(logs);
        sb.append("</div></div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    public static String buildApprovalPage(String clientIp) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>");
        sb.append("<title>Access Pending</title>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append(CSS);
        sb.append("<style>");
        sb.append(
                "  .qr-container { padding: 30px; background: white; border-radius: 20px; box-shadow: inset 5px 5px 10px #d1d9e6, inset -5px -5px 10px #ffffff; margin: 20px auto; max-width: 250px; }");
        sb.append("  .qr-container svg { width: 100%; height: auto; display: block; }");
        sb.append(
                "  .ip-badge { background: #e0e5ec; padding: 10px 20px; border-radius: 50px; font-weight: bold; color: #444; border: 1px solid rgba(0,0,0,0.05); }");
        sb.append("</style>");
        sb.append("</head><body><div class='container'>");
        sb.append("<div class='header'><h1>Access Request</h1></div>");
        sb.append("<div class='card' style='text-align:center;'>");
        sb.append(
                "<p style='font-size:16px; margin-bottom:20px; color:#555;'>Strict Mode is active.<br>Please ask the host to scan this code to grant access.</p>");
        sb.append("<div class='qr-container'>");
        sb.append(generateQrSvg(clientIp));
        sb.append("</div>");
        sb.append("<div style='margin-top:20px;'><span class='ip-badge'>").append(clientIp).append("</span></div>");
        sb.append(
                "<p style='font-size:12px; margin-top:30px; opacity:0.6;'>Your identification IP will be whitelisted on scan.</p>");
        sb.append("</div></div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String generateQrSvg(String content) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 2);
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE,
                    250, 250, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(width).append(" ").append(height)
                    .append("' fill='black'>");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        svg.append("<rect width='1' height='1' x='").append(x).append("' y='").append(y).append("'/>");
                    }
                }
            }
            svg.append("</svg>");
            return svg.toString();
        } catch (Exception e) {
            return "<div style='color:red;'>QR Error: " + e.getMessage() + "</div>";
        }
    }

    private static void appendHead(StringBuilder sb, String title) {
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<link rel='icon' type='image/png' href='").append(APP_LOGO).append("'>")
                .append("<link href='/assets/inter.css' rel='stylesheet'>")
                .append("<link rel='stylesheet' href='/assets/lib/all.min.css'>")
                .append("<script src='/assets/sweetalert2.min.js'></script>")
                .append("<title>Share File | ").append(escapeHtml(title)).append("</title>")
                .append(CSS).append(JS)
                .append("</head><body>");
    }

    private static void appendHeader(StringBuilder sb, String title, String subtitle) {
        String backUrl = "/";
        boolean showBack = true;

        if (title.equals("Home")) {
            showBack = false;
        } else if (title.equals("File Manager")) {
            // Need to parse path from subtitle or handle via JS.
            // Better handle via JS to avoid complex Java parsing in shared header.
            backUrl = "javascript:goBack()";
        } else if (title.equals("Installed Apps")) {
            backUrl = "/";
        }

        sb.append("<div class='sticky-header'>")
                .append("<header>");

        if (showBack) {
            sb.append("<a href='").append(backUrl)
                    .append("' class='back-btn'><i class='fa-solid fa-arrow-left'></i></a>");
        }

        sb.append(
                "<button class='theme-toggle' onclick='toggleTheme()'><i id='theme-icon' class='fa-solid fa-moon'></i></button>")
                .append("<h1 style='display:flex; justify-content:center; align-items:center; cursor:pointer' onclick=\"location.href='/'\"><img src='")
                .append(APP_LOGO)
                .append("' style='width: 32px; height: 32px; margin-right: 12px; border-radius: 8px; box-shadow: 2px 2px 5px var(--shadow-dark);'> Share File</h1>");
        if (subtitle != null) {
            sb.append("<div class='subtitle'>").append(subtitle).append("</div>");
        }
        sb.append("</header></div>");
    }

    private static void appendFooter(StringBuilder sb) {
        sb.append("<footer>")
                .append("<div>Developed by <a href='https://saheermk.pages.dev' target='_blank'>saheermk</a></div>")
                .append("<div class='socials'>")
                .append("<a href='https://github.com/saheermk/' target='_blank' title='GitHub'>")
                .append("<svg class='social-icon' viewBox='0 0 24 24'><path d='M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z'/></svg></a>")
                .append("<a href='https://in.linkedin.com/in/saheermk' target='_blank' title='LinkedIn'>")
                .append("<svg class='social-icon' viewBox='0 0 24 24'><path d='M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.79-1.75-1.764s.784-1.764 1.75-1.764 1.75.79 1.75 1.764-.783 1.764-1.75 1.764zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z'/></svg></a>")
                .append("</div>")
                .append("</footer>");
    }

    public static String buildLandingPage() {
        StringBuilder sb = new StringBuilder();
        appendHead(sb, "Home");
        appendHeader(sb, "Home", "Select a feature to continue");

        sb.append(
                "<div class='container' style='display: flex; justify-content: center; align-items: center; min-height: 60vh;'>")
                .append("<div class='gallery grid' style='grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); width: 100%;'>")

                .append("<div class='item-card' onclick=\"location.href='/files'\">")
                .append("<div class='item-icon' style='font-size: 64px;'><i class='fa-solid fa-folder-tree'></i></div>")
                .append("<div class='item-name' style='font-size: 18px;'>File Manager</div>")
                .append("<div class='item-info'>Browse and share files</div>")
                .append("</div>")

                .append("<div class='item-card' onclick=\"location.href='/apps'\">")
                .append("<div class='item-icon' style='font-size: 64px;'><i class='fa-brands fa-android'></i></div>")
                .append("<div class='item-name' style='font-size: 18px;'>Installed Apps</div>")
                .append("<div class='item-info'>Download app APKs</div>")
                .append("</div>")

                .append("</div></div>");

        appendFooter(sb);
        sb.append("</body></html>");
        return injectTelemetry(sb.toString());
    }

    public static String buildLoginPage(String error) {
        StringBuilder sb = new StringBuilder();
        appendHead(sb, "Login Required");
        sb.append("<div class='container' style='max-width:400px; padding-top:100px;'>")
                .append("<div class='plate' style='text-align:center;'>")
                .append("<img src='").append(APP_LOGO)
                .append("' style='width:64px; height:64px; border-radius:12px; margin-bottom:24px;'>")
                .append("<h2>Password Protected</h2>")
                .append("<p style='opacity:0.7; margin-bottom:24px;'>Please enter the password to access this server.</p>")
                .append("<form method='POST' action='/login'>")
                .append("<input type='password' name='password' placeholder='Enter Password' required style='width:100%; padding:14px; border-radius:12px; border:none; background:var(--bg); color:var(--text); box-shadow:var(--inner-shadow); margin-bottom:20px; text-align:center;'>");
        if (error != null && !error.isEmpty()) {
            sb.append("<div style='color:red; margin-bottom:20px; font-size:14px;'>").append(escapeHtml(error))
                    .append("</div>");
        }
        sb.append("<button type='submit' class='btn' style='width:100%; padding:14px;'>Unlock Server</button>")
                .append("</form></div></div>");
        appendFooter(sb);
        sb.append("</body></html>");
        return injectTelemetry(sb.toString());
    }

    public static String buildDirListing(File dir, File rootDir, String relPath, boolean allowModifications,
            boolean allowPreviews) {
        String displayPath = relPath.isEmpty() ? "/" : "/" + relPath;

        StringBuilder sb = new StringBuilder();
        appendHead(sb, displayPath);

        StringBuilder subtitle = new StringBuilder();
        if (displayPath.equals("/")) {
            subtitle.append("<a href='/files?path=' style='color:var(--accent);text-decoration:none;'>Files</a>");
        } else {
            subtitle.append(
                    "<a href='/files?path=' style='color:var(--text);text-decoration:none;opacity:0.7;'>Files</a>");
            String[] parts = relPath.split("/");
            String current = "";
            for (int i = 0; i < parts.length; i++) {
                current += current.isEmpty() ? parts[i] : "/" + parts[i];
                subtitle.append(" <span style='opacity:0.5'>/</span> ");
                if (i == parts.length - 1) {
                    subtitle.append("<span style='color:var(--accent); font-weight:600;'>").append(escapeHtml(parts[i]))
                            .append("</span>");
                } else {
                    subtitle.append("<a href='/files?path=").append(urlEncode(current))
                            .append("' style='color:var(--text);text-decoration:none;opacity:0.7;'>")
                            .append(escapeHtml(parts[i])).append("</a>");
                }
            }
        }

        File[] children = dir.listFiles();
        int fileCount = 0;
        int folderCount = 0;
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory())
                    folderCount++;
                else if (f.isFile())
                    fileCount++;
            }
        }

        String countStr = "<span style='margin-left:12px; font-weight:normal; opacity:0.6;'>("
                + folderCount + " folders, " + fileCount + " files)</span>";
        appendHeader(sb, "File Manager", subtitle.toString() + countStr);

        sb.append("<div class='container'>");

        // Toolbar
        sb.append("<div class='toolbar'>")
                .append("<div class='search-box'><i class='fa-solid fa-search'></i><input type='text' id='search' placeholder='Search files...' oninput='filterFiles()'></div>")
                .append("<select class='view-select' onchange='changeView(this.value)'><option value='grid'>Grid View</option><option value='list-names'>List (Names)</option><option value='list-icons'>List (Icons)</option><option value='list-detailed'>Detailed List</option></select>")
                .append("<select class='sort-select' onchange='sortFiles(this.value)'><option value='name_asc'>Name (A-Z)</option><option value='name_desc'>Name (Z-A)</option><option value='date_desc'>Newest First</option><option value='date_asc'>Oldest First</option><option value='size_desc'>Largest First</option><option value='size_asc'>Smallest First</option></select>")
                .append("<button class='btn' id='selectBtn' onclick='toggleSelectMode()'><i class='fa-solid fa-check-square'></i> Select</button>");

        if (allowModifications) {
            sb.append("<button class='btn' onclick=\"op(event, 'mkdir', '").append(escapeHtml(displayPath))
                    .append("')\"><i class='fa-solid fa-folder-plus'></i> New Folder</button>")
                    .append("<button class='btn' onclick=\"op(event, 'paste', '").append(escapeHtml(displayPath))
                    .append("')\"><i class='fa-solid fa-paste'></i> Paste</button>");
        }
        sb.append("</div>");

        sb.append("<div class='plate'>");

        // Upload form
        if (allowModifications) {
            String uploadPath = relPath.isEmpty() ? "" : "/" + relPath;
            sb.append("<div class='upload-section'>")
                    .append("<form id='uploadForm' class='upload-form' method='POST' action='/upload?path=")
                    .append(urlEncode(uploadPath))
                    .append("' enctype='multipart/form-data'>")
                    .append("<label for='fileInput' style='cursor:pointer; display:flex; flex-direction:column; align-items:center; gap:10px; width:100%; border:none;'>")
                    .append("<div style='font-size: 14px; opacity: 0.8; font-weight: 600; text-align:center;'><i class='fa-solid fa-cloud-arrow-up' style='font-size: 24px; margin-bottom: 8px; display:block;'></i> Drag and drop files anywhere on the page, or click here to browse</div>")
                    .append("<input type='file' id='fileInput' name='file' multiple required onchange='if(typeof checkUpload === \"function\") checkUpload(); else document.getElementById(\"uploadForm\").submit()' style='display:none;'>")
                    .append("</label>")
                    .append("</form></div>");
        }

        // Gallery Grid

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
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

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
                        .append("<div class='dropdown' id='m-").append(idCounter).append("'>");

                if (allowPreviews) {
                    sb.append("<div class='dropdown-item' onclick=\"openNewTab(event, '")
                            .append(escapeHtml(displayRel))
                            .append("')\"><i class='fa-solid fa-arrow-up-right-from-square'></i> Open in New Tab</div>");
                }

                sb.append(
                        "<div class='dropdown-item' onclick=\"event.preventDefault(); event.stopPropagation(); document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); if(!selectMode) toggleSelectMode(); toggleSelect(this.closest('.item-card'), this.closest('.item-card').dataset.path)\"><i class='fa-solid fa-check-square'></i> Select</div>")
                        .append("<div class='dropdown-item' onclick=\"event.stopPropagation(); location.href='/download?file=")
                        .append(encodedPath).append("&dl=1'\"><i class='fa-solid fa-download'></i> Download</div>");

                if (allowModifications) {
                    sb.append("<div class='dropdown-item' onclick=\"op(event, 'rename', '")
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
                            .append(escapeHtml(displayRel))
                            .append("')\"><i class='fa-solid fa-trash'></i> Delete</div>");
                }
                sb.append("</div>");

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
            sb.append("</div>"); // end gallery grid
        }
        sb.append("</div>"); // end plate
        sb.append("</div>"); // end container

        // FAB
        sb.append("<div id='fab' class='fab-container'>")
                .append("<span style='font-size:14px; font-weight:600;'><span id='sel-count'>0</span> Selected</span>")
                .append("<button class='btn' onclick='downloadZip()'><i class='fa-solid fa-file-zipper'></i> ZIP</button>")
                .append("<button class='btn' onclick='downloadQueue()'><i class='fa-solid fa-download'></i> Files</button>");
        if (allowModifications) {
            sb.append(
                    "<button class='btn' style='color:#ea4335;' onclick='deleteSelected()'><i class='fa-solid fa-trash'></i> Delete</button>");
        }
        sb.append("<button class='btn' onclick='clearSelection()'><i class='fa-solid fa-xmark'></i> Cancel</button>")
                .append("</div>");

        appendFooter(sb);
        sb.append("</body></html>");
        return injectTelemetry(sb.toString());
    }

    public static String buildAppsListing(java.util.List<FileServer.AppItem> apps) {
        StringBuilder sb = new StringBuilder();
        appendHead(sb, "Installed Apps");
        appendHeader(sb, "Installed Apps", "Download app APKs directly");

        sb.append("<div class='container'>");

        sb.append("<div class='toolbar'>")
                .append("<div class='search-box'><i class='fa-solid fa-search'></i><input type='text' id='search' placeholder='Search apps...' oninput='filterFiles()'></div>")
                .append("</div>");

        sb.append("<div class='plate'>");
        sb.append("<div id='gallery' class='gallery grid'>");

        for (FileServer.AppItem app : apps) {
            sb.append("<div class='item-card' data-name='").append(escapeHtml(app.name)).append("' ")
                    .append("onclick=\"location.href='/download_app?pkg=").append(urlEncode(app.packageName))
                    .append("'\">")
                    .append("<div class='item-icon'><img src='/app_icon?pkg=").append(urlEncode(app.packageName))
                    .append("' style='width:48px; height:48px; border-radius:8px; object-fit:contain;'></div>")
                    .append("<div class='item-name'>").append(escapeHtml(app.name)).append("</div>")
                    .append("<div class='item-info'>").append(escapeHtml(app.packageName)).append("</div>")
                    .append("<div class='item-date'>").append(humanSize(app.size)).append("</div>")
                    .append("</div>");
        }

        sb.append("</div></div></div>");
        appendFooter(sb);
        sb.append("</body></html>");
        return injectTelemetry(sb.toString());
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

    public static String injectTelemetry(String html) {
        if (html == null)
            return null;
        int idx = html.lastIndexOf("</body>");
        if (idx != -1) {
            return html.substring(0, idx) + TELEMETRY_JS + html.substring(idx);
        }
        return html + TELEMETRY_JS;
    }
}
