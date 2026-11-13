package com.reelhouse.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import com.reelhouse.app.data.FolderEntity
import com.reelhouse.app.data.Movie
import com.reelhouse.app.ui.MainViewModel

/** Returns a function that opens Android's folder picker and adds the chosen folder to the library. */
@Composable
fun rememberFolderPicker(vm: MainViewModel): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Permission may already be persisted.
            }
            vm.addFolder(uri)
        }
    }
    return { launcher.launch(null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onAddFolder: () -> Unit
) {
    val folders by vm.folders.collectAsState()
    val movies by vm.allMovies.collectAsState()
    val scan by vm.scanState.collectAsState()
    var removing by remember { mutableStateOf<FolderEntity?>(null) }
    val needFix = movies.count { it.matchStatus == 2 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.rescanAll() }, enabled = !scan.running) {
                        Icon(Icons.Filled.Refresh, "Rescan all")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFolder,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add folder") }
            )
        }
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding() + 4.dp, bottom = 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Card(
                    onClick = onReview,
                    colors = CardDefaults.cardColors(
                        containerColor = if (needFix > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (needFix > 0) Icons.Filled.Warning else Icons.Filled.Check, null)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("Review matches", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (needFix > 0) "$needFix movies need a manual fix" else "See how each movie was recognized",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            if (folders.isEmpty()) {
                item {
                    EmptyState(
                        "No folders yet",
                        "Add every folder that holds movies. Reelhouse scans them (including sub-folders) for video files.",
                        Modifier.padding(top = 24.dp)
                    )
                }
            }
            items(folders, key = { it.id }) { f ->
                val n = movies.count { it.folderId == f.id }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (n == 1) "1 movie" else "$n movies",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                )
                            }
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.rescan(f.id) }, enabled = !scan.running) { Text("Rescan") }
                            TextButton(onClick = { removing = f }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove folder?") },
            text = { Text("\"${f.name}\" and its movies will be removed from the library. Your files are not deleted.") },
            confirmButton = { TextButton(onClick = { vm.removeFolder(f.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewMatchesScreen(vm: MainViewModel, onBack: () -> Unit, onChangePoster: (Long) -> Unit) {
    val movies by vm.allMovies.collectAsState()
    val scan by vm.scanState.collectAsState()
    val needFix = movies.filter { it.matchStatus == 2 || it.matchStatus == 0 }.sortedBy { it.title.lowercase() }
    val good = movies.filter { it.matchStatus == 1 || it.matchStatus == 3 }.sortedBy { it.title.lowercase() }
    var editing by remember { mutableStateOf<Movie?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review matches", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.rematchPending() }, enabled = !scan.running) {
                        Icon(Icons.Filled.Refresh, "Try matching again")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SectionTitle("Needs fixing (${needFix.size})", Modifier.padding(horizontal = 16.dp))
                if (needFix.isEmpty()) {
                    Text(
                        "Everything was recognized.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(needFix, key = { "n${it.id}" }) { m -> ReviewRow(m) { editing = m } }
            item { SectionTitle("Recognized (${good.size})", Modifier.padding(horizontal = 16.dp)) }
            items(good, key = { "g${it.id}" }) { m -> ReviewRow(m) { editing = m } }
        }
    }

    editing?.let { m ->
        EditMovieDialog(
            vm = vm, movie = m, onDismiss = { editing = null },
            onChangePoster = { editing = null; onChangePoster(m.id) }
        )
    }
}

@Composable
private fun ReviewRow(m: Movie, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(m.title + (m.year?.let { " ($it)" } ?: "")) },
        supportingContent = { Text(m.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { PosterImage(m, Modifier.width(40.dp).height(60.dp)) },
        trailingContent = {
            if (m.matchStatus == 2 || m.matchStatus == 0) Icon(Icons.Filled.Warning, "Needs fixing", tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Filled.Check, "Matched", tint = androidx.compose.ui.graphics.Color(0xFF2E7D32))
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
