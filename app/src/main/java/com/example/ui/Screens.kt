package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- MAIN ENTRANCE CONTROLLER ---

@Composable
fun BookVerseAppContent(viewModel: BookVerseViewModel) {
    var currentTab by remember { mutableStateOf("shelf") }
    val showReaderBookId by viewModel.currentBookId.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val userPin by viewModel.userPinCode.collectAsState()

    if (isAppLocked && userPin.isNotEmpty()) {
        SecurityLockScreen(correctPin = userPin) {
            viewModel.toggleAppLock(false)
        }
    } else {
        Scaffold(
            containerColor = Color(0xFF1C1B1F),
            bottomBar = {
                if (showReaderBookId == null) {
                    BookVerseBottomNavigation(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF1C1B1F))
            ) {
                if (showReaderBookId != null) {
                    BookReaderScreen(viewModel = viewModel)
                } else {
                    Crossfade(targetState = currentTab, label = "tabCrossfade") { tab ->
                        when (tab) {
                            "shelf" -> BookshelfScreen(viewModel = viewModel)
                            "board" -> StickyBoardScreen(viewModel = viewModel)
                            "ai" -> StudyHubScreen(viewModel = viewModel)
                            "hub" -> ProductivityHubScreen(viewModel = viewModel)
                            "analytics" -> AnalyticsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

// --- CORE ACCESSIBLE NAVIGATION BAR ---

@Composable
fun BookVerseBottomNavigation(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF2B2930),
        contentColor = Color(0xFFE6E1E5),
        modifier = Modifier.testTag("app_navigation_bar")
    ) {
        val items = listOf(
            Triple("shelf", "Shelf", Icons.Filled.Home),
            Triple("board", "Sticky Board", Icons.Filled.List),
            Triple("ai", "AI Study", Icons.Filled.Star),
            Triple("hub", "Task Hub", Icons.Filled.Check),
            Triple("analytics", "Analytics", Icons.Filled.Info)
        )
        items.forEach { (tabId, label, icon) ->
            NavigationBarItem(
                selected = currentTab == tabId,
                onClick = { onTabSelected(tabId) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF21005D),
                    unselectedIconColor = Color(0xFFE6E1E5),
                    selectedTextColor = Color(0xFFEADDFF),
                    unselectedTextColor = Color(0xFFE6E1E5).copy(alpha = 0.6f),
                    indicatorColor = Color(0xFFEADDFF)
                )
            )
        }
    }
}

private fun computeLocalSemanticScore(book: Book, query: String): Float {
    if (query.isBlank()) return 1.0f
    val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return 1.0f

    val synonyms = mapOf(
        "tech" to listOf("technology", "computer", "science", "software", "digital", "architecture", "ai"),
        "science" to listOf("physics", "tech", "technology", "informational", "experiments", "scholar"),
        "notes" to listOf("study", "exam", "revision", "academic", "lecture", "sticky"),
        "fiction" to listOf("story", "novel", "literature", "celestial", "magic"),
        "history" to listOf("historical", "timelines", "paradigms", "foundations"),
        "learn" to listOf("study", "education", "retention", "quiz", "flashcard", "exercises"),
        "games" to listOf("gamification", "xp", "streak"),
        "ai" to listOf("artificial", "intelligence", "rag", "gemini", "wisperia", "scanning")
    )

    var score = 0f

    val titleLower = book.title.lowercase()
    val authorLower = book.author.lowercase()
    val subjectLower = book.subject.lowercase()
    val categoryLower = book.category.lowercase()
    val summaryLower = book.fullSummary.lowercase()

    for (term in terms) {
        if (titleLower.contains(term)) score += 10.0f
        if (authorLower.contains(term)) score += 8.0f
        if (subjectLower.contains(term)) score += 5.0f
        if (categoryLower.contains(term)) score += 5.0f
        if (summaryLower.contains(term)) score += 3.0f

        val related = synonyms[term] ?: emptyList()
        for (rel in related) {
            if (titleLower.contains(rel)) score += 4.0f
            if (authorLower.contains(rel)) score += 3.0f
            if (subjectLower.contains(rel)) score += 2.0f
            if (categoryLower.contains(rel)) score += 2.0f
            if (summaryLower.contains(rel)) score += 1.0f
        }
    }

    return score
}

// --- 1. PREMIUM WOODEN BOOKSHELF SCREEN ---

@Composable
fun BookshelfScreen(viewModel: BookVerseViewModel) {
    val books by viewModel.books.collectAsState()
    val gamification by viewModel.userGamification.collectAsState()
    val isOperating by viewModel.isAIOperating.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val semanticRankingIds by viewModel.semanticRankingIds.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var aiSmartSort by remember { mutableStateOf(true) }

    val categories = listOf("All") + books.map { it.category }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Wooden Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Wisperia",
                    fontSize = 28.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF)
                )
                Text(
                    text = "Welcome, ${gamification?.levelName ?: "Reader"} (XP: ${gamification?.xp ?: 0})",
                    fontSize = 12.sp,
                    color = Color(0xFF938F99)
                )
            }

            IconButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFD0BCFF), CircleShape)
                    .testTag("import_book_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Import PDF Book", tint = Color(0xFF381E72))
            }
        }

        // Semantic Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("semantic_search_bar"),
            placeholder = { Text("Find books by title, author, or summary concepts...", color = Color(0xFF938F99), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = if (searchQuery.isNotEmpty()) Color(0xFFD0BCFF) else Color(0xFF938F99)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = Color(0xFF938F99)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Smart AI search badge",
                        tint = Color(0xFFD0BCFF).copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1D1B20),
                unfocusedContainerColor = Color(0xFF1D1B20),
                disabledContainerColor = Color(0xFF1D1B20),
                focusedBorderColor = Color(0xFFD0BCFF),
                unfocusedBorderColor = Color(0xFF49454F),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )

        // AI Smart Sorting Toggle Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "AI sorted",
                        tint = if (aiSmartSort) Color(0xFFD0BCFF) else Color(0xFF938F99),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (aiSmartSort) "AI Smart Shelf Sorting Active" else "Standard Shelf Ordering",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (aiSmartSort) Color(0xFFD0BCFF) else Color.White
                        )
                        Text(
                            text = if (aiSmartSort) "Books auto-sorted by reading progress, favorites & history" else "Books listed in chronological upload order",
                            fontSize = 10.sp,
                            color = Color(0xFF938F99)
                        )
                    }
                }
                Switch(
                    checked = aiSmartSort,
                    onCheckedChange = { aiSmartSort = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF381E72),
                        checkedTrackColor = Color(0xFFD0BCFF),
                        uncheckedThumbColor = Color(0xFF938F99),
                        uncheckedTrackColor = Color(0xFF1C1B1F)
                    )
                )
            }
        }

        // Category filter chips
        Row(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val selected = selectedCategoryFilter == cat
                FilterChip(
                    selected = selected,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD0BCFF),
                        selectedLabelColor = Color(0xFF381E72),
                        containerColor = Color(0xFF2B2930),
                        labelColor = Color(0xFF938F99)
                    )
                )
            }
        }

        if (isOperating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF2B2930), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFD0BCFF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("AI scanning structural metadata and covers...", color = Color(0xFFE6E1E5))
                }
            }
        }

        // Score the books based on semantic match
        val scoredBooks = remember(books, searchQuery, semanticRankingIds) {
            if (searchQuery.isBlank()) {
                books.map { it to 1f }
            } else {
                if (semanticRankingIds.isNotEmpty()) {
                    books.map { book ->
                        val rankIndex = semanticRankingIds.indexOf(book.id)
                        val score = if (rankIndex != -1) {
                            5000f - rankIndex * 10f
                        } else {
                            computeLocalSemanticScore(book, searchQuery)
                        }
                        book to score
                    }
                } else {
                    books.map { book ->
                        book to computeLocalSemanticScore(book, searchQuery)
                    }
                }
            }
        }

        // Apply dynamic AI-driven auto sorting of books or normal sorting
        val sortedBooks = remember(scoredBooks, aiSmartSort, selectedCategoryFilter, searchQuery) {
            var list = scoredBooks
            if (selectedCategoryFilter != "All") {
                list = list.filter { it.first.category == selectedCategoryFilter }
            }

            if (searchQuery.isNotBlank()) {
                list.filter { it.second > 0f }
                    .sortedByDescending { it.second }
                    .map { it.first }
            } else {
                val booksList = list.map { it.first }
                if (aiSmartSort) {
                    // AI-driven scoring model
                    booksList.sortedWith(compareByDescending<Book> { book ->
                        var score = 0.0
                        // Favorite boost
                        if (book.isFavorite) {
                            score += 5000.0
                        }
                        // Current active reading boost
                        if (book.progressPages > 0 && book.progressPages < book.totalPages) {
                            val fraction = book.progressPages.toFloat() / book.totalPages.toFloat()
                            score += 3000.0 + (fraction * 1000.0)
                        } else if (book.progressPages == book.totalPages - 1) {
                            // Completed books sorted a bit lower to give room to actively read content
                            score += 500.0
                        }
                        // Recency booster decay curve
                        val recencyMs = System.currentTimeMillis() - book.lastReadTime
                        val recencyMinutes = recencyMs / (60.0 * 1000.0)
                        if (recencyMinutes >= 0) {
                            score += (20000.0 / (recencyMinutes + 1.0))
                        }
                        score
                    })
                } else {
                    booksList.sortedByDescending { it.id }
                }
            }
        }

        // Extracted features lists
        val recentlyOpened = remember(books) {
            books.filter { it.progressPages > 0 }
                .sortedByDescending { it.lastReadTime }
                .take(3)
        }

        val favoriteBooks = remember(books) {
            books.filter { it.isFavorite }
                .sortedByDescending { it.lastReadTime }
        }

        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Empty Shelf",
                        modifier = Modifier.size(72.dp),
                        tint = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your Wisperia shelf is empty.", color = Color(0xFF938F99), fontWeight = FontWeight.Bold)
                    Text("Import any document to generate summarize modules instantly.", color = Color(0xFF49454F), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    // Search layout section
                    item {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Search Results (${sortedBooks.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF)
                                )
                                if (isSearching) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFD0BCFF),
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Syncing AI concepts...",
                                            fontSize = 11.sp,
                                            color = Color(0xFF938F99)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (semanticRankingIds.isNotEmpty()) "⚡ AI Vector Sorted" else "Local Semantic Match",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (semanticRankingIds.isNotEmpty()) Color(0xFFD0BCFF) else Color(0xFF938F99)
                                    )
                                }
                            }
                            if (sortedBooks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matches. Try terms like 'AI', 'study', or 'principles'.",
                                        color = Color(0xFF938F99),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    val rows = sortedBooks.chunked(3)
                    items(rows) { shelfBooks ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                shelfBooks.forEach { book ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.selectBook(book.id) }
                                    ) {
                                        BookVisualCard(book = book, onFavoriteClick = { viewModel.toggleFavorite(book) })
                                    }
                                }
                                repeat(3 - shelfBooks.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = 12.dp)
                                    .height(16.dp)
                                    .shadow(8.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF8B5E3C),
                                                Color(0xFF6E472D),
                                                Color(0xFF4C2F1C)
                                            )
                                        ),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                } else {
                    // Default view rows
                    // Section 1: Recently Opened Books Row
                    if (recentlyOpened.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "Recently Opened Books",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF2B2930).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    items(recentlyOpened) { book ->
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .clickable { viewModel.selectBook(book.id) }
                                        ) {
                                            BookVisualCard(book = book, onFavoriteClick = { viewModel.toggleFavorite(book) })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Favorites Row
                    if (favoriteBooks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "Favorite Books",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF2B2930).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    items(favoriteBooks) { book ->
                                        Box(
                                            modifier = Modifier
                                                .width(90.dp)
                                                .clickable { viewModel.selectBook(book.id) }
                                        ) {
                                            BookVisualCard(book = book, onFavoriteClick = { viewModel.toggleFavorite(book) })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: General wooden bookshelves organized by category filter
                    item {
                        Text(
                            text = if (selectedCategoryFilter == "All") "All Library Bookshelves" else "Bookshelf: $selectedCategoryFilter",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    val rows = sortedBooks.chunked(3)
                    items(rows) { shelfBooks ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                shelfBooks.forEach { book ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.selectBook(book.id) }
                                    ) {
                                        BookVisualCard(book = book, onFavoriteClick = { viewModel.toggleFavorite(book) })
                                    }
                                }
                                repeat(3 - shelfBooks.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = 12.dp)
                                    .height(16.dp)
                                    .shadow(8.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF8B5E3C),
                                                Color(0xFF6E472D),
                                                Color(0xFF4C2F1C)
                                            )
                                        ),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportBookDialog(
            onDismiss = { showImportDialog = false },
            onImport = { fileName, sample ->
                viewModel.importNewBook(fileName, sample)
                showImportDialog = false
            }
        )
    }
}

@Composable
fun BookVisualCard(book: Book, onFavoriteClick: () -> Unit) {
    val progressFraction = book.progressPages.toFloat() / book.totalPages.coerceAtLeast(1).toFloat()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(130.dp)
                .shadow(12.dp, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(book.coverColor),
                            Color(book.coverColor).copy(alpha = 0.85f)
                        )
                    ),
                    RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                )
        ) {
            // Book Spine shader effect
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.CenterStart)
            )

            // Tactile Gold Bookmark Ribbon proportional to reading progress
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp)
                        .width(6.dp)
                        .height((12 + progressFraction * 45).dp) // hangs down as user progresses!
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFD700), // Pure Gold
                                    Color(0xFFB8860B), // Deep bronze
                                    Color(0xFFFFD700)
                                )
                            ),
                            shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                        )
                        .shadow(3.dp)
                )
            }

            // Cover Layout Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Column {
                    Text(
                        text = book.author,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Reusable Book progress bar running at the bottom
            BookProgressBar(
                progressPages = book.progressPages,
                totalPages = book.totalPages,
                baseColor = if (book.coverColor != 0) Color(book.coverColor) else null,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Favorite Icon overlay (star sticker)
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favorite",
                tint = if (book.isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .clickable { onFavoriteClick() }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.title,
            color = Color(0xFFE6E1E5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun BookProgressBar(
    progressPages: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    baseColor: Color? = null
) {
    if (totalPages <= 0) return
    val progressFraction = (progressPages.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
    if (progressFraction <= 0f) return

    val progressPercentage = (progressFraction * 100).toInt()

    val dynamicColor = remember(progressFraction, baseColor) {
        when {
            progressFraction >= 0.99f -> Color(0xFF4CAF50) // Emerald Green for complete
            baseColor != null -> baseColor
            progressFraction > 0.7f -> Color(0xFF81C784) // Soft green for near complete
            progressFraction > 0.4f -> Color(0xFFD0BCFF) // Elegant Orchid Purple
            progressFraction > 0.15f -> Color(0xFFFFD700) // Pure Gold
            else -> Color(0xFFFF8A65) // Sunset Coral for starting out
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.75f)
                    )
                )
            )
            .padding(vertical = 4.dp, horizontal = 6.dp)
            .testTag("book_progress_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$progressPercentage%",
                color = dynamicColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("progress_percentage_text")
            )
            LinearProgressIndicator(
                progress = { progressFraction },
                color = dynamicColor,
                trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .testTag("progress_indicator")
            )
        }
    }
}

// --- 2. 3D PAGE CURL READER SCREEN ---

@Composable
fun BookReaderScreen(viewModel: BookVerseViewModel) {
    val book by viewModel.currentBook.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()

    if (book == null) return

    val currentBookRef = book!!
    var localPage by remember(currentBookRef.id) { mutableIntStateOf(currentBookRef.progressPages) }
    var pageOffsetX by remember { mutableFloatStateOf(0f) }
    val maxPages = currentBookRef.totalPages

    // Side-chapter menu drawer state
    var showDrawer by remember { mutableStateOf(false) }

    val mockPageText = listOf(
        "Page 1: Introduction to BookVerse principles. Digital media can mimic tactile elements.",
        "Page 2: Standard structures build complex interfaces cleanly. Ensure to satisfy core user goals.",
        "Page 3: Responsive layouts dynamically resize to prevent stretching on expanded tablets.",
        "Page 4: High fidelity details build premium appeal. Choose cohesive deep leather palettes.",
        "Page 5: Active recall models suggest checking correct quiz answer states instantly.",
        "Page 6: Focus routines require robust local persistence to prevent telemetry interruptions.",
        "Page 7: Gamification XP levels: Reader, Scholar, Researcher, Master, Sage, Grand Sage.",
        "Page 8: The Bookshelf holds wooden display structures to showcase books aesthetically.",
        "Page 9: Smart Board displays notes like fridge card magnets with custom color configurations.",
        "Page 10: RAG assistants answer context-sensitive queries directly matched against the textbook index.",
        "Page 11: Task management checklists synchronize seamlessly into persistent database schemas.",
        "Page 12: Continuous analytics tracking provides progress scores to maintain consistent reading habits."
    )

    val currentPageText = mockPageText.getOrNull(localPage % mockPageText.size) ?: "Empty page. Proceed forward."

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.selectBook(null) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Shelf", tint = Color(0xFFE6E1E5))
                }
                Text(
                    text = currentBookRef.title,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                IconButton(onClick = { showDrawer = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Table of Contents", tint = Color(0xFFE6E1E5))
                }
            }
        },
        bottomBar = {
            // Audiobook TTS + progress bar controller
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930))
                    .padding(16.dp)
            ) {
                // Audiobook audio layout panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Page ${localPage + 1} of ${maxPages}",
                        fontSize = 13.sp,
                        color = Color(0xFF938F99)
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.togglePlayTTS(currentPageText) },
                            modifier = Modifier
                                .background(Color(0xFFD0BCFF), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                contentDescription = if (isTtsPlaying) "Pause Audio" else "Play Audiobook",
                                tint = Color(0xFF381E72)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val nextPageText = mockPageText.getOrNull((localPage + 1) % mockPageText.size) ?: "The end of this celestial book. Wisperia scanning finished."
            val prevPageText = mockPageText.getOrNull((localPage - 1).coerceAtLeast(0) % mockPageText.size) ?: "Cover of this celestial book."

            TactilePageCurlReader(
                currentPageText = currentPageText,
                nextPageText = nextPageText,
                prevPageText = prevPageText,
                localPage = localPage,
                maxPages = maxPages,
                currentBookRef = currentBookRef,
                viewModel = viewModel,
                onPageTurned = { targetPage ->
                    localPage = targetPage
                    viewModel.updateBookReadingPage(currentBookRef, targetPage)
                }
            )

            // Slide Chapter Drawer modal integration
            if (showDrawer) {
                ModalDrawer(onDismiss = { showDrawer = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                            .background(Color(0xFF1C1B1F))
                            .padding(24.dp)
                    ) {
                        Text("Chapters Menu", color = Color(0xFFD0BCFF), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Divider(color = Color(0xFF49454F))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            // Display list of chapters
                            val chaptersArr = try {
                                JSONArray(currentBookRef.chaptersJson)
                            } catch (e: Exception) {
                                JSONArray()
                            }
                            
                            items(chaptersArr.length()) { idx ->
                                val chObj = chaptersArr.getJSONObject(idx)
                                val title = chObj.getString("title")
                                val page = chObj.optInt("page", 0)

                                Text(
                                    text = title,
                                    color = if (page == localPage) Color(0xFFD0BCFF) else Color(0xFF938F99),
                                    fontSize = 14.sp,
                                    fontWeight = if (page == localPage) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            localPage = page
                                            viewModel.updateBookReadingPage(currentBookRef, localPage)
                                            showDrawer = false
                                        }
                                        .padding(vertical = 12.dp)
                                )
                                Divider(color = Color(0xFF332522))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBookPageSkins(offsetX: Float) {
    // Elegant wooden backboard layout base
    drawRect(
        color = Color(0xFF4A342B),
        size = Size(size.width, size.height)
    )

    // Center Binding line shade
    drawLine(
        color = Color.Black.copy(alpha = 0.6f),
        start = Offset(size.width / 2, 0f),
        end = Offset(size.width / 2, size.height),
        strokeWidth = 14f
    )

    // Side curves simulating curls
    if (offsetX != 0f) {
        val curlPath = Path().apply {
            moveTo(size.width / 2 + offsetX, 0f)
            lineTo(size.width / 2 + offsetX + 40f, size.height)
            lineTo(size.width / 2, size.height)
            lineTo(size.width / 2, 0f)
            close()
        }
        drawPath(
            path = curlPath,
            color = Color.Black.copy(alpha = 0.25f)
        )
    }
}

@Composable
fun ModalDrawer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = false) {}
        ) {
            content()
        }
    }
}

// --- 3. SMART NOTES DISPLAY PIN BOARD ---

@Composable
fun StickyBoardScreen(viewModel: BookVerseViewModel) {
    val notes by viewModel.stickyNotes.collectAsState()
    var showNoteCreator by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sticky Pins",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Color(0xFFD0BCFF)
                )
                Text(
                    text = "Tactile review board for handwritten notes & lectures",
                    color = Color(0xFF938F99),
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = { showNoteCreator = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF381E72))
                Text("Pin Note", color = Color(0xFF381E72), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Magnetic pin-board styling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
        ) {
            // Background Canvas drawing elegant pattern resembling bulletin cork board
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFF2B2930))
                // Draw decorative pins or horizontal line grids
            }

            if (notes.isEmpty()) {
                Text(
                    text = "No pins on board yet. Click Pin Note above.",
                    color = Color(0xFF938F99),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                notes.forEach { note ->
                    StickyNoteItem(note = note, viewModel = viewModel)
                }
            }
        }
    }

    if (showNoteCreator) {
        StickyNoteCreatorDialog(
            onDismiss = { showNoteCreator = false },
            onSave = { text, title, colorHex ->
                viewModel.createStickyNote(text, title, colorHex)
                showNoteCreator = false
            }
        )
    }
}

