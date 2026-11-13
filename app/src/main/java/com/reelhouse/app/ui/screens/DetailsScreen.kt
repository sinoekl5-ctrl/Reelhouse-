package com.reelhouse.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastSession
import com.reelhouse.app.cast.CastHelper
import com.reelhouse.app.cast.SimpleCastListener
import com.reelhouse.app.data.Movie
import com.reelhouse.app.data.TmdbClient
import com.reelhouse.app.player.PlayerActivity
import com.reelhouse.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(
    vm: MainViewModel,
    movieId: Long,
    onBack: () -> Unit,
    onChangePoster: (Long) -> Unit
) {
    val movie by vm.repo.observeMovie(movieId).collectAsState(initial = null)
    val m = movie
    if (m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    DetailsContent(vm, m, onBack, onChangePoster)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsContent(vm: MainViewModel, m: Movie, onBack: () -> Unit, onChangePoster: (Long) -> Unit) {
    val ctx = LocalContext.current
    val playlists by vm.playlists.collectAsState()
    val subtitleBusy by vm.subtitleBusy.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }

    val hasResume = m.positionMs > 5_000

    // Notes: saved shortly after typing stops, and when leaving the screen.
    var notes by remember(m.id) { mutableStateOf(m.notes) }
    val latestNotes by rememberUpdatedState(notes)
    val savedNotes by rememberUpdatedState(m.notes)
    LaunchedEffect(notes) {
        if (notes != m.notes) {
            delay(700)
            vm.setNotes(m.id, notes)
        }
    }
    DisposableEffect(m.id) {
        onDispose { if (latestNotes != savedNotes) vm.setNotes(m.id, latestNotes) }
    }

    // When the user picks a TV in the Cast dialog, start casting right away.
    val castCtx = remember { CastHelper.castContext(ctx) }
    DisposableEffect(castCtx, m.id) {
        val l = object : SimpleCastListener() {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                PlayerActivity.start(ctx, m.id, startOver = false)
            }
        }
        castCtx?.sessionManager?.addSessionManagerListener(l, CastSession::class.java)
        onDispose { castCtx?.sessionManager?.removeSessionManagerListener(l, CastSession::class.java) }
    }

    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSubtitle(m.id, uri)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Blurred poster backdrop
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            PosterImage(m, Modifier.fillMaxSize().blur(28.dp), ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), MaterialTheme.colorScheme.background)
                    )
                )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(64.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                PosterImage(
                    m,
                    Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .clip(MaterialTheme.shapes.large)
                )
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(m.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(m.year?.toString(), formatRuntime(m.runtimeMin)).joinToString(" • ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    StarRating(m.myRating, { vm.setRating(m.id, it) }, 24)
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { PlayerActivity.start(ctx, m.id, startOver = false) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(28.dp))
                Text(
                    if (hasResume) "Resume from ${formatMs(m.positionMs)}" else "Play",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (hasResume) {
                TextButton(
                    onClick = { PlayerActivity.start(ctx, m.id, startOver = true) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Start from beginning") }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChipButton(
                    if (m.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Favorite", m.favorite, Modifier.weight(1f)
                ) { vm.toggleFavorite(m) }
                ActionChipButton(
                    if (m.watched) Icons.Filled.Check else Icons.Outlined.CheckCircle,
                    "Watched", m.watched, Modifier.weight(1f)
                ) { vm.toggleWatched(m) }
                if (castCtx != null) {
                    ActionChipButton(Icons.Filled.Cast, "Cast", false, Modifier.weight(1f)) {
                        if (CastHelper.session(ctx) != null) {
                            PlayerActivity.start(ctx, m.id, startOver = false)
                        } else {
                            routeButton?.performClick()
                        }
                    }
                }
            }
            if (castCtx != null) {
                // Invisible MediaRouteButton: it opens Google's TV chooser when the Cast button above is tapped.
                AndroidView(
                    factory = { c ->
                        MediaRouteButton(c).also {
                            CastHelper.setUpButton(it)
                            routeButton = it
                        }
                    },
                    modifier = Modifier.size(1.dp).alpha(0f)
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { addToPlaylist = true }) {
                Icon(Icons.Outlined.PlaylistAdd, null)
                Text("Add to playlist", modifier = Modifier.padding(start = 8.dp))
            }

            // Overview
            SectionTitle("Overview")
            if (m.overview != null) {
                Text(m.overview, lineHeight = 22.sp)
            } else {
                Text("No summary yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.repo.tmdb.hasKey()) {
                    OutlinedButton(onClick = { vm.fetchInfo(m) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Fetch from TMDB")
                    }
                } else {
                    Text(
                        "Add a TMDB key in Settings to fetch summaries.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Notes
            SectionTitle("My notes")
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                placeholder = { Text("Write your own notes about this movie…") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )

            // Subtitles
            SectionTitle("Subtitles")
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Subtitles, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Arabic", fontWeight = FontWeight.SemiBold)
                            Text(
                                when (m.subtitleStatus) {
                                    1 -> "Arabic downloaded"
                                    2 -> "Arabic not found"
                                    3 -> "Subtitle file chosen"
                                    else -> "Not downloaded yet"
                                },
                                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.downloadSubtitle(m.id) }, enabled = !subtitleBusy) {
                            if (subtitleBusy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                            }
                            Text("Download", modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = { subPicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp))
                            Text("Choose file", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (m.subtitleFile != null) {
                        Text(
                            "Subtitle timing: ${formatOffset(m.subtitleOffsetMs)}. You can adjust it while playing (⚙ → Subtitle timing).",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                Text("Edit title / fix match / change poster", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                m.fileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
            )
        }

        // Floating back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape)
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
    }

    if (editing) {
        EditMovieDialog(vm, m, onDismiss = { editing = false }, onChangePoster = { editing = false; onChangePoster(m.id) })
    }
    if (addToPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { vm.addToPlaylist(it.id, m.id, it.name); addToPlaylist = false },
            onCreate = { vm.createPlaylist(it, m.id); addToPlaylist = false },
            onDismiss = { addToPlaylist = false }
        )
    }
}

private fun formatOffset(ms: Long): String =
    if (ms == 0L) "in sync" else "%+.1f s".format(ms / 1000.0)

@Composable
private fun ActionChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Edit title/year by hand, or search TMDB and pick the right movie (fixes wrong auto-matches).
 */
@Composable
fun EditMovieDialog(vm: MainViewModel, movie: Movie, onDismiss: () -> Unit, onChangePoster: () -> Unit) {
    var title by remember { mutableStateOf(movie.title) }
    var year by remember { mutableStateOf(movie.year?.toString() ?: "") }
    var results by remember { mutableStateOf<List<TmdbClient.SearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit movie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Title") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = !searching && title.isNotBlank(),
                        onClick = {
                            searching = true; error = null
                            scope.launch {
                                try {
                                    results = vm.searchTmdb(title.trim(), year.toIntOrNull())
                                } catch (e: Exception) {
                                    results = null
                                    error = e.message ?: "Search failed. Check your connection."
                                }
                                searching = false
                            }
                        }
                    ) { Text(if (searching) "Searching…" else "Search TMDB") }
                    OutlinedButton(onClick = onChangePoster) { Text("Change poster") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                results?.let { list ->
                    if (list.isEmpty()) {
                        Text("No results. Try a different spelling.", modifier = Modifier.padding(top = 8.dp))
                    }
                    list.take(8).forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.applyMatch(movie.id, r); onDismiss() }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (r.posterPath != null) {
                                AsyncImage(
                                    model = TmdbClient.imageUrl(r.posterPath, "w185"), contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(44.dp).height(66.dp).clip(MaterialTheme.shapes.extraSmall)
                                )
                            } else {
                                Box(
                                    Modifier.width(44.dp).height(66.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                                )
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(r.title, fontWeight = FontWeight.SemiBold)
                                Text(r.year?.toString() ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveManual(movie.id, title, year.toIntOrNull())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
