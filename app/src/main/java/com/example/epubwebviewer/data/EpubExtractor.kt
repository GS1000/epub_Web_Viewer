package com.example.epubwebviewer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class EpubExtractor(private val context: Context) {

    fun extract(uri: Uri): Flow<Float> = flow {
        emit(0f)
        val tempEpubFile = File(context.cacheDir, "temp_${UUID.randomUUID()}.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempEpubFile).use { output ->
                val buffer = ByteArray(8192)
                var bytes = input.read(buffer)
                while (bytes != -1) {
                    output.write(buffer, 0, bytes)
                    bytes = input.read(buffer)
                }
            }
        } ?: throw Exception("Cannot open EPUB file")
        emit(0.1f)

        val tempDir = File(context.cacheDir, "epub_extract_${UUID.randomUUID()}")
        tempDir.mkdirs()
        unzip(tempEpubFile, tempDir)
        tempEpubFile.delete()
        emit(0.4f)

        val containerFile = File(tempDir, "META-INF/container.xml")
        val opfPath = parseContainerXml(containerFile)
        val opfDir = File(tempDir, opfPath).parentFile ?: tempDir
        emit(0.5f)

        val opfFile = File(tempDir, opfPath)
        val (spineIds, allItems) = parseOpf(opfFile)
        emit(0.6f)

        val bookId = UUID.randomUUID().toString()
        val bookDir = File(context.filesDir, "books/$bookId")
        bookDir.mkdirs()
        val mediaDir = File(bookDir, "media")
        mediaDir.mkdirs()
        val chaptersDir = File(bookDir, "chapters")
        chaptersDir.mkdirs()

        val chapterContents = mutableListOf<String>()
        val totalItems = spineIds.size
        var processed = 0

        for (id in spineIds) {
            val item = allItems[id] ?: continue
            val href = item["href"] ?: continue
            val file = File(opfDir, href)
            if (file.exists() && file.extension.lowercase() in listOf("xhtml", "html", "htm")) {
                val doc: Document = Jsoup.parse(file, "UTF-8")

                // Remove scripts and event handlers
                doc.select("script").remove()
                doc.allElements.forEach { el ->
                    val attrs = el.attributes()
                    val toRemove = attrs.filter { it.key.startsWith("on") }
                    toRemove.forEach { el.removeAttr(it.key) }
                }

                // Handle images: copy to media/ and fix src
                doc.select("img").forEach { img ->
                    val src = img.attr("src")
                    if (src.isNotBlank() && !src.startsWith("http")) {
                        var imgFile = resolveFile(opfDir, src)
                        if (imgFile == null) imgFile = resolveFile(file.parentFile ?: opfDir, src)
                        if (imgFile == null) {
                            val filename = src.substringAfterLast("/")
                            imgFile = tempDir.walkTopDown().find { it.name == filename }
                        }
                        if (imgFile != null && imgFile.exists()) {
                            val filename = imgFile.name
                            val dest = File(mediaDir, filename)
                            if (!dest.exists()) {
                                imgFile.copyTo(dest)
                            }
                            img.attr("src", "media/$filename")
                        }
                    }
                }

                // Extract cleaned body HTML
                val bodyHtml = doc.body().html()
                chapterContents.add(bodyHtml)
            }
            processed++
            emit(0.6f + 0.3f * (processed.toFloat() / totalItems))
        }

        // Write each chapter to a separate file
        chapterContents.forEachIndexed { index, content ->
            val chapterFile = File(chaptersDir, "chapter_${index + 1}.html")
            chapterFile.writeText(content)
        }

        // Create metadata object
        val metadata = BookMetadata(
            id = bookId,
            title = uri.lastPathSegment ?: "Unknown EPUB",
            chapterCount = chapterContents.size
        )

        // Generate the lightweight index.html
        val html = generateReaderHtml(bookId, metadata)
        File(bookDir, "index.html").writeText(html)

        // Save metadata
        File(bookDir, "metadata.json").writeText(metadata.toJson())

        // Clean up temp
        tempDir.deleteRecursively()
        emit(1.0f)
    }.flowOn(Dispatchers.IO)

    // ---------- Helper functions ----------
    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { fos ->
                        val buffer = ByteArray(8192)
                        var bytes = zis.read(buffer)
                        while (bytes != -1) {
                            fos.write(buffer, 0, bytes)
                            bytes = zis.read(buffer)
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun parseContainerXml(file: File): String {
        val doc = Jsoup.parse(file, "UTF-8")
        val rootfile = doc.select("rootfile").first()
            ?: throw Exception("Invalid container.xml")
        return rootfile.attr("full-path")
    }

    private fun parseOpf(file: File): Pair<List<String>, Map<String, Map<String, String>>> {
        val doc = Jsoup.parse(file, "UTF-8")
        val items = mutableMapOf<String, Map<String, String>>()
        doc.select("manifest > item").forEach { el ->
            val id = el.attr("id")
            val attrs = mutableMapOf<String, String>()
            attrs["href"] = el.attr("href")
            attrs["media-type"] = el.attr("media-type")
            items[id] = attrs
        }
        val spineIds = doc.select("spine > itemref").map { it.attr("idref") }
        return spineIds to items
    }

    private fun resolveFile(baseDir: File, href: String): File? {
        return try {
            val f = File(baseDir, href)
            if (f.exists()) f else null
        } catch (e: Exception) { null }
    }

    // ---------- HTML generation (updated) ----------
    private fun generateReaderHtml(bookId: String, metadata: BookMetadata): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/>
<title>EPUB Reader</title>
<style>
:root {
  --bg: #111; --panel: #181818; --panel-2: #202020; --text: #f2f2f2;
  --muted: #b6b6b6; --border: #2f2f2f; --accent: #7aa2ff; --accent-2: #9db8ff;
  --reader-width: 760px; --font-size: 20px; --line-height: 1.75;
}
[data-theme="black"] { --bg:#111; --panel:#171717; --panel-2:#1f1f1f; --text:#f0f0f0; --muted:#c0c0c0; --border:#2f2f2f; --accent:#7aa2ff; --accent-2:#a8bfff; }
[data-theme="dark-grey"] { --bg:#242424; --panel:#2d2d2d; --panel-2:#353535; --text:#f4f4f4; --muted:#d0d0d0; --border:#4a4a4a; --accent:#9fc1ff; --accent-2:#bfd3ff; }
[data-theme="light-grey"] { --bg:#d8d8d8; --panel:#e6e6e6; --panel-2:#f0f0f0; --text:#1f1f1f; --muted:#444; --border:#b7b7b7; --accent:#325dff; --accent-2:#1e4de8; }
[data-theme="white"] { --bg:#fff; --panel:#f4f4f4; --panel-2:#f8f8f8; --text:#101010; --muted:#4f4f4f; --border:#dadada; --accent:#2457ff; --accent-2:#1d49d6; }
[data-theme="true-black"] { --bg:#000; --panel:#080808; --panel-2:#101010; --text:#fff; --muted:#cfcfcf; --border:#1a1a1a; --accent:#8fb1ff; --accent-2:#b6c8ff; }
[data-theme="custom"] {
  --bg:var(--custom-bg);
  --panel:hsl(from var(--custom-bg) h s calc(l + 3));
  --panel-2:hsl(from var(--custom-bg) h s calc(l + 6));
  --text:var(--custom-text);
  --muted:var(--custom-text-muted);
  --border:hsl(from var(--custom-bg) h s calc(l + 15));
  --accent:#7aa2ff;
  --accent-2:#a8bfff;
}
* { box-sizing:border-box; }
html,body { height:100%; margin:0; background:var(--bg); color:var(--text); font-family:Inter, system-ui, sans-serif; overflow:hidden; }
body { display:flex; flex-direction:column; }
button,input { font:inherit; }

/* Floating toggle – always visible when topbar is closed */
.floating-toggle {
  position:fixed;
  top:16px;
  right:16px;
  z-index:30;
  background:var(--panel);
  border:1px solid var(--border);
  border-radius:50%;
  width:44px;
  height:44px;
  display:flex;
  align-items:center;
  justify-content:center;
  cursor:pointer;
  font-size:24px;
  color:var(--text);
  box-shadow:0 2px 8px rgba(0,0,0,0.4);
  pointer-events:auto;
  transition:transform 0.2s ease, background 0.2s;
}
.floating-toggle:hover { background:var(--panel-2); }
.floating-toggle.hidden { display:none; }

/* Topbar – hidden initially, slides in */
.topbar {
  display:flex;
  flex-direction:column;
  gap:6px;
  padding:12px 16px;
  border-bottom:1px solid var(--border);
  background:var(--panel);
  transition:transform 0.25s ease, opacity 0.25s ease;
  transform:translateY(-100%);
  opacity:0;
  height:0;
  overflow:hidden;
  padding-top:0;
  padding-bottom:0;
  border-width:0;
  z-index:20;
  position:relative;
}
.topbar.open {
  transform:translateY(0);
  opacity:1;
  height:auto;
  overflow:visible;
  padding-top:12px;
  padding-bottom:12px;
  border-bottom-width:1px;
}
.topbar-row {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:8px;
  min-height:0;
}
.topbar-row.chapter-info {
  justify-content:flex-start;
  gap:12px;
  font-size:14px;
  color:var(--muted);
}
.title-container {
  flex:1;
  min-width:0;
  overflow:hidden;
  white-space:nowrap;
  text-overflow:ellipsis;
}
.title-container h1 {
  margin:0;
  font-size:18px;
  font-weight:600;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
}
.actions {
  display:flex;
  gap:6px;
  flex-shrink:0;
  align-items:center;
}
/* Toggle inside topbar – hidden by default */
#topbarToggle {
  display:none;
}
.topbar.open #topbarToggle {
  display:inline-flex;
}

.btn {
  border:1px solid var(--border);
  background:var(--panel-2);
  color:var(--text);
  border-radius:999px;
  padding:6px 12px;
  cursor:pointer;
  font-size:13px;
  white-space:nowrap;
  transition:border-color 0.15s, background 0.15s;
}
.btn:hover { border-color:var(--accent); }
.btn.primary { background:var(--accent); color:#fff; border-color:transparent; }
.btn.primary:hover { background:var(--accent-2); }
.btn:disabled { opacity:0.5; cursor:not-allowed; }

.main { display:flex; flex:1; min-height:0; }
.sidebar {
  border-right:1px solid var(--border);
  background:var(--panel);
  display:flex;
  flex-direction:column;
  min-height:0;
  width:280px;
  transition:transform 0.2s ease;
}
.sidebar.open { transform:translateX(0); }
.sidebar.closed { transform:translateX(-100%); }
.sidebar-header { padding:10px 14px; border-bottom:1px solid var(--border); display:flex; justify-content:space-between; align-items:center; }
.toc-list { flex:1; overflow-y:auto; padding:8px; }
.toc-item { display:block; width:100%; text-align:left; border:0; background:transparent; color:var(--text); padding:10px 12px; border-radius:12px; cursor:pointer; font-size:14px; }
.toc-item:hover { background:rgba(255,255,255,0.05); }
.toc-item.active { background:var(--accent); color:#fff; }

.reader-wrap { display:flex; flex-direction:column; flex:1; min-width:0; min-height:0; }

.reader-body {
  flex:1;
  overflow-y:auto;
  padding:20px 14px 40px;
}
.reader-inner {
  max-width:var(--reader-width);
  margin:0 auto;
  font-size:var(--font-size);
  line-height:var(--line-height);
  word-break:break-word;
  overflow-wrap:anywhere;
}
.reader-inner p { margin:0 0 1em; white-space:normal; }
.reader-inner p:last-child { margin-bottom:0; }
.reader-inner img { max-width:100%; height:auto; }

.bottom-bar {
  display:flex;
  justify-content:space-between;
  align-items:center;
  gap:12px;
  padding:10px 16px;
  border-top:1px solid var(--border);
  background:var(--panel);
  flex-wrap:wrap;
}
.progress-bar {
  width:min(300px,50vw);
  height:8px;
  border-radius:999px;
  background:var(--panel-2);
  border:1px solid var(--border);
  overflow:hidden;
}
.progress-fill { height:100%; width:0%; background:var(--accent); transition:width 0.2s; }
.badge {
  font-size:11px;
  color:var(--muted);
  padding:4px 10px;
  border-radius:999px;
  background:var(--panel-2);
  border:1px solid var(--border);
}
.modal-backdrop { position:fixed; inset:0; background:rgba(0,0,0,0.6); display:none; align-items:center; justify-content:center; z-index:50; }
.modal-backdrop.show { display:flex; }
.modal { width:min(600px,94%); max-height:85vh; overflow-y:auto; border-radius:24px; background:var(--panel); border:1px solid var(--border); padding:20px; }
.modal h3 { margin:0 0 16px; font-size:18px; }
.setting-row { display:flex; gap:10px; align-items:center; margin-bottom:14px; flex-wrap:wrap; }
.setting-row label { width:110px; font-size:13px; color:var(--muted); }
.theme-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:8px; }
.theme-chip { border:1px solid var(--border); background:transparent; color:var(--text); border-radius:12px; padding:10px; cursor:pointer; text-align:left; }
.theme-chip.active { border-color:var(--accent); }
.color-picker { display:flex; gap:8px; align-items:center; }
.color-picker input { width:60px; }
.modal-footer { display:flex; justify-content:flex-end; gap:10px; margin-top:16px; }

@media (max-width:850px) {
  .main { flex-direction:column; }
  .sidebar { position:fixed; left:0; top:0; bottom:0; width:260px; z-index:40; transform:translateX(-100%); }
  .sidebar.open { transform:translateX(0); }
  .topbar.open { padding:10px 12px; }
  .title-container h1 { font-size:16px; }
  .btn { font-size:12px; padding:4px 10px; }
}
</style>
</head>
<body data-theme="black" id="app">

<!-- Floating toggle – visible when topbar closed -->
<button class="floating-toggle" id="floatingToggle">☰</button>

<!-- Topbar – hidden by default -->
<div class="topbar" id="topbar">
  <div class="topbar-row">
    <div class="title-container">
      <h1 id="bookTitleDisplay">${metadata.title}</h1>
    </div>
    <div class="actions">
      <button class="btn" id="chaptersBtn">Chapters</button>
      <button class="btn" id="settingsBtn">Settings</button>
      <button class="btn" id="topbarToggle">☰</button>
    </div>
  </div>
  <div class="topbar-row chapter-info">
    <span id="chapterTitleDisplay">Chapter 1</span>
    <span class="badge" id="chapterBadgeDisplay">1/${metadata.chapterCount}</span>
  </div>
</div>

<div class="main">
  <aside class="sidebar closed" id="sidebar">
    <div class="sidebar-header">
      <span style="font-weight:600;">Chapters</span>
      <button class="btn" id="closeSidebarBtn">✕</button>
    </div>
    <div class="toc-list" id="tocList"></div>
  </aside>
  <section class="reader-wrap">
    <div class="reader-body" id="readerBody">
      <div class="reader-inner" id="readerContent">
        <p>Loading...</p>
      </div>
    </div>
    <div class="bottom-bar" id="bottomBar">
      <div>
        <button class="btn" id="prevBtn" disabled>← Prev</button>
        <button class="btn" id="nextBtn" disabled>Next →</button>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" id="progressFill"></div>
      </div>
    </div>
  </section>
</div>

<!-- Settings Modal (unchanged) -->
<div class="modal-backdrop" id="settingsModal">
  <div class="modal">
    <h3>Reader Settings</h3>
    <div class="setting-row"><label>Font size</label><input type="range" id="fontSizeSlider" min="14" max="32" step="1"><span class="badge" id="fontSizeValue"></span></div>
    <div class="setting-row"><label>Line height</label><input type="range" id="lineHeightSlider" min="1.3" max="2.5" step="0.05"><span class="badge" id="lineHeightValue"></span></div>
    <div class="setting-row"><label>Width</label><input type="range" id="widthSlider" min="500" max="1000" step="10"><span class="badge" id="widthValue"></span></div>
    <div><label style="display:block;margin-bottom:8px;font-size:13px;color:var(--muted);">Theme</label><div class="theme-grid" id="themeGrid"></div></div>
    <div id="customColorPanel" style="display:none;margin-top:8px;">
      <label style="display:block;font-size:13px;color:var(--muted);">Custom background colour</label>
      <div class="color-picker"><label>R</label><input type="range" min="0" max="255" id="colorR"><label>G</label><input type="range" min="0" max="255" id="colorG"><label>B</label><input type="range" min="0" max="255" id="colorB"></div>
      <label style="display:block;font-size:13px;color:var(--muted);margin-top:8px;">Custom text colour</label>
      <div class="color-picker"><label>R</label><input type="range" min="0" max="255" id="textColorR"><label>G</label><input type="range" min="0" max="255" id="textColorG"><label>B</label><input type="range" min="0" max="255" id="textColorB"></div>
    </div>
    <div class="modal-footer"><button class="btn" id="resetSettingsBtn">Defaults</button><button class="btn primary" id="closeSettingsBtn">Done</button></div>
  </div>
</div>

<script>
// ------ STATE ------
var BOOK_STATE = {
  chapterCount: ${metadata.chapterCount},
  currentChapterIdx: 0,
  scrollPercent: 0,
  fontSize: 20,
  lineHeight: 1.75,
  readerWidth: 760,
  theme: "black",
  customBg: null,
  customTextColor: null
};

// Try to load saved state from server
fetch('/api/state')
  .then(res => res.json())
  .then(data => {
    if (data && data.currentChapterIdx !== undefined) {
      BOOK_STATE = { ...BOOK_STATE, ...data };
    }
    init();
  })
  .catch(() => init());

function init() {
  const app = document.getElementById('app');
  const topbar = document.getElementById('topbar');
  const floatingToggle = document.getElementById('floatingToggle');
  const topbarToggle = document.getElementById('topbarToggle');
  const settingsModal = document.getElementById('settingsModal');
  const themeGrid = document.getElementById('themeGrid');
  const customColorPanel = document.getElementById('customColorPanel');
  const colorR = document.getElementById('colorR');
  const colorG = document.getElementById('colorG');
  const colorB = document.getElementById('colorB');
  const textColorR = document.getElementById('textColorR');
  const textColorG = document.getElementById('textColorG');
  const textColorB = document.getElementById('textColorB');
  const fontSizeSlider = document.getElementById('fontSizeSlider');
  const lineHeightSlider = document.getElementById('lineHeightSlider');
  const widthSlider = document.getElementById('widthSlider');
  const fontSizeValue = document.getElementById('fontSizeValue');
  const lineHeightValue = document.getElementById('lineHeightValue');
  const widthValue = document.getElementById('widthValue');
  const readerBody = document.getElementById('readerBody');
  const readerContent = document.getElementById('readerContent');
  const tocList = document.getElementById('tocList');
  const prevBtn = document.getElementById('prevBtn');
  const nextBtn = document.getElementById('nextBtn');
  const progressFill = document.getElementById('progressFill');
  const chapterTitleDisplay = document.getElementById('chapterTitleDisplay');
  const chapterBadgeDisplay = document.getElementById('chapterBadgeDisplay');
  const sidebar = document.getElementById('sidebar');
  const chaptersBtn = document.getElementById('chaptersBtn');

  let currentChapterIdx = BOOK_STATE.currentChapterIdx || 0;
  let chapterCount = BOOK_STATE.chapterCount || 0;
  let chapterContentCache = {};

  // ----- Toggle topbar and both toggle buttons -----
  function toggleTopbar() {
    topbar.classList.toggle('open');
    floatingToggle.classList.toggle('hidden');
    topbarToggle.classList.toggle('hidden');
  }

  // Attach toggles
  floatingToggle.addEventListener('click', toggleTopbar);
  topbarToggle.addEventListener('click', toggleTopbar);

  // ----- Helper functions -----
  function applySettings() {
    document.documentElement.style.setProperty('--font-size', BOOK_STATE.fontSize + 'px');
    document.documentElement.style.setProperty('--line-height', BOOK_STATE.lineHeight);
    document.documentElement.style.setProperty('--reader-width', BOOK_STATE.readerWidth + 'px');
    app.setAttribute('data-theme', BOOK_STATE.theme);
    if (BOOK_STATE.theme === 'custom' && BOOK_STATE.customBg) {
      document.documentElement.style.setProperty('--custom-bg', BOOK_STATE.customBg);
      const r = parseInt(BOOK_STATE.customBg.slice(1,3), 16);
      const g = parseInt(BOOK_STATE.customBg.slice(3,5), 16);
      const b = parseInt(BOOK_STATE.customBg.slice(5,7), 16);
      const luminance = (0.299*r + 0.587*g + 0.114*b) / 255;
      const textColor = luminance > 0.5 ? '#000' : '#fff';
      let finalText = BOOK_STATE.customTextColor || textColor;
      document.documentElement.style.setProperty('--custom-text', finalText);
      let muted = luminance > 0.5 ? '#333' : '#bbb';
      if (BOOK_STATE.customTextColor) {
        muted = finalText + '80';
      }
      document.documentElement.style.setProperty('--custom-text-muted', muted);
      colorR.value = r; colorG.value = g; colorB.value = b;
      if (BOOK_STATE.customTextColor) {
        const tr = parseInt(BOOK_STATE.customTextColor.slice(1,3), 16);
        const tg = parseInt(BOOK_STATE.customTextColor.slice(3,5), 16);
        const tb = parseInt(BOOK_STATE.customTextColor.slice(5,7), 16);
        textColorR.value = tr; textColorG.value = tg; textColorB.value = tb;
      } else {
        if (luminance > 0.5) {
          textColorR.value = 0; textColorG.value = 0; textColorB.value = 0;
        } else {
          textColorR.value = 255; textColorG.value = 255; textColorB.value = 255;
        }
      }
      customColorPanel.style.display = 'block';
    } else {
      customColorPanel.style.display = 'none';
      if (BOOK_STATE.theme !== 'custom') {
        document.documentElement.style.setProperty('--custom-text', null);
        document.documentElement.style.setProperty('--custom-text-muted', null);
      }
    }
    fontSizeSlider.value = BOOK_STATE.fontSize;
    lineHeightSlider.value = BOOK_STATE.lineHeight;
    widthSlider.value = BOOK_STATE.readerWidth;
    fontSizeValue.textContent = BOOK_STATE.fontSize + 'px';
    lineHeightValue.textContent = BOOK_STATE.lineHeight.toFixed(2);
    widthValue.textContent = BOOK_STATE.readerWidth + 'px';
    renderThemeGrid();
  }

  const THEMES = { 'true-black':'True Black', 'black':'Black', 'dark-grey':'Dark Grey', 'light-grey':'Light Grey', 'white':'White', 'custom':'Custom' };

  function renderThemeGrid() {
    themeGrid.innerHTML = '';
    Object.entries(THEMES).forEach(([key, name]) => {
      const btn = document.createElement('button');
      btn.className = 'theme-chip' + (BOOK_STATE.theme === key ? ' active' : '');
      btn.textContent = name;
      btn.onclick = () => {
        BOOK_STATE.theme = key;
        if (key === 'custom') {
          if (!BOOK_STATE.customBg) BOOK_STATE.customBg = '#1a1a1a';
          if (!BOOK_STATE.customTextColor) BOOK_STATE.customTextColor = '#ffffff';
          customColorPanel.style.display = 'block';
        } else {
          customColorPanel.style.display = 'none';
          BOOK_STATE.customTextColor = null;
        }
        applySettings(); saveState();
      };
      themeGrid.appendChild(btn);
    });
    if (BOOK_STATE.theme === 'custom') customColorPanel.style.display = 'block';
  }

  let debounceTimer = null;
  function saveState() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      fetch('/api/state', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          currentChapterIdx: currentChapterIdx,
          scrollPercent: getScrollPercent(),
          fontSize: BOOK_STATE.fontSize,
          lineHeight: BOOK_STATE.lineHeight,
          readerWidth: BOOK_STATE.readerWidth,
          theme: BOOK_STATE.theme,
          customBg: BOOK_STATE.customBg,
          customTextColor: BOOK_STATE.customTextColor
        })
      }).catch(() => {});
    }, 600);
  }

  function getScrollPercent() {
    const el = readerBody;
    if (el.scrollHeight <= el.clientHeight) return 0;
    return el.scrollTop / (el.scrollHeight - el.clientHeight) * 100;
  }

  function restoreScroll(percent) {
    if (percent > 0) {
      readerBody.scrollTop = (readerBody.scrollHeight - readerBody.clientHeight) * (percent / 100);
    }
  }

  // ----- Chapter loading -----
  function loadChapter(index, callback) {
    if (index < 0 || index >= chapterCount) return;
    const url = '/api/chapter/' + (index + 1);
    if (chapterContentCache[index]) {
      displayChapterContent(index, chapterContentCache[index]);
      if (callback) callback();
      return;
    }
    fetch(url)
      .then(res => res.text())
      .then(html => {
        chapterContentCache[index] = html;
        displayChapterContent(index, html);
        if (callback) callback();
      })
      .catch(() => {
        readerContent.innerHTML = '<p>Error loading chapter.</p>';
      });
  }

  function displayChapterContent(index, html) {
    readerContent.innerHTML = html;
    // Update topbar chapter info
    const chapterNum = index + 1;
    chapterTitleDisplay.textContent = 'Chapter ' + chapterNum;
    chapterBadgeDisplay.textContent = chapterNum + '/' + chapterCount;
    updateNavButtons();
    updateProgress();
    if (BOOK_STATE.scrollPercent > 0 && index === currentChapterIdx) {
      setTimeout(() => restoreScroll(BOOK_STATE.scrollPercent), 50);
    } else {
      readerBody.scrollTop = 0;
    }
    // Update TOC active state
    Array.from(tocList.children).forEach((el, i) => {
      el.classList.toggle('active', i === index);
    });
  }

  function displayChapter(index) {
    if (index < 0 || index >= chapterCount) return;
    currentChapterIdx = index;
    loadChapter(index);
    saveState();
  }

  function updateNavButtons() {
    prevBtn.disabled = currentChapterIdx <= 0;
    nextBtn.disabled = currentChapterIdx >= chapterCount - 1;
  }

  function updateProgress() {
    const chapterProgress = getScrollPercent() / 100;
    const progress = (currentChapterIdx + chapterProgress) / chapterCount * 100;
    progressFill.style.width = Math.min(progress, 100) + '%';
  }

  // ----- Build TOC -----
  function buildToc() {
    tocList.innerHTML = '';
    for (let i = 0; i < chapterCount; i++) {
      const btn = document.createElement('button');
      btn.className = 'toc-item' + (i === currentChapterIdx ? ' active' : '');
      btn.textContent = 'Chapter ' + (i + 1);
      btn.onclick = () => {
        displayChapter(i);
        if (window.innerWidth <= 850) sidebar.classList.remove('open');
      };
      tocList.appendChild(btn);
    }
  }

  // ----- Event listeners -----
  chaptersBtn.addEventListener('click', () => {
    sidebar.classList.toggle('open');
  });

  document.getElementById('settingsBtn').addEventListener('click', () => settingsModal.classList.add('show'));
  document.getElementById('closeSettingsBtn').addEventListener('click', () => settingsModal.classList.remove('show'));
  settingsModal.addEventListener('click', (e) => { if (e.target === settingsModal) settingsModal.classList.remove('show'); });
  document.getElementById('resetSettingsBtn').addEventListener('click', () => {
    BOOK_STATE.fontSize = 20; BOOK_STATE.lineHeight = 1.75; BOOK_STATE.readerWidth = 760;
    BOOK_STATE.theme = 'black'; BOOK_STATE.customBg = null; BOOK_STATE.customTextColor = null;
    applySettings(); saveState();
  });
  fontSizeSlider.addEventListener('input', () => { BOOK_STATE.fontSize = parseInt(fontSizeSlider.value); applySettings(); saveState(); });
  lineHeightSlider.addEventListener('input', () => { BOOK_STATE.lineHeight = parseFloat(lineHeightSlider.value); applySettings(); saveState(); });
  widthSlider.addEventListener('input', () => { BOOK_STATE.readerWidth = parseInt(widthSlider.value); applySettings(); saveState(); });

  function onColorChange() {
    const hex = '#' + [colorR, colorG, colorB].map(e => parseInt(e.value).toString(16).padStart(2,'0')).join('');
    BOOK_STATE.customBg = hex; applySettings(); saveState();
  }
  colorR.addEventListener('input', onColorChange);
  colorG.addEventListener('input', onColorChange);
  colorB.addEventListener('input', onColorChange);

  function onTextColorChange() {
    const hex = '#' + [textColorR, textColorG, textColorB].map(e => parseInt(e.value).toString(16).padStart(2,'0')).join('');
    BOOK_STATE.customTextColor = hex; applySettings(); saveState();
  }
  textColorR.addEventListener('input', onTextColorChange);
  textColorG.addEventListener('input', onTextColorChange);
  textColorB.addEventListener('input', onTextColorChange);

  prevBtn.addEventListener('click', () => { if (currentChapterIdx > 0) displayChapter(currentChapterIdx - 1); });
  nextBtn.addEventListener('click', () => { if (currentChapterIdx < chapterCount - 1) displayChapter(currentChapterIdx + 1); });
  readerBody.addEventListener('scroll', () => { updateProgress(); saveState(); });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowLeft' && !prevBtn.disabled) displayChapter(currentChapterIdx - 1);
    if (e.key === 'ArrowRight' && !nextBtn.disabled) displayChapter(currentChapterIdx + 1);
    if (e.key === 'Escape') settingsModal.classList.remove('show');
  });
  document.getElementById('closeSidebarBtn').addEventListener('click', () => sidebar.classList.remove('open'));

  // ----- Initialisation -----
  applySettings();
  buildToc();
  // Load initial chapter
  loadChapter(currentChapterIdx, () => {
    if (BOOK_STATE.scrollPercent > 0) {
      setTimeout(() => restoreScroll(BOOK_STATE.scrollPercent), 100);
    }
    updateNavButtons();
    updateProgress();
    // Ensure topbar is hidden by default and toggle buttons are in correct state
    topbar.classList.remove('open');
    floatingToggle.classList.remove('hidden');
    topbarToggle.classList.add('hidden');
  });
}
</script>
</body>
</html>
        """.trimIndent()
    }
}

// ---------- Extension function to serialise metadata to JSON ----------
fun BookMetadata.toJson(): String {
    return """
{
  "id": "$id",
  "title": "${title.replace("\"", "\\\"")}",
  "chapterCount": $chapterCount,
  "currentChapterIdx": $currentChapterIdx,
  "scrollPercent": $scrollPercent,
  "fontSize": $fontSize,
  "lineHeight": $lineHeight,
  "readerWidth": $readerWidth,
  "theme": "$theme",
  "customBg": ${customBg?.let { "\"$it\"" } ?: "null"},
  "customTextColor": ${customTextColor?.let { "\"$it\"" } ?: "null"},
  "lastReadTimestamp": $lastReadTimestamp
}
    """.trimIndent()
}