@Composable
fun StickyNoteItem(note: StickyNote, viewModel: BookVerseViewModel) {
    var offsetX by remember { mutableFloatStateOf(note.xOffset) }
    var offsetY by remember { mutableFloatStateOf(note.yOffset) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(140.dp, 140.dp)
            .shadow(6.dp, RoundedCornerShape(2.dp))
            .background(Color(android.graphics.Color.parseColor(note.colorHex)))
            .pointerInput(note.id) {
                detectDragGestures(
                    onDragEnd = {
                        viewModel.updateStickyNotePosition(note, offsetX, offsetY)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Draw a shiny metal thumb-pin at top center
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.Red, CircleShape)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (note.title.isNotEmpty()) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = note.text,
                fontSize = 11.sp,
                color = Color.Black.copy(alpha = 0.8f),
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { viewModel.deleteSticky(note) },
                modifier = Modifier
                    .align(Alignment.End)
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// --- 4. AI STUDY SUMMARY, QUIZ & STUDY TOOLS HUB ---

@Composable
fun StudyHubScreen(viewModel: BookVerseViewModel) {
    val currentBook by viewModel.currentBook.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI Summary & Quizzes",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            color = Color(0xFFD0BCFF)
        )
        Text(
            text = "Intelligent active recall aids and revise loops",
            color = Color(0xFF938F99),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (currentBook == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Please open a book from the shelf to view study aids & chat.",
                    color = Color(0xFF938F99),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val bookRef = currentBook!!

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF2B2930),
                contentColor = Color(0xFFD0BCFF),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFFD0BCFF)
                    )
                }
            ) {
                val menuTabs = listOf("Summary", "Flashcards", "Quiz", "Book Chat")
                menuTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = Color(0xFFD0BCFF),
                        unselectedContentColor = Color(0xFF938F99)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> SummarySection(book = bookRef)
                1 -> FlashcardsSection(book = bookRef)
                2 -> InteractiveQuizSection(book = bookRef)
                3 -> BookChatSection(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SummarySection(book: Book) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI Core Overview",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (book.fullSummary.isEmpty()) "Processing textbook context..." else book.fullSummary,
                        color = Color(0xFFE6E1E5),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Speed Revision Sheet",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (book.revisionNotes.isEmpty()) "No explicit speed revisions extracted." else book.revisionNotes,
                        color = Color(0xFFE6E1E5),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FlashcardsSection(book: Book) {
    val flashcardsList = remember(book.id) {
        try {
            val list = mutableListOf<Pair<String, String>>()
            val arr = JSONArray(book.flashcardsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.getString("question"), obj.getString("answer")))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (flashcardsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No flashcards found. Scan the book using AI.", color = Color(0xFF8E7D6F))
        }
    } else {
        var cardIndex by remember { mutableIntStateOf(0) }
        var isFlipped by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Card ${cardIndex + 1} of ${flashcardsList.size}",
                color = Color(0xFFC2B2A2),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Flippable interactive visual card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(240.dp)
                    .shadow(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2B2930))
                    .border(2.dp, Color(0xFFD0BCFF), RoundedCornerShape(16.dp))
                    .clickable { isFlipped = !isFlipped }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (!isFlipped) "QUESTION" else "ANSWER",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (!isFlipped) flashcardsList[cardIndex].first else flashcardsList[cardIndex].second,
                        color = Color(0xFFE6E1E5),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        isFlipped = false
                        if (cardIndex > 0) cardIndex -= 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930))
                ) {
                    Text("Previous", color = Color(0xFF938F99))
                }

                Button(
                    onClick = {
                        isFlipped = false
                        if (cardIndex < flashcardsList.size - 1) cardIndex += 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                ) {
                    Text("Next", color = Color(0xFF381E72))
                }
            }
        }
    }
}

