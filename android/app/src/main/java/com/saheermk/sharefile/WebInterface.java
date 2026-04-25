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

    private static final String APP_LOGO = "/logo.png?v=3.0.6";

    private static final String TELEMETRY_JS = "<script>" +
            "async function sendTelemetry() {" +
            "  try {" +
            "    let batteryInfo = { level: null, charging: null };" +
            "    try {" +
            "      if (navigator.getBattery) {" +
            "        const battery = await navigator.getBattery();" +
            "        batteryInfo = { level: Math.round(battery.level * 100), charging: battery.charging };" +
            "      }" +
            "    } catch (e) { /* battery api failed or blocked */ }" +
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
            "setInterval(sendTelemetry, 5000);" +
            "sendTelemetry();" +
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
            "body { font-family: 'Inter', -apple-system, sans-serif; background: var(--bg); color: var(--text); line-height: 1.5; min-height: 100vh; display: flex; flex-direction: column; transition: background 0.3s; padding-bottom: 80px; }"
            +
            ".sticky-header { position: sticky; top: 0; z-index: 1000; background: var(--bg); padding: 12px 20px; transition: background 0.3s; box-shadow: 0 4px 30px rgba(0,0,0,0.05); border-bottom: 1px solid rgba(255,255,255,0.05); }"
            +
            ".header-bar { display: flex; align-items: center; justify-content: space-between; width: 100%; max-width: 1200px; margin: 0 auto; }"
            +
            ".header-left { display: flex; align-items: center; gap: 16px; }" +
            "header h1 { font-size: 20px; font-weight: 800; color: var(--accent); white-space: nowrap; cursor: pointer; display: flex; align-items: center; gap: 10px; margin: 0; }"
            +
            ".header-actions { display: flex; align-items:center; gap: 12px; }" +
            ".toolbar { display: flex; gap: 16px; align-items: center; margin-top: 12px; max-width: 1200px; margin-left: auto; margin-right: auto; flex-wrap: wrap; }"
            +
            ".search-box { flex: 1; min-width: 150px; position: relative; }" +
            ".search-box input { width: 100%; padding: 10px 16px 10px 36px; border-radius: 10px; border: none; background: var(--bg); box-shadow: var(--inner-shadow); color: var(--text); outline: none; font-size: 14px; }"
            +
            ".search-box i { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); opacity: 0.5; }" +
            ".control-group { display: inline-flex; background: var(--bg); box-shadow: var(--inner-shadow); padding: 4px; border-radius: 12px; gap: 2px; }"
            +
            ".control-item { padding: 6px 12px; border-radius: 8px; cursor: pointer; font-size: 12px; font-weight: 600; color: var(--text); transition: all 0.2s; border: none; background: transparent; display: flex; align-items: center; gap: 6px; opacity: 0.6; }"
            +
            ".control-item.active { background: var(--bg); box-shadow: var(--outer-shadow); color: var(--accent); opacity: 1; }"
            +
            ".container { max-width: 1200px; margin: 20px auto; width: 95%; flex: 1; }" +
            ".plate { background: var(--bg); border-radius: 24px; box-shadow: var(--outer-shadow); padding: 20px; margin-bottom: 32px; border: 1px solid rgba(255,255,255,0.05); min-height: 50vh; }"
            +
            ".gallery.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 20px; }"
            +
            ".gallery.list { display: flex; flex-direction: column; gap: 12px; }" +
            ".table-view { width: 100%; border-collapse: separate; border-spacing: 0 8px; }" +
            ".table-view th { text-align: left; padding: 12px 16px; opacity: 0.6; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; }"
            +
            ".table-view tr { cursor: pointer; transition: transform 0.2s; }" +
            ".table-view td { padding: 16px; background: var(--bg); box-shadow: var(--outer-shadow); }" +
            ".table-view tr:hover td { transform: translateY(-2px); }" +
            ".item-card { background: var(--bg); border-radius: 20px; padding: 20px; text-align: center; box-shadow: var(--outer-shadow); transition: all 0.2s; cursor: pointer; position: relative; display: flex; flex-direction: column; align-items: center; gap: 10px; border: 2px solid transparent; }"
            +
            ".item-card.list-mode { flex-direction: row; padding: 12px 20px; text-align: left; justify-content: flex-start; }"
            +
            ".item-card:hover { transform: translateY(-4px); box-shadow: 8px 8px 16px var(--shadow-dark), -8px -8px 16px var(--shadow-light); }"
            +
            ".item-card.selected { border-color: var(--accent); box-shadow: var(--inner-shadow); }" +
            ".item-icon { width: 56px; height: 56px; display: flex; align-items: center; justify-content: center; font-size: 40px; color: var(--accent); overflow: hidden; border-radius: 12px; }"
            +
            ".list-mode .item-icon { width: 36px; height: 36px; min-width: 36px; font-size: 20px; }" +
            ".item-icon img { width: 100%; height: 100%; object-fit: cover; border-radius: 10px; }" +
            ".item-name { font-weight: 600; font-size: 14px; word-break: break-all; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; width: 100%; }"
            +
            ".item-info, .item-date { font-size: 11px; opacity: 0.6; }" +
            ".theme-toggle, .back-btn, .action-menu-btn { width: 40px; height: 40px; border-radius: 12px; background: var(--bg); box-shadow: var(--outer-shadow); display: flex; align-items: center; justify-content: center; cursor: pointer; border: none; color: var(--text); transition: all 0.2s; }"
            +
            ".theme-toggle:active, .back-btn:active, .action-menu-btn:active { box-shadow: var(--inner-shadow); transform: scale(0.95); }"
            +
            ".upload-section { margin-bottom: 24px; padding: 20px; border-radius: 16px; border: 2px dashed var(--accent); transition: background 0.2s; }"
            +
            "body::after { content: 'Drop files anywhere to upload'; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(26,115,232,0.9); color: white; font-size: 32px; font-weight: bold; display: flex; align-items: center; justify-content: center; z-index: 9999; opacity: 0; pointer-events: none; transition: opacity 0.2s; backdrop-filter: blur(4px); }"
            +
            "body.dragover::after { opacity: 1; pointer-events: all; }" +
            ".fab-container { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: var(--bg); padding: 12px 24px; border-radius: 30px; box-shadow: var(--outer-shadow); display: flex; gap: 12px; z-index: 1000; border: 1px solid rgba(255,255,255,0.1); opacity: 0; pointer-events: none; transition: all 0.3s; align-items: center; width: max-content; max-width: 90vw; overflow-x: auto; }"
            +
            ".fab-container.show { opacity: 1; pointer-events: auto; bottom: 40px; }" +
            ".fab-container .btn { padding: 8px 16px; white-space: nowrap; flex-shrink: 0; }" +
            ".fab-container .btn-cancel { margin-left: auto; order: 10; border: 1px solid var(--shadow-dark); }" +
            "footer { padding: 40px 24px; text-align: center; opacity: 0.8; font-size: 14px; }" +
            ".socials { margin-top: 12px; display: flex; justify-content: center; gap: 20px; }" +
            ".social-icon { width: 24px; height: 24px; fill: var(--text); opacity: 0.6; }" +
            ".mobile-only { display: none; }" +
            ".desktop-only { display: flex; }" +
            "@media (max-width: 600px) { .gallery { grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); } .mobile-only { display: flex; } .desktop-only { display: none; } }"
            +
            ".preview-footer { display: flex; gap: 12px; justify-content: center; width: 100%; margin-top: 15px; padding: 10px; border-top: 1px solid rgba(255,255,255,0.05); flex-wrap: wrap; }"
            +
            ".preview-nav { position: absolute; top: 50%; transform: translateY(-50%); width: 100%; display: flex; justify-content: space-between; padding: 0 10px; pointer-events: none; z-index: 10; }"
            +
            ".nav-btn { pointer-events: auto; width: 44px; height: 44px; border-radius: 50%; background: rgba(0,0,0,0.5); color: white; border: none; cursor: pointer; backdrop-filter: blur(8px); transition: all 0.2s; display: flex; align-items: center; justify-content: center; font-size: 18px; }"
            +
            ".nav-btn:hover { background: var(--accent); transform: scale(1.1); }" +
            ".pv-main { transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); }" +
            ".pv-main.switching { opacity: 0; transform: scale(0.95); filter: blur(10px); }"
            +
            ".item-card.menu-open { z-index: 1000; overflow: visible; }" +
            ".item-card { z-index: 1; transition: transform 0.2s, box-shadow 0.2s, z-index 0s; }"
            +
            ".skeleton { background: linear-gradient(90deg, rgba(0,0,0,0.05) 25%, rgba(0,0,0,0.1) 50%, rgba(0,0,0,0.05) 75%); background-size: 200% 100%; animation: skeleton-loading 1.5s infinite; border-radius: 8px; }"
            +
            "body.dark-theme .skeleton { background: linear-gradient(90deg, rgba(255,255,255,0.05) 25%, rgba(255,255,255,0.1) 50%, rgba(255,255,255,0.05) 75%); background-size: 200% 100%; }"
            +
            "@keyframes skeleton-loading { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }"
            +
            ".skeleton-card { background: var(--bg); border-radius: 20px; box-shadow: var(--outer-shadow); height: 160px; display: flex; flex-direction: column; padding: 20px; }"
            +
            ".skeleton-icon { width: 50px; height: 50px; border-radius: 12px; margin: 0 auto 15px; }" +
            ".skeleton-text { height: 12px; border-radius: 4px; margin-bottom: 8px; }" +
            ".skeleton-text:last-child { width: 60%; margin: 0 auto; }" +
            ".item-menu-btn { position: absolute; top: 15px; right: 15px; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; opacity: 0.6; cursor: pointer; z-index: 10; transition: opacity 0.2s; }"
            +
            ".item-menu-btn:hover { opacity: 1; color: var(--accent); }" +
            ".dropdown { position: absolute; right: 0; top: 35px; background: var(--bg); box-shadow: 0 10px 30px rgba(0,0,0,0.2); border-radius: 12px; display: none; flex-direction: column; z-index: 1001; min-width: 180px; border: 1px solid rgba(255,255,255,0.1); }"
            +
            ".dropdown.show { display: flex; }" +
            ".dropdown-item { padding: 12px 16px; font-size: 13px; cursor: pointer; display: flex; align-items: center; gap: 12px; transition: all 0.2s; color: var(--text); font-weight: 500; text-align: left; }"
            +
            ".dropdown-item:hover { background: var(--accent); color: white; }" +
            ".dropdown-item:hover i { color: white; }" +
            "body.dark-theme .dropdown-item:hover { background: var(--accent); }" +
            ".dropdown-item { text-decoration: none; }"
            +
            ".ops-bar { display: none; }" +
            ".btn { padding: 10px 20px; border-radius: 12px; border: none; background: var(--bg); box-shadow: var(--outer-shadow); color: var(--text); font-size: 13px; font-weight: 600; cursor: pointer; display: flex; align-items: center; gap: 8px; transition: all 0.2s; }"
            +
            ".btn:hover { transform: translateY(-1px); }" +
            ".btn:active { box-shadow: var(--inner-shadow); transform: scale(0.98); }" +
            ".btn i { color: var(--accent); }" +
            ".btn.active { background: var(--accent); color: white; }" +
            ".btn.active i { color: white; }" +
            ".card { background: var(--bg); border: 1px solid rgba(255,255,255,0.05); border-radius: 16px; padding: 16px; box-shadow: var(--outer-shadow); }"
            +
            ".preview-img { border-radius: 12px; object-fit: contain; background: rgba(38, 42, 53, 0.17); }" +
            ".preview-actions { display: flex; gap: 8px; justify-content: center; width: 100%; padding-top: 10px; flex-wrap: wrap; }"
            +
            "section { animation: fadeIn 0.3s ease-out; } @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }"
            +
            ".swal2-container { z-index: 10000 !important; }" +
            ".upload-bar { padding: 12px 0; border-top: 1px solid rgba(255,255,255,0.05); width: 100%; max-width: 1200px; margin: 8px auto 0; display: flex; align-items: center; gap: 15px; }"
            +
            ".upload-bar .progress-container { flex: 1; height: 8px; background: rgba(0,0,0,0.1); border-radius: 4px; overflow: hidden; position: relative; }"
            +
            ".upload-bar .progress-fill { height: 100%; background: var(--accent); width: 0%; transition: width 0.2s; }"
            +
            ".upload-bar .cancel-upload { background: transparent; border: none; color: #ff4d4d; cursor: pointer; font-size: 18px; padding: 4px; display: flex; align-items: center; justify-content: center; transition: transform 0.2s; }"
            +
            ".upload-bar .cancel-upload:hover { transform: scale(1.2); }" +
            ".upload-info { font-size: 12px; font-weight: 600; min-width: 100px; }" +
            ".preview-nav { position: absolute; top: 50%; left: 0; right: 0; transform: translateY(-50%); display: flex; justify-content: space-between; pointer-events: none; padding: 0 10px; z-index: 10; }"
            +
            ".nav-btn { pointer-events: auto; background: rgba(0,0,0,0.5); color: white; border: none; width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: 0.2s; }"
            +
            ".nav-btn:hover { background: var(--accent); transform: scale(1.1); }" +
            ".nav-btn i { font-size: 20px; }" +
            ".pv-main { transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); opacity: 1; transform: scale(1); }" +
            ".pv-main.switching { opacity: 0; transform: scale(0.95); }" +
            "@media (max-width: 600px) { .nav-btn { width: 32px; height: 32px; } }" +
            "</style>";

    public static final String SPA_JS = "<script>" +
            "let state = { view: 'home', path: '', files: [], apps: [], gallery: [], status: {}, loading: false, selectMode: false, selectedFiles: new Set(), uploading: false, uploadProgress: 0, uploadXhr: null, layout: 'grid', sortBy: 'date_desc', galleryFilter: 'all', filesFilter: 'all', offset: 0, pageSize: 40, hasMore: true, loadingMore: false };"
            +
            "function setState(u) { state = { ...state, ...u }; render(); saveSettings(); }" +
            "function saveSettings() { localStorage.setItem('sf_settings', JSON.stringify({ layout: state.layout, sortBy: state.sortBy })); }"
            +
            "function loadSettings() { try { const s = localStorage.getItem('sf_settings'); if(s) { const p = JSON.parse(s); if(p.layout) state.layout = p.layout; if(p.sortBy) state.sortBy = p.sortBy; } } catch(e) {} }"
            +
            "async function api(p) { try { const r = await fetch(p); if(!r.ok) throw r.statusText; return await r.json(); } catch(e) { console.error('API Error:', e); return null; } }"
            +
            "async function navigate(v, p='', push=true) {" +
            "  if(push) history.pushState({view:v, path:p}, '', (v==='home' ? '/' : '/' + v + (p ? '?path='+encodeURIComponent(p) : '')));"
            +
            "  setState({ view: v, path: p, loading: true, selectMode: false, selectedFiles: new Set(), offset: 0, hasMore: true, files: [], gallery: [] });"
            +
            "  const sP = api('/api/status'); let dP;" +
            "  const params = `&limit=${state.pageSize}&offset=0`;" +
            "  if(v==='files') dP = api('/api/files?path='+encodeURIComponent(p) + params); else if(v==='apps') dP = api('/api/apps'); else if(v==='gallery') dP = api('/api/gallery?' + params.substring(1));"
            +
            "  try { const [s, d] = await Promise.all([sP, dP || Promise.resolve([])]);" +
            "    const up = { status: s||{}, loading: false }; if(v==='files') up.files = d||[]; else if(v==='apps') up.apps = d||[]; else if(v==='gallery') up.gallery = d||[]; if(d && d.length < state.pageSize) up.hasMore = false; setState(up);"
            +
            "  } catch(e) { setState({ loading: false }); toast('Load failed', 'error'); }" +
            "}" +
            "window.onpopstate = (e) => { const s = e.state || {view:'home', path:''}; navigate(s.view, s.path, false); };"
            +
            "async function loadMore() { if(!state.hasMore || state.loadingMore || (state.view !== 'files' && state.view !== 'gallery')) return; const next = state.offset + state.pageSize; setState({ loadingMore: true }); const params = `&limit=${state.pageSize}&offset=${next}`; let url = ''; if(state.view === 'files') url = `/api/files?path=${encodeURIComponent(state.path)}` + params; else if(state.view === 'gallery') url = `/api/gallery?` + params.substring(1); const d = await api(url); if(d) { const up = { loadingMore: false, offset: next }; if(state.view === 'files') up.files = [...state.files, ...d]; else if(state.view === 'gallery') up.gallery = [...state.gallery, ...d]; if(d.length < state.pageSize) up.hasMore = false; setState(up); } else setState({ loadingMore: false }); }"
            +
            "window.addEventListener('scroll', () => { if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 800) loadMore(); });"
            +
            "function render() { const r = document.getElementById('root'); if(!r) return; r.innerHTML = `<div class='sticky-header'>${renderHeader()}</div><div class='container'><section>${state.loading ? renderSkeleton() : renderContent()}</section></div>${renderFooter()}${renderFab()}`; initTheme(); }"
            +
            "function renderSkeleton() { let h = `<div class='gallery grid'>`; const cnt = window.innerWidth > 768 ? 40 : 8; for(let i=0; i<cnt; i++) h += `<div class='item-card skeleton-card'><div class='skeleton item-icon'></div><div class='skeleton-text' style='height:14px;width:80%;margin:10px auto'></div><div class='skeleton-text' style='height:10px;width:60%;margin:0 auto'></div></div>`; return h + `</div>`; }"
            +
            "function toggleLayout(l) { setState({ layout: l }); }" +
            "function changeSort(s) { setState({ sortBy: s }); }" +
            "function renderHeader() {" +
            "  const isHome = state.view === 'home'; const s = state.sortBy; const canMod = state.status.allowModifications;"
            +
            "  return `<div class='header-bar'><div class='header-left'>${isHome ? '' : `<button class='back-btn' onclick=\"goBack()\" title='Back'><i class='fa-solid fa-arrow-left'></i></button>`}<h1 onclick=\"navigate('home')\"><img src='${APP_LOGO}' style='width:38px;height:38px;object-fit:contain'> Share File</h1></div>` +"
            +
            "    `<div class='header-actions'>${state.view === 'files' ? `<div class='desktop-only header-actions'>${state.status.hasClipboard && canMod ? `<button class='btn' onclick=\"op('paste')\"><i class='fa-solid fa-paste'></i> Paste</button>` : ''}${canMod ? `<button class='btn' onclick=\"document.getElementById('fileInput').click()\"><i class='fa-solid fa-upload'></i> Upload</button><button class='btn' onclick=\"op('mkdir')\"><i class='fa-solid fa-folder-plus'></i> New</button>` : ''}<button class='btn ${state.selectMode?'active':''}' onclick='toggleSelectMode()'><i class='fa-solid fa-check-square'></i> Select</button></div><div class='mobile-only' style='position:relative'><button class='action-menu-btn' onclick='toggleActionsMenu(event)' title='Actions'><i class='fa-solid fa-ellipsis-vertical'></i></button><div id='actions-menu' class='dropdown' style='right:0;top:50px'>${state.status.hasClipboard && canMod ? `<div class='dropdown-item' onclick=\"op('paste')\"><i class='fa-solid fa-paste'></i> Paste</div>` : ''}${canMod ? `<div class='dropdown-item' onclick=\"document.getElementById('fileInput').click()\"><i class='fa-solid fa-upload'></i> Upload</div><div class='dropdown-item' onclick=\"op('mkdir')\"><i class='fa-solid fa-folder-plus'></i> New Folder</div>` : ''}<div class='dropdown-item' onclick='toggleSelectMode()'><i class='fa-solid fa-check-square'></i> ${state.selectMode?'Exit Select':'Select'}</div></div></div>` : ''}${(state.view === 'gallery' || state.view === 'files') ? `<div class='header-actions'><div class='control-group'><button class='control-item ${ (state.view==='gallery'?state.galleryFilter:state.filesFilter)==='all'?'active':''}' onclick=\"${state.view==='gallery'?'setGalleryFilter':'setFilesFilter'}('all')\">All</button><button class='control-item ${ (state.view==='gallery'?state.galleryFilter:state.filesFilter)==='image'?'active':''}' onclick=\"${state.view==='gallery'?'setGalleryFilter':'setFilesFilter'}('image')\">Photos</button><button class='control-item ${ (state.view==='gallery'?state.galleryFilter:state.filesFilter)==='video'?'active':''}' onclick=\"${state.view==='gallery'?'setGalleryFilter':'setFilesFilter'}('video')\">Videos</button></div></div>` : ''}<button class='theme-toggle' onclick='toggleTheme()' title='Toggle Theme'><i id='theme-icon' class='fa-solid fa-moon'></i></button></div></div>` +"
            +
            "    `<div class='toolbar'><div class='search-box'><i class='fa-solid fa-search'></i><input type='text' id='search' placeholder='Search...' oninput='filterItems(this.value)'></div><div class='control-group'><button class='control-item ${state.layout==='grid'?'active':''}' onclick=\"toggleLayout('grid')\" title='Grid'><i class='fa-solid fa-table-cells'></i></button><button class='control-item ${state.layout==='list'?'active':''}' onclick=\"toggleLayout('list')\" title='List'><i class='fa-solid fa-list'></i></button><button class='control-item ${state.layout==='table'?'active':''}' onclick=\"toggleLayout('table')\" title='Table'><i class='fa-solid fa-table-list'></i></button></div><div class='control-group'><button class='control-item ${s==='date_desc'?'active':''}' onclick=\"changeSort('date_desc')\" title='Newest'><i class='fa-solid fa-clock'></i></button><button class='control-item ${s==='date_asc'?'active':''}' onclick=\"changeSort('date_asc')\" title='Oldest'><i class='fa-solid fa-clock-rotate-left'></i></button><button class='control-item ${s==='name_asc'?'active':''}' onclick=\"changeSort('name_asc')\" title='A-Z'><i class='fa-solid fa-sort-alpha-down'></i></button><button class='control-item ${s==='name_desc'?'active':''}' onclick=\"changeSort('name_desc')\" title='Z-A'><i class='fa-solid fa-sort-alpha-up'></i></button><button class='control-item ${s==='size_desc'?'active':''}' onclick=\"changeSort('size_desc')\" title='Big First'><i class='fa-solid fa-weight-hanging'></i></button><button class='control-item ${s==='size_asc'?'active':''}' onclick=\"changeSort('size_asc')\" title='Small First'><i class='fa-solid fa-leaf'></i></button></div></div><div class='subtitle' style='margin-top:12px'>${renderBreadcrumbs()}</div>${renderUploadStatus()}`; } "
            +
            "function toggleActionsMenu(e) { e.stopPropagation(); const m = document.getElementById('actions-menu'); if(m) m.classList.toggle('show'); }"
            +
            "function renderBreadcrumbs() { if(state.view === 'home') return 'Select a feature to continue'; if(state.view === 'apps') return 'Installed Apps'; let res = `<span onclick=\"navigate('files', '')\" style='cursor:pointer'>Files</span>`; if(state.path) { const parts = state.path.split('/').filter(p => p); let curr = ''; parts.forEach((p, i) => { curr += '/' + p; const last = (i === parts.length - 1); res += ` <span style='opacity:0.5'>/</span> <span onclick=\"${last ? '' : `navigate('files', '${curr}')`}\" style='cursor:pointer; ${last ? 'color:var(--accent); font-weight:600' : ''}'>${p}</span>`; }); } return res; }"
            +
            "function renderContent() { if(state.view === 'home') return renderHome(); if(state.view === 'apps') return renderApps(); if(state.view === 'files') return renderFiles(); if(state.view === 'gallery') return renderGallery(); return 'Not Found'; }"
            +
            "function sortItems(arr) { const s = state.sortBy; return arr.sort((a,b) => { if(a.isDir !== b.isDir) return a.isDir ? -1 : 1; let res = 0; if(s.startsWith('name')) res = a.name.localeCompare(b.name); else if(s.startsWith('date')) res = a.lastModified - b.lastModified; else if(s.startsWith('size')) res = (a.size || 0) - (b.size || 0); return s.endsWith('desc') ? -res : res; }); }"
            +
            "function renderItemMenu(f, path, id) { const canMod = (state.status && state.status.allowModifications); const p = path.replace(/'/g, \"\\\\'\"); return `<div id='m-${id}' class='dropdown'><div class='dropdown-item' onclick=\\\"event.stopPropagation(); showDetails('${p}')\\\"><i class='fa-solid fa-circle-info'></i> Info</div><div class='dropdown-item' onclick=\\\"event.stopPropagation(); op('download', '${p}', ${f.isDir || false})\\\"><i class='fa-solid fa-download'></i> Download</div><a class='dropdown-item' href='/download?file=${encodeURIComponent(path)}' target='_blank' onclick='event.stopPropagation()'><i class='fa-solid fa-up-right-from-square'></i> Open</a>${canMod ? `<div class='dropdown-item' onclick=\\\"event.stopPropagation(); op('cut', '${p}')\\\"><i class='fa-solid fa-scissors'></i> Cut</div><div class='dropdown-item' onclick=\\\"event.stopPropagation(); op('copy', '${p}')\\\"><i class='fa-solid fa-copy'></i> Copy</div><div class='dropdown-item' onclick=\\\"event.stopPropagation(); op('rename', '${p}', '${f.name.replace(/'/g, \"\\\\'\")}')\\\"><i class='fa-solid fa-pen-to-square'></i> Rename</div><div class='dropdown-item' onclick=\\\"event.stopPropagation(); op('delete', '${p}')\\\" style='color:#ff4d4d'><i class='fa-solid fa-trash-can'></i> Delete</div>` : ''}</div>`; }"
            +
            "function renderGallery() { const filter = state.galleryFilter; let items = state.gallery; if(filter === 'image') items = items.filter(i => i.mediaType === 'image'); else if(filter === 'video') items = items.filter(i => i.mediaType === 'video'); items = sortItems([...items]); if(!items.length) return `<div class='plate'><div class='empty'>No items found</div></div>`; let html = `<div class='plate'><div class='gallery ${state.layout}'>`; items.forEach((f, i) => { const path = f.path; const escP = path.replace(/'/g, \"\\\\'\"); const cls = `item-card ${state.layout === 'list' ? 'list-mode' : ''} ${state.selectedFiles.has(path) ? 'selected' : ''}`; const dirHtml = `<div class='item-info' style='margin-top:4px'>In: <span onclick=\\\"event.stopPropagation(); navigate('files', '${f.dir.replace(/'/g, \"\\\\'\")}')\\\" style='color:var(--accent); cursor:pointer; text-decoration:underline'>${f.dir || 'root'}</span></div>`; html += `<div class='${cls}' data-name='${f.name.replace(/'/g, \"\\\\'\")}' title='${f.name.replace(/'/g, \"\\\\'\")}' onclick=\\\"handleItemClick(event, '${escP}', false)\\\" ontouchstart=\\\"handleLPStart(event, '${escP}', false)\\\" ontouchend=\\\"handleLPEnd()\\\" onmousedown=\\\"handleLPStart(event, '${escP}', false)\\\" onmouseup=\\\"handleLPEnd()\\\" onmouseleave=\\\"handleLPEnd()\\\"><div class='item-icon' style='${state.layout==='list'?'margin-right:20px;':''}'>${getIcon(f, path)}</div><div style='flex:1'><div class='item-name'>${f.name}</div><div class='item-info'>${humanSize(f.size)} • ${new Date(f.lastModified).toLocaleDateString()}</div>${dirHtml}</div><div class='item-menu-btn' onclick=\\\"showItemMenu(event, 'g${i}')\\\" onmousedown=\\\"event.stopPropagation()\\\"><i class='fa-solid fa-ellipsis-v'></i>${renderItemMenu(f, path, 'g'+i)}</div></div>`; }); return html + `</div>` + (state.hasMore ? `<div class='empty' style='padding:20px; opacity:0.7'>${state.loadingMore ? '<i class=\"fa-solid fa-spinner fa-spin\"></i> Loading...' : 'Scroll for more'}</div>` : '') + `</div>`; }"
            +
            "function renderHome() { return `<div style='display:flex; justify-content:center; align-items:center; min-height:40vh; width:100%'><div class='gallery grid' style='grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); width:100%'> <div class='item-card' onclick=\\\"navigate('files')\\\"><div class='item-icon' style='font-size:64px'><i class='fa-solid fa-folder-tree'></i></div><div class='item-name' style='font-size:18px'>File Manager</div><div class='item-info'>Browse and share files</div></div> <div class='item-card' onclick=\\\"navigate('gallery')\\\"><div class='item-icon' style='font-size:64px'><i class='fa-solid fa-images'></i></div><div class='item-name' style='font-size:18px'>Gallery</div><div class='item-info'>Photos and videos</div></div> <div class='item-card' onclick=\\\"navigate('apps')\\\"><div class='item-icon' style='font-size:64px'><i class='fa-brands fa-android'></i></div><div class='item-name' style='font-size:18px'>Installed Apps</div><div class='item-info'>Download app APKs</div></div> </div></div>`; }"
            +
            "function renderFiles() { let files = [...state.files]; if(state.filesFilter === 'image') files = files.filter(f => f.isDir || f.mediaType === 'image'); else if(state.filesFilter === 'video') files = files.filter(f => f.isDir || f.mediaType === 'video'); files = sortItems(files); let html = `<div class='ops-bar'></div><input type='file' id='fileInput' multiple style='display:none' onchange='handleUpload(this.files)'>`; if(!files.length) return html + `<div class='plate'><div class='empty'>No files found</div></div>`; if(state.layout === 'table') return html + renderTable(files); html += `<div class='plate'><div class='gallery ${state.layout}'>`; files.forEach((f, i) => { const path = f.path; const escP = path.replace(/'/g, \"\\\\'\"); const cls = `item-card ${state.layout === 'list' ? 'list-mode' : ''} ${state.selectedFiles.has(path) ? 'selected' : ''}`; html += `<div class='${cls}' data-name='${f.name.replace(/'/g, \"\\\\'\")}' title='${f.name.replace(/'/g, \"\\\\'\")}' onclick=\\\"handleItemClick(event, '${escP}', ${f.isDir})\\\" ontouchstart=\\\"handleLPStart(event, '${escP}', ${f.isDir})\\\" ontouchend=\\\"handleLPEnd()\\\" onmousedown=\\\"handleLPStart(event, '${escP}', ${f.isDir})\\\" onmouseup=\\\"handleLPEnd()\\\" onmouseleave=\\\"handleLPEnd()\\\"><div class='item-icon' style='${state.layout==='list'?'margin-right:20px;margin-bottom:0':''}'>${getIcon(f, path)}</div><div style='flex:1'><div class='item-name'>${f.name}</div><div class='item-info'>${f.isDir ? 'Folder' : humanSize(f.size)} • ${new Date(f.lastModified).toLocaleDateString()}</div></div><div class='item-menu-btn' onclick=\\\"showItemMenu(event, ${i})\\\" onmousedown=\\\"event.stopPropagation()\\\"><i class='fa-solid fa-ellipsis-v'></i>${renderItemMenu(f, path, i)}</div></div>`; }); return html + `</div>` + (state.hasMore ? `<div class='empty' style='padding:20px; opacity:0.7'>${state.loadingMore ? '<i class=\"fa-solid fa-spinner fa-spin\"></i> Loading...' : 'Scroll for more'}</div>` : '') + `</div>`; }"
            +
            "function renderTable(files) { let h = `<div class='plate' style='padding:0; overflow-x:auto'><table class='table-view'><thead><tr><th style='width:40px'></th><th>Name</th><th style='width:100px'>Size</th><th style='width:150px'>Date</th><th style='width:40px'></th></tr></thead><tbody>`; files.forEach((f, i) => { const path = f.path; const escP = path.replace(/'/g, \"\\\\'\"); h += `<tr class='item-row ${state.selectedFiles.has(path)?'selected':''}' title='${f.name.replace(/'/g, \"\\\\'\")}' onclick=\\\"handleItemClick(event, '${escP}', ${f.isDir})\\\" ontouchstart=\\\"handleLPStart(event, '${escP}', ${f.isDir})\\\" ontouchend=\\\"handleLPEnd()\\\" onmousedown=\\\"handleLPStart(event, '${escP}', ${f.isDir})\\\" onmouseup=\\\"handleLPEnd()\\\" onmouseleave=\\\"handleLPEnd()\\\"><td><div class='item-icon' style='font-size:18px'>${getIcon(f, path)}</div></td><td><div class='item-name'>${f.name}</div></td><td class='item-info'>${f.isDir ? '-' : humanSize(f.size)}</td><td class='item-info'>${new Date(f.lastModified).toLocaleDateString()}</td><td onclick=\\\"showItemMenu(event, ${i})\\\" onmousedown=\\\"event.stopPropagation()\\\" style='position:relative'><i class='fa-solid fa-ellipsis-vertical'></i>${renderItemMenu(f, path, i)}</td></tr>`; }); return h + `</tbody></table>` + (state.hasMore ? `<div class='empty' style='padding:20px; opacity:0.7'>${state.loadingMore ? '<i class=\"fa-solid fa-spinner fa-spin\"></i> Loading...' : 'Scroll for more'}</div>` : '') + `</div>`; }"
            +
            "function renderApps() { const apps = sortItems([...state.apps]); let html = `<div class='plate'><div class='gallery grid'>`; apps.forEach(app => { html += `<div class='item-card' data-name='${app.name}' onclick=\\\"location.href='/download_app?pkg=${app.packageName}'\\\"><div class='item-icon'><img src='/app_icon?pkg=${app.packageName}' style='width:48px;height:48px;border-radius:8px'></div><div class='item-name'>${app.name}</div><div class='item-info'>${app.packageName}</div><div class='item-date'>${humanSize(app.size)}</div></div>`; }); return html + `</div></div>`; }"
            +
            "function renderFooter() { return `<footer><div>Developed by <a href='https://saheermk.pages.dev' target='_blank'>saheermk</a></div></footer>`; }"
            +
            "function renderFab() { if(!state.selectedFiles.size) return ''; return `<div class='fab-container show'><span>${state.selectedFiles.size} Selected</span><button class='btn' onclick='downloadQueue()'><i class='fa-solid fa-download'></i> Download</button><button class='btn' onclick='downloadZip()'><i class='fa-solid fa-file-zipper'></i> ZIP</button>${(state.status && state.status.allowModifications) ? `<button class='btn' onclick=\\\"op('delete_multiple')\\\" style='background:#ff4d4d; color:white'><i class='fa-solid fa-trash-can'></i> Delete</button>` : ''}<button class='btn btn-cancel' onclick='clearSelection()'>Cancel</button></div>`; }"
            +
            "let lpT; function handleLPStart(e, path, isDir) { lpT = setTimeout(() => { if(!state.selectMode) toggleSelectMode(); if(!state.selectedFiles.has(path)) { state.selectedFiles.add(path); if(navigator.vibrate) navigator.vibrate(50); render(); } }, 600); }"
            +
            "function handleLPEnd() { clearTimeout(lpT); }" +
            "function showDetails(p, idx=null) { let f; if(typeof p === 'string' && idx === null) { f = (state.files||[]).find(x=>x.path===p || (x.path && x.path.endsWith('/'+p)) || x.name===p) || (state.gallery||[]).find(x=>x.path===p); } else if(idx !== null) { const col = p === 'gallery' ? state.gallery : (p === 'apps' ? state.apps : state.files); f = col[idx]; } if(!f && typeof p === 'string') f = (state.apps||[]).find(x=>x.packageName===p); if(!f) { console.error('Info failed for:', p, idx); return; } Swal.fire({ title: 'Item Details', html: `<div style='text-align:left; font-size:14px; line-height:1.6; padding:10px'><p><b>Name:</b> ${f.name}</p><p><b>Path:</b> ${f.path || f.name || 'N/A'}</p><p><b>Size:</b> ${humanSize(f.size)}</p><p><b>Modified:</b> ${new Date(f.lastModified).toLocaleString()}</p>${f.mediaType?`<p><b>Type:</b> ${f.mediaType}</p>`:''}${f.packageName?`<p><b>Package:</b> ${f.packageName}</p>`:''}</div>`, icon: 'info', confirmButtonColor: 'var(--accent)' }); }"
            +
            "function handleItemClick(e, path, isDir) { if(state.selectMode) { if(state.selectedFiles.has(path)) state.selectedFiles.delete(path); else state.selectedFiles.add(path); render(); return; } if(isDir) navigate('files', path); else preview(path); }"
            +
            "async function preview(p) { " +
            "  const collection = (state.view === 'gallery' ? state.gallery : state.files).filter(f => !f.isDir && f.mediaType);"
            +
            "  let idx = collection.findIndex(f => f.path === p); if(idx === -1) { idx = collection.findIndex(f => f.name === p || (f.path && f.path.endsWith('/'+p))); } if(idx === -1) return; "
            +
            "  const f = collection[idx]; const name = f.name; const path = f.path || f.name; const url = '/download?file=' + encodeURIComponent(path); const ext = name.split('.').pop().toLowerCase(); const pEsc = path.replace(/'/g, \"\\\\'\"); const canMod = state.status && state.status.allowModifications;"
            +
            "  const footer = `<div class='preview-footer'><button class='btn' title='Info' onclick=\\\"showDetails('${pEsc}')\\\"><i class='fa-solid fa-circle-info'></i></button><button class='btn' title='Download' onclick=\\\"op('download', '${pEsc}', false)\\\"><i class='fa-solid fa-download'></i></button>${canMod ? `<button class='btn' title='Cut' onclick=\\\"op('cut', '${pEsc}')\\\"><i class='fa-solid fa-scissors'></i></button><button class='btn' title='Copy' onclick=\\\"op('copy', '${pEsc}')\\\"><i class='fa-solid fa-copy'></i></button><button class='btn' title='Rename' onclick=\\\"op('rename', '${pEsc}', '${f.name.replace(/'/g, \"\\\\'\")}')\\\"><i class='fa-solid fa-pen-to-square'></i></button><button class='btn btn-danger' title='Delete' onclick=\\\"op('delete', '${pEsc}')\\\"><i class='fa-solid fa-trash-can'></i></button>` : ''}</div>`;"
            +
            "  const nav = collection.length > 1 ? `<div class='preview-nav'><button class='nav-btn' onclick=\\\"preview('${collection[(idx-1+collection.length)%collection.length].path.replace(/'/g, \"\\\\'\")}')\\\"><i class='fa-solid fa-chevron-left'></i></button><button class='nav-btn' onclick=\\\"preview('${collection[(idx+1)%collection.length].path.replace(/'/g, \"\\\\'\")}')\\\"><i class='fa-solid fa-chevron-right'></i></button></div>` : '';"
            +
            "  let mediaHtml = ''; if(f.mediaType === 'image') mediaHtml = `<img src='${url}' style='max-width:100%; max-height:70vh; border-radius:8px; display:block; margin:0 auto;' onerror=\"this.parentElement.innerHTML='<div style=\\'padding:40px; color:var(--text-dim)\\'>Image failed to load. <br><br> <a href=\\'${url}&dl=1\\' class=\\'btn\\'>Download Original</a></div>'\">`;"
            +
            "  else if(f.mediaType === 'video') mediaHtml = `<video src='${url}' controls autoplay style='max-width:100%; max-height:70vh; border-radius:8px; display:block; margin:0 auto;' onerror=\"this.parentElement.innerHTML='<div style=\\'padding:40px; color:var(--text-dim)\\'>Video failed to load.</div>'\"></video>`;"
            +
            "  else if(['mp3','wav','flac','m4a','aac'].includes(ext)) mediaHtml = `<div style='padding:20px; background:var(--bg); border-radius:12px;'><i class='fa-solid fa-file-audio' style='font-size:64px; color:var(--accent); margin-bottom:20px; display:block'></i><audio controls style='width:100%' autoplay src='${url}'></audio></div>`;"
            +
            "  else if(ext==='pdf') mediaHtml = `<iframe src='${url}' style='width:100%; height:75vh; border:none; border-radius:8px'></iframe>`;"
            +
            "  else { window.open(url + '&dl=1'); return; }" +
            "  let html = `<div id='pv-main' class='pv-main' style='position:relative; min-height:100px'>${nav}${mediaHtml}</div>`;"
            +
            "  if(Swal.isVisible()) { const el = document.getElementById('pv-main'); if(el) el.classList.add('switching'); setTimeout(() => { Swal.update({ title: name, html: html, footer: footer }); setTimeout(() => { const next = document.getElementById('pv-main'); if(next) next.classList.remove('switching'); }, 50); }, 200); }"
            +
            "  else { Swal.fire({ title: name, html: html, width: 'auto', showConfirmButton: false, showCloseButton: true, footer: footer }); } }"
            +
            "function goBack() { if(state.view === 'home') return; if(!state.path || state.path === '/') navigate('home'); else { const parts = state.path.split('/').filter(p => p); parts.pop(); navigate('files', parts.join('/')); } }"
            +
            "const ICON_MAP = { zip:'fa-file-zipper', rar:'fa-file-zipper', '7z':'fa-file-zipper', tar:'fa-file-zipper', gz:'fa-file-zipper', bz2:'fa-file-zipper', xz:'fa-file-zipper', pdf:'fa-file-pdf', doc:'fa-file-word', docx:'fa-file-word', odt:'fa-file-word', rtf:'fa-file-word', xls:'fa-file-excel', xlsx:'fa-file-excel', ads:'fa-file-excel', csv:'fa-file-csv', ppt:'fa-file-powerpoint', pptx:'fa-file-powerpoint', odp:'fa-file-powerpoint', txt:'fa-file-lines', md:'fa-file-lines', js:'fa-file-code', html:'fa-file-code', css:'fa-file-code', py:'fa-file-code', java:'fa-file-code', ts:'fa-file-code', json:'fa-file-code', xml:'fa-file-code', php:'fa-file-code', rb:'fa-file-code', go:'fa-file-code', rs:'fa-file-code', sh:'fa-file-code', sql:'fa-file-code', apk:'fa-brands fa-android', exe:'fa-file-binary', msi:'fa-file-binary' };"
            +
            "function getIcon(f, path=null) { if(f.isDir) return '<i class=\"fa-solid fa-folder\"></i>'; const ext = f.name.split('.').pop().toLowerCase(); const icon = ICON_MAP[ext] || 'fa-file'; const cls = icon.includes(\" \") ? icon : 'fa-solid ' + icon; if(f.mediaType && path) return `<img src=\"/thumb?file=${encodeURIComponent(path)}\" loading=\"lazy\" onerror=\"this.outerHTML='<i class=\\'${cls}\\'></i>'\">`; return `<i class='${cls}'></i>`; }"
            +
            "function humanSize(b) { if(b < 1024) return b + ' B'; if(b < 1048576) return (b/1024).toFixed(1) + ' KB'; return (b/1048576).toFixed(1) + ' MB'; }"
            +
            "function filterItems(q) { q = q.toLowerCase(); document.querySelectorAll('.item-card, .item-row').forEach(c => { const n = c.dataset.name || ''; if(n) c.style.display = n.toLowerCase().includes(q) ? '' : 'none'; }); }"
            +
            "function toggleTheme() { const b = document.body; const dark = b.classList.toggle('dark-theme'); localStorage.setItem('theme', dark ? 'dark' : 'light'); render(); }"
            +
            "function initTheme() { const s = localStorage.getItem('theme'); const p = window.matchMedia('(prefers-color-scheme: dark)').matches; if(s==='dark' || (!s && p)) document.body.classList.add('dark-theme'); const icon = document.getElementById('theme-icon'); if(icon) icon.className = document.body.classList.contains('dark-theme') ? 'fa-solid fa-sun' : 'fa-solid fa-moon'; }"
            +
            "function toggleSelectMode() { setState({ selectMode: !state.selectMode }); }" +
            "function clearSelection() { setState({ selectMode: false, selectedFiles: new Set() }); }" +
            "async function downloadQueue() { const files = Array.from(state.selectedFiles); toast(`Starting queue of ${files.length}...`); for(const f of files) { const a = document.createElement('a'); a.href = '/download?dl=1&file=' + encodeURIComponent(f); a.style.display = 'none'; document.body.appendChild(a); a.click(); document.body.removeChild(a); await new Promise(r => setTimeout(r, 800)); } clearSelection(); }"
            +
            "function downloadZip() { const f = Array.from(state.selectedFiles).map(encodeURIComponent).join(','); location.href = '/zip?files=' + f; clearSelection(); }"
            +
            "function showItemMenu(e, id) { e.preventDefault(); e.stopPropagation(); document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); document.querySelectorAll('.item-card, .item-row').forEach(c => c.classList.remove('menu-open')); const m = document.getElementById('m-'+id); if(m) { m.classList.toggle('show'); const parent = m.closest('.item-card') || m.closest('tr'); if(parent) parent.classList.add('menu-open'); } }"
            +
            "window.onclick = () => { document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show')); document.querySelectorAll('.item-card, .item-row').forEach(c => c.classList.remove('menu-open')); };"
            +
            "function toast(t, i='success') { Swal.mixin({ toast: true, position: 'top-end', showConfirmButton: false, timer: 3000, timerProgressBar: true }).fire({ icon: i, title: t }); }"
            +
            "async function checkConflict(name) { const exists = state.files.some(f => f.name === name); if(!exists) return 'none'; return await promptConflict(name); }"
            +
            "async function promptConflict(name) { const res = await Swal.fire({ title: 'File exists', text: '\"' + name + '\" already exists. What would you like to do?', icon: 'warning', showCancelButton: true, showDenyButton: true, confirmButtonText: 'Override', denyButtonText: 'Save Anyway', cancelButtonText: 'Cancel', confirmButtonColor: '#1a73e8', denyButtonColor: '#34a853' }); if(res.isConfirmed) return 'override'; if(res.isDenied) return 'auto'; return 'cancel'; }"
            +
            "async function op(a, p, e=null) { if(a==='download') { if(e) location.href='/zip?files='+encodeURIComponent(p); else window.open('/download?dl=1&file='+encodeURIComponent(p)); return; } if(!state.status.allowModifications) return; let url = ''; if(a==='mkdir') { const {value:n} = await Swal.fire({title:'New Folder', symbol:'folder', input:'text', showCancelButton:true}); if(!n) return; url = `/mkdir?path=${encodeURIComponent(state.path)}&name=${encodeURIComponent(n)}`; } else if(a==='rename') { const {value:n} = await Swal.fire({title:'Rename', input:'text', inputValue:e, showCancelButton:true}); if(!n || n === e) return; const c = await checkConflict(n); if(c === 'cancel') return; url = `/rename?file=${encodeURIComponent(p)}&new=${encodeURIComponent(n)}${c!=='none'?'&conflict='+c:''}`; } else if(a==='delete') { const res = await Swal.fire({title:'Delete?', text:'Are you sure?', icon:'warning', showCancelButton:true, confirmButtonColor:'#d33'}); if(!res.isConfirmed) return; url = `/delete?file=${encodeURIComponent(p)}`; } else if(a==='delete_multiple') { const res = await Swal.fire({title:'Delete ' + state.selectedFiles.size + ' items?', text:'Are you sure?', icon:'warning', showCancelButton:true, confirmButtonColor:'#d33'}); if(!res.isConfirmed) return; const f = Array.from(state.selectedFiles).map(encodeURIComponent).join(','); url = `/delete_multiple?files=${f}&path=${encodeURIComponent(state.path)}`; } else if(a==='cut' || a==='copy') { url = `/${a}?file=${encodeURIComponent(p)}`; } else if(a==='paste') { const name = state.status.clipboardName || (state.status.hasClipboard ? 'File' : ''); const c = await checkConflict(name); if(c === 'cancel') return; url = `/paste?path=${encodeURIComponent(state.path)}${c!=='none'?'&conflict='+c:''}`; } if(!url) return; const res = await api(url); if(res && res.success) { toast('Operation successful'); if(Swal.isVisible() && ['delete','rename','delete_multiple'].includes(a)) Swal.close(); navigate(state.view, state.path, false); } else Swal.fire('Error', res ? res.error || 'Operation failed' : 'Network error', 'error'); }"
            +
            "function renderUploadStatus() { if(!state.uploading) return ''; return `<div class='upload-bar'><div class='upload-info' id='upload-progress-text'>Uploading... ${state.uploadProgress}%</div><div class='progress-container'><div id='upload-progress-bar' class='progress-fill' style='width:${state.uploadProgress}%'></div></div><button class='cancel-upload' onclick='cancelUpload()' title='Cancel Upload'><i class='fa-solid fa-circle-xmark'></i></button></div>`; }"
            +
            "function cancelUpload() { if(state.uploadXhr) { state.uploadXhr.abort(); setState({ uploading: false, uploadXhr: null, uploadProgress: 0 }); toast('Upload cancelled', 'warning'); } }"
            +
            "function setFilesFilter(f) { setState({ filesFilter: f }); }" +
            "function setGalleryFilter(f) { setState({ galleryFilter: f }); }" +
            "async function handleUpload(files, forcedConflict = 'none') { if(!files || !files.length) return; let c = forcedConflict; if(c === 'none') { if(files.length === 1) { c = await checkConflict(files[0].name); if(c === 'cancel') return; } else { const anyExists = Array.from(files).some(f => state.files.some(sf => sf.name === f.name)); if(anyExists) { const res = await Swal.fire({ title: 'Multiple Conflicts', text: 'Some files already exist. How to handle?', icon: 'warning', showCancelButton: true, showDenyButton: true, confirmButtonText: 'Override ALL', denyButtonText: 'Rename Anyway', cancelButtonText: 'Cancel' }); if(res.isConfirmed) c = 'override'; else if(res.isDenied) c = 'auto'; else return; } } } const fd = new FormData(); for(let f of files) fd.append('file', f); setState({ uploading: true, uploadProgress: 0 }); const xhr = new XMLHttpRequest(); state.uploadXhr = xhr; xhr.open('POST', `/upload?path=${encodeURIComponent(state.path)}${c!=='none'?'&conflict='+c:''}`); xhr.upload.onprogress = (e) => { if(e.lengthComputable) { const p = Math.round((e.loaded/e.total)*100); state.uploadProgress = p; const bar = document.getElementById('upload-progress-bar'); const txt = document.getElementById('upload-progress-text'); if(bar) bar.style.width = p + '%'; if(txt) txt.innerText = 'Uploading... ' + p + '%'; } }; xhr.onload = async () => { if(state.uploading) { if(xhr.status === 200) { setState({ uploading: false, uploadXhr: null }); toast(`Uploaded success`); navigate(state.view, state.path, false); } else if(xhr.status === 409) { setState({ uploading: false, uploadXhr: null }); const strategy = await promptConflict(files.length === 1 ? files[0].name : 'Selection'); if(strategy !== 'cancel') handleUpload(files, strategy); } else { setState({ uploading: false, uploadXhr: null }); Swal.fire('Error', xhr.responseText || 'Upload failed', 'error'); } } }; xhr.onerror = () => { if(state.uploading) { setState({ uploading: false, uploadXhr: null }); Swal.fire('Error', 'Network error', 'error'); } }; xhr.send(fd); }"
            +
            "const APP_LOGO = '" + APP_LOGO + "';" +
            "document.addEventListener('DOMContentLoaded', () => { console.log('Share File SPA v3.0.7 - Developed by saheermk'); loadSettings(); const params = new URLSearchParams(window.location.search); const view = window.location.pathname.substring(1) || 'home'; navigate(view === 'files' || view === 'apps' || view === 'gallery' ? view : 'home', params.get('path') || '', false); ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(name => { document.body.addEventListener(name, e => { e.preventDefault(); e.stopPropagation(); }, false); }); document.body.addEventListener('dragover', () => document.body.classList.add('dragover')); document.body.addEventListener('dragleave', () => document.body.classList.remove('dragover')); document.body.addEventListener('drop', e => { document.body.classList.remove('dragover'); if(e.dataTransfer.files.length) handleUpload(e.dataTransfer.files); }); });"
            +
            "window.toggleActionsMenu = toggleActionsMenu; window.navigate = navigate; window.goBack = goBack; window.toggleLayout = toggleLayout; window.changeSort = changeSort; window.toggleTheme = toggleTheme; window.toggleSelectMode = toggleSelectMode; window.clearSelection = clearSelection; window.downloadQueue = downloadQueue; window.downloadZip = downloadZip; window.showItemMenu = showItemMenu; window.op = op; window.showDetails = showDetails; window.preview = preview; window.setFilesFilter = setFilesFilter; window.setGalleryFilter = setGalleryFilter; window.cancelUpload = cancelUpload; window.filterItems = filterItems; "
            +
            "</script>";

    public static String buildSpaShell() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<link rel='icon' type='image/png' href='").append(APP_LOGO).append("'>")
                .append("<link href='/assets/inter.css' rel='stylesheet'>")
                .append("<link rel='stylesheet' href='/assets/lib/all.min.css'>")
                .append("<script src='/assets/sweetalert2.min.js'></script>")
                .append("<title>Share File</title>")
                .append(CSS).append(SPA_JS)
                .append("</head><body><div id='root'>")
                .append("<script>initTheme();</script></div></body></html>");
        return injectTelemetry(sb.toString());
    }

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
        sb.append("<script>");
        sb.append("  setInterval(async () => {");
        sb.append("    try {");
        sb.append("      const resp = await fetch('/check_auth');");
        sb.append("      if (resp.ok) {");
        sb.append("        const status = await resp.text();");
        sb.append("        if (status.trim() === 'authorized') {");
        sb.append("          window.location.href = '/';");
        sb.append("        }");
        sb.append("      }");
        sb.append("    } catch (e) { console.error('Auth check failed', e); }");
        sb.append("  }, 2000);");
        sb.append("</script>");
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
                .append(CSS).append(SPA_JS)
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
                .append("<div class='header-bar'>")
                .append("<div class='header-left'>");

        if (showBack) {
            sb.append("<a href='").append(backUrl)
                    .append("' class='back-btn' title='Back'><i class='fa-solid fa-arrow-left'></i></a>");
        }

        sb.append("<h1 style='cursor:pointer' onclick=\"location.href='/'\"><img src='")
                .append(APP_LOGO)
                .append("' style='width: 38px; height: 38px; object-fit: contain;'> Share File</h1></div>")
                .append("<div class='header-right'><button class='theme-toggle' onclick='toggleTheme()' title='Toggle Theme'><i id='theme-icon' class='fa-solid fa-moon'></i></button></div>")
                .append("</div>");

        if (subtitle != null) {
            sb.append(
                    "<div class='subtitle' style='margin-top:12px; max-width:1200px; margin-left:auto; margin-right:auto;'>")
                    .append(subtitle).append("</div>");
        }
        sb.append("</div>");
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
                .append("' style='width:80px; height:80px; object-fit: contain; margin-bottom:24px;'>")
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
