package com.example.epubwebviewer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class BookRepository(private val context: Context) {

    private val booksDir: File
        get() = File(context.filesDir, "books")

    /**
     * Returns a list of all imported books, sorted by last read date.
     */
    suspend fun getAllBooks(): List<BookMetadata> = withContext(Dispatchers.IO) {
        val books = mutableListOf<BookMetadata>()
        booksDir.mkdirs()
        booksDir.listFiles()?.forEach { folder ->
            if (folder.isDirectory) {
                val metaFile = File(folder, "metadata.json")
                if (metaFile.exists()) {
                    try {
                        val json = JSONObject(metaFile.readText())
                        books.add(jsonToMetadata(json))
                    } catch (_: Exception) {
                        // Invalid metadata – ignore
                    }
                }
            }
        }
        books.sortedByDescending { it.lastReadTimestamp }
    }

    suspend fun renameBook(bookId: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            val metaFile = File(booksDir, "$bookId/metadata.json")
            if (metaFile.exists()) {
                val json = JSONObject(metaFile.readText())
                json.put("title", newTitle)
                metaFile.writeText(json.toString(2))
            }
        }
    }

    suspend fun deleteBook(bookId: String) {
        withContext(Dispatchers.IO) {
            val folder = File(booksDir, bookId)
            folder.deleteRecursively()
        }
    }

    private fun jsonToMetadata(json: JSONObject): BookMetadata {
        return BookMetadata(
            id = json.getString("id"),
            title = json.getString("title"),
            chapterCount = json.optInt("chapterCount", 0),
            currentChapterIdx = json.optInt("currentChapterIdx", 0),
            scrollPercent = json.optDouble("scrollPercent", 0.0).toFloat(),
            fontSize = json.optInt("fontSize", 20),
            lineHeight = json.optDouble("lineHeight", 1.75).toFloat(),
            readerWidth = json.optInt("readerWidth", 760),
            theme = json.optString("theme", "black"),
            customBg = json.optString("customBg", null),
            customTextColor = json.optString("customTextColor", null), // new
            lastReadTimestamp = json.optLong("lastReadTimestamp", System.currentTimeMillis())
        )
    }
}