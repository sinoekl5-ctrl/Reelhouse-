package com.reelhouse.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.reelhouse.app.data.Movie
import com.reelhouse.app.player.PlayerActivity
import com.reelhouse.app.ui.Filters
import com.reelhouse.app.ui.MainViewModel

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 18.dp, bottom = 10.dp)
    )
}

@Composable
fun HorizontalPosterRow(
    title: String,
    movies: List<Movie>,
    progress: Boolean,
    onOpen: (Movie) -> Unit,
    onMenu: (Movie) -> Unit
) {
    Column {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies, key = { it.id }) { m ->
                PosterCard(
                    m, Modifier.width(118.dp), showProgress = progress,
                    onClick = { onOpen(m) }, onLongClick = { onMenu(m) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpen: (Long) -> Unit,
    onChangePoster: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onFolders: () -> Unit
) {
    val library by vm.library.collectAsState()
    val all by vm.allMovies.collectAsState()
    val cont by vm.continueWatching.collectAsState()
    val recent by vm.recentlyAdded.collectAsState()
    val query by vm.query.collectAsState()
    val filters by vm.filters.collectAsState()
    val grid by vm.prefs.libraryGrid.collectAsState()
    val sort by vm.prefs.sort.collectAsState()
    val years by vm.years.collectAsState()
    val folders by vm.folders.collectAsState()

    var searching by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var menuMovie by remember { mutableStateOf<Movie?>(null) }
    val focus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { vm.query.value = it },
                            placeholder = { Text("Search movies") }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else {
                        Text("Reelhouse", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) vm.query.value = ""
                        searching = !searching
                    }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search")
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Filled.Tune, "Filters",
                            tint = if (filters.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { vm.prefs.setLibraryGrid(!grid) }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            if (grid) "List view" else "Grid view"
                        )
                    }
                    IconButton(onClick = onAddFolder) { Icon(Icons.Filled.Add, "Add folder") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (showFilters) {
                FilterPanel(
                    filters = filters, years = years,
                    folders = folders.map { it.id to it.name },
                    sort = sort,
                    onFilters = { vm.filters.value = it },
                    onSort = { vm.prefs.setSort(it) }
                )
            }
            if (all.isEmpty()) {
                EmptyState(
                    "Your library is empty",
                    "Add one or more folders that contain your movies and Reelhouse will build your library.",
                    action = {
                        androidx.compose.material3.Button(onClick = onAddFolder) {
                            Icon(Icons.Filled.Add, null)
                            Text("Add movie folder", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                )
            } else {
                val showRows = query.isBlank() && !filters.active
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cols = if (maxWidth < 600.dp) 2 else 3
                    if (grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (showRows && cont.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    HorizontalPosterRow("Continue Watching", cont, true, { onOpen(it.id) }, { menuMovie = it })
                                }
                            }
                            if (showRows && recent.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    HorizontalPosterRow("Recently Added", recent, false, { onOpen(it.id) }, { menuMovie = it })
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(if (showRows) "All Movies" else "Results (${library.size})")
                            }
                            if (library.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text("No movies match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(library, key = { it.id }) { m ->
                                PosterCard(m, onClick = { onOpen(m.id) }, onLongClick = { menuMovie = m })
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (showRows && cont.isNotEmpty()) {
                                item { HorizontalPosterRow("Continue Watching", cont, true, { onOpen(it.id) }, { menuMovie = it }) }
                            }
                            if (showRows && recent.isNotEmpty()) {
                                item { HorizontalPosterRow("Recently Added", recent, false, { onOpen(it.id) }, { menuMovie = it }) }
                            }
                            item { SectionTitle(if (showRows) "All Movies" else "Results (${library.size})") }
                            if (library.isEmpty()) {
                                item { Text("No movies match.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            items(library, key = { it.id }) { m ->
                                MovieListRow(m, onClick = { onOpen(m.id) }, onLongClick = { menuMovie = m })
                            }
                        }
                    }
                }
            }
        }
    }

    menuMovie?.let { m ->
        MovieActionsSheet(
            movie = m, vm = vm,
            onDismiss = { menuMovie = null },
            onChangePoster = { menuMovie = null; onChangePoster(m.id) }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MovieListRow(m: Movie, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImage(m, Modifier.width(60.dp).height(90.dp).clip(MaterialTheme.shapes.small))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(m.year?.toString(), formatRuntime(m.runtimeMin)).joinToString(" • "),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (m.myRating > 0) StarRating(m.myRating, null, 14)
        }
        if (m.favorite) Icon(Icons.Filled.Favorite, "Favorite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        if (m.watched) Icon(
            Icons.Filled.Check, "Watched", tint = androidx.compose.ui.graphics.Color(0xFF2E7D32),
            modifier = Modifier.padding(start = 8.dp, end = 4.dp).size(22.dp)
        )
    }
}

@Composable
private fun FilterPanel(
    filters: Filters,
    years: List<Int>,
    folders: List<Pair<Long, String>>,
    sort: String,
    onFilters: (Filters) -> Unit,
    onSort: (String) -> Unit
) {
    var yearMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filters.watched == true,
                onClick = { onFilters(filters.copy(watched = if (filters.watched == true) null else true)) },
                label = { Text("Watched") }
            )
            FilterChip(
                selected = filters.watched == false,
                onClick = { onFilters(filters.copy(watched = if (filters.watched == false) null else false)) },
                label = { Text("Unwatched") }
            )
            FilterChip(
                selected = filters.favorites,
                onClick = { onFilters(filters.copy(favorites = !filters.favorites)) },
                label = { Text("Favorites") }
            )
            Box {
                FilterChip(
                    selected = filters.year != null,
                    onClick = { yearMenu = true },
                    label = { Text(filters.year?.toString() ?: "Year") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
                )
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    DropdownMenuItem(text = { Text("Any year") }, onClick = { onFilters(filters.copy(year = null)); yearMenu = false })
                    years.forEach { y ->
                        DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onFilters(filters.copy(year = y)); yearMenu = false })
                    }
                }
            }
            Box {
                FilterChip(
                    selected = filters.folderId != null,
                    onClick = { folderMenu = true },
                    label = { Text(folders.firstOrNull { it.first == filters.folderId }?.second ?: "Folder") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
                )
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    DropdownMenuItem(text = { Text("All folders") }, onClick = { onFilters(filters.copy(folderId = null)); folderMenu = false })
                    folders.forEach { (id, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { onFilters(filters.copy(folderId = id)); folderMenu = false })
                    }
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort by", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(
                        when (sort) {
                            "title" -> "Title"; "year" -> "Year"; "rating" -> "My Rating"; else -> "Recently Added"
                        }
                    )
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("recent" to "Recently Added", "title" to "Title", "year" to "Year", "rating" to "My Rating").forEach { (k, v) ->
                        DropdownMenuItem(text = { Text(v) }, onClick = { onSort(k); sortMenu = false })
                    }
                }
            }
            if (filters.active) {
                TextButton(onClick = { onFilters(Filters()) }) { Text("Clear filters") }
            }
        }
    }
}

