package com.reelhouse.app.data

import org.json.JSONObject
import java.net.URLEncoder

/**
 * OpenSubtitles.com REST API v1 client (Arabic only).
 * - Search needs only the API key.
 * - Download needs the API key and, for most accounts, a logged-in user (username + password -> token).
 */
class OpenSubtitlesClient(private val prefs: Prefs) {
    sealed class Search {
        data class Found(val fileId: Long) : Search()
        object None : Search()
        data class Error(val message: String) : Search()
    }

    sealed class Download {
        data class Ok(val bytes: ByteArray) : Download()
        data class Error(val message: String) : Download()
    }

    private var token: String? = null
    private var host = "api.opensubtitles.com"

    private fun headers(withToken: Boolean): Map<String, String> {
        val h = HashMap<String, String>()
        h["Api-Key"] = prefs.osKey.value
        h["User-Agent"] = "Reelhouse v1.0"
        h["Content-Type"] = "application/json"
        h["Accept"] = "application/json"
        if (withToken) token?.let { h["Authorization"] = "Bearer $it" }
        return h
    }

    fun search(hash: String?, tmdbId: Int?, title: String, year: Int?): Search {
        // 1) exact file hash, 2) TMDB id, 3) title + year
        val attempts = ArrayList<String>()
        if (!hash.isNullOrBlank()) attempts.add("languages=ar&moviehash=$hash")
        if (tmdbId != null) attempts.add("languages=ar&tmdb_id=$tmdbId")
        var q = "languages=ar&query=" + URLEncoder.encode(title.lowercase(), "UTF-8")
        if (year != null) q += "&year=$year"
        attempts.add(q)

        for (params in attempts) {
            val res = try {
                Http.request("https://api.opensubtitles.com/api/v1/subtitles?$params", headers = headers(false))
            } catch (e: Exception) {
                return Search.Error("No connection to OpenSubtitles.")
            }
            if (res.code == 401 || res.code == 403) return Search.Error("OpenSubtitles rejected the API key. Check it in Settings.")
            if (res.code == 429) return Search.Error("OpenSubtitles rate limit reached. Try again later.")
            if (!res.ok) continue
            val data = JSONObject(res.text).optJSONArray("data") ?: continue
            var bestId = -1L
            var bestScore = -1L
            for (i in 0 until data.length()) {
                val attr = data.getJSONObject(i).optJSONObject("attributes") ?: continue
                val files = attr.optJSONArray("files") ?: continue
                if (files.length() == 0) continue
                val fileId = files.getJSONObject(0).optLong("file_id", -1)
                if (fileId < 0) continue
                val hashMatch = attr.optBoolean("moviehash_match", false)
                val score = attr.optLong("download_count", 0) + if (hashMatch) 1_000_000_000L else 0L
                if (score > bestScore) {
                    bestScore = score; bestId = fileId
                }
            }
            if (bestId >= 0) return Search.Found(bestId)
        }
        return Search.None
    }

    private fun login(): String? {
        val user = prefs.osUser.value
        val pass = prefs.osPass.value
        if (user.isBlank() || pass.isBlank()) return null
        val body = JSONObject().put("username", user).put("password", pass).toString().toByteArray()
        val res = Http.request("https://api.opensubtitles.com/api/v1/login", "POST", headers(false), body)
        if (!res.ok) return null
        val o = JSONObject(res.text)
        o.optString("base_url", "").takeIf { it.isNotBlank() }?.let {
            host = it.removePrefix("https://").removePrefix("http://").trimEnd('/')
        }
        token = o.optString("token", "").takeIf { it.isNotBlank() }
        return token
    }

    fun download(fileId: Long): Download {
        try {
            if (token == null) login()
            var res = requestDownload(fileId)
            if (res.code == 401 && login() != null) res = requestDownload(fileId)
            when {
                res.code == 401 -> return Download.Error(
                    "OpenSubtitles needs a login to download. Add your OpenSubtitles username and password in Settings → Accounts / Keys."
                )
                res.code == 406 -> return Download.Error("Daily OpenSubtitles download limit reached. Try again tomorrow.")
                res.code == 429 -> return Download.Error("Too many requests. Try again in a moment.")
                !res.ok -> return Download.Error("OpenSubtitles error ${res.code}.")
            }
            val link = JSONObject(res.text).optString("link", "")
            if (link.isBlank()) return Download.Error("OpenSubtitles returned no download link.")
            val file = Http.request(link, timeoutMs = 30_000)
            if (!file.ok || file.body.isEmpty()) return Download.Error("Could not download the subtitle file.")
            return Download.Ok(file.body)
        } catch (e: Exception) {
            return Download.Error("No connection to OpenSubtitles.")
        }
    }

    private fun requestDownload(fileId: Long): Http.Result {
        val body = JSONObject().put("file_id", fileId).put("sub_format", "srt").toString().toByteArray()
        return Http.request("https://$host/api/v1/download", "POST", headers(true), body)
    }
}
