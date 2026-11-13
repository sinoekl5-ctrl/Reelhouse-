package com.reelhouse.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reelhouse.app.data.Http
import com.reelhouse.app.data.TmdbClient
import com.reelhouse.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private fun decodeBytes(bytes: ByteArray, maxDim: Int = 2200): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private fun decodeUri(ctx: android.content.Context, uri: Uri, maxDim: Int = 2200): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        // ImageDecoder also applies the photo's rotation (EXIF) for us.
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = max(info.size.width, info.size.height)
            if (big > maxDim) {
                val k = maxDim.toFloat() / big
                decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
        }
    }
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return decodeBytes(bytes, maxDim)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterEditorScreen(vm: MainViewModel, movieId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val movie by vm.repo.observeMovie(movieId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var suggestions by remember { mutableStateOf<List<String>?>(null) }
    var link by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val m = movie

    LaunchedEffect(m?.id) {
        if (m != null && suggestions == null) {
            suggestions = try {
                vm.posterSuggestions(m)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun load(block: suspend () -> Bitmap?) {
        loading = true; error = null
        scope.launch {
            val bmp = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                null
            }
            loading = false
            if (bmp == null) error = "Could not load that image." else bitmap = bmp
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load { decodeUri(ctx, uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (bitmap == null) "Change poster" else "Crop poster", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (bitmap != null) bitmap = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val bmp = bitmap
            if (bmp != null && m != null) {
                CropStep(
                    bmp = bmp, title = m.title, year = m.year?.toString().orEmpty(), saving = saving,
                    onSave = { out ->
                        saving = true
                        vm.saveCustomPoster(m.id, out) { saving = false; onBack() }
                    }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Suggestions") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Gallery") })
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Link") })
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                    when (tab) {
                        0 -> {
                            val list = suggestions
                            when {
                                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                list.isEmpty() -> EmptyState(
                                    "No suggestions",
                                    if (vm.repo.tmdb.hasKey()) "TMDB has no posters for this movie. Use Gallery or Link instead."
                                    else "Add your TMDB API key in Settings to get poster suggestions, or use Gallery / Link."
                                )
                                else -> LazyVerticalGrid(
                                    columns = GridCells.Adaptive(110.dp),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(list, key = { it }) { path ->
                                        AsyncImage(
                                            model = TmdbClient.imageUrl(path, "w342"), contentDescription = "Poster option",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(165.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable {
                                                    load {
                                                        val r = Http.request(TmdbClient.imageUrl(path, "w780"))
                                                        if (r.ok) decodeBytes(r.body) else null
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> EmptyState(
                            "Pick from your gallery",
                            "Choose any image on your phone, then crop it to a movie-poster shape.",
                            action = {
                                Button(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(Icons.Filled.PhotoLibrary, null)
                                    Text("Open gallery", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        )
                        else -> Column(Modifier.padding(20.dp)) {
                            Text("Paste a link to an image", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = link, onValueChange = { link = it },
                                label = { Text("https://…/poster.jpg") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Button(
                                onClick = {
                                    val u = link.trim()
                                    load {
                                        val r = Http.request(u)
                                        if (r.ok) decodeBytes(r.body) else null
                                    }
                                },
                                enabled = link.trim().startsWith("http"),
                                modifier = Modifier.padding(top = 12.dp)
                            ) { Text("Load image") }
                        }
                    }
                }
            }
            if (loading) {
                Box(
                    Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
    }
}

/** Crop with the real poster ratio (2:3): drag to move, pinch or use the slider to zoom. */
@Composable
private fun CropStep(bmp: Bitmap, title: String, year: String, saving: Boolean, onSave: (Bitmap) -> Unit) {
    val density = LocalDensity.current
    var zoom by remember(bmp) { mutableFloatStateOf(1f) }
    var offset by remember(bmp) { mutableStateOf(Offset.Zero) }
    val bw = bmp.width.toFloat()
    val bh = bmp.height.toFloat()
    val img = remember(bmp) { bmp.asImageBitmap() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameW = minOf(maxWidth - 48.dp, 300.dp)
        val frameH = frameW * 1.5f
        val fw = with(density) { frameW.toPx() }
        val fh = with(density) { frameH.toPx() }
        val base = max(fw / bw, fh / bh)

        fun clampOffset(o: Offset, z: Float): Offset {
            val total = base * z
            val mx = ((bw * total - fw) / 2f).coerceAtLeast(0f)
            val my = ((bh * total - fh) / 2f).coerceAtLeast(0f)
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }

        val eff = clampOffset(offset, zoom)

        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Drag to position, pinch to zoom", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Canvas(
                    Modifier
                        .size(frameW, frameH)
                        .clip(MaterialTheme.shapes.large)
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .pointerInput(bmp) {
                            detectTransformGestures { _, pan, gz, _ ->
                                val nz = (zoom * gz).coerceIn(1f, 6f)
                                offset = clampOffset(clampOffset(offset, zoom) + pan, nz)
                                zoom = nz
                            }
                        }
                ) {
                    val total = base * zoom
                    val left = size.width / 2f - bw * total / 2f + eff.x
                    val top = size.height / 2f - bh * total / 2f + eff.y
                    withTransform({
                        translate(left, top)
                        scale(total, total, pivot = Offset.Zero)
                    }) { drawImage(img) }
                }
            }

            Slider(
                value = zoom, onValueChange = { zoom = it; offset = clampOffset(offset, it) },
                valueRange = 1f..6f, modifier = Modifier.width(frameW).padding(top = 8.dp)
            )

            // Small preview exactly as it will look on a library card
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val pw = 64.dp
                Canvas(Modifier.size(pw, pw * 1.5f).clip(MaterialTheme.shapes.small)) {
                    val k = size.width / fw
                    val total = base * zoom * k
                    val left = size.width / 2f - bw * total / 2f + eff.x * k
                    val top = size.height / 2f - bh * total / 2f + eff.y * k
                    withTransform({
                        translate(left, top)
                        scale(total, total, pivot = Offset.Zero)
                    }) { drawImage(img) }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Preview", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(year, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Text("Reset") }
                Button(
                    enabled = !saving,
                    onClick = {
                        val outW = 600
                        val outH = 900
                        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        val c = AndroidCanvas(out)
                        val s = outW / fw
                        val total = base * zoom
                        val left = fw / 2f - bw * total / 2f + eff.x
                        val top = fh / 2f - bh * total / 2f + eff.y
                        c.scale(s, s)
                        c.translate(left, top)
                        c.scale(total, total)
                        c.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                        onSave(out)
                    }
                ) { Text(if (saving) "Saving…" else "Save poster") }
            }
        }
    }
}