/** Long-press menu: Play, Favorite, Add to playlist, Change Poster, Remove from Library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieActionsSheet(
    movie: Movie,
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onChangePoster: () -> Unit
) {
    val ctx = LocalContext.current
    val playlists by vm.playlists.collectAsState()
    var addToPlaylist by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (addToPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { vm.addToPlaylist(it.id, movie.id, it.name); onDismiss() },
            onCreate = { vm.createPlaylist(it, movie.id); onDismiss() },
            onDismiss = { addToPlaylist = false }
        )
    }
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove from library?") },
            text = { Text("\"${movie.title}\" will be removed from Reelhouse. The video file on your phone is not deleted.") },
            confirmButton = {
                TextButton(onClick = { vm.removeFromLibrary(movie.id); confirmRemove = false; onDismiss() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            movie.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        SheetItem(Icons.Filled.PlayArrow, if (movie.positionMs > 5_000) "Resume" else "Play") {
            onDismiss(); PlayerActivity.start(ctx, movie.id)
        }
        SheetItem(
            if (movie.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (movie.favorite) "Remove from Favorites" else "Favorite"
        ) { vm.toggleFavorite(movie); onDismiss() }
        SheetItem(Icons.Outlined.PlaylistAdd, "Add to playlist") { addToPlaylist = true }
        SheetItem(Icons.Outlined.Image, "Change Poster") { onChangePoster() }
        SheetItem(Icons.Outlined.Delete, "Remove from Library") { confirmRemove = true }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickableSimple(onClick)
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
