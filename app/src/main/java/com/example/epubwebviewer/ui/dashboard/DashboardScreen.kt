package com.example.epubwebviewer.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.epubwebviewer.data.BookMetadata
import com.example.epubwebviewer.ui.settings.SettingsScreen
import com.example.epubwebviewer.viewmodel.DashboardViewModel
import com.example.epubwebviewer.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val books = viewModel.books
    val showProgress = viewModel.showExtractingDialog
    val progress = viewModel.extractionProgress

    // Settings navigation state
    var showSettings by remember { mutableStateOf(false) }

    // Theme state
    val systemDark = isSystemInDarkTheme()
    var isDark by remember { mutableStateOf(systemDark) }

    // File picker for EPUB import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEpubSelected(it) }
    }

    // Dialog states
    var renameDialogVisible by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<BookMetadata?>(null) }
    var deleteConfirmVisible by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<BookMetadata?>(null) }

    if (showSettings) {
        // Show settings screen
        SettingsScreen(onBackPressed = { showSettings = false })
    } else {
        // Main dashboard
        MaterialTheme(
            colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("EPUB Browser Viewer") },
                        actions = {
                            // Settings button
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                            // Theme toggle
                            IconButton(onClick = { isDark = !isDark }) {
                                Icon(
                                    if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme"
                                )
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { filePickerLauncher.launch("application/epub+zip") }) {
                        Icon(Icons.Default.Add, contentDescription = "Add EPUB")
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    if (books.isEmpty() && !showProgress) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No books imported yet.\nTap + to add an EPUB.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(books, key = { it.id }) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { viewModel.openBook(book) },
                                    onLongClick = {
                                        bookToRename = book
                                        renameDialogVisible = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Extraction progress dialog
            if (showProgress) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Importing EPUB...") },
                    text = {
                        Column {
                            Text("Extracting and converting...")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { progress })
                        }
                    },
                    confirmButton = {}
                )
            }

            // Rename + Delete dialog
            if (renameDialogVisible && bookToRename != null) {
                val book = bookToRename!!
                var titleState = remember { mutableStateOf(book.title) }
                AlertDialog(
                    onDismissRequest = { renameDialogVisible = false },
                    title = { Text("Manage Book") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = titleState.value,
                                onValueChange = { titleState.value = it },
                                label = { Text("Title") }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    renameDialogVisible = false
                                    bookToDelete = book
                                    deleteConfirmVisible = true
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete this book")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.renameBook(book.id, titleState.value)
                            renameDialogVisible = false
                        }) { Text("Rename") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameDialogVisible = false }) { Text("Cancel") }
                    }
                )
            }

            // Delete confirmation
            if (deleteConfirmVisible && bookToDelete != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmVisible = false },
                    title = { Text("Delete Book") },
                    text = { Text("Are you sure you want to delete \"${bookToDelete!!.title}\"?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteBook(bookToDelete!!.id)
                            deleteConfirmVisible = false
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmVisible = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: BookMetadata,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.chapterCount} chapters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (book.lastReadTimestamp > 0) {
                Text(
                    text = "Last read: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(book.lastReadTimestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}