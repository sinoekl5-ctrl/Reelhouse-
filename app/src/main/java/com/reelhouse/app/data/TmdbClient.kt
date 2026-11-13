package com.reelhouse.app.data

import org.json.JSONObject
import java.net.URLEncoder

class TmdbClient(private val prefs: Prefs) {
    data class SearchResult(
        val id: Int,
        val title: String,
        val year: Int?,
        val posterPath: String?,
        val overview: String?
    )

    data class Details(val overview: String?, val runtime: Int?, val posterPath: String?)

    fun hasKey(): Boolean = prefs.tmdbKey.value.isNotBlank()

    private fun get(path: String, params: String = ""): Http.Result {
        val key = prefs.tmdbKey.value
        val headers = HashMap<String, String>()
        headers["Accept"] = "application/json"
        var url = "https://api.themoviedb.org/3$path?$params"
        if (key.startsWith("eyJ")) {
            // v4 "API Read Access Token"
            headers["Authorization"] = "Bearer $key"
        } else {
            url += "&api_key=" + URLEncoder.encode(key, "UTF-8")
        }
        return Http.request(url, headers = headers)
    }

    /** Throws on network / key errors so callers can tell "no results" from "failed". */
    fun search(query: String, year: Int?): List<SearchResult> {
        if (!hasKey()) throw IllegalStateException("Add your TMDB API key in Settings → Accounts / Keys.")
        val q = URLEncoder.encode(query, "UTF-8")
        var params = "query=$q&include_adult=false&language=en-US"
        if (year != null) params += "&year=$year"
        val res = get("/search/movie", params)
        if (res.code == 401) throw IllegalStateException("TMDB rejected the API key (401). Check it in Settings.")
        if (!res.ok) throw IllegalStateException("TMDB error ${res.code}")
        val arr = JSONObject(res.text).optJSONArray("results") ?: return emptyList()
        val list = ArrayList<SearchResult>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val date = o.optString("release_date", "")
            list.add(
                SearchResult(
                    id = o.getInt("id"),
                    title = o.optString("title", ""),
                    year = date.take(4).toIntOrNull(),
                    posterPath = o.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" },
                    overview = o.optString("overview", "").takeIf { it.isNotBlank() }
                )
            )
        }
        return list
    }

    fun details(id: Int): Details? {
        val res = get("/movie/$id", "language=en-US")
        if (!res.ok) return null
        val o = JSONObject(res.text)
        return Details(
            overview = o.optString("overview", "").takeIf { it.isNotBlank() },
            runtime = if (o.isNull("runtime")) null else o.optInt("runtime").takeIf { it > 0 },
            posterPath = o.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /** All poster file paths TMDB has for a movie (English + language-neutral). */
    fun posters(id: Int): List<String> {
        val res = get("/movie/$id/images", "include_image_language=en,null")
        if (!res.ok) return emptyList()
        val arr = JSONObject(res.text).optJSONArray("posters") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i).optString("file_path", "")
            if (p.isNotBlank()) out.add(p)
            if (out.size >= 30) break
        }
        return out
    }

    companion object {
        fun imageUrl(path: String, size: String = "w500") = "https://image.tmdb.org/t/p/$size$path"
    }
}
