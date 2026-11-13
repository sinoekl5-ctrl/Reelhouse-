package com.reelhouse.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reelhouse.app.data.Movie
import com.reelhouse.app.data.Playlist
import com.reelhouse.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onChangePoster: (Long) -> Unit) {
    val favs by vm.favorites.collectAsState()
    var menuMovie by remember { mutableStateOf<Movie?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Favorites", fontWeight = FontWeight.Bold) }) }
    ) { pad ->
        if (favs.isEmpty()) {
            EmptyState(
                "No favorites yet",
                "Tap the heart on a movie, or long-press a poster and choose Favorite.",
                Modifier.padding(pad)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favs, key = { it.id }) { m ->
                    PosterCard(m, onClick = { onOpen(m.id) }, onLongClick = { menuMovie = m })
                }
            }
        }
    }
    menuMovie?.let { m ->
        MovieActionsSheet(m, vm, { menuMovie = null }, { menuMovie = null; onChangePoster(m.id) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(vm: MainViewModel, onOpenPlaylist: (Long) -> Unit) {
    val playlists by vm.playlists.collectAsState()
    val entries by vm.entries.collectAsState()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlists", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New playlist") }
        }
    ) { pad ->
        if (playlists.isEmpty()) {
            EmptyState(
                "No playlists yet",
                "Create a playlist such as \"Weekend Movie Night\" and add movies from their details page or the long-press menu.",
                Modifier.padding(pad)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
                items(playlists, key = { it.id }) { p ->
                    val n = entries.count { it.playlistId == p.id }
                    ListItem(
                        headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(if (n == 1) "1 movie" else "$n movies") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { onOpenPlaylist(p.id) }
                    )
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            "New playlist", label = "Playlist name", confirmText = "Create",
            onDismiss = { creating = false },
            onConfirm = { vm.createPlaylist(it); creating = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    vm: MainViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val playlists by vm.playlists.collectAsState()
    val entries by vm.entries.collectAsState()
    val all by vm.allMovies.collectAsState()
    val playlist: Playlist? = playlists.firstOrNull { it.id == playlistId }
    val ids = entries.filter { it.playlistId == playlistId }.sortedBy { it.position }.map { it.movieId }
    val movies = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }

    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; rename = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menu = false; confirmDelete = true }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        if (movies.isEmpty()) {
            EmptyState(
                "This playlist is empty",
                "Open a movie and choose \"Add to playlist\".",
                Modifier.padding(pad)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(movies, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            MovieListRow(m, onClick = { onOpen(m.id) }, onLongClick = {})
                        }
                        IconButton(onClick = { vm.removeFromPlaylist(playlistId, m.id) }) {
                            Icon(Icons.Filled.Close, "Remove from playlist")
                        }
                    }
                }
            }
        }
    }

    if (rename && playlist != null) {
        TextInputDialog(
            "Rename playlist", initial = playlist.name, label = "Playlist name",
            onDismiss = { rename = false },
            onConfirm = { vm.renamePlaylist(playlistId, it); rename = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The playlist will be deleted. Your movies stay in the library.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(playlistId); onBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
