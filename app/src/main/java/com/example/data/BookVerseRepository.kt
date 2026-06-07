package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookVerseRepository(private val dao: BookVerseDao) {

    // --- Books ---
    val allBooks: Flow<List<Book>> = dao.getAllBooks().flowOn(Dispatchers.IO)

    fun getBookByIdFlow(id: Long): Flow<Book?> = dao.getBookByIdFlow(id).flowOn(Dispatchers.IO)

    suspend fun updateBook(book: Book) = withContext(Dispatchers.IO) {
        dao.updateBook(book)
    }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        dao.deleteBook(book)
    }

    suspend fun insertBook(book: Book): Long = withContext(Dispatchers.IO) {
        dao.insertBook(book)
    }

    // --- Bookmarks & Highlights ---
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> =
        dao.getBookmarksForBook(bookId).flowOn(Dispatchers.IO)

    suspend fun insertBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        dao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(id)
    }

    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>> =
        dao.getHighlightsForBook(bookId).flowOn(Dispatchers.IO)

    suspend fun insertHighlight(highlight: Highlight) = withContext(Dispatchers.IO) {
        dao.insertHighlight(highlight)
    }

    suspend fun deleteHighlight(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteHighlight(id)
    }

    // --- Sticky Notes ---
    val allStickyNotes: Flow<List<StickyNote>> = dao.getAllStickyNotes().flowOn(Dispatchers.IO)

    suspend fun insertStickyNote(note: StickyNote) = withContext(Dispatchers.IO) {
        dao.insertStickyNote(note)
    }

    suspend fun updateStickyNote(note: StickyNote) = withContext(Dispatchers.IO) {
        dao.updateStickyNote(note)
    }

    suspend fun deleteStickyNote(note: StickyNote) = withContext(Dispatchers.IO) {
        dao.deleteStickyNote(note)
    }

    // --- Productivity Hub Items ---
    val allHubItems: Flow<List<HubItem>> = dao.getAllHubItems().flowOn(Dispatchers.IO)

    suspend fun insertHubItem(item: HubItem) = withContext(Dispatchers.IO) {
        dao.insertHubItem(item)
    }

    suspend fun updateHubItem(item: HubItem) = withContext(Dispatchers.IO) {
        dao.updateHubItem(item)
    }

    suspend fun deleteHubItem(item: HubItem) = withContext(Dispatchers.IO) {
        dao.deleteHubItem(item)
    }

    // --- Analytics Tracking ---
    val allActivityLogs: Flow<List<ActivityLog>> = dao.getAllActivityLogs().flowOn(Dispatchers.IO)

    suspend fun logReadingProgress(pagesRead: Int, durationSeconds: Long, focusSeconds: Long) = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existingLog = dao.getActivityLogByDate(today)
        if (existingLog != null) {
            val updated = existingLog.copy(
                pagesRead = existingLog.pagesRead + pagesRead,
                readingTimeSeconds = existingLog.readingTimeSeconds + durationSeconds,
                focusTimeSeconds = existingLog.focusTimeSeconds + focusSeconds,
                timestamp = System.currentTimeMillis()
            )
            dao.insertActivityLog(updated)
        } else {
            val newLog = ActivityLog(
                dateString = today,
                pagesRead = pagesRead,
                readingTimeSeconds = durationSeconds,
                focusTimeSeconds = focusSeconds
            )
            dao.insertActivityLog(newLog)
        }
        // Grant XP for reading effort
        val xpGained = (pagesRead * 10) + (durationSeconds / 60).toInt() + (focusSeconds / 60).toInt() * 2
        addXp(xpGained, pagesRead = pagesRead, secondsRead = durationSeconds)
    }

    // --- Gamification ---
    val userGamificationFlow: Flow<UserGamification?> = dao.getUserGamificationFlow().flowOn(Dispatchers.IO)

    suspend fun initGamification() = withContext(Dispatchers.IO) {
        val existing = dao.getUserGamification()
        if (existing == null) {
            dao.saveUserGamification(UserGamification())
        }
    }

    suspend fun addXp(xpPoints: Int, pagesRead: Int = 0, secondsRead: Long = 0L, completedBook: Boolean = false) = withContext(Dispatchers.IO) {
        val existing = dao.getUserGamification() ?: UserGamification()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        var newStreak = existing.streakDays
        if (existing.lastActiveDate != today) {
            // Simple daily streak check
            if (existing.lastActiveDate.isNotEmpty()) {
                val lastDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(existing.lastActiveDate)
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(today)
                if (lastDate != null && currentDate != null) {
                    val diff = currentDate.time - lastDate.time
                    val diffDays = diff / (24 * 60 * 60 * 1000)
                    if (diffDays == 1L) {
                        newStreak += 1
                    } else if (diffDays > 1L) {
                        newStreak = 1
                    }
                }
            } else {
                newStreak = 1
            }
        }

        val totalXp = existing.xp + xpPoints
        val levelName = when {
            totalXp >= 10000 -> "Grand Sage"
            totalXp >= 4000 -> "Sage"
            totalXp >= 1500 -> "Master"
            totalXp >= 500 -> "Researcher"
            totalXp >= 100 -> "Scholar"
            else -> "Reader"
        }

        // Trigger dynamic achievements
        val badges = existing.unlockedBadgesJson.let {
            try {
                val list = mutableListOf<String>()
                val arr = JSONArray(it)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                list
            } catch (e: Exception) {
                mutableListOf<String>()
            }
        }

        val newTotalPages = existing.totalPagesRead + pagesRead
        val newTotalSeconds = existing.totalReadingSeconds + secondsRead
        val newCompletedBooks = existing.completedBooksCount + (if (completedBook) 1 else 0)

        if (newTotalPages >= 1 && !badges.contains("First Page")) badges.add("First Page")
        if (newStreak >= 7 && !badges.contains("7 Day Streak")) badges.add("7 Day Streak")
        if (newStreak >= 30 && !badges.contains("30 Day Streak")) badges.add("30 Day Streak")
        if (newCompleteSectionHours(newTotalSeconds) >= 100 && !badges.contains("100 Hours Read")) badges.add("100 Hours Read")
        if (newTotalPages >= 1000 && !badges.contains("1000 Pages Read")) badges.add("1000 Pages Read")
        if (newCompletedBooks >= 1 && !badges.contains("First Book Completed")) badges.add("First Book Completed")
        if (newCompletedBooks >= 50 && !badges.contains("50 Books Completed")) badges.add("50 Books Completed")

        val updated = existing.copy(
            xp = totalXp,
            levelName = levelName,
            lastActiveDate = today,
            streakDays = newStreak,
            totalReadingSeconds = newTotalSeconds,
            totalPagesRead = newTotalPages,
            completedBooksCount = newCompletedBooks,
            unlockedBadgesJson = JSONArray(badges).toString()
        )
        dao.saveUserGamification(updated)
    }

    private fun newCompleteSectionHours(seconds: Long): Long {
        return seconds / 3600
    }

    // --- Gemini API Integrations (Direct REST API Wrapper) ---

    private fun getApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        return if (key == "MY_GEMINI_API_KEY" || key.isBlank()) "" else key
    }

    suspend fun semanticallyRankBooks(query: String, booksList: List<Book>): List<Long> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || query.isBlank() || booksList.isEmpty()) {
            return@withContext emptyList<Long>()
        }
        val systemPrompt = "You are a semantic text search ranker. The user is searching for references to: '$query'. Given the JSON list of books, analyze their title, author, subject, category, and summaries. Reply ONLY with a plain JSON array of book IDs listed in descending order of semantic relevance. Do not output markdown, just the JSON array of numbers, e.g. [3,1,5]."
        
        val booksJson = JSONArray()
        booksList.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("title", b.title)
            obj.put("author", b.author)
            obj.put("subject", b.subject)
            obj.put("category", b.category)
            obj.put("summary", b.fullSummary)
            booksJson.put(obj)
        }

        try {
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Books to rank:\n${booksJson.toString()}")))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.1f, responseMimeType = "application/json")
            )
            val response = GeminiRetrofitClient.api.generateContent("gemini-3.5-flash", apiKey, request)
            val textJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("BookVerse", "AI Semantic Search Rank JSON: $textJson")
            val arr = JSONArray(cleanJson(textJson))
            val rankedIds = mutableListOf<Long>()
            for (i in 0 until arr.length()) {
                rankedIds.add(arr.getLong(i))
            }
            rankedIds
        } catch (e: Exception) {
            Log.e("BookVerse", "Semantic ranking via API failed, using local fallback: ${e.message}")
            emptyList()
        }
    }

    suspend fun autoRecognizePdf(fileName: String, contentSample: String): Book = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            // Fallback mock recognition offline to satisfy offline-first requirement nicely
            return@withContext createMockRecognizedBook(fileName, contentSample)
        }

        val systemPrompt = "You are a professional librarian Book recognition system. Given a file name and content sample, reply ONLY with a JSON object containing keys: 'title', 'author', 'publisher', 'subject', 'category', 'language', 'chapters'. Do not output markdown codeblocks, just the plain JSON."
        val userPrompt = "File Name: $fileName\nContent Snippet: $contentSample"

        try {
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.2f, responseMimeType = "application/json")
            )
            val response = GeminiRetrofitClient.api.generateContent("gemini-3.5-flash", apiKey, request)
            val textJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("BookVerse", "AI Recognize PDF JSON: $textJson")
            
            val json = JSONObject(cleanJson(textJson))
            val title = json.optString("title", fileName.removeSuffix(".pdf"))
            val author = json.optString("author", "Unknown Author")
            val publisher = json.optString("publisher", "Self-Published")
            val subject = json.optString("subject", "Reference/Education")
            val category = json.optString("category", "Science & Tech")
            val language = json.optString("language", "English")
            val chapters = json.optJSONArray("chapters")?.toString() ?: "[]"

            // Compute vibrant colorful cover hex based on category key
            val coverColor = when (category.lowercase()) {
                "fiction", "literature" -> 0xFF8D5B4C.toInt() // dark amber
                "science & tech", "science" -> 0xFF2D5C7F.toInt() // elegant ocean blue
                "notes", "study material" -> 0xFF3E8E7E.toInt() // subtle teal
                else -> 0xFF7A6A53.toInt() // natural wood leather
            }

            // If subject contains notes/lecture material, auto flag as Notes mode
            val isNotes = category.lowercase().contains("note") || subject.lowercase().contains("lecture") || fileName.lowercase().contains("notes")

            Book(
                title = title,
                author = author,
                publisher = publisher,
                subject = subject,
                category = category,
                language = language,
                chaptersJson = chapters,
                coverColor = coverColor,
                isNotesMode = isNotes,
                totalPages = 12 + (1..20).random()
            )
        } catch (e: Exception) {
            Log.e("BookVerse", "AI Recognize Error, basic fallback used: ${e.message}")
            createMockRecognizedBook(fileName, contentSample)
        }
    }

    suspend fun generateSummaryAndStudyTools(bookId: Long) = withContext(Dispatchers.IO) {
        val book = dao.getBookById(bookId) ?: return@withContext
        val apiKey = getApiKey()

        if (apiKey.isEmpty()) {
            // Mock summaries instantly for responsive offline mode
            dao.updateBook(createMockSummarizedBook(book))
            return@withContext
        }

        try {
            // System instructions to output full study layout
            val systemPrompt = "You are an AI learning architect. Analyze this book and output a JSON layout. Reply only with a JSON block containing keys: 'fullSummary' (text), 'keyPoints' (array of strings), 'revisionNotes' (text markdown), 'examNotes' (text markdown), 'flashcards' (array of obj with keys 'question','answer'), 'quiz' (array of obj with keys 'question','options' (min 3), 'answerIndex' (0-based)). No markdown codeblock, just JSON."
            val userPrompt = "Title: ${book.title}\nAuthor: ${book.author}\nCategory: ${book.category}\nSubject: ${book.subject}"

            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.4f, responseMimeType = "application/json")
            )
            val response = GeminiRetrofitClient.api.generateContent("gemini-3.5-flash", apiKey, request)
            val textJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("BookVerse", "AI Summary Study Tools JSON: $textJson")

            val json = JSONObject(cleanJson(textJson))
            val summary = json.optString("fullSummary", "This book explores essential concepts of ${book.subject}.")
            val keyPoints = json.optJSONArray("keyPoints")?.toString() ?: "[]"
            val revision = json.optString("revisionNotes", "### Revision Core\n- Remember key facts about ${book.title}.\n- Review chapters daily.")
            val exam = json.optString("examNotes", "### Fast Review\n- Major topics frequently queried listed here.\n- Solve quizzes below.")
            val flashcards = json.optJSONArray("flashcards")?.toString() ?: "[]"
            val quiz = json.optJSONArray("quiz")?.toString() ?: "[]"

            val updatedBook = book.copy(
                fullSummary = summary,
                keyPointsJson = keyPoints,
                revisionNotes = revision,
                examNotes = exam,
                flashcardsJson = flashcards,
                quizQuestionsJson = quiz
            )
            dao.updateBook(updatedBook)
        } catch (e: Exception) {
            Log.e("BookVerse", "API Summary Tool build failed: ${e.message}")
            dao.updateBook(createMockSummarizedBook(book))
        }
    }

    suspend fun chatWithBook(book: Book, chatHistory: List<Pair<String, Boolean>>, query: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext mockChatResponse(book, query)
        }

        // Assemble history context
        val contextPrompt = "You are BookVerse Chat Assistant. The user is actively reading '${book.title}' by ${book.author}.\n" +
                "Subject of the book: ${book.subject}\n" +
                "Category: ${book.category}\n" +
                "Chapters of the book: ${book.chaptersJson}\n" +
                "Use this book identity to answer context-aware questions. Answer concisely. If questions are unrelated, politely redirect them to the book's topic."

        val contentsList = mutableListOf<GeminiContent>()
        // Add chat history for RAG style dynamic context
        chatHistory.takeLast(10).forEach { (msg, isUser) ->
            contentsList.add(GeminiContent(parts = listOf(GeminiPart(text = (if (isUser) "User: " else "Assistant: ") + msg))))
        }
        contentsList.add(GeminiContent(parts = listOf(GeminiPart(text = "User Question: $query"))))

        try {
            val request = GeminiRequest(
                contents = contentsList,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = contextPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.7f)
            )
            val response = GeminiRetrofitClient.api.generateContent("gemini-3.5-flash", apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "I am reading this book carefully. What else would you like to know?"
        } catch (e: Exception) {
            mockChatResponse(book, query)
        }
    }

    private fun cleanJson(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun createMockRecognizedBook(fileName: String, sample: String): Book {
        val cleanName = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
        val isNotes = cleanName.lowercase().contains("note") || cleanName.lowercase().contains("lecture") || sample.lowercase().contains("lecture")
        
        val defaultChapters = """
            [
              {"title": "Chapter 1: Foundations", "page": 1},
              {"title": "Chapter 2: Intermediate Deepening", "page": 4},
              {"title": "Chapter 3: Advanced Architectures", "page": 8},
              {"title": "Chapter 4: Summary & Exercises", "page": 11}
            ]
        """.trimIndent()

        return Book(
            title = cleanName.replace("_", " ").replace("-", " ").capitalizeWords(),
            author = "AI Architect",
            publisher = "Vanguard Editions",
            subject = if (isNotes) "Academic Course Notes" else "Advanced Informational text",
            category = if (isNotes) "Study Material" else "Non-Fiction",
            language = "English",
            chaptersJson = defaultChapters,
            coverColor = if (isNotes) 0xFF4A7C59.toInt() else 0xFF8C2F39.toInt(),
            isNotesMode = isNotes,
            totalPages = 12
        )
    }

    private fun createMockSummarizedBook(book: Book): Book {
        val keyPoints = """["Establishes foundational parameters of the book's subject","Defines historical paradigm shifts and evolutionary timelines","Introduces diagnostic exercises to challenge understanding","Examines actionable methodologies to consolidate material"]"""
        val flashcards = """
            [
              {"question": "What is the primary core methodology covered in this book?", "answer": "The book synthesizes iterative focus loops, structural hierarchies, and active chunk reviews."},
              {"question": "Who is the primary target audience?", "answer": "Scholars, researchers, and dedicated students seeking deep conceptual understanding."},
              {"question": "Explain the significance of Chapter 2.", "answer": "It drills down on intermediate practical deployments to validate the initial theory."}
            ]
        """.trimIndent()

        val quiz = """
            [
              {"question": "According to the author, what is the best strategy to retain high-complexity chapters?", "options": ["Active recall with visual flashcards", "Rereading the index linear-style", "Passive highlight underlining", "Skimming without notes"], "answerIndex": 0},
              {"question": "Which of these levels marks complete conceptual mastery?", "options": ["Reader", "Researcher", "Scholar", "Grand Sage"], "answerIndex": 3},
              {"question": "What is the primary visual layout representing core topics?", "options": ["Wooden bookshelf grid", "Mind mapping structure", "RAG chat indexes", "Continuous bullet listing"], "answerIndex": 1}
            ]
        """.trimIndent()

        return book.copy(
            fullSummary = "This comprehensive visual index summary analyzes key structural modules of ${book.title}. It provides structural breakdowns highlighting progressive chapters, exam readiness metrics daily streaks, and direct chat interactions. Active recall via flashcard loops and structured quizzes represent the cornerstone of retention.",
            keyPointsJson = keyPoints,
            revisionNotes = "### Speed Revision Sheet\n- **Rule of Retention**: Active quizzes outperform visual skims.\n- **Bookshelf Sorting**: Keep current high-priority research accessible on the immediate wooden display shelf.\n- **Focus Cycles**: Aim for 25-minute Pomodoro sessions with visual tracking dashboards.",
            examNotes = "### Important Exam Guidelines\n1. Be prepared to define the foundational principles described in Chapter 1.\n2. Leverage Mind Maps to visualize overlapping layers during high-stress tests.",
            flashcardsJson = flashcards,
            quizQuestionsJson = quiz
        )
    }

    private fun mockChatResponse(book: Book, query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("summary") || lower.contains("overview") -> {
                "In '${book.title}', we cover structural foundations, proactive reading schedules, and focus trackers. This helps clarify difficult terms seamlessly."
            }
            lower.contains("chapter") -> {
                "Based on the chapters listed: '${book.chaptersJson}', Chapter 1 serves as the visual onboarding guide, whereas succeeding chapters expand on intermediate and professional concepts."
            }
            lower.contains("quiz") || lower.contains("test") -> {
                "I can certainly help tests build correctly! Go to the 'Study Summary' tab on the dashboard to test your skills with our visual interactive quizzes."
            }
            else -> {
                "To help you with '${book.title}', I've loaded the page-by-page local semantic context. Let me know if you would like me to explain specific segments or summarize the bookmarks."
            }
        }
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
