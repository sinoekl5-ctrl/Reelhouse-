package com.reelhouse.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelhouse.app.data.Prefs
import com.reelhouse.app.data.SubStyle
import com.reelhouse.app.ui.AppLock
import com.reelhouse.app.ui.MainViewModel

@Composable
private fun Header(text: String) {
    Text(
        text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp)
    )
}

@Composable
private fun OptionChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (k, label) ->
            FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onFolders: () -> Unit, onReview: () -> Unit) {
    val ctx = LocalContext.current
    val p: Prefs = vm.prefs
    val theme by p.themeMode.collectAsState()
    val lock by p.appLock.collectAsState()
    val font by p.subFont.collectAsState()
    val size by p.subSize.collectAsState()
    val color by p.subColor.collectAsState()
    val bg by p.subBg.collectAsState()
    val auto by p.autoSubs.collectAsState()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore(uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            // ---------------------------------------------------------------- Library
            Header("Library")
            ListItem(
                headlineContent = { Text("Folders") },
                supportingContent = { Text("Add, rescan or remove movie folders") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onFolders)
            )
            ListItem(
                headlineContent = { Text("Review matches") },
                supportingContent = { Text("Fix movies that were recognized wrongly") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onReview)
            )

            // ---------------------------------------------------------------- Appearance
            Header("Appearance")
            OptionChips(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), theme) { p.setThemeMode(it) }

            // ---------------------------------------------------------------- Security
            Header("Security")
            ListItem(
                headlineContent = { Text("App Lock") },
                supportingContent = { Text("Ask for your fingerprint when opening Reelhouse") },
                trailingContent = {
                    Switch(checked = lock, onCheckedChange = { want ->
                        if (want && !AppLock.available(ctx)) {
                            vm.say("Set up a fingerprint or screen lock in your phone's settings first.")
                        } else {
                            p.setAppLock(want)
                        }
                    })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // ---------------------------------------------------------------- Subtitles
            Header("Subtitles")
            ListItem(
                headlineContent = { Text("Default language") },
                supportingContent = { Text("Arabic") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ListItem(
                headlineContent = { Text("Download automatically") },
                supportingContent = { Text("Fetch the Arabic subtitle the first time you open a movie (needs an OpenSubtitles key)") },
                trailingContent = { Switch(checked = auto, onCheckedChange = { p.setAutoSubs(it) }) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            Label("Font")
            OptionChips(SubStyle.fonts, font) { p.setSubFont(it) }
            Label("Size")
            OptionChips(SubStyle.sizes, size) { p.setSubSize(it) }
            Label("Color")
            OptionChips(SubStyle.colors, color) { p.setSubColor(it) }
            Label("Background")
            OptionChips(SubStyle.backgrounds, bg) { p.setSubBg(it) }
            Box(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF3A3A3A), MaterialTheme.shapes.medium)
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "مرحباً بكم في Reelhouse",
                    fontSize = SubStyle.sizeSp(size).sp,
                    fontFamily = FontFamily(SubStyle.typeface(font)),
                    color = Color(SubStyle.fg(color)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color(SubStyle.bg(bg))).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // ---------------------------------------------------------------- Accounts / keys
            Header("Accounts / Keys")
            Text(
                "Reelhouse needs free keys to fetch posters and Arabic subtitles. Your keys are stored only on this phone.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            val tmdb by p.tmdbKey.collectAsState()
            val osKey by p.osKey.collectAsState()
            val osUser by p.osUser.collectAsState()
            val osPass by p.osPass.collectAsState()
            KeyField("TMDB API key", tmdb, { p.setTmdbKey(it) }, secret = true)
            Hint("Free at themoviedb.org → Settings → API. Either the \"API Key\" or the long \"API Read Access Token\" works.")
            KeyField("OpenSubtitles API key", osKey, { p.setOsKey(it) }, secret = true)
            Hint("Free at opensubtitles.com → Your profile → API consumers → create a consumer.")
            KeyField("OpenSubtitles username", osUser, { p.setOsUser(it) }, secret = false)
            KeyField("OpenSubtitles password", osPass, { p.setOsPass(it) }, secret = true)
            Hint("Username and password are needed to download subtitles. The free plan has a daily download limit.")

            // ---------------------------------------------------------------- Data
            Header("Data")
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { backupLauncher.launch("reelhouse-backup.zip") }) { Text("Backup") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) { Text("Restore") }
                OutlinedButton(onClick = { vm.clearCache() }) { Text("Clear cache") }
            }
            Hint("Backup saves your library, favorites, playlists, ratings, notes, resume positions, posters and subtitles. API keys are not included.")

            Text(
                "Reelhouse 1.0",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)
    )
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit, secret: Boolean) {
    var text by remember { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(text) { if (text != value) onChange(text) }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = {
            if (secret) {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show or hide")
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
    )
}
