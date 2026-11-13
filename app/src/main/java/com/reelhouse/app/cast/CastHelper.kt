package com.reelhouse.app.cast

import android.content.Context
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.reelhouse.app.data.Movie

object CastHelper {
    /** Null on devices without Google Play services (the Cast button is then hidden). */
    fun castContext(ctx: Context): CastContext? = try {
        CastContext.getSharedInstance(ctx.applicationContext)
    } catch (e: Exception) {
        null
    }

    fun isAvailable(ctx: Context) = castContext(ctx) != null

    fun session(ctx: Context): CastSession? = castContext(ctx)?.sessionManager?.currentCastSession

    fun setUpButton(button: MediaRouteButton) {
        try {
            CastButtonFactory.setUpMediaRouteButton(button.context, button)
        } catch (e: Exception) {
            // Cast not available
        }
    }

    /** Warns about formats that older Chromecasts often cannot decode. */
    fun compatibilityNote(fileName: String): String? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> null
            else -> "This file type (.${fileName.substringAfterLast('.', "")}) may not play on every Chromecast. " +
                "If the TV shows an error, MP4 (H.264 + AAC) works best."
        }
    }

    /** Loads the movie on the connected TV. Returns an error message, or null on success. */
    fun load(ctx: Context, movie: Movie, startMs: Long): String? {
        val session = session(ctx) ?: return "Not connected to a Chromecast. Tap the Cast button and choose your TV."
        val client = session.remoteMediaClient ?: return "The TV is not ready yet. Try again in a moment."
        CastServerService.start(ctx)
        val url = StreamServer.urlFor(ctx, movie.id, movie.fileName)
            ?: return "Your phone must be connected to Wi-Fi (the same network as the TV) to cast."

        val meta = MediaMetadata(MediaMetadata.METADATA_TYPE_MOVIE)
        meta.putString(MediaMetadata.KEY_TITLE, movie.title)
        val builder = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(StreamServer.mimeFor(movie.fileName))
            .setMetadata(meta)

        var active: LongArray? = null
        if (movie.subtitleFile != null) {
            val vtt = url.substringBeforeLast("/movie/") + "/sub/${movie.id}.vtt"
            val track = MediaTrack.Builder(1, MediaTrack.TYPE_TEXT)
                .setName("Arabic")
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(vtt)
                .setContentType("text/vtt")
                .setLanguage("ar")
                .build()
            builder.setMediaTracks(listOf(track))
            active = longArrayOf(1)
        }

        val req = MediaLoadRequestData.Builder()
            .setMediaInfo(builder.build())
            .setAutoplay(true)
            .setCurrentTime(startMs)
        if (active != null) req.setActiveTrackIds(active)
        client.load(req.build())
        return null
    }
}

/** SessionManagerListener with empty defaults, so callers only override what they need. */
abstract class SimpleCastListener : com.google.android.gms.cast.framework.SessionManagerListener<CastSession> {
    override fun onSessionStarting(session: CastSession) {}
    override fun onSessionStarted(session: CastSession, sessionId: String) {}
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionEnding(session: CastSession) {}
    override fun onSessionEnded(session: CastSession, error: Int) {}
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}
}
