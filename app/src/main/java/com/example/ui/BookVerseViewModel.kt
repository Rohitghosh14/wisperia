package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookVerseViewModel(
    application: Application,
    private val repository: BookVerseRepository
) : AndroidViewModel(application) {

    // --- Core UI States ---
    val books = repository.allBooks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val stickyNotes = repository.allStickyNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val hubItems = repository.allHubItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activityLogs = repository.allActivityLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val userGamification = repository.userGamificationFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Current Active Book State
    private val _currentBookId = MutableStateFlow<Long?>(null)
    val currentBookId: StateFlow<Long?> = _currentBookId.asStateFlow()

    val currentBook: StateFlow<Book?> = _currentBookId
        .flatMapLatest { id ->
            if (id != null) repository.getBookByIdFlow(id) else flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val activeBookmarks: StateFlow<List<Bookmark>> = _currentBookId
        .flatMapLatest { id ->
            if (id != null) repository.getBookmarksForBook(id) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeHighlights: StateFlow<List<Highlight>> = _currentBookId
        .flatMapLatest { id ->
            if (id != null) repository.getHighlightsForBook(id) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Loading & Processing Indicators ---
    private val _isAIOperating = MutableStateFlow(false)
    val isAIOperating: StateFlow<Boolean> = _isAIOperating.asStateFlow()

    // --- Semantic Search States ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _semanticRankingIds = MutableStateFlow<List<Long>>(emptyList())
    val semanticRankingIds: StateFlow<List<Long>> = _semanticRankingIds.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _semanticRankingIds.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val currentBooks = books.value
                val ranked = repository.semanticallyRankBooks(query, currentBooks)
                _semanticRankingIds.value = ranked
                if (ranked.isNotEmpty()) {
                    repository.addXp(10)
                }
            } catch (e: Exception) {
                Log.e("BookVerseViewModel", "AI semantic search failed: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    // --- AI Chat History State ---
    private val _chatHistory = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList()) // Pair(Message, IsUser)
    val chatHistory: StateFlow<List<Pair<String, Boolean>>> = _chatHistory.asStateFlow()

    // --- Text To Speech Engine (Audiobook System) ---
    private var tts: TextToSpeech? = null
    private val _isTtsPlaying = MutableStateFlow(false)
    val isTtsPlaying: StateFlow<Boolean> = _isTtsPlaying.asStateFlow()
    
    private val _ttsPlaybackSpeed = MutableStateFlow(1.0f)
    val ttsPlaybackSpeed: StateFlow<Float> = _ttsPlaybackSpeed.asStateFlow()

    // --- Pomodoro State ---
    private val _pomoTimeRemaining = MutableStateFlow(25 * 60) // in seconds
    val pomoTimeRemaining: StateFlow<Int> = _pomoTimeRemaining.asStateFlow()

    private val _isPomoRunning = MutableStateFlow(false)
    val isPomoRunning: StateFlow<Boolean> = _isPomoRunning.asStateFlow()

    private val _pomoMode = MutableStateFlow("25/5") // "25/5", "50/10", "Custom"
    val pomoMode: StateFlow<String> = _pomoMode.asStateFlow()

    // --- Security PIN state ---
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _userPinCode = MutableStateFlow("") // e.g. "1234" (Empty means disabled)
    val userPinCode: StateFlow<String> = _userPinCode.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initGamification()
        }

        // Setup TTS
        tts = TextToSpeech(application) { status ->
            if (status != TextToSpeech.ERROR) {
                tts?.language = Locale.getDefault()
            }
        }

        // Start timer updater thread
        startTimerLoop()
    }

    fun selectBook(id: Long?) {
        _currentBookId.value = id
        _chatHistory.value = emptyList()
        stopTTS()
    }

    // --- Book and AI Actions ---
    fun importNewBook(fileName: String, sampleText: String) {
        viewModelScope.launch {
            _isAIOperating.value = true
            try {
                // Recognize metadata using Gemini API
                val recognizedBook = repository.autoRecognizePdf(fileName, sampleText)
                val bookId = repository.insertBook(recognizedBook)
                
                // Triggers AI summary and quiz notes creation
                repository.generateSummaryAndStudyTools(bookId)
            } catch (e: Exception) {
                Log.e("BookVerseViewModel", "Import Book failed: ${e.message}")
            } finally {
                _isAIOperating.value = false
            }
        }
    }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book.copy(isFavorite = !book.isFavorite))
        }
    }

    fun updateBookReadingPage(book: Book, nextPage: Int) {
        if (nextPage < 0 || nextPage >= book.totalPages) return
        viewModelScope.launch {
            val updated = book.copy(progressPages = nextPage, lastReadTime = System.currentTimeMillis())
            repository.updateBook(updated)
            
            // Log pages viewed inside Analytics & add XP
            repository.logReadingProgress(pagesRead = 1, durationSeconds = 30L, focusSeconds = 0L)
            
            // Check if user completed the entire book
            if (nextPage == book.totalPages - 1 && book.progressPages < book.totalPages - 1) {
                repository.addXp(200, pagesRead = 0, secondsRead = 0L, completedBook = true)
            }
        }
    }

    // --- Highlights & Bookmarks ---
    fun addBookmark(bookId: Long, page: Int, label: String) {
        viewModelScope.launch {
            repository.insertBookmark(Bookmark(bookId = bookId, pageNumber = page, label = label))
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun addHighlight(bookId: Long, page: Int, text: String, note: String = "", color: String) {
        viewModelScope.launch {
            repository.insertHighlight(
                Highlight(bookId = bookId, pageNumber = page, text = text, note = note, colorHex = color)
            )
            repository.addXp(5) // Gaining small XP for active summarizing highlights!
        }
    }

    fun removeHighlight(id: Long) {
        viewModelScope.launch {
            repository.deleteHighlight(id)
        }
    }

    // --- Sticky Notes (Smart Board) ---
    fun createStickyNote(text: String, title: String = "", colorHex: String = "#FFEB3B", category: String = "General") {
        viewModelScope.launch {
            repository.insertStickyNote(
                StickyNote(text = text, title = title, colorHex = colorHex, category = category)
            )
            repository.addXp(10)
        }
    }

    fun updateStickyNotePosition(note: StickyNote, x: Float, y: Float) {
        viewModelScope.launch {
            repository.updateStickyNote(note.copy(xOffset = x, yOffset = y))
        }
    }

    fun deleteSticky(note: StickyNote) {
        viewModelScope.launch {
            repository.deleteStickyNote(note)
        }
    }

    // --- Productivity Hub ---
    fun addHubItem(type: String, title: String, content: String = "", linkUrl: String = "", audioPath: String = "") {
        viewModelScope.launch {
            repository.insertHubItem(
                HubItem(type = type, title = title, content = content, linkUrl = linkUrl, audioPath = audioPath)
            )
            repository.addXp(15)
        }
    }

    fun toggleHubItemCompleted(item: HubItem) {
        viewModelScope.launch {
            repository.updateHubItem(item.copy(isCompleted = !item.isCompleted))
            if (!item.isCompleted) {
                repository.addXp(20) // completion XP!
            }
        }
    }

    fun deleteHubItem(item: HubItem) {
        viewModelScope.launch {
            repository.deleteHubItem(item)
        }
    }

    // --- AI Chat (RAG Book Assistant) ---
    fun sendChatMessage(query: String) {
        val book = currentBook.value ?: return
        if (query.trim().isEmpty()) return

        // Update history synchronously for chat UI responsiveness
        _chatHistory.value = _chatHistory.value + Pair(query, true)

        viewModelScope.launch {
            _isAIOperating.value = true
            val response = repository.chatWithBook(book, _chatHistory.value.dropLast(1), query)
            _chatHistory.value = _chatHistory.value + Pair(response, false)
            _isAIOperating.value = false
            repository.addXp(15) // bonus points for study curiosity!
        }
    }

    // --- Audiobook TTS Settings ---
    fun togglePlayTTS(textToSpeak: String) {
        if (_isTtsPlaying.value) {
            stopTTS()
        } else {
            if (textToSpeak.isNotBlank()) {
                tts?.setSpeechRate(_ttsPlaybackSpeed.value)
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "BookVerseTTS")
                _isTtsPlaying.value = true
            }
        }
    }

    fun setTtsSpeechRate(speed: Float) {
        _ttsPlaybackSpeed.value = speed
        if (_isTtsPlaying.value) {
            tts?.setSpeechRate(speed)
        }
    }

    private fun stopTTS() {
        tts?.stop()
        _isTtsPlaying.value = false
    }

    // --- Pomodoro Timers ---
    fun setPomodoroMode(mode: String, minutes: Int = 25) {
        _pomoMode.value = mode
        _pomoTimeRemaining.value = minutes * 60
        _isPomoRunning.value = false
    }

    fun togglePomodoro() {
        _isPomoRunning.value = !_isPomoRunning.value
    }

    private fun startTimerLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                if (_isPomoRunning.value) {
                    if (_pomoTimeRemaining.value > 0) {
                        _pomoTimeRemaining.value -= 1
                    } else {
                        // Completed Pomodoro session!
                        _isPomoRunning.value = false
                        val currentMode = _pomoMode.value
                        val logMinutes = if (currentMode == "25/5") 25 else if (currentMode == "50/10") 50 else 30
                        
                        // Log focus statistics
                        repository.logReadingProgress(pagesRead = 0, durationSeconds = 0L, focusSeconds = logMinutes * 60L)
                        
                        // Reset remaining depending on break or new cycle
                        _pomoTimeRemaining.value = 5 * 60 // Go to break alert
                    }
                }
            }
        }
    }

    // --- Security Locks Settings ---
    fun updateSecurityPin(pin: String) {
        _userPinCode.value = pin
    }

    fun toggleAppLock(locked: Boolean) {
        _isAppLocked.value = locked
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }
}
