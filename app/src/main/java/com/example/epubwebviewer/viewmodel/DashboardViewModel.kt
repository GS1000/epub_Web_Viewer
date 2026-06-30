package com.example.epubwebviewer.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubwebviewer.data.BookMetadata
import com.example.epubwebviewer.data.BookRepository
import com.example.epubwebviewer.data.EpubExtractor
import com.example.epubwebviewer.data.SettingsRepository
import com.example.epubwebviewer.server.ReaderServerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val repository = BookRepository(context)
    private val settingsRepository = SettingsRepository(context)
    private val extractor = EpubExtractor(context)

    // ---- UI State (expose as State) ----
    private val _books = mutableStateOf<List<BookMetadata>>(emptyList())
    val books: State<List<BookMetadata>> = _books

    private val _pinnedBooks = mutableStateOf<List<BookMetadata>>(emptyList())
    val pinnedBooks: State<List<BookMetadata>> = _pinnedBooks

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    // Extraction progress dialog
    private val _showExtractingDialog = mutableStateOf(false)
    val showExtractingDialog: State<Boolean> = _showExtractingDialog

    private val _extractionProgress = mutableFloatStateOf(0f)
    val extractionProgress: State<Float> = _extractionProgress

    // Sort order
    private var sortOrder by mutableStateOf(settingsRepository.getSortOrder())

    // ---- Server / Service ----
    private var currentBookId: String? = null
    private var launchJob: kotlinx.coroutines.Job? = null

    init {
        loadBooks()
    }

    fun loadBooks() {
        viewModelScope.launch {
            _isLoading.value = true
            val allBooks = repository.getAllBooks()
            val sorted = applySorting(allBooks)
            _books.value = sorted
            _pinnedBooks.value = sorted.filter { it.pinned }
            _isLoading.value = false
        }
    }

    fun refreshBooks() {
        loadBooks()
    }

    private fun applySorting(list: List<BookMetadata>): List<BookMetadata> {
        return when (sortOrder) {
            "last_read_asc" -> list.sortedBy { it.lastReadTimestamp }
            "title_asc" -> list.sortedBy { it.title.lowercase() }
            "title_desc" -> list.sortedByDescending { it.title.lowercase() }
            "import_desc" -> list.sortedByDescending { it.importTimestamp }
            "import_asc" -> list.sortedBy { it.importTimestamp }
            else -> list.sortedByDescending { it.lastReadTimestamp }
        }
    }

    fun onSortOrderChanged(newOrder: String) {
        sortOrder = newOrder
        loadBooks()
    }

    fun onEpubSelected(uri: Uri) {
        viewModelScope.launch {
            _showExtractingDialog.value = true
            _extractionProgress.floatValue = 0f
            try {
                extractor.extract(uri).collectLatest { progress ->
                    _extractionProgress.floatValue = progress
                }
                loadBooks()
            } catch (e: Exception) {
                // TODO: show error
            } finally {
                _showExtractingDialog.value = false
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

    fun togglePinned(bookId: String) {
        viewModelScope.launch {
            repository.togglePinned(bookId)
            loadBooks()
        }
    }

    fun changeCoverImage(bookId: String, uri: Uri?) {
        viewModelScope.launch {
            repository.updateCoverImage(bookId, uri)
            loadBooks()
        }
    }

    suspend fun getImagesForBook(bookId: String): List<File>? {
        return repository.getImagesForBook(bookId)
    }

    fun openBook(book: BookMetadata) {
        if (currentBookId != null && currentBookId != book.id) {
            stopService()
        }
        currentBookId = book.id

        val intent = Intent(context, ReaderServerService::class.java).apply {
            putExtra(ReaderServerService.EXTRA_BOOK_ID, book.id)
        }
        context.startService(intent)

        launchJob?.cancel()
        launchJob = viewModelScope.launch {
            val port = ReaderServerService.portFlow.first { it != null }
            val url = "http://127.0.0.1:$port/"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

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
        stopService()
    }
}