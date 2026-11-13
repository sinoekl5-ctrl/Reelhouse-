package com.reelhouse.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup = one ZIP containing data.json (library, folders, playlists, favorites, watched flags,
 * resume positions, ratings, notes) plus the poster and subtitle files. API keys are NOT included.
 */
object BackupManager {
    private fun Movie.toJson(): JSONObject = JSONObject()
        .put("id", id).put("uri", uri).put("folderId", folderId).put("fileName", fileName)
        .put("fileSize", fileSize).put("title", title).put("year", year ?: JSONObject.NULL)
        .put("tmdbId", tmdbId ?: JSONObject.NULL).put("posterFile", posterFile ?: JSONObject.NULL)
        .put("overview", overview ?: JSONObject.NULL).put("runtimeMin", runtimeMin ?: JSONObject.NULL)
        .put("durationMs", durationMs).put("myRating", myRating).put("favorite", favorite)
        .put("watched", watched).put("positionMs", positionMs).put("resumeSeq", resumeSeq)
        .put("notes", notes).put("addedAt", addedAt).put("matchStatus", matchStatus)
        .put("hidden", hidden).put("subtitleFile", subtitleFile ?: JSONObject.NULL)
        .put("subtitleStatus", subtitleStatus).put("subtitleOffsetMs", subtitleOffsetMs)

    private fun JSONObject.optStr(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.optIntN(k: String): Int? = if (isNull(k)) null else optInt(k)

    private fun movieFromJson(o: JSONObject) = Movie(
        id = o.getLong("id"), uri = o.getString("uri"), folderId = o.getLong("folderId"),
        fileName = o.optString("fileName"), fileSize = o.optLong("fileSize"),
        title = o.optString("title"), year = o.optIntN("year"), tmdbId = o.optIntN("tmdbId"),
        posterFile = o.optStr("posterFile"), overview = o.optStr("overview"), runtimeMin = o.optIntN("runtimeMin"),
        durationMs = o.optLong("durationMs"), myRating = o.optInt("myRating"),
        favorite = o.optBoolean("favorite"), watched = o.optBoolean("watched"),
        positionMs = o.optLong("positionMs"), resumeSeq = o.optInt("resumeSeq"),
        notes = o.optString("notes"), addedAt = o.optLong("addedAt"), matchStatus = o.optInt("matchStatus"),
        hidden = o.optBoolean("hidden"), subtitleFile = o.optStr("subtitleFile"),
        subtitleStatus = o.optInt("subtitleStatus"), subtitleOffsetMs = o.optLong("subtitleOffsetMs")
    )

    suspend fun export(ctx: Context, db: AppDatabase, out: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val folders = JSONArray()
            db.folderDao().getAll().forEach {
                folders.put(JSONObject().put("id", it.id).put("treeUri", it.treeUri).put("name", it.name))
            }
            val movies = JSONArray()
            val movieList = db.movieDao().getAll()
            movieList.forEach { movies.put(it.toJson()) }
            val playlists = JSONArray()
            db.playlistDao().getPlaylists().forEach { playlists.put(JSONObject().put("id", it.id).put("name", it.name)) }
            val entries = JSONArray()
            db.playlistDao().getEntries().forEach {
                entries.put(JSONObject().put("playlistId", it.playlistId).put("movieId", it.movieId).put("position", it.position))
            }
            val root = JSONObject().put("version", 1).put("folders", folders).put("movies", movies)
                .put("playlists", playlists).put("entries", entries)

            val os = ctx.contentResolver.openOutputStream(out) ?: return@withContext false
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                movieList.forEach { m ->
                    m.posterFile?.let { addFile(zip, "posters/$it", File(AppPaths.posterDir, it)) }
                    m.subtitleFile?.let { addFile(zip, "subs/$it", File(AppPaths.subsDir, it)) }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addFile(zip: ZipOutputStream, entryName: String, f: File) {
        if (!f.exists()) return
        zip.putNextEntry(ZipEntry(entryName))
        f.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    suspend fun import(ctx: Context, db: AppDatabase, input: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            var json: String? = null
            val ins = ctx.contentResolver.openInputStream(input) ?: return@withContext false
            ZipInputStream(ins.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == "data.json" -> {
                            val bos = ByteArrayOutputStream()
                            zip.copyTo(bos)
                            json = bos.toString("UTF-8")
                        }
                        name.startsWith("posters/") && !e.isDirectory ->
                            File(AppPaths.posterDir, File(name).name).outputStream().use { zip.copyTo(it) }
                        name.startsWith("subs/") && !e.isDirectory ->
                            File(AppPaths.subsDir, File(name).name).outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
            val text = json ?: return@withContext false
            val root = JSONObject(text)
            val folders = root.getJSONArray("folders").let { a ->
                (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    FolderEntity(o.getLong("id"), o.getString("treeUri"), o.optString("name", "Folder"))
                }
            }
            val movies = root.getJSONArray("movies").let { a -> (0 until a.length()).map { movieFromJson(a.getJSONObject(it)) } }
            val playlists = root.getJSONArray("playlists").let { a ->
                (0 until a.length()).map { Playlist(a.getJSONObject(it).getLong("id"), a.getJSONObject(it).getString("name")) }
            }
            val entries = root.getJSONArray("entries").let { a ->
                (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    PlaylistEntry(o.getLong("playlistId"), o.getLong("movieId"), o.optInt("position"))
                }
            }
            db.withTransaction {
                db.playlistDao().clearEntries()
                db.playlistDao().clearPlaylists()
                db.movieDao().clearAll()
                db.folderDao().clearAll()
                db.folderDao().insertAll(folders)
                db.movieDao().insertAll(movies)
                db.playlistDao().insertPlaylists(playlists)
                db.playlistDao().insertEntries(entries)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
