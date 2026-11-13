package com.reelhouse.app.ui

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reelhouse.app.ui.screens.DetailsScreen
import com.reelhouse.app.ui.screens.FavoritesScreen
import com.reelhouse.app.ui.screens.FoldersScreen
import com.reelhouse.app.ui.screens.LibraryScreen
import com.reelhouse.app.ui.screens.LockScreen
import com.reelhouse.app.ui.screens.PlaylistDetailScreen
import com.reelhouse.app.ui.screens.PlaylistsScreen
import com.reelhouse.app.ui.screens.PosterEditorScreen
import com.reelhouse.app.ui.screens.ReviewMatchesScreen
import com.reelhouse.app.ui.screens.SettingsScreen
import com.reelhouse.app.ui.screens.rememberFolderPicker
import com.reelhouse.app.ui.theme.ReelhouseTheme
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private var authInFlight = false

    private val credentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            authInFlight = false
            if (r.resultCode == Activity.RESULT_OK) locked = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = vm.prefs.appLock.value

        setContent {
            val themeMode by vm.prefs.themeMode.collectAsState()
            ReelhouseTheme(themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked) {
                        LaunchedEffect(Unit) {
                            delay(350)
                            fingerprint()
                        }
                        LockScreen(onFingerprint = { fingerprint() }, onPin = { phonePin() })
                    } else {
                        AppRoot(vm)
                    }
                }
            }
        }
    }

    private fun fingerprint() {
        if (authInFlight) return
        authInFlight = true
        AppLock.authenticate(
            this,
            onSuccess = { authInFlight = false; locked = false },
            onFallback = { authInFlight = false; phonePin() }
        )
    }

    private fun phonePin() {
        val intent = AppLock.credentialIntent(this)
        if (intent == null) {
            locked = false // the phone has no screen lock, nothing to ask for
        } else {
            authInFlight = true
            credentialLauncher.launch(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.skipNextRelock) {
            AppLock.skipNextRelock = false
            return
        }
        if (stoppedAt > 0 && !locked && vm.prefs.appLock.value &&
            SystemClock.elapsedRealtime() - stoppedAt > 20_000
        ) {
            locked = true
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }
}

private val bottomRoutes = listOf("library", "favorites", "playlists", "settings")

@androidx.compose.runtime.Composable
private fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scan by vm.scanState.collectAsState()
    val addFolder = rememberFolderPicker(vm)

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Announce when a scan finishes.
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(scan.running, scan.message) {
        if (wasRunning && !scan.running && scan.message.isNotBlank()) snackbar.showSnackbar(scan.message)
        wasRunning = scan.running
    }

    fun openDetails(id: Long) = nav.navigate("details/$id")
    fun openPoster(id: Long) = nav.navigate("poster/$id")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route in bottomRoutes) {
                NavigationBar {
                    bottomRoutes.forEach { r ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    when (r) {
                                        "library" -> Icons.Filled.VideoLibrary
                                        "favorites" -> Icons.Filled.Favorite
                                        "playlists" -> Icons.AutoMirrored.Filled.PlaylistPlay
                                        else -> Icons.Filled.Settings
                                    },
                                    null
                                )
                            },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (scan.running) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(scan.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (scan.total > 0) {
                        LinearProgressIndicator(
                            progress = { scan.done.toFloat() / scan.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
            NavHost(
                navController = nav,
                startDestination = "library",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 14 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 14 } }
            ) {
                composable("library") {
                    LibraryScreen(
                        vm, onOpen = ::openDetails, onChangePoster = ::openPoster,
                        onAddFolder = addFolder, onFolders = { nav.navigate("folders") }
                    )
                }
                composable("favorites") { FavoritesScreen(vm, ::openDetails, ::openPoster) }
                composable("playlists") { PlaylistsScreen(vm) { nav.navigate("playlist/$it") } }
                composable("settings") {
                    SettingsScreen(vm, onFolders = { nav.navigate("folders") }, onReview = { nav.navigate("review") })
                }
                composable("folders") {
                    FoldersScreen(vm, onBack = { nav.popBackStack() }, onReview = { nav.navigate("review") }, onAddFolder = addFolder)
                }
                composable("review") {
                    ReviewMatchesScreen(vm, onBack = { nav.popBackStack() }, onChangePoster = ::openPoster)
                }
                composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    DetailsScreen(vm, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onChangePoster = ::openPoster)
                }
                composable("poster/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    PosterEditorScreen(vm, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                }
                composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    PlaylistDetailScreen(vm, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onOpen = ::openDetails)
                }
            }
        }
    }
}