@Composable
fun InteractiveQuizSection(book: Book) {
    val quizList = remember(book.id) {
        try {
            val list = mutableListOf<JSONObject>()
            val arr = JSONArray(book.quizQuestionsJson)
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (quizList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No quizzes formulated under active catalog.", color = Color(0xFF8E7D6F))
        }
    } else {
        var currentQIndex by remember { mutableIntStateOf(0) }
        var selectedOptIndex by remember { mutableIntStateOf(-1) }
        var showResults by remember { mutableStateOf(false) }

        val qItem = quizList[currentQIndex]
        val question = qItem.getString("question")
        val optionsArr = qItem.getJSONArray("options")
        val options = List(optionsArr.length()) { optionsArr.getString(it) }
        val correctIndex = qItem.getInt("answerIndex")

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Challenge Quiz: Question ${currentQIndex + 1} of ${quizList.size}",
                color = Color(0xFFD0BCFF),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = question,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6E1E5),
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            options.forEachIndexed { optIndex, optText ->
                val isSelected = selectedOptIndex == optIndex
                val canSeeCorrect = showResults

                val tint = when {
                    canSeeCorrect && optIndex == correctIndex -> Color(0xFF4CAF50) // Green shows correct
                    canSeeCorrect && isSelected && optIndex != correctIndex -> Color(0xFFF44336) // Red shows incorrect
                    isSelected -> Color(0xFFD0BCFF)
                    else -> Color(0xFF2B2930)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable(!showResults) { selectedOptIndex = optIndex },
                    colors = CardDefaults.cardColors(containerColor = tint),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = optText,
                        color = if (isSelected && !canSeeCorrect) Color(0xFF381E72) else Color(0xFFE6E1E5),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.End)) {
                if (!showResults) {
                    Button(
                        onClick = { showResults = true },
                        enabled = selectedOptIndex != -1,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                    ) {
                        Text("Submit Option", color = Color(0xFF381E72))
                    }
                } else {
                    Button(
                        onClick = {
                            if (currentQIndex < quizList.size - 1) {
                                currentQIndex += 1
                                selectedOptIndex = -1
                                showResults = false
                            } else {
                                // Done! Reset
                                currentQIndex = 0
                                selectedOptIndex = -1
                                showResults = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                    ) {
                        Text(if (currentQIndex < quizList.size - 1) "Next Query" else "Restart Quiz", color = Color(0xFF381E72))
                    }
                }
            }
        }
    }
}

// --- 5. RAG EXPERT AI INTERACTIVE BOOK CHAT SCREEN ---

@Composable
fun BookChatSection(viewModel: BookVerseViewModel) {
    val chatHistory by viewModel.chatHistory.collectAsState()
    var userText by remember { mutableStateOf("") }
    val isOperating by viewModel.isAIOperating.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            if (chatHistory.isEmpty()) {
                Text(
                    text = "Welcome to RAG book chat! Ask items like:\n- Explain Chapter 1 in layman terms.\n- Generate a summary of key points.",
                    color = Color(0xFF938F99),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatHistory) { (msg, isUser) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isUser) Color(0xFFD0BCFF) else Color(0xFF1C1B1F))
                                    .padding(10.dp)
                                    .widthIn(max = 240.dp)
                            ) {
                                Text(
                                    text = msg,
                                    fontSize = 13.sp,
                                    color = if (isUser) Color(0xFF381E72) else Color(0xFFE6E1E5)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userText,
                onValueChange = { userText = it },
                textStyle = LocalTextStyle.current.copy(color = Color.White),
                placeholder = { Text("Ask about textbook...", color = Color(0xFF938F99)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFF1C1B1F),
                    focusedContainerColor = Color(0xFF1C1B1F),
                    unfocusedBorderColor = Color(0xFF49454F),
                    focusedBorderColor = Color(0xFFD0BCFF)
                )
            )

            IconButton(
                onClick = {
                    if (userText.isNotBlank()) {
                        viewModel.sendChatMessage(userText)
                        userText = ""
                    }
                },
                enabled = !isOperating,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFD0BCFF), CircleShape)
            ) {
                if (isOperating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF381E72))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "Send Message", tint = Color(0xFF381E72))
                }
            }
        }
    }
}

