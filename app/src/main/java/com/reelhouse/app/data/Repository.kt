package com.reelhouse.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ScanState(
    val running: Boolean = false,
    val message: String = "",
    val done: Int = 0,
    val total: Int = 0
)

sealed class SubResult {
    object Success : SubResult()
    object NotFound : SubResult()
    data class Error(val message: String) : SubResult()
}

object AppPaths {
    lateinit var posterDir: File
    lateinit var subsDir: File
}

class Repository(val ctx: Context, val db: AppDatabase, val prefs: Prefs) {
    private val movieDao = db.movieDao()
    private val folderDao = db.folderDao()
    private val playlistDao = db.playlistDao()

    val tmdb = TmdbClient(prefs)
    private val subs = OpenSubtitlesClient(prefs)

    val movies = movieDao.observeVisible()
    val folders = folderDao.observeAll()
    val playlists = playlistDao.observePlaylists()
    val entries = playlistDao.observeEntries()

    val scanState = MutableStateFlow(ScanState())
    private val scanMutex = Mutex()

    fun observeMovie(id: Long) = movieDao.observe(id)
    suspend fun getMovie(id: Long): Movie? = movieDao.get(id)

    // ---------------------------------------------------------------- folders / scanning

    suspend fun addFolder(tree: Uri): Long = withContext(Dispatchers.IO) {
        val existing = folderDao.getAll().firstOrNull { it.treeUri == tree.toString() }
        val id = existing?.id
            ?: folderDao.insert(FolderEntity(treeUri = tree.toString(), name = LibraryScanner.folderName(ctx, tree)))
        scanFolder(id)
        id
    }

    suspend fun removeFolder(folderId: Long) = withContext(Dispatchers.IO) {
        movieDao.getByFolder(folderId).forEach { cleanupFiles(it); playlistDao.deleteEntriesOfMovie(it.id) }
        movieDao.deleteByFolder(folderId)
        folderDao.delete(folderId)
    }

    suspend fun rescanAll() = withContext(Dispatchers.IO) {
        folderDao.getAll().forEach { scanFolder(it.id) }
    }

    suspend fun scanFolder(folderId: Long) = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val folder = folderDao.get(folderId) ?: return@withLock
            scanState.value = ScanState(true, "Scanning ${folder.name}…")
            val found = try {
                LibraryScanner.scan(ctx, Uri.parse(folder.treeUri))
            } catch (e: Exception) {
                scanState.value = ScanState(false, "Could not read \"${folder.name}\". Remove it and add it again.")
                return@withLock
            }

            val existing = movieDao.getByFolder(folderId).associateBy { it.uri }
            val foundUris = found.map { it.uri.toString() }.toHashSet()
            existing.values.filter { it.uri !in foundUris }.forEach {
                cleanupFiles(it)
                playlistDao.deleteEntriesOfMovie(it.id)
                movieDao.delete(it.id)
            }

            var added = 0
            for (f in found) {
                val key = f.uri.toString()
                val ex = existing[key]
                if (ex == null) {
                    val p = FilenameParser.parse(f.name)
                    val id = movieDao.insert(
                        Movie(
                            uri = key, folderId = folderId, fileName = f.name, fileSize = f.size,
                            title = p.title, year = p.year, addedAt = System.currentTimeMillis()
                        )
                    )
                    if (id > 0) added++
                } else if (ex.fileSize != f.size) {
                    movieDao.update(ex.copy(fileSize = f.size))
                }
            }

            val pending = movieDao.getByFolder(folderId).filter { it.matchStatus == 0 && !it.hidden }
            var failed = 0
            if (pending.isNotEmpty() && tmdb.hasKey()) {
                pending.forEachIndexed { i, m ->
                    scanState.value = ScanState(true, "Matching movies… ${i + 1}/${pending.size}", i, pending.size)
                    try {
                        autoMatch(m)
                    } catch (e: Exception) {
                        failed++
                    }
                }
            }

