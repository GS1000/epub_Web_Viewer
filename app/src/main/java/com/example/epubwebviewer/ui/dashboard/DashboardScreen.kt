package com.example.epubwebviewer.ui.dashboard

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.epubwebviewer.data.BookMetadata
import com.example.epubwebviewer.ui.settings.SettingsScreen
import com.example.epubwebviewer.viewmodel.DashboardViewModel
import com.example.epubwebviewer.viewmodel.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val books by viewModel.books
    val pinnedBooks by viewModel.pinnedBooks
    val showProgress by viewModel.showExtractingDialog
    val progress by viewModel.extractionProgress
    val context = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    var isDark by remember { mutableStateOf(systemDark) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEpubSelected(it) }
    }

    var coverChangeBookId by remember { mutableStateOf<String?>(null) }
    var coverImages by remember { mutableStateOf<List<File>>(emptyList()) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        coverChangeBookId?.let { bookId ->
            viewModel.changeCoverImage(bookId, uri)
            coverChangeBookId = null
        }
    }

    var renameDialogVisible by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<BookMetadata?>(null) }
    var deleteConfirmVisible by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<BookMetadata?>(null) }
    var coverChangeDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(coverChangeDialogVisible, coverChangeBookId) {
        if (coverChangeDialogVisible && coverChangeBookId != null) {
            coverImages = viewModel.getImagesForBook(coverChangeBookId!!) ?: emptyList()
        }
    }

    if (showSettings) {
        SettingsScreen(
            onBackPressed = { showSettings = false },
            viewModel = viewModel()
        )
    } else {
        MaterialTheme(
            colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("EPUB Browser Viewer") },
                        actions = {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                            IconButton(onClick = { isDark = !isDark }) {
                                Icon(
                                    if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Star, contentDescription = "Pinned") },
                            label = { Text("Pinned") },
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { filePickerLauncher.launch("application/epub+zip") }) {
                        Icon(Icons.Default.Add, contentDescription = "Add EPUB")
                    }
                }
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Search books...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    val currentList = if (selectedTab == 0) books else pinnedBooks
                    val filteredList = remember(currentList, searchQuery) {
                        if (searchQuery.isBlank()) currentList
                        else currentList.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            currentList.isEmpty() && !showProgress -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedTab == 0) "No books imported yet.\nTap + to add an EPUB."
                                        else "No pinned books.\nPin a book from the Home tab.",
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            filteredList.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No books match \"$searchQuery\".",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 150.dp),
                                    contentPadding = PaddingValues(
                                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredList, key = { it.id }) { book ->
                                        BookCard(
                                            book = book,
                                            onClick = { viewModel.openBook(book) },
                                            onLongClick = {
                                                bookToRename = book
                                                renameDialogVisible = true
                                            },
                                            onPinToggle = { viewModel.togglePinned(book.id) },
                                            onCoverChange = {
                                                coverChangeBookId = book.id
                                                coverChangeDialogVisible = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ----- Dialogs (unchanged) -----
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pinned")
                                Switch(
                                    checked = book.pinned,
                                    onCheckedChange = {
                                        viewModel.togglePinned(book.id)
                                        renameDialogVisible = false
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    renameDialogVisible = false
                                    coverChangeBookId = book.id
                                    coverChangeDialogVisible = true
                                }
                            ) {
                                Text("Change cover image")
                            }
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

            if (deleteConfirmVisible && bookToDelete != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmVisible = false },
                    title = { Text("Delete Book") },
                    text = { Text("Are you sure you want to delete \"${bookToDelete!!.title}\"?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteBook(bookToDelete!!.id)
                            deleteConfirmVisible = false
                            bookToDelete = null
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmVisible = false }) { Text("Cancel") }
                    }
                )
            }

            if (coverChangeDialogVisible && coverChangeBookId != null) {
                AlertDialog(
                    onDismissRequest = {
                        coverChangeDialogVisible = false
                        coverChangeBookId = null
                    },
                    title = { Text("Choose Cover Image") },
                    text = {
                        Column {
                            Text("Select an image from the book or pick from gallery.")
                            Spacer(modifier = Modifier.height(12.dp))
                            if (coverImages.isEmpty()) {
                                Text("No images found in this book.")
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 80.dp),
                                    modifier = Modifier.height(200.dp)
                                ) {
                                    items(coverImages, key = { it.absolutePath }) { file ->
                                        Card(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .size(80.dp)
                                                .combinedClickable(
                                                    onClick = {
                                                        val uri = Uri.fromFile(file)
                                                        viewModel.changeCoverImage(coverChangeBookId!!, uri)
                                                        coverChangeDialogVisible = false
                                                        coverChangeBookId = null
                                                    }
                                                ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            AsyncImage(
                                                model = file,
                                                contentDescription = "Cover image",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    galleryLauncher.launch("image/*")
                                    coverChangeDialogVisible = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pick from gallery")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.changeCoverImage(coverChangeBookId!!, null)
                                    coverChangeDialogVisible = false
                                    coverChangeBookId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove cover")
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = {
                            coverChangeDialogVisible = false
                            coverChangeBookId = null
                        }) { Text("Cancel") }
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
    onLongClick: () -> Unit,
    onPinToggle: () -> Unit,
    onCoverChange: () -> Unit
) {
    val context = LocalContext.current
    val coverFile = remember(book.coverImagePath) {
        book.coverImagePath?.let { File(context.filesDir, "books/${book.id}/$it") }
            ?.takeIf { it.exists() }
    }

    val totalChapters = book.chapterCount.coerceAtLeast(1)
    val readChapters = (book.currentChapterIdx + 1).coerceIn(0, totalChapters)
    val progress = readChapters.toFloat() / totalChapters

    Box(
        modifier = Modifier
            .aspectRatio(0.68f)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // ---- Background: cover image or placeholder ----
        if (coverFile != null) {
            AsyncImage(
                model = coverFile,
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // ---- Bottom gradient scrim so text stays readable on any cover ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
        )

        // ---- Floating pin star badge ----
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onPinToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (book.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (book.pinned) "Unpin" else "Pin",
                tint = if (book.pinned) Color(0xFFFFD54F) else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // ---- Title, progress bar, chapter/time info ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF9DB8FF),
                trackColor = Color.White.copy(alpha = 0.25f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$readChapters/${book.chapterCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (book.lastReadTimestamp > 0) {
                    val timeAgo = DateUtils.getRelativeTimeSpanString(
                        book.lastReadTimestamp,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                    Text(
                        text = timeAgo.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}