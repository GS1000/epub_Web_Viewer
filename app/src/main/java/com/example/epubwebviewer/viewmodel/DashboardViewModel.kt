package com.example.epubwebviewer.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubwebviewer.data.BookMetadata
import com.example.epubwebviewer.data.BookRepository
import com.example.epubwebviewer.data.EpubExtractor
import com.example.epubwebviewer.server.ReaderServerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val repository = BookRepository(context)
    private val extractor = EpubExtractor(context)

    // ---- UI State ----
    var books by mutableStateOf<List<BookMetadata>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set

    // Extraction progress dialog
    var showExtractingDialog by mutableStateOf(false)
        private set
    var extractionProgress by mutableFloatStateOf(0f)
        private set

    // ---- Server / Service ----
    private var currentBookId: String? = null
    private var launchJob: kotlinx.coroutines.Job? = null

    init {
        loadBooks()
    }

    fun loadBooks() {
        viewModelScope.launch {
            isLoading = true
            books = repository.getAllBooks()
            isLoading = false
        }
    }

    // Called when user picks an EPUB file
    fun onEpubSelected(uri: Uri) {
        viewModelScope.launch {
            showExtractingDialog = true
            extractionProgress = 0f
            try {
                extractor.extract(uri).collectLatest { progress: Float ->
                    extractionProgress = progress
                }
                // Refresh list after extraction
                loadBooks()
            } catch (e: Exception) {
                // TODO: show error
            } finally {
                showExtractingDialog = false
            }
        }
    }

    fun renameBook(bookId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameBook(bookId, newTitle)
            loadBooks()
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            loadBooks()
        }
    }

    /**
     * Opens a book by starting the foreground service,
     * then launching the browser once the server port is known.
     */
    fun openBook(book: BookMetadata) {
        // If a different book is already open, stop the old service first
        if (currentBookId != null && currentBookId != book.id) {
            stopService()
        }

        currentBookId = book.id

        // Start the service
        val intent = Intent(context, ReaderServerService::class.java).apply {
            putExtra(ReaderServerService.EXTRA_BOOK_ID, book.id)
        }
        context.startService(intent)

        // Cancel any previous launch job
        launchJob?.cancel()
        launchJob = viewModelScope.launch {
            // Wait for a non‑null port from the service
            val port = ReaderServerService.portFlow.first { it != null }
            val url = "http://127.0.0.1:$port/"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    /**
     * Stops the foreground service manually.
     * This is called when the user returns to the dashboard (e.g., in onResume).
     */
    fun stopService() {
        if (currentBookId != null) {
            val intent = Intent(context, ReaderServerService::class.java)
            context.stopService(intent)
            currentBookId = null
            launchJob?.cancel()
            launchJob = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure service is stopped if ViewModel is destroyed
        stopService()
    }
}