            val msg = buildString {
                append("Scan complete: ${found.size} movies")
                if (added > 0) append(", $added new")
                if (pending.isNotEmpty() && !tmdb.hasKey()) append(". Add a TMDB key in Settings to fetch posters and details")
                if (failed > 0) append(". $failed could not be matched (no connection?) — rescan later")
            }
            scanState.value = ScanState(false, msg)
        }
    }

    // ---------------------------------------------------------------- matching

    suspend fun autoMatch(m: Movie): Movie {
        var results = tmdb.search(m.title, m.year)
        if (results.isEmpty() && m.year != null) results = tmdb.search(m.title, null)
        val nt = FilenameParser.normalize(m.title)
        val best = results.firstOrNull {
            FilenameParser.normalize(it.title) == nt && (m.year == null || it.year == null || it.year == m.year)
        } ?: results.firstOrNull {
            m.year != null && it.year == m.year && FilenameParser.normalize(it.title).let { t ->
                t.contains(nt) || nt.contains(t)
            }
        }
        return if (best != null) {
            applyMatch(m, best)
        } else {
            val u = m.copy(matchStatus = 2)
            movieDao.update(u)
            u
        }
    }

    suspend fun applyMatch(m: Movie, r: TmdbClient.SearchResult, keepPoster: Boolean = false): Movie =
        withContext(Dispatchers.IO) {
            val d = runCatching { tmdb.details(r.id) }.getOrNull()
            var posterName = m.posterFile
            val pp = r.posterPath ?: d?.posterPath
            if (!keepPoster && pp != null) {
                val bytes = runCatching { Http.request(TmdbClient.imageUrl(pp, "w500")).takeIf { it.ok }?.body }.getOrNull()
                if (bytes != null) posterName = writePoster(m.id, bytes, m.posterFile)
            }
            val u = m.copy(
                title = r.title.ifBlank { m.title },
                year = r.year ?: m.year,
                tmdbId = r.id,
                overview = d?.overview ?: r.overview,
                runtimeMin = d?.runtime,
                posterFile = posterName,
                matchStatus = 1
            )
            movieDao.update(u)
            u
        }

    suspend fun applyMatchById(movieId: Long, r: TmdbClient.SearchResult) {
        val m = movieDao.get(movieId) ?: return
        applyMatch(m, r)
    }

    suspend fun saveManualInfo(movieId: Long, title: String, year: Int?) = withContext(Dispatchers.IO) {
        val m = movieDao.get(movieId) ?: return@withContext
        movieDao.update(m.copy(title = title.trim().ifBlank { m.title }, year = year, matchStatus = 3))
    }

    suspend fun rematchPending() = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val pending = movieDao.getAll().filter { it.matchStatus == 0 && !it.hidden }
            var failed = 0
            pending.forEachIndexed { i, m ->
                scanState.value = ScanState(true, "Matching movies… ${i + 1}/${pending.size}", i, pending.size)
                try { autoMatch(m) } catch (e: Exception) { failed++ }
            }
            scanState.value = ScanState(false, if (failed > 0) "$failed movies could not be matched." else "Matching finished.")
        }
    }

    suspend fun searchTmdb(query: String, year: Int?): List<TmdbClient.SearchResult> =
        withContext(Dispatchers.IO) { tmdb.search(query, year) }

    suspend fun posterSuggestions(movie: Movie): List<String> = withContext(Dispatchers.IO) {
        if (!tmdb.hasKey()) return@withContext emptyList()
        val id = movie.tmdbId ?: tmdb.search(movie.title, movie.year).firstOrNull()?.id ?: return@withContext emptyList()
        tmdb.posters(id)
    }

    // ---------------------------------------------------------------- posters

    private fun writePoster(movieId: Long, bytes: ByteArray, old: String?): String {
        val name = "p${movieId}_${System.nanoTime()}.jpg"
        File(AppPaths.posterDir, name).writeBytes(bytes)
        old?.let { File(AppPaths.posterDir, it).delete() }
        return name
    }

    suspend fun saveCustomPoster(movieId: Long, bmp: Bitmap) = withContext(Dispatchers.IO) {
        val m = movieDao.get(movieId) ?: return@withContext
        val name = "p${movieId}_${System.nanoTime()}.jpg"
        File(AppPaths.posterDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        m.posterFile?.let { File(AppPaths.posterDir, it).delete() }
        movieDao.update(m.copy(posterFile = name))
    }

    private fun cleanupFiles(m: Movie) {
        m.posterFile?.let { File(AppPaths.posterDir, it).delete() }
        m.subtitleFile?.let { File(AppPaths.subsDir, it).delete() }
    }

    // ---------------------------------------------------------------- user state

    suspend fun toggleFavorite(m: Movie) = movieDao.setFavorite(m.id, !m.favorite)
    suspend fun toggleWatched(m: Movie) = movieDao.setWatched(m.id, !m.watched)
    suspend fun setRating(id: Long, stars: Int) = movieDao.setRating(id, stars)
    suspend fun setNotes(id: Long, notes: String) = movieDao.setNotes(id, notes)

    /** Removes from the library only; the video file itself is never touched or deleted. */
    suspend fun removeFromLibrary(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.deleteEntriesOfMovie(id)
        movieDao.hide(id)
    }

    /** Called by the player. Stores only the resume position and the Watched flag — no dates. */
    suspend fun saveProgress(id: Long, pos: Long, dur: Long, watched: Boolean, seq: Int) =
        withContext(Dispatchers.IO) { movieDao.updateProgress(id, pos, dur, watched, seq) }

    // ---------------------------------------------------------------- playlists

    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(Playlist(name = name.trim()))
    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name.trim())
    suspend fun deletePlaylist(id: Long) {
        playlistDao.deleteEntriesOfPlaylist(id); playlistDao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(pid: Long, mid: Long) =
        playlistDao.insertEntry(PlaylistEntry(pid, mid, playlistDao.countEntries(pid)))

    suspend fun removeFromPlaylist(pid: Long, mid: Long) = playlistDao.deleteEntry(pid, mid)

    // ---------------------------------------------------------------- subtitles

    suspend fun downloadSubtitle(movieId: Long): SubResult = withContext(Dispatchers.IO) { downloadSubtitleInternal(movieId) }

    private suspend fun downloadSubtitleInternal(movieId: Long): SubResult {
        val m = movieDao.get(movieId) ?: return SubResult.Error("Movie not found.")
        if (prefs.osKey.value.isBlank()) {
            return SubResult.Error("Add your OpenSubtitles API key in Settings → Accounts / Keys.")
        }
        val hash = runCatching { SubtitleUtils.movieHash(ctx, Uri.parse(m.uri)) }.getOrNull()
        return when (val s = subs.search(hash, m.tmdbId, m.title, m.year)) {
            is OpenSubtitlesClient.Search.Error -> SubResult.Error(s.message)
            OpenSubtitlesClient.Search.None -> {
                movieDao.update(m.copy(subtitleStatus = 2))
                SubResult.NotFound
            }
            is OpenSubtitlesClient.Search.Found -> when (val d = subs.download(s.fileId)) {
                is OpenSubtitlesClient.Download.Error -> SubResult.Error(d.message)
                is OpenSubtitlesClient.Download.Ok -> {
                    storeSubtitle(m, d.bytes, 1)
                    SubResult.Success
                }
            }
        }
    }

    suspend fun importSubtitle(movieId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val m = movieDao.get(movieId) ?: return@withContext false
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext false
        if (bytes.isEmpty()) return@withContext false
        storeSubtitle(m, bytes, 3)
        true
    }

    private suspend fun storeSubtitle(m: Movie, bytes: ByteArray, status: Int) {
        // Always saved as UTF-8 (Windows-1256 etc. is converted) so Arabic shows correctly.
        val text = SubtitleUtils.decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val name = "s${m.id}_${System.nanoTime()}.srt"
        File(AppPaths.subsDir, name).writeText(text, Charsets.UTF_8)
        m.subtitleFile?.let { File(AppPaths.subsDir, it).delete() }
        movieDao.update(m.copy(subtitleFile = name, subtitleStatus = status, subtitleOffsetMs = 0))
    }

    suspend fun setSubtitleOffset(movieId: Long, offsetMs: Long) = withContext(Dispatchers.IO) {
        val m = movieDao.get(movieId) ?: return@withContext
        movieDao.update(m.copy(subtitleOffsetMs = offsetMs))
    }

    // ---------------------------------------------------------------- data

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        ctx.cacheDir.listFiles()?.forEach { f ->
            freed += f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            f.deleteRecursively()
        }
        freed
    }
}
