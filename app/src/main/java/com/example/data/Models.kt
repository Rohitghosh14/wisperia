package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val publisher: String = "Unknown Publisher",
    val subject: String = "General Reading",
    val category: String = "Uncategorized",
    val language: String = "English",
    val chaptersJson: String = "[]", // List of chapters
    val filePath: String = "",
    val coverColor: Int = 0xFFC29B70.toInt(), // Wooden cover style fallback color
    val progressPages: Int = 0,
    val totalPages: Int = 100,
    val lastReadTime: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isNotesMode: Boolean = false, // Automatically switched if sticky note content
    // AI generated Summaries & Study tools
    val fullSummary: String = "",
    val keyPointsJson: String = "[]",
    val revisionNotes: String = "",
    val examNotes: String = "",
    val mindMapMarkdown: String = "",
    val flashcardsJson: String = "[]", // [{question, answer}]
    val quizQuestionsJson: String = "[]" // [{question, options:[], answerIndex}]
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageNumber: Int,
    val label: String = "Bookmark",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_highlights")
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageNumber: Int,
    val text: String,
    val note: String = "",
    val colorHex: String = "#FFEB3B", // Default highlighter yellow
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sticky_notes")
data class StickyNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String,
    val colorHex: String = "#FFEB3B", // yellow, blue, pink, green, orange
    val category: String = "General",
    val isPinned: Boolean = false,
    val xOffset: Float = 0f,
    val yOffset: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String, // YYYY-MM-DD
    val pagesRead: Int = 0,
    val readingTimeSeconds: Long = 0,
    val focusTimeSeconds: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "hub_items")
data class HubItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "TASK", "SHOPPING", "QUICK_NOTE", "VOICE_NOTE", "LINK"
    val title: String,
    val content: String = "",
    val isCompleted: Boolean = false,
    val linkUrl: String = "",
    val reminderTime: Long = 0L,
    val audioPath: String = "",
    val category: String = "Inbox",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_gamification")
data class UserGamification(
    @PrimaryKey val id: Int = 1,
    val xp: Int = 0,
    val levelName: String = "Reader", // Reader, Scholar, Researcher, Master, Sage, Grand Sage
    val lastActiveDate: String = "",
    val streakDays: Int = 0,
    val totalReadingSeconds: Long = 0,
    val totalPagesRead: Int = 0,
    val completedBooksCount: Int = 0,
    val unlockedBadgesJson: String = "[]" // list of strings
)
