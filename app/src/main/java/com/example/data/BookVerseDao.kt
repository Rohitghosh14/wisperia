package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookVerseDao {

    // --- Books ---
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: Long): Flow<Book?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    // --- Bookmarks & Highlights ---
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY pageNumber ASC")
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM reading_highlights WHERE bookId = :bookId ORDER BY pageNumber ASC")
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: Highlight): Long

    @Query("DELETE FROM reading_highlights WHERE id = :id")
    suspend fun deleteHighlight(id: Long)

    // --- Sticky Notes ---
    @Query("SELECT * FROM sticky_notes ORDER BY timestamp DESC")
    fun getAllStickyNotes(): Flow<List<StickyNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickyNote(note: StickyNote): Long

    @Update
    suspend fun updateStickyNote(note: StickyNote)

    @Delete
    suspend fun deleteStickyNote(note: StickyNote)

    // --- Activity Logs (Analytics) ---
    @Query("SELECT * FROM activity_logs ORDER BY dateString DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE dateString = :dateString")
    suspend fun getActivityLogByDate(dateString: String): ActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLog)

    // --- Productivity Hub Items ---
    @Query("SELECT * FROM hub_items ORDER BY timestamp DESC")
    fun getAllHubItems(): Flow<List<HubItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHubItem(item: HubItem): Long

    @Update
    suspend fun updateHubItem(item: HubItem)

    @Delete
    suspend fun deleteHubItem(item: HubItem)

    // --- Gamification ---
    @Query("SELECT * FROM user_gamification WHERE id = 1")
    fun getUserGamificationFlow(): Flow<UserGamification?>

    @Query("SELECT * FROM user_gamification WHERE id = 1")
    suspend fun getUserGamification(): UserGamification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserGamification(gamification: UserGamification)
}
