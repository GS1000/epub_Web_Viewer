package com.example.epubwebviewer.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class LocalHttpServer(
    private val context: Context,
    private val bookDir: File,
    private val sleepDelayMs: Long = 30_000L // default 30 seconds
) : NanoHTTPD(0) {

    var port: Int = 0
        private set

    // Sleep state
    @Volatile
    private var isSleeping = false
    private var lastActivity = System.currentTimeMillis()

    /**
     * Called by the service periodically to check if we should sleep.
     */
    fun checkSleep() {
        if (!isSleeping && System.currentTimeMillis() - lastActivity > sleepDelayMs) {
            isSleeping = true
            Log.d("Server", "Going to sleep (idle for ${sleepDelayMs}ms)")
            // Optionally: reduce thread pool, clear caches, etc.
        }
    }

    /**
     * Wake the server up (called on any request, or explicitly)
     */
    fun wakeUp() {
        if (isSleeping) {
            isSleeping = false
            Log.d("Server", "Waking up from sleep")
            // Optionally restore resources
        }
        lastActivity = System.currentTimeMillis()
    }

    override fun start(): Unit = super.start().also {
        port = listeningPort
        Log.d("Server", "Started on port $port")
    }

    override fun serve(session: IHTTPSession): Response {
        // Wake on any request
        if (isSleeping) {
            wakeUp()
        } else {
            // Reset activity timer even if not sleeping
            lastActivity = System.currentTimeMillis()
        }

        val uri = session.uri.trimStart('/')

        // Route: /api/...
        if (uri.startsWith("api/")) {
            return apiHandler(session)
        }

        // Default: serve static files from bookDir
        val file = if (uri.isEmpty()) {
            File(bookDir, "index.html")
        } else {
            File(bookDir, uri)
        }

        return if (file.exists() && file.isFile) {
            try {
                val mimeType = getMimeType(file.name)
                val input = FileInputStream(file)
                NanoHTTPD.newChunkedResponse(Response.Status.OK, mimeType, input)
            } catch (e: IOException) {
                NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error reading file")
            }
        } else {
            NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun apiHandler(session: IHTTPSession): Response {
        val uri = session.uri.trimStart('/')
        val metaFile = File(bookDir, "metadata.json")

        // Chapter endpoint
        if (uri.startsWith("api/chapter/")) {
            val chapterNum = uri.substringAfterLast("/").toIntOrNull()
            if (chapterNum == null || chapterNum < 1) {
                return NanoHTTPD.newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "text/plain",
                    "Invalid chapter number"
                )
            }
            val chapterFile = File(bookDir, "chapters/chapter_$chapterNum.html")
            return if (chapterFile.exists() && chapterFile.isFile) {
                try {
                    val content = chapterFile.readText()
                    NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", content)
                } catch (e: Exception) {
                    NanoHTTPD.newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "text/plain",
                        "Error reading chapter"
                    )
                }
            } else {
                NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Chapter not found")
            }
        }

        // State endpoint
        if (uri == "api/state") {
            when (session.method) {
                Method.GET -> {
                    return if (metaFile.exists()) {
                        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", metaFile.readText())
                    } else {
                        NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{}")
                    }
                }
                Method.POST -> {
                    val body = parseBody(session)
                    if (body != null) {
                        try {
                            val json = JSONObject(body)
                            val current = if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
                            current.put("currentChapterIdx", json.optInt("currentChapterIdx", current.optInt("currentChapterIdx")))
                            current.put("scrollPercent", json.optDouble("scrollPercent", current.optDouble("scrollPercent")))
                            current.put("fontSize", json.optInt("fontSize", current.optInt("fontSize")))
                            current.put("lineHeight", json.optDouble("lineHeight", current.optDouble("lineHeight")))
                            current.put("readerWidth", json.optInt("readerWidth", current.optInt("readerWidth")))
                            current.put("theme", json.optString("theme", current.optString("theme")))
                            current.put("customBg", if (json.has("customBg")) json.optString("customBg") else current.optString("customBg", null))
                            current.put("customTextColor", if (json.has("customTextColor")) json.optString("customTextColor") else current.optString("customTextColor", null))
                            current.put("lastReadTimestamp", System.currentTimeMillis())
                            metaFile.writeText(current.toString(2))
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", "{}")
                        } catch (e: Exception) {
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid JSON")
                        }
                    }
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Empty body")
                }
                else -> return NanoHTTPD.newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Not allowed")
            }
        }

        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun parseBody(session: IHTTPSession): String? {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength <= 0) return null
        val buffer = ByteArray(contentLength)
        val input = session.inputStream
        var bytesRead = 0
        while (bytesRead < contentLength) {
            val r = input.read(buffer, bytesRead, contentLength - bytesRead)
            if (r == -1) break
            bytesRead += r
        }
        return String(buffer, 0, bytesRead)
    }

    private fun getMimeType(filename: String): String {
        return when {
            filename.endsWith(".html") || filename.endsWith(".htm") -> "text/html"
            filename.endsWith(".css") -> "text/css"
            filename.endsWith(".js") -> "application/javascript"
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
            filename.endsWith(".gif") -> "image/gif"
            filename.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}