// --- 6. PRODUCTIVITY HUB (TASKS AND POMODORO) ---

@Composable
fun ProductivityHubScreen(viewModel: BookVerseViewModel) {
    val items by viewModel.hubItems.collectAsState()
    val pomoTimeRemaining by viewModel.pomoTimeRemaining.collectAsState()
    val isPomoRunning by viewModel.isPomoRunning.collectAsState()
    val pomoMode by viewModel.pomoMode.collectAsState()

    var showAddTaskDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Productivity Hub",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            color = Color(0xFFD0BCFF)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // POMODORO TIMER PANEL
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("POMODORO FOCUS ENGINE", fontWeight = FontWeight.Bold, color = Color(0xFF938F99), fontSize = 11.sp)
                
                Spacer(modifier = Modifier.height(8.dp))

                val minutes = pomoTimeRemaining / 60
                val seconds = pomoTimeRemaining % 60
                val timerString = String.format("%02d:%02d", minutes, seconds)

                Text(
                    text = timerString,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD0BCFF),
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.setPomodoroMode("25/5", 25) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (pomoMode == "25/5") Color(0xFFD0BCFF) else Color(0xFF1C1B1F))
                    ) {
                        Text("25/5 Cycle", fontSize = 11.sp, color = if (pomoMode == "25/5") Color(0xFF381E72) else Color(0xFF938F99))
                    }

                    Button(
                        onClick = { viewModel.setPomodoroMode("50/10", 50) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (pomoMode == "50/10") Color(0xFFD0BCFF) else Color(0xFF1C1B1F))
                    ) {
                        Text("50/10 Cycle", fontSize = 11.sp, color = if (pomoMode == "50/10") Color(0xFF381E72) else Color(0xFF938F99))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.togglePomodoro() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isPomoRunning) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF381E72),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPomoRunning) "Pause Focus" else "Start Focus Timer", color = Color(0xFF381E72), fontWeight = FontWeight.Bold)
                }
            }
        }

        Divider(color = Color(0xFF49454F))

        // TASKS MANAGER PANEL
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Proactive Study Tasks", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontSize = 16.sp)
            IconButton(
                onClick = { showAddTaskDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFD0BCFF), CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Task", tint = Color(0xFF381E72), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No study goals scheduled today.", color = Color(0xFF938F99))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { task ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { viewModel.toggleHubItemCompleted(task) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFD0BCFF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (task.isCompleted) Color(0xFF938F99) else Color(0xFFE6E1E5)
                                    )
                                    if (task.content.isNotEmpty()) {
                                        Text(
                                            text = task.content,
                                            fontSize = 11.sp,
                                            color = Color(0xFF938F99)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.deleteHubItem(task) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFE76F51))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        TaskCreatorDialog(
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, content ->
                viewModel.addHubItem("TASK", title, content)
                showAddTaskDialog = false
            }
        )
    }
}

