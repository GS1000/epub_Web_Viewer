package com.example.epubwebviewer.data

data class BookMetadata(
    val id: String,                // UUID of the book folder
    val title: String,             // user‑facing name (can be renamed)
    val chapterCount: Int = 0,
    val currentChapterIdx: Int = 0,
    val scrollPercent: Float = 0f,
    val fontSize: Int = 20,
    val lineHeight: Float = 1.75f,
    val readerWidth: Int = 760,
    val theme: String = "black",   // one of presets, or "custom"
    val customBg: String? = null,  // e.g. "#1a2b3c" if theme == "custom"
    val customTextColor: String? = null,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,   // new: pinned status
    val coverImagePath: String? = null, // new: relative path to cover image inside book folder, e.g. "cover.jpg"
    val importTimestamp: Long = System.currentTimeMillis() // new: time when book was imported
)