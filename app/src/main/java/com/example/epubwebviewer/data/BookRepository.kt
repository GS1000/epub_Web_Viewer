package com.example.epubwebviewer.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class BookRepository(private val context: Context) {

    private val booksDir: File
        get() = File(context.filesDir, "books")

    /**
     * Returns a list of all imported books, sorted by last read date (default).
     * New fields (pinned, coverImagePath, importTimestamp) are read safely with defaults.
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

    /**
     * Toggle the pinned status of a book.
     */
    suspend fun togglePinned(bookId: String) {
        withContext(Dispatchers.IO) {
            val metaFile = File(booksDir, "$bookId/metadata.json")
            if (metaFile.exists()) {
                val json = JSONObject(metaFile.readText())
                val current = json.optBoolean("pinned", false)
                json.put("pinned", !current)
                metaFile.writeText(json.toString(2))
            }
        }
    }

    /**
     * Update the cover image of a book.
     * If imageUri is null, the cover is removed (set to null).
     * Otherwise, the image is copied to the book folder as "cover.jpg".
     * Returns true on success.
     */
    suspend fun updateCoverImage(bookId: String, imageUri: Uri?): Boolean = withContext(Dispatchers.IO) {
        val bookFolder = File(booksDir, bookId)
        if (!bookFolder.exists()) return@withContext false

        val metaFile = File(bookFolder, "metadata.json")
        if (!metaFile.exists()) return@withContext false

        if (imageUri == null) {
            // Remove cover
            val json = JSONObject(metaFile.readText())
            json.put("coverImagePath", JSONObject.NULL)
            metaFile.writeText(json.toString(2))
            // Also delete the cover file if it exists
            File(bookFolder, "cover.jpg").delete()
            return@withContext true
        }

        // Copy the new image to cover.jpg
        val coverFile = File(bookFolder, "cover.jpg")
        context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
            FileOutputStream(coverFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return@withContext false

        // Update metadata
        val json = JSONObject(metaFile.readText())
        json.put("coverImagePath", "cover.jpg")
        metaFile.writeText(json.toString(2))

        return@withContext true
    }

    /**
     * Get all image files associated with a book (from its media/ folder and the cover).
     * Returns a list of File objects, or null if the book folder doesn't exist.
     */
    suspend fun getImagesForBook(bookId: String): List<File>? = withContext(Dispatchers.IO) {
        val bookFolder = File(booksDir, bookId)
        if (!bookFolder.exists()) return@withContext null

        val images = mutableListOf<File>()
        // Add cover if exists
        val cover = File(bookFolder, "cover.jpg")
        if (cover.exists()) images.add(cover)

        // Add all images from media/ folder
        val mediaDir = File(bookFolder, "media")
        if (mediaDir.exists()) {
            mediaDir.listFiles()?.filter { file ->
                file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            }?.let { images.addAll(it) }
        }
        return@withContext images
    }

    /**
     * Safely parse JSON to BookMetadata with defaults for new fields.
     */
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
            customTextColor = json.optString("customTextColor", null),
            lastReadTimestamp = json.optLong("lastReadTimestamp", System.currentTimeMillis()),
            pinned = json.optBoolean("pinned", false),
            coverImagePath = json.optString("coverImagePath", null),
            importTimestamp = json.optLong("importTimestamp", 0L)
        )
    }
}