// --- 7. READING ANALYTICS DASHBOARD ---

@Composable
fun AnalyticsScreen(viewModel: BookVerseViewModel) {
    val gamification by viewModel.userGamification.collectAsState()
    val activityLogs by viewModel.activityLogs.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Reading Analytics",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = Color(0xFFD0BCFF)
            )
            Text(
                text = "Interactive heatmaps detailing continuous study progress",
                color = Color(0xFF938F99),
                fontSize = 11.sp
            )
        }

        // LEVEL XP BADGE CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Title: ${gamification?.levelName ?: "Reader"}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                fontSize = 16.sp
                            )
                            Text("Total XP Points: ${gamification?.xp ?: 0}", color = Color(0xFF938F99), fontSize = 12.sp)
                        }
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated Level Progress Bar (Next milestone calculation)
                    val nextLevelXp = 1000
                    val progress = ((gamification?.xp ?: 0) % nextLevelXp).toFloat() / nextLevelXp.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFFD0BCFF),
                        trackColor = Color(0xFF1C1B1F)
                    )
                }
            }
        }

        // INTERACTIVE RETENTION HEATMAP (GitHub Style grid layout representation)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Study Heatmap (Continuous Streak)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Beautiful Grid of 7x15 contribution cubes
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(5) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(14) { colIndex ->
                                    val isStudyDay = (rowIndex * 14 + colIndex) % 3 == 0
                                    val cubeColor = if (isStudyDay) Color(0xFFD0BCFF) else Color(0xFF1C1B1F)
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(cubeColor, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Active Streak: ${gamification?.streakDays ?: 0} Consecutive Days",
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // UNLOCKED BADGES LIST Showcase
        item {
            Text(
                "Earned Academic Badges",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0BCFF),
                fontSize = 16.sp
            )
        }

        item {
            val badgeList = remember(gamification?.unlockedBadgesJson) {
                try {
                    val arr = JSONArray(gamification?.unlockedBadgesJson ?: "[]")
                    List(arr.length()) { arr.getString(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            if (badgeList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Read more pages to unlock physical book badges!", color = Color(0xFF938F99))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(badgeList) { badge ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                            border = BorderStroke(1.dp, Color(0xFF49454F))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CORE UTILITIES DIALOGS OVERLAYS ---

@Composable
fun ImportBookDialog(onDismiss: () -> Unit, onImport: (String, String) -> Unit) {
    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PDF Document", color = Color(0xFFD0BCFF), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif) },
        containerColor = Color(0xFF2B2930),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    label = { Text("File Name (e.g. computer_science_notes.pdf)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F)
                    )
                )

                OutlinedTextField(
                    value = contentInput,
                    onValueChange = { contentInput = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    label = { Text("Content excerpt snippet for AI indexing") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (titleInput.isNotBlank()) {
                        val finalSnippet = contentInput.ifBlank { "This document explores computer architecture mechanisms, memory addressing routines, cache configurations, and pipelining structures." }
                        onImport(titleInput, finalSnippet)
                    }
                }
            ) {
                Text("Confirm Scan", color = Color(0xFFD0BCFF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF938F99))
            }
        }
    )
}

@Composable
fun StickyNoteCreatorDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FCF6BD") } // cozy soft yellow

    val colors = listOf("#FCF6BD", "#D6F6FF", "#FFD6E8", "#D6FFD8", "#FFE5D9")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin Sticky Note", color = Color(0xFFD0BCFF), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif) },
        containerColor = Color(0xFF2B2930),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    label = { Text("Note Header Label (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F)
                    )
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    label = { Text("Note content...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text, title, selectedColor) }) {
                Text("Pin card", color = Color(0xFFD0BCFF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color(0xFF938F99))
            }
        }
    )
}

@Composable
fun TaskCreatorDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Study Goal", color = Color(0xFFD0BCFF), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif) },
        containerColor = Color(0xFF2B2930),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    label = { Text("Goal Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F)
                    )
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    label = { Text("Steps or Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onSave(title, content) }) {
                Text("Add Goal", color = Color(0xFFD0BCFF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF938F99))
            }
        }
    )
}

@Composable
fun SecurityLockScreen(correctPin: String, onUnlocks: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFD0BCFF))
            Spacer(modifier = Modifier.height(16.dp))
            Text("BookVerse Secure Pin", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Enter correct biometric PIN index", color = Color(0xFF938F99))

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = enteredPin.replace(".".toRegex(), "•"),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 8.sp,
                modifier = Modifier
                    .background(Color(0xFF2B2930), RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3x4 numeric keypad keyboard
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val chunks = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "OK"))
                chunks.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { num ->
                            Button(
                                onClick = {
                                    when (num) {
                                        "C" -> enteredPin = ""
                                        "OK" -> {
                                            if (enteredPin == correctPin) {
                                                onUnlocks()
                                            } else {
                                                enteredPin = ""
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += num
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930))
                            ) {
                                Text(num, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.size(size: androidx.compose.ui.unit.Dp, density: Float) = this.size(size)

// --- PHYSICAL BOOK PAGE CURL IMPLEMENTATIONS ---

class PerfectCurlTopShape(
    val width: Float,
    val height: Float,
    val xb: Float,
    val yr: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, yr.coerceIn(0f, height))
            lineTo(xb.coerceIn(0f, width), height)
            lineTo(0f, height)
            close()
        }
        return Outline.Generic(path)
    }
}

class PerfectCurlRevealedShape(
    val width: Float,
    val height: Float,
    val xb: Float,
    val yr: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(width, height)
            lineTo(xb.coerceIn(0f, width), height)
            lineTo(width, yr.coerceIn(0f, height))
            close()
        }
        return Outline.Generic(path)
    }
}

class PerfectCurlFlapShape(
    val dragX: Float,
    val dragY: Float,
    val xb: Float,
    val yr: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(dragX, dragY)
            lineTo(xb.coerceIn(0f, size.width), size.height)
            lineTo(size.width, yr.coerceIn(0f, size.height))
            close()
        }
        return Outline.Generic(path)
    }
}

class PerfectCurlTopBackShape(
    val width: Float,
    val height: Float,
    val xb: Float,
    val yl: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, height)
            lineTo(xb.coerceIn(0f, width), height)
            lineTo(0f, yl.coerceIn(0f, height))
            close()
        }
        return Outline.Generic(path)
    }
}

