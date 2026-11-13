package com.reelhouse.app.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastSession
import com.reelhouse.app.ReelhouseApp
import com.reelhouse.app.cast.CastHelper
import com.reelhouse.app.cast.CastServerService
import com.reelhouse.app.cast.SimpleCastListener
import com.reelhouse.app.data.Movie
import com.reelhouse.app.data.SubStyle
import com.reelhouse.app.data.SubResult
import com.reelhouse.app.data.SubtitleUtils
import com.reelhouse.app.ui.AppLock
import com.reelhouse.app.ui.screens.formatMs
import com.reelhouse.app.ui.theme.CinemaRed
import com.reelhouse.app.ui.theme.ReelhouseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private data class AudioOpt(val label: String, val group: Tracks.Group, val selected: Boolean)

/**
 * Full-screen landscape player. Simple YouTube-style controls; advanced options live behind the gear.
 * No watch dates are stored anywhere: only the resume position and the Watched flag.
 */
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_ID = "movie_id"
        private const val EXTRA_START_OVER = "start_over"

        fun start(ctx: Context, movieId: Long, startOver: Boolean = false) {
            AppLock.skipNextRelock = true
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_ID, movieId)
                    .putExtra(EXTRA_START_OVER, startOver)
            )
        }
    }

    private val app get() = application as ReelhouseApp
    private val repo get() = app.repository
    private val prefs get() = app.prefs
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var movieId = 0L
    private lateinit var movie: Movie
    private lateinit var player: ExoPlayer
    private var playerView: PlayerView? = null

    // ---- UI state
    private var controlsVisible by mutableStateOf(true)
    private var hideToken by mutableIntStateOf(0)
    private var screenLocked by mutableStateOf(false)
    private var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var speed by mutableFloatStateOf(1f)
    private var isPlaying by mutableStateOf(false)
    private var buffering by mutableStateOf(false)
    private var positionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var inPip by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var settingsPage by mutableStateOf("main")
    private var audioOptions by mutableStateOf<List<AudioOpt>>(emptyList())
    private var subsOn by mutableStateOf(true)
    private var subOffset by mutableLongStateOf(0L)
    private var hasSub by mutableStateOf(false)
    private var subBusy by mutableStateOf(false)
    private var errorText by mutableStateOf<String?>(null)
    private var indicator by mutableStateOf<String?>(null)
    private var indicatorFrac by mutableFloatStateOf(-1f)
    private var indicatorToken by mutableIntStateOf(0)
    private var videoW = 0
    private var videoH = 0

    // ---- cast state
    private var castMode by mutableStateOf(false)
    private var castPos by mutableLongStateOf(0L)
    private var castDur by mutableLongStateOf(0L)
    private var castPlaying by mutableStateOf(false)
    private var castName by mutableStateOf("your TV")

    private var started = false
    private var offsetJob: Job? = null
    private var volFrac = 0.5f
    private var brightFrac = 0.5f

    private val castListener = object : SimpleCastListener() {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (!castMode) startCast(player.currentPosition)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (!castMode) startCast(player.currentPosition)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            if (castMode) stopCastAndResumeLocal()
        }
    }

    // =====================================================================================
    // lifecycle
    // =====================================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        movieId = intent.getLongExtra(EXTRA_ID, -1L)
        val m = runBlocking { repo.getMovie(movieId) }
        if (m == null) {
            finish()
            return
        }
        movie = m
        subOffset = m.subtitleOffsetMs
        hasSub = m.subtitleFile != null

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volFrac = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        brightFrac = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f

        buildPlayer()

        val startOver = intent.getBooleanExtra(EXTRA_START_OVER, false)
        val resumeAt = if (!startOver && m.positionMs > 5_000) m.positionMs else 0L
        positionMs = resumeAt

        val castCtx = CastHelper.castContext(this)
        castCtx?.sessionManager?.addSessionManagerListener(castListener, CastSession::class.java)
        if (CastHelper.session(this)?.isConnected == true) startCast(resumeAt) else startLocal(resumeAt)

        setContent {
            ReelhouseTheme("dark") { PlayerUi(castAvailable = castCtx != null) }
        }

        // First time opening this movie: fetch the Arabic subtitle automatically (if a key is set).
        if (m.subtitleStatus == 0 && m.subtitleFile == null && prefs.autoSubs.value && prefs.osKey.value.isNotBlank()) {
            downloadSubtitle(silentIfMissing = true)
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!::player.isInitialized) return
        saveProgress()
        CastHelper.castContext(this)?.sessionManager?.removeSessionManagerListener(castListener, CastSession::class.java)
        if (castMode) {
            castMode = false
            CastHelper.castContext(this)?.sessionManager?.endCurrentSession(true)
            CastServerService.stop(this)
        }
        player.release()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlaying && !castMode && !screenLocked && errorText == null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode) hideSystemBars()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || castMode) return
        try {
            val ratio = if (videoW > 0 && videoH > 0) (videoW.toFloat() / videoH).coerceIn(0.45f, 2.3f) else 16f / 9f
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000)).build()
            )
        } catch (e: Exception) {
            // PiP not available on this device
        }
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    // =====================================================================================
    // player setup
    // =====================================================================================

    private fun buildPlayer() {
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true) // pauses when headphones / Bluetooth disconnect
            .setWakeMode(C.WAKE_MODE_LOCAL)    // keeps playing audio when the screen turns off
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // pauses for phone calls and other apps taking audio focus
            )
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("ar")
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    val d = player.duration
                    if (d != C.TIME_UNSET && d > 0) durationMs = d
                }
                if (state == Player.STATE_ENDED) {
                    controlsVisible = true
                    appScope.launch { repo.saveProgress(movieId, 0, durationMs, true, 0) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshAudio(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoW = videoSize.width
                videoH = videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                errorText = "This video can't be played on this phone. The file may be damaged, moved, or use a format Android can't decode."
            }
        })
    }

    private fun buildItem(): MediaItem {
        val b = MediaItem.Builder().setUri(Uri.parse(movie.uri))
        val name = movie.subtitleFile
        if (name != null) {
            val src = File(com.reelhouse.app.data.AppPaths.subsDir, name)
            if (src.exists()) {
                // Timing offset is applied by writing a shifted copy; the original file stays untouched.
                val tmp = File(cacheDir, "sub_${movie.id}.srt")
                tmp.writeText(SubtitleUtils.shiftSrt(src.readText(Charsets.UTF_8), movie.subtitleOffsetMs), Charsets.UTF_8)
                b.setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(tmp))
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("ar")
                            .setLabel("Arabic")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                )
            }
        }
        return b.build()
    }

    private fun startLocal(startMs: Long) {
        started = true
        player.setMediaItem(buildItem(), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun reloadSubtitles() {
        lifecycleScope.launch {
            val m = withContext(Dispatchers.IO) { repo.getMovie(movieId) } ?: return@launch
            movie = m
            subOffset = m.subtitleOffsetMs
            hasSub = m.subtitleFile != null
            if (!castMode) {
                val pos = player.currentPosition
                val play = player.playWhenReady
                player.setMediaItem(buildItem(), pos)
                player.prepare()
                player.playWhenReady = play
            } else {
                CastHelper.load(this@PlayerActivity, m, castPos)
            }
        }
    }

    private fun refreshAudio(tracks: Tracks) {
        val list = ArrayList<AudioOpt>()
        for (g in tracks.groups) {
            if (g.type != C.TRACK_TYPE_AUDIO || g.length == 0) continue
            val f = g.getTrackFormat(0)
            val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }?.takeIf { it.isNotBlank() }
            val ch = if (f.channelCount > 0) "${f.channelCount} ch" else null
            val label = listOfNotNull(f.label, lang, ch).distinct().joinToString(" • ").ifBlank { "Track ${list.size + 1}" }
            list.add(AudioOpt(label, g, g.isSelected))
        }
        audioOptions = list
    }

    private fun chooseAudio(opt: AudioOpt) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, 0))
            .build()
    }

    private fun setSubsEnabled(on: Boolean) {
        subsOn = on
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
            .build()
    }

    private fun changeSubOffset(deltaMs: Long, reset: Boolean = false) {
        subOffset = if (reset) 0L else (subOffset + deltaMs).coerceIn(-120_000L, 120_000L)
        offsetJob?.cancel()
        offsetJob = lifecycleScope.launch {
            delay(600) // apply once the user stops tapping
            withContext(Dispatchers.IO) { repo.setSubtitleOffset(movieId, subOffset) }
            reloadSubtitles()
        }
    }

    private fun downloadSubtitle(silentIfMissing: Boolean = false) {
        if (subBusy) return
        subBusy = true
        appScope.launch {
            when (val r = repo.downloadSubtitle(movieId)) {
                SubResult.Success -> {
                    toast("Arabic subtitle downloaded.")
                    runOnUiThread { reloadSubtitles() }
                }
                SubResult.NotFound -> if (!silentIfMissing) toast("Arabic subtitle not found. You can choose an SRT file from the ⚙ menu.")
                is SubResult.Error -> if (!silentIfMissing) toast(r.message)
            }
            subBusy = false
        }
    }

    private fun importSubtitle(uri: Uri) {
        appScope.launch {
            if (repo.importSubtitle(movieId, uri)) {
                toast("Subtitle file added.")
                runOnUiThread { reloadSubtitles() }
            } else {
                toast("Could not read that subtitle file.")
            }
        }
    }

    // =====================================================================================
    // Chromecast
    // =====================================================================================

    private fun remoteClient() = CastHelper.session(this)?.remoteMediaClient

    private fun startCast(startMs: Long) {
        if (::player.isInitialized) player.pause()
        castName = CastHelper.session(this)?.castDevice?.friendlyName ?: "your TV"
        val err = CastHelper.load(this, movie, startMs)
        if (err != null) {
            toast(err)
            castMode = false
            if (!started) startLocal(startMs)
            return
        }
        castMode = true
        started = true
        castPos = startMs
        castPlaying = true
        CastHelper.compatibilityNote(movie.fileName)?.let { toast(it) }
    }

    private fun stopCastAndResumeLocal() {
        val pos = castPos
        castMode = false
        CastServerService.stop(this)
        if (player.mediaItemCount == 0) startLocal(pos) else {
            player.seekTo(pos)
            player.play()
        }
    }

    // =====================================================================================
    // progress / controls helpers
    // =====================================================================================

    private fun saveProgress() {
        if (!started || !::player.isInitialized) return
        val pos: Long
        val dur: Long
        if (castMode) {
            pos = castPos; dur = castDur
        } else {
            pos = player.currentPosition
            dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: durationMs
        }
        val watched = dur > 0 && pos >= dur * 0.9
        val savePos = if (watched) 0L else pos
        val seq = if (savePos > 5_000) prefs.nextSeq() else 0
        appScope.launch { repo.saveProgress(movieId, savePos, dur, watched, seq) }
    }

    private fun seekToMs(ms: Long) {
        val target = ms.coerceAtLeast(0)
        if (castMode) {
            remoteClient()?.seek(MediaSeekOptions.Builder().setPosition(target).build())
            castPos = target
        } else {
            player.seekTo(target)
            positionMs = target
        }
    }

    private fun seekBy(delta: Long) {
        val cur = if (castMode) castPos else player.currentPosition
        val dur = if (castMode) castDur else durationMs
        val t = if (dur > 0) (cur + delta).coerceIn(0, dur) else (cur + delta).coerceAtLeast(0)
        seekToMs(t)
        flashIndicator(if (delta > 0) "+${delta / 1000}s" else "${delta / 1000}s", -1f)
    }

    private fun togglePlay() {
        if (castMode) {
            remoteClient()?.togglePlayback()
            castPlaying = !castPlaying
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0); player.play()
        } else if (player.isPlaying) player.pause() else player.play()
    }

    private fun flashIndicator(text: String, frac: Float) {
        indicator = text
        indicatorFrac = frac
        indicatorToken++
    }

    private fun adjustVolume(frac: Float) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volFrac = frac.coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volFrac * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
        flashIndicator("Volume ${(volFrac * 100).roundToInt()}%", volFrac)
    }

    private fun adjustBrightness(frac: Float) {
        brightFrac = frac.coerceIn(0.02f, 1f)
        val lp = window.attributes
        lp.screenBrightness = brightFrac
        window.attributes = lp
        flashIndicator("Brightness ${(brightFrac * 100).roundToInt()}%", brightFrac)
    }

    // =====================================================================================
    // UI
    // =====================================================================================

    @Composable
    private fun PlayerUi(castAvailable: Boolean) {
        val subFont by prefs.subFont.collectAsState()
        val subSize by prefs.subSize.collectAsState()
        val subColor by prefs.subColor.collectAsState()
        val subBg by prefs.subBg.collectAsState()
        val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importSubtitle(uri)
        }

        // Clock: position / duration, plus saving progress every ~5 seconds.
        LaunchedEffect(Unit) {
            var tick = 0
            while (true) {
                if (castMode) {
                    val c = remoteClient()
                    if (c != null) {
                        castPos = c.approximateStreamPosition
                        val d = c.streamDuration
                        if (d > 0) castDur = d
                        castPlaying = c.isPlaying
                    }
                } else if (::player.isInitialized) {
                    positionMs = player.currentPosition
                    val d = player.duration
                    if (d != C.TIME_UNSET && d > 0) durationMs = d
                }
                if (++tick % 10 == 0 && (isPlaying || castPlaying)) saveProgress()
                delay(500)
            }
        }

        // Controls disappear by themselves after two seconds of no interaction.
        LaunchedEffect(controlsVisible, hideToken, isPlaying, castPlaying, showSettings, castMode) {
            if (controlsVisible && !showSettings && (if (castMode) false else isPlaying)) {
                delay(2000)
                controlsVisible = false
            }
        }
        LaunchedEffect(indicatorToken) {
            if (indicator != null) {
                delay(800)
                indicator = null
            }
        }
        // Lock button hides again by itself too.
        LaunchedEffect(screenLocked, controlsVisible, hideToken) {
            if (screenLocked && controlsVisible) {
                delay(2000)
                controlsVisible = false
            }
        }

        BackHandler(enabled = showSettings || screenLocked) {
            if (showSettings) { showSettings = false; hideSystemBars() } else controlsVisible = true
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // ---- video surface
            if (!castMode) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            player = this@PlayerActivity.player
                            playerView = this
                        }
                    },
                    update = { pv ->
                        pv.resizeMode = resizeMode
                        pv.subtitleView?.apply {
                            setStyle(
                                CaptionStyleCompat(
                                    SubStyle.fg(subColor), SubStyle.bg(subBg), android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK,
                                    SubStyle.typeface(subFont)
                                )
                            )
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, SubStyle.sizeSp(subSize))
                            setBottomPaddingFraction(0.09f)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CastingBackdrop()
            }

            if (buffering && !castMode && errorText == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
            }

            // ---- gesture layer
            val gestureModifier = if (screenLocked || inPip) {
                Modifier.pointerInput(screenLocked) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible; hideToken++ })
                }
            } else {
                Modifier
                    .pointerInput(castMode) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible; hideToken++ },
                            onDoubleTap = { off ->
                                val w = size.width
                                when {
                                    off.x < w * 0.4f -> seekBy(-10_000)
                                    off.x > w * 0.6f -> seekBy(10_000)
                                    else -> togglePlay()
                                }
                            }
                        )
                    }
                    .pointerInput(castMode) {
                        if (castMode) return@pointerInput
                        var leftSide = false
                        detectVerticalDragGestures(
                            onDragStart = { off -> leftSide = off.x < size.width / 2f },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                val d = -dy / size.height * 1.4f
                                if (leftSide) adjustBrightness(brightFrac + d) else adjustVolume(volFrac + d)
                            }
                        )
                    }
            }
            Box(Modifier.fillMaxSize().then(gestureModifier))

            // ---- seek / volume / brightness feedback
            indicator?.let { text ->
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (indicatorFrac >= 0f) {
                        LinearProgressIndicator(
                            progress = { indicatorFrac }, color = CinemaRed, trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = 8.dp).width(140.dp)
                        )
                    }
                }
            }

            // ---- error
            errorText?.let { msg ->
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(msg, color = Color.White, textAlign = TextAlign.Center)
                    TextButton(onClick = { finish() }) { Text("Go back", color = Color.White) }
                }
            }

            // ---- controls
            if (!inPip) {
                if (screenLocked) {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(), exit = fadeOut(),
                        modifier = Modifier.align(Alignment.CenterStart).windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        IconButton(
                            onClick = { screenLocked = false; controlsVisible = true; hideToken++ },
                            modifier = Modifier.padding(16.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) { Icon(Icons.Filled.LockOpen, "Unlock screen", tint = Color.White) }
                    }
                } else {
                    AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                        Controls(castAvailable)
                    }
                }
            }

            if (showSettings) SettingsDialog(onPickFile = { subPicker.launch(arrayOf("*/*")) })
        }
    }

    @Composable
    private fun CastingBackdrop() {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF101010)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Cast, null, tint = CinemaRed, modifier = Modifier.size(64.dp))
            Text("Casting to $castName", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(movie.title, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Keep this app open and stay on the same Wi-Fi as the TV. Closing the player stops casting.",
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 48.dp, end = 48.dp)
            )
        }
    }

    @Composable
    private fun Controls(castAvailable: Boolean) {
        val curPos = if (castMode) castPos else positionMs
        val curDur = if (castMode) castDur else durationMs
        val playingNow = if (castMode) castPlaying else isPlaying
        var seeking by remember { mutableStateOf(false) }
        var seekValue by remember { mutableFloatStateOf(0f) }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.65f),
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // top bar
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(
                    movie.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                if (castAvailable) {
                    AndroidView(
                        factory = { c -> MediaRouteButton(c).also { CastHelper.setUpButton(it) } },
                        modifier = Modifier.size(48.dp)
                    )
                }
                if (!castMode && Build.VERSION.SDK_INT >= 26) {
                    IconButton(onClick = { enterPip() }) {
                        Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White)
                    }
                }
                IconButton(onClick = { settingsPage = "main"; showSettings = true }) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Color.White)
                }
            }

            // center buttons
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { seekBy(-10_000); hideToken++ }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(
                    onClick = { togglePlay(); hideToken++ },
                    modifier = Modifier.size(72.dp).background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        if (playingNow) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playingNow) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = { seekBy(10_000); hideToken++ }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }

            // bottom bar
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMs(if (seeking) (seekValue * curDur).toLong() else curPos), color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = if (seeking) seekValue else if (curDur > 0) (curPos.toFloat() / curDur).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seeking = true; seekValue = it; hideToken++ },
                        onValueChangeFinished = {
                            seekToMs((seekValue * curDur).toLong())
                            seeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = CinemaRed, activeTrackColor = CinemaRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(formatMs(curDur), color = Color.White, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { screenLocked = true; controlsVisible = true; hideToken++ }) {
                        Icon(Icons.Filled.Lock, "Lock screen", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    if (!castMode) {
                        TextButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            hideToken++
                        }) {
                            Icon(Icons.Filled.AspectRatio, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text(
                                when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                    else -> "Fit"
                                },
                                color = Color.White, modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsDialog(onPickFile: () -> Unit) {
        Dialog(
            onDismissRequest = { showSettings = false; hideSystemBars() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1B1B))
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                when (settingsPage) {
                    "main" -> {
                        if (!castMode) {
                            Row2("Playback speed", if (speed == 1f) "Normal" else "${speed}x") { settingsPage = "speed" }
                            Row2("Audio track", audioOptions.firstOrNull { it.selected }?.label ?: "Default") { settingsPage = "audio" }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Subtitles (Arabic)", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = subsOn && hasSub, enabled = hasSub && !castMode,
                                onCheckedChange = { setSubsEnabled(it) }
                            )
                        }
                        Row2("Subtitle timing", if (!hasSub) "No subtitle" else if (subOffset == 0L) "In sync" else "%+.1f s".format(subOffset / 1000.0)) {
                            if (hasSub) settingsPage = "timing" else toast("Download or choose a subtitle first.")
                        }
                        Row2("Download Arabic subtitle", if (subBusy) "Searching…" else "OpenSubtitles") { downloadSubtitle() }
                        Row2("Choose subtitle file (SRT)", "From phone") { onPickFile() }
                    }
                    "speed" -> {
                        BackRow("Playback speed")
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            Row2(if (s == 1f) "Normal" else "${s}x", if (speed == s) "✓" else "") {
                                speed = s; player.setPlaybackSpeed(s); settingsPage = "main"
                            }
                        }
                    }
                    "audio" -> {
                        BackRow("Audio track")
                        if (audioOptions.isEmpty()) Text("Only one audio track.", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(20.dp))
                        audioOptions.forEach { o ->
                            Row2(o.label, if (o.selected) "✓" else "") { chooseAudio(o); settingsPage = "main" }
                        }
                    }
                    "timing" -> {
                        BackRow("Subtitle timing")
                        Text(
                            if (subOffset == 0L) "In sync" else "%+.1f seconds".format(subOffset / 1000.0),
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center
                        )
                        Text(
                            "Positive = subtitles appear later. Negative = earlier.",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { changeSubOffset(-500) }) { Text("−0.5s") }
                            TextButton(onClick = { changeSubOffset(-100) }) { Text("−0.1s") }
                            TextButton(onClick = { changeSubOffset(100) }) { Text("+0.1s") }
                            TextButton(onClick = { changeSubOffset(500) }) { Text("+0.5s") }
                        }
                        TextButton(onClick = { changeSubOffset(0, reset = true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Row2(title: String, value: String, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun BackRow(title: String) {
        Row(
            Modifier.fillMaxWidth().clickable { settingsPage = "main" }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
