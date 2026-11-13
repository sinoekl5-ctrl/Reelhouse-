package com.reelhouse.app.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import com.reelhouse.app.ReelhouseApp
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address

/**
 * Tiny local HTTP server so a Chromecast can pull the video file from the phone over Wi-Fi.
 * Supports HTTP Range requests (seeking) and CORS headers (required by the Cast receiver).
 */
class StreamServer(private val app: ReelhouseApp, port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""))
        if (session.uri.startsWith("/sub/")) return serveSubtitle(session)
        val id = session.uri.removePrefix("/movie/").substringBefore('.').toLongOrNull()
            ?: return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found"))
        val movie = runBlocking { app.repository.getMovie(id) }
            ?: return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found"))

        val uri = Uri.parse(movie.uri)
        val pfd = try {
            app.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            null
        } ?: return cors(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Cannot open file"))

        val total = pfd.statSize
        val mime = mimeFor(movie.fileName)

        var start = 0L
        var end = total - 1
        val range = session.headers["range"]
        var partial = false
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(',')
            val s = spec.substringBefore('-')
            val e = spec.substringAfter('-', "")
            if (s.isNotEmpty()) {
                start = s.toLongOrNull() ?: 0L
                if (e.isNotEmpty()) end = e.toLongOrNull() ?: end
            } else if (e.isNotEmpty()) {
                // suffix range: last N bytes
                val n = e.toLongOrNull() ?: 0L
                start = (total - n).coerceAtLeast(0)
            }
            partial = true
        }
        if (start >= total) {
            pfd.close()
            val r = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
            r.addHeader("Content-Range", "bytes */$total")
            return cors(r)
        }
        if (end >= total) end = total - 1
        val length = end - start + 1

        val channel = FileInputStream(pfd.fileDescriptor).channel
        channel.position(start)
        val stream = object : InputStream() {
            private var remaining = length
            override fun read(): Int {
                val b = ByteArray(1)
                val n = read(b, 0, 1)
                return if (n <= 0) -1 else b[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val toRead = minOf(len.toLong(), remaining).toInt()
                val n = channel.read(java.nio.ByteBuffer.wrap(b, off, toRead))
                if (n > 0) remaining -= n
                return n
            }

            override fun available(): Int = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            override fun close() {
                try { channel.close() } catch (_: Exception) {}
                try { pfd.close() } catch (_: Exception) {}
            }
        }

        val resp = newFixedLengthResponse(
            if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK, mime, stream, length
        )
        resp.addHeader("Accept-Ranges", "bytes")
        if (partial) resp.addHeader("Content-Range", "bytes $start-$end/$total")
        return cors(resp)
    }

    /** Serves the movie's saved SRT as WebVTT (with the user's timing offset applied) for the TV. */
    private fun serveSubtitle(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/sub/").substringBefore('.').toLongOrNull()
            ?: return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found"))
        val movie = runBlocking { app.repository.getMovie(id) }
        val name = movie?.subtitleFile
        val file = if (name != null) java.io.File(com.reelhouse.app.data.AppPaths.subsDir, name) else null
        if (movie == null || file == null || !file.exists()) {
            return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No subtitle"))
        }
        val shifted = com.reelhouse.app.data.SubtitleUtils.shiftSrt(file.readText(Charsets.UTF_8), movie.subtitleOffsetMs)
        val vtt = "WEBVTT\n\n" + Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})").replace(shifted, "$1.$2")
        return cors(newFixedLengthResponse(Response.Status.OK, "text/vtt; charset=utf-8", vtt))
    }

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        r.addHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
        return r
    }

    companion object {
        @Volatile
        private var instance: StreamServer? = null

        @Volatile
        var port: Int = 0
            private set

        fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }

        @Synchronized
        fun start(app: ReelhouseApp): Boolean {
            if (instance?.isAlive == true) return true
            for (p in 8765..8775) {
                try {
                    val s = StreamServer(app, p)
                    s.start(SOCKET_READ_TIMEOUT, false)
                    instance = s
                    port = p
                    return true
                } catch (e: Exception) {
                    // try the next port
                }
            }
            return false
        }

        @Synchronized
        fun stop() {
            instance?.stop()
            instance = null
        }

        /** Phone's Wi-Fi/LAN IPv4 address, or null if not on a local network. */
        fun localIp(ctx: Context): String? {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return null
            val props = cm.getLinkProperties(net) ?: return null
            return props.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }

        fun urlFor(ctx: Context, movieId: Long, fileName: String): String? {
            val ip = localIp(ctx) ?: return null
            val ext = fileName.substringAfterLast('.', "mp4")
            return "http://$ip:$port/movie/$movieId.$ext"
        }
    }
}