class PerfectCurlRevealedBackShape(
    val width: Float,
    val height: Float,
    val xb: Float,
    val yl: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, height)
            lineTo(xb.coerceIn(0f, width), height)
            lineTo(0f, yl.coerceIn(0f, height))
            close()
        }
        return Outline.Generic(path)
    }
}

class PerfectCurlFlapBackShape(
    val dragX: Float,
    val dragY: Float,
    val xb: Float,
    val yl: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(dragX, dragY)
            lineTo(xb.coerceIn(0f, size.width), size.height)
            lineTo(0f, yl.coerceIn(0f, size.height))
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun TactilePageCurlReader(
    currentPageText: String,
    nextPageText: String,
    prevPageText: String,
    localPage: Int,
    maxPages: Int,
    currentBookRef: Book,
    viewModel: BookVerseViewModel,
    onPageTurned: (Int) -> Unit
) {
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }

    var dragMode by remember { mutableStateOf("none") } // "none", "forward", "backward"
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    var isFlickFlashed by remember { mutableStateOf(false) }
    var flickFlashMessage by remember { mutableStateOf("") }
    var touchTime by remember { mutableLongStateOf(0L) }
    var touchX by remember { mutableFloatStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    val animDragX = remember { Animatable(0f) }
    val animDragY = remember { Animatable(0f) }

    LaunchedEffect(isDragging, dragX, dragY) {
        if (isDragging) {
            animDragX.snapTo(dragX)
            animDragY.snapTo(dragY)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .pointerInput(width, height, localPage) {
                if (width <= 0f || height <= 0f) return@pointerInput
                
                detectDragGestures(
                    onDragStart = { offset ->
                        val tx = offset.x
                        val ty = offset.y
                        var activeDragMode = "none"
                        if (tx > width * 0.7f && ty > height * 0.7f) {
                            activeDragMode = "forward"
                        } else if (tx < width * 0.3f && ty > height * 0.7f) {
                            activeDragMode = "backward"
                        }

                        if (activeDragMode != "none") {
                            dragMode = activeDragMode
                            isDragging = true
                            dragX = tx
                            dragY = ty
                            touchTime = System.currentTimeMillis()
                            touchX = tx
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (dragMode != "none") {
                            change.consume()
                            dragX = change.position.x.coerceIn(0f, width)
                            dragY = change.position.y.coerceIn(0f, height)
                        }
                    },
                    onDragEnd = {
                        if (dragMode != "none") {
                            isDragging = false
                            val duration = System.currentTimeMillis() - touchTime
                            val deltaX = dragX - touchX
                            val speedX = if (duration > 0L) kotlin.math.abs(deltaX) / (duration / 1000f) else 0f

                            if (speedX > 1800f && duration < 300L && kotlin.math.abs(deltaX) > 150f) {
                                val jumpAmount = 3
                                if (deltaX < 0) {
                                    val target = (localPage + jumpAmount).coerceAtMost(maxPages - 1)
                                    if (target != localPage) {
                                        flickFlashMessage = "⚡ Flick Skip: Jumped $jumpAmount pages forward!"
                                        isFlickFlashed = true
                                        onPageTurned(target)
                                    }
                                } else {
                                    val target = (localPage - jumpAmount).coerceAtLeast(0)
                                    if (target != localPage) {
                                        flickFlashMessage = "⚡ Flick Skip: Jumped $jumpAmount pages backward!"
                                        isFlickFlashed = true
                                        onPageTurned(target)
                                    }
                                }
                                dragMode = "none"
                            } else {
                                if (dragMode == "forward") {
                                    if (dragX < width * 0.5f) {
                                        coroutineScope.launch {
                                            animDragX.animateTo(0f, tween(300, easing = LinearOutSlowInEasing))
                                            onPageTurned((localPage + 1).coerceAtMost(maxPages - 1))
                                            dragMode = "none"
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            animDragX.animateTo(width, tween(200))
                                            dragMode = "none"
                                        }
                                    }
                                } else if (dragMode == "backward") {
                                    if (dragX > width * 0.5f) {
                                        coroutineScope.launch {
                                            animDragX.animateTo(width, tween(300, easing = LinearOutSlowInEasing))
                                            onPageTurned((localPage - 1).coerceAtLeast(0))
                                            dragMode = "none"
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            animDragX.animateTo(0f, tween(200))
                                            dragMode = "none"
                                        }
                                    }
                                } else {
                                    dragMode = "none"
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (dragMode != "none") {
                            isDragging = false
                            if (dragMode == "forward") {
                                coroutineScope.launch {
                                    animDragX.animateTo(width, tween(200))
                                    dragMode = "none"
                                }
                            } else if (dragMode == "backward") {
                                coroutineScope.launch {
                                    animDragX.animateTo(0f, tween(200))
                                    dragMode = "none"
                                }
                            } else {
                                dragMode = "none"
                            }
                        }
                    }
                )
            }
    ) {
        if (width <= 0f || height <= 0f) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        val currentX = if (isDragging) dragX else animDragX.value
        val currentY = if (isDragging) dragY else animDragY.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color(0xFF332522))
                    drawLine(
                        color = Color.Black.copy(alpha = 0.5f),
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 20f
                    )
                }
                .padding(24.dp)
        ) {
            if (dragMode == "none") {
                IndividualBookPage(
                    text = currentPageText,
                    category = currentBookRef.category,
                    pageNumber = localPage + 1,
                    currentBookRef = currentBookRef,
                    viewModel = viewModel
                )
            } else if (dragMode == "forward") {
                val clampedDragX = currentX.coerceIn(width * 0.2f, width)
                val clampedDragY = currentY.coerceIn(height * 0.3f, height)

                val dx = (width - clampedDragX).coerceAtLeast(1f)
                val dy = (height - clampedDragY).coerceAtLeast(1f)
                val mx = (width + clampedDragX) / 2f
                val my = (height + clampedDragY) / 2f
                
                val xb = (mx + (height - my) * dy / dx).coerceIn(width * 0.3f, width)
                val yr = (my + (width - mx) * dx / dy).coerceIn(height * 0.4f, height)

                val topShape = PerfectCurlTopShape(width, height, xb, yr)
                val revealedShape = PerfectCurlRevealedShape(width, height, xb, yr)
                val flapShape = PerfectCurlFlapShape(clampedDragX, clampedDragY, xb, yr)

                Box(modifier = Modifier.clip(revealedShape)) {
                    IndividualBookPage(
                        text = nextPageText,
                        category = currentBookRef.category,
                        pageNumber = (localPage + 1).coerceAtMost(maxPages - 1) + 1,
                        currentBookRef = currentBookRef,
                        viewModel = viewModel
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(flapShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE4D9C8),
                                    Color(0xFFFCF6BD)
                                ),
                                start = Offset(clampedDragX, clampedDragY),
                                end = Offset(xb, height)
                            )
                        )
                        .drawBehind {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.4f),
                                start = Offset(xb, height),
                                end = Offset(width, yr),
                                strokeWidth = 12f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(xb, height),
                                end = Offset(clampedDragX, clampedDragY),
                                strokeWidth = 3f
                            )
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier
                        .clip(topShape)
                        .drawBehind {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.15f),
                                start = Offset(xb, height),
                                end = Offset(width, yr),
                                strokeWidth = 24f
                            )
                        }
                ) {
                    IndividualBookPage(
                        text = currentPageText,
                        category = currentBookRef.category,
                        pageNumber = localPage + 1,
                        currentBookRef = currentBookRef,
                        viewModel = viewModel
                    )
                }

            } else if (dragMode == "backward") {
                val clampedDragX = currentX.coerceIn(0f, width * 0.8f)
                val clampedDragY = currentY.coerceIn(height * 0.3f, height)

                val dx = clampedDragX.coerceAtLeast(1f)
                val dy = (height - clampedDragY).coerceAtLeast(1f)
                val mx = clampedDragX / 2f
                val my = (height + clampedDragY) / 2f

                val xb = (mx + (height - my) * dy / dx).coerceIn(0f, width * 0.7f)
                val yl = (my + mx * dx / dy).coerceIn(height * 0.4f, height)

                val topShape = PerfectCurlTopBackShape(width, height, xb, yl)
                val revealedShape = PerfectCurlRevealedBackShape(width, height, xb, yl)
                val flapShape = PerfectCurlFlapBackShape(clampedDragX, clampedDragY, xb, yl)

                Box(modifier = Modifier.clip(revealedShape)) {
                    IndividualBookPage(
                        text = prevPageText,
                        category = currentBookRef.category,
                        pageNumber = (localPage - 1).coerceAtLeast(0) + 1,
                        currentBookRef = currentBookRef,
                        viewModel = viewModel
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(flapShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE4D9C8),
                                    Color(0xFFFCF6BD)
                                ),
                                start = Offset(clampedDragX, clampedDragY),
                                end = Offset(xb, height)
                            )
                        )
                        .drawBehind {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.4f),
                                start = Offset(xb, height),
                                end = Offset(0f, yl),
                                strokeWidth = 12f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(xb, height),
                                end = Offset(clampedDragX, clampedDragY),
                                strokeWidth = 3f
                            )
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier
                        .clip(topShape)
                        .drawBehind {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.15f),
                                start = Offset(xb, height),
                                end = Offset(0f, yl),
                                strokeWidth = 24f
                            )
                        }
                ) {
                    IndividualBookPage(
                        text = currentPageText,
                        category = currentBookRef.category,
                        pageNumber = localPage + 1,
                        currentBookRef = currentBookRef,
                        viewModel = viewModel
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isFlickFlashed,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD381E72)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD700))
                    Text(
                        text = flickFlashMessage,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            LaunchedEffect(isFlickFlashed) {
                if (isFlickFlashed) {
                    delay(1500)
                    isFlickFlashed = false
                }
            }
        }
    }
}

@Composable
fun IndividualBookPage(
    text: String,
    category: String,
    pageNumber: Int,
    currentBookRef: Book,
    viewModel: BookVerseViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCF6BD), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE5D5C5), RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E7D6F)
            )
            IconButton(
                onClick = {
                    viewModel.addBookmark(
                        currentBookRef.id,
                        pageNumber - 1,
                        "Bookmark P.${pageNumber}"
                    )
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Add stamp",
                    tint = Color(0xFF8E7D6F),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = text,
            fontSize = 18.sp,
            lineHeight = 28.sp,
            fontFamily = FontFamily.Serif,
            color = Color(0xFF261D1A),
            modifier = Modifier.weight(1f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "P. $pageNumber",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E7D6F)
            )
            Button(
                onClick = {
                    viewModel.addHighlight(
                        currentBookRef.id,
                        pageNumber - 1,
                        text.take(50) + "...",
                        "Summ",
                        "#D0BCFF"
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Highlight text", fontSize = 11.sp, color = Color(0xFF381E72))
            }
        }
    }
}
