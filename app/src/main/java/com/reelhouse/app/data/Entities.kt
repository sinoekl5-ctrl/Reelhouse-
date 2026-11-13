package com.reelhouse.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val name: String
)

/**
 * NOTE: there is deliberately no "date watched" field anywhere. Only [watched] and [positionMs]
 * (resume position) are stored for viewing state. [resumeSeq] is a plain counter used to order the
 * "Continue Watching" row; it is not a date and is never shown.
 */
@Entity(
    tableName = "movies",
    indices = [Index(value = ["uri"], unique = true), Index("folderId")]
)
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val folderId: Long,
    val fileName: String,
    val fileSize: Long = 0,
    val title: String,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val posterFile: String? = null,
    val overview: String? = null,
    val runtimeMin: Int? = null,
    val durationMs: Long = 0,
    val myRating: Int = 0,
    val favorite: Boolean = false,
    val watched: Boolean = false,
    val positionMs: Long = 0,
    val resumeSeq: Int = 0,
    val notes: String = "",
    val addedAt: Long = 0,
    /** 0 = not matched yet, 1 = matched with TMDB, 2 = needs manual fix, 3 = fixed manually */
    val matchStatus: Int = 0,
    val hidden: Boolean = false,
    val subtitleFile: String? = null,
    /** 0 = not searched, 1 = Arabic downloaded, 2 = not found, 3 = file chosen manually */
    val subtitleStatus: Int = 0,
    val subtitleOffsetMs: Long = 0
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "movieId"])
data class PlaylistEntry(
    val playlistId: Long,
    val movieId: Long,
    val position: Int = 0
)
