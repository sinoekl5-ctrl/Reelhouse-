package com.reelhouse.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE hidden = 0")
    fun observeVisible(): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE id = :id")
    fun observe(id: Long): Flow<Movie?>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun get(id: Long): Movie?

    @Query("SELECT * FROM movies")
    suspend fun getAll(): List<Movie>

    @Query("SELECT * FROM movies WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<Movie>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Movie): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Movie>)

    @Update
    suspend fun update(m: Movie)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM movies WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    @Query("DELETE FROM movies")
    suspend fun clearAll()

    @Query(
        "UPDATE movies SET positionMs = :pos, " +
            "durationMs = CASE WHEN :dur > 0 THEN :dur ELSE durationMs END, " +
            "watched = CASE WHEN :markWatched THEN 1 ELSE watched END, " +
            "resumeSeq = CASE WHEN :seq > 0 THEN :seq ELSE resumeSeq END " +
            "WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, pos: Long, dur: Long, markWatched: Boolean, seq: Int)

    @Query("UPDATE movies SET favorite = :v WHERE id = :id")
    suspend fun setFavorite(id: Long, v: Boolean)

    @Query("UPDATE movies SET watched = :v, positionMs = 0 WHERE id = :id")
    suspend fun setWatched(id: Long, v: Boolean)

    @Query("UPDATE movies SET myRating = :v WHERE id = :id")
    suspend fun setRating(id: Long, v: Int)

    @Query("UPDATE movies SET notes = :v WHERE id = :id")
    suspend fun setNotes(id: Long, v: String)

    @Query("UPDATE movies SET hidden = 1 WHERE id = :id")
    suspend fun hide(id: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM folders")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name")
    fun observePlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist_entries ORDER BY position")
    fun observeEntries(): Flow<List<PlaylistEntry>>

    @Query("SELECT * FROM playlists")
    suspend fun getPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlist_entries")
    suspend fun getEntries(): List<PlaylistEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(p: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(list: List<Playlist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(e: PlaylistEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(list: List<PlaylistEntry>)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun deleteEntriesOfPlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :pid AND movieId = :mid")
    suspend fun deleteEntry(pid: Long, mid: Long)

    @Query("DELETE FROM playlist_entries WHERE movieId = :mid")
    suspend fun deleteEntriesOfMovie(mid: Long)

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :pid")
    suspend fun countEntries(pid: Long): Int

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM playlist_entries")
    suspend fun clearEntries()
}

@Database(
    entities = [Movie::class, FolderEntity::class, Playlist::class, PlaylistEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun folderDao(): FolderDao
    abstract fun playlistDao(): PlaylistDao
}
