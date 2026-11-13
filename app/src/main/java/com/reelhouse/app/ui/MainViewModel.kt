package com.reelhouse.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reelhouse.app.ReelhouseApp
import com.reelhouse.app.data.BackupManager
import com.reelhouse.app.data.Movie
import com.reelhouse.app.data.SubResult
import com.reelhouse.app.data.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Filters(
    val watched: Boolean? = null, // true = Watched, false = Unwatched, null = any
    val favorites: Boolean = false,
    val year: Int? = null,
    val folderId: Long? = null
) {
    val active: Boolean get() = watched != null || favorites || year != null || folderId != null
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as ReelhouseApp
    val repo = application.repository
    val prefs = application.prefs

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())

    val allMovies: StateFlow<List<Movie>> =
        repo.movies.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<List<Movie>> = combine(allMovies, query, filters, prefs.sort) { list, q, f, sort ->
        var r = list
        val qq = q.trim()
        if (qq.isNotEmpty()) r = r.filter { it.title.contains(qq, true) || it.fileName.contains(qq, true) }
        f.watched?.let { w -> r = r.filter { it.watched == w } }
        if (f.favorites) r = r.filter { it.favorite }
        f.year?.let { y -> r = r.filter { it.year == y } }
        f.folderId?.let { id -> r = r.filter { it.folderId == id } }
        when (sort) {
            "title" -> r.sortedBy { it.title.lowercase() }
            "year" -> r.sortedWith(compareByDescending<Movie> { it.year ?: 0 }.thenBy { it.title.lowercase() })
            "rating" -> r.sortedWith(compareByDescending<Movie> { it.myRating }.thenBy { it.title.lowercase() })
            else -> r.sortedByDescending { it.addedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val continueWatching: StateFlow<List<Movie>> = allMovies.combine(query) { l, _ ->
        l.filter { it.positionMs > 5_000 && !it.watched }.sortedByDescending { it.resumeSeq }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentlyAdded: StateFlow<List<Movie>> = allMovies.combine(query) { l, _ ->
        l.sortedByDescending { it.addedAt }.take(15)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<Movie>> = allMovies.combine(query) { l, _ ->
        l.filter { it.favorite }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val years: StateFlow<List<Int>> = allMovies.combine(query) { l, _ ->
        l.mapNotNull { it.year }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders = repo.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = repo.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val scanState = repo.scanState

    fun say(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                say(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.")
            }
        }
    }

    // ------------------------------------------------------------ library

    fun addFolder(uri: Uri) = io { repo.addFolder(uri) }
    fun removeFolder(id: Long) = io { repo.removeFolder(id); say("Folder removed from the library. Your files were not touched.") }
    fun rescan(id: Long) = io { repo.scanFolder(id) }
    fun rescanAll() = io { repo.rescanAll() }
    fun rematchPending() = io { repo.rematchPending() }

    fun toggleFavorite(m: Movie) = io { repo.toggleFavorite(m) }
    fun toggleWatched(m: Movie) = io { repo.toggleWatched(m) }
    fun setRating(id: Long, stars: Int) = io { repo.setRating(id, stars) }
    fun setNotes(id: Long, notes: String) = io { repo.setNotes(id, notes) }
    fun removeFromLibrary(id: Long) = io { repo.removeFromLibrary(id); say("Removed from library. The video file was not deleted.") }

    // ------------------------------------------------------------ matching / info

    fun fetchInfo(m: Movie) = io {
        if (!repo.tmdb.hasKey()) {
            say("Add your TMDB API key in Settings first.")
            return@io
        }
        if (m.tmdbId == null) {
            val u = repo.autoMatch(m)
            say(if (u.matchStatus == 1) "Details loaded." else "No confident match found. Use Edit to search manually.")
        } else {
            repo.applyMatch(
                m, TmdbClient.SearchResult(m.tmdbId, m.title, m.year, null, null), keepPoster = true
            )
            say("Details updated.")
        }
    }

    suspend fun searchTmdb(query: String, year: Int?): List<TmdbClient.SearchResult> = repo.searchTmdb(query, year)
    fun applyMatch(movieId: Long, r: TmdbClient.SearchResult) = io { repo.applyMatchById(movieId, r); say("Movie updated.") }
    fun saveManual(movieId: Long, title: String, year: Int?) = io { repo.saveManualInfo(movieId, title, year) }
    suspend fun posterSuggestions(m: Movie) = repo.posterSuggestions(m)
    fun saveCustomPoster(movieId: Long, bmp: Bitmap, done: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.saveCustomPoster(movieId, bmp)
            } catch (e: Exception) {
                say("Could not save the poster.")
            }
            launch(Dispatchers.Main) { done() }
        }
    }

    // ------------------------------------------------------------ playlists

    fun createPlaylist(name: String, thenAdd: Long? = null) = io {
        val id = repo.createPlaylist(name)
        if (thenAdd != null) {
            repo.addToPlaylist(id, thenAdd)
            say("Added to \"${name.trim()}\".")
        }
    }

    fun renamePlaylist(id: Long, name: String) = io { repo.renamePlaylist(id, name) }
    fun deletePlaylist(id: Long) = io { repo.deletePlaylist(id) }
    fun addToPlaylist(pid: Long, mid: Long, name: String) = io { repo.addToPlaylist(pid, mid); say("Added to \"$name\".") }
    fun removeFromPlaylist(pid: Long, mid: Long) = io { repo.removeFromPlaylist(pid, mid) }

    // ------------------------------------------------------------ subtitles

    val subtitleBusy = MutableStateFlow(false)

    fun downloadSubtitle(movieId: Long) {
        if (subtitleBusy.value) return
        subtitleBusy.value = true
        io {
            try {
                when (val r = repo.downloadSubtitle(movieId)) {
                    SubResult.Success -> say("Arabic subtitle downloaded.")
                    SubResult.NotFound -> say("Arabic subtitle not found for this movie. You can choose an SRT file instead.")
                    is SubResult.Error -> say(r.message)
                }
            } finally {
                subtitleBusy.value = false
            }
        }
    }

    fun importSubtitle(movieId: Long, uri: Uri) = io {
        say(if (repo.importSubtitle(movieId, uri)) "Subtitle file added." else "Could not read that subtitle file.")
    }

    fun setSubtitleOffset(movieId: Long, ms: Long) = io { repo.setSubtitleOffset(movieId, ms) }

    // ------------------------------------------------------------ data

    fun backup(uri: Uri) = io {
        say(if (BackupManager.export(application, repo.db, uri)) "Backup saved." else "Backup failed.")
    }

    fun restore(uri: Uri) = io {
        say(if (BackupManager.import(application, repo.db, uri)) "Library restored." else "That file is not a valid Reelhouse backup.")
    }

    fun clearCache() = io {
        val freed = repo.clearCache()
        say("Cache cleared (${freed / 1024} KB).")
    }
}
