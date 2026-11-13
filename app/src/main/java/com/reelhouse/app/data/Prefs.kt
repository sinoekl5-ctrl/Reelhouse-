package com.reelhouse.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small SharedPreferences wrapper that exposes observable StateFlows for the UI. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("reelhouse_prefs", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = MutableStateFlow(sp.getString(key, def) ?: def)
    private fun bool(key: String, def: Boolean) = MutableStateFlow(sp.getBoolean(key, def))

    private val _theme = str("theme", "system")
    private val _appLock = bool("app_lock", false)
    private val _libraryGrid = bool("library_grid", true)
    private val _sort = str("sort", "recent")
    private val _subFont = str("sub_font", "sans")
    private val _subSize = str("sub_size", "medium")
    private val _subColor = str("sub_color", "white")
    private val _subBg = str("sub_bg", "dim")
    private val _autoSubs = bool("auto_subs", true)
    private val _tmdbKey = str("tmdb_key", "")
    private val _osKey = str("os_key", "")
    private val _osUser = str("os_user", "")
    private val _osPass = str("os_pass", "")

    /** "system" | "light" | "dark" */
    val themeMode: StateFlow<String> = _theme
    val appLock: StateFlow<Boolean> = _appLock
    val libraryGrid: StateFlow<Boolean> = _libraryGrid
    val sort: StateFlow<String> = _sort

    /** "sans" | "serif" | "mono" | "casual" */
    val subFont: StateFlow<String> = _subFont

    /** "small" | "medium" | "large" | "xlarge" */
    val subSize: StateFlow<String> = _subSize

    /** "white" | "yellow" | "cyan" | "green" */
    val subColor: StateFlow<String> = _subColor

    /** "none" | "dim" | "black" */
    val subBg: StateFlow<String> = _subBg
    val autoSubs: StateFlow<Boolean> = _autoSubs
    val tmdbKey: StateFlow<String> = _tmdbKey
    val osKey: StateFlow<String> = _osKey
    val osUser: StateFlow<String> = _osUser
    val osPass: StateFlow<String> = _osPass

    private fun putS(key: String, v: String, flow: MutableStateFlow<String>) {
        sp.edit().putString(key, v).apply(); flow.value = v
    }

    private fun putB(key: String, v: Boolean, flow: MutableStateFlow<Boolean>) {
        sp.edit().putBoolean(key, v).apply(); flow.value = v
    }

    fun setThemeMode(v: String) = putS("theme", v, _theme)
    fun setAppLock(v: Boolean) = putB("app_lock", v, _appLock)
    fun setLibraryGrid(v: Boolean) = putB("library_grid", v, _libraryGrid)
    fun setSort(v: String) = putS("sort", v, _sort)
    fun setSubFont(v: String) = putS("sub_font", v, _subFont)
    fun setSubSize(v: String) = putS("sub_size", v, _subSize)
    fun setSubColor(v: String) = putS("sub_color", v, _subColor)
    fun setSubBg(v: String) = putS("sub_bg", v, _subBg)
    fun setAutoSubs(v: Boolean) = putB("auto_subs", v, _autoSubs)
    fun setTmdbKey(v: String) = putS("tmdb_key", v.trim(), _tmdbKey)
    fun setOsKey(v: String) = putS("os_key", v.trim(), _osKey)
    fun setOsUser(v: String) = putS("os_user", v.trim(), _osUser)
    fun setOsPass(v: String) = putS("os_pass", v, _osPass)

    /** Monotonic counter used only to order "Continue Watching". Not a date. */
    @Synchronized
    fun nextSeq(): Int {
        val n = sp.getInt("resume_seq", 0) + 1
        sp.edit().putInt("resume_seq", n).apply()
        return n
    }
}
