package com.reelhouse.app.data

import java.text.Normalizer

/**
 * Turns file names such as "The.Matrix.1999.1080p.BluRay.x264-GRP.mp4" or "Inception (2010).mp4"
 * into a clean title and a year. Handles both tidy and messy names.
 */
object FilenameParser {
    data class Parsed(val title: String, val year: Int?)

    private val junk = Regex(
        "^(\\d{3,4}[pi]|[hx]\\.?26[45]|hevc|avc|bluray|blu-ray|brrip|bdrip|bdremux|remux|web-?dl|webrip|web|hdrip|hdtv|" +
            "dvdrip|dvdscr|hdcam|aac\\d*|ac3|eac3|dts.*|dd[p+]?\\d.*|atmos|10bit|8bit|hdr\\d*|uhd|4k|imax|proper|repack|" +
            "extended|unrated|remastered|multi|dual|yify|yts.*|rarbg|amzn|hmax|dsnp|s\\d{1,2}e\\d{1,2})$",
        RegexOption.IGNORE_CASE
    )
    private val yearRegex = Regex("^(19|20)\\d{2}$")

    fun parse(fileName: String): Parsed {
        var name = fileName.substringBeforeLast('.', fileName)
        name = name.replace(Regex("\\[[^\\]]*\\]"), " ")
        name = name.replace(Regex("\\((\\d{4})\\)"), " $1 ")
        name = name.replace(Regex("\\([^)]*\\)"), " ")
        name = name.replace('.', ' ').replace('_', ' ')
        name = name.replace(Regex("\\s+-\\s+"), " ")
        val tokens = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Parsed(fileName.substringBeforeLast('.'), null)

        var cut = tokens.indexOfFirst { junk.matches(it.trimEnd('-').substringBefore('-')) || junk.matches(it) }
        if (cut == 0) cut = tokens.size
        if (cut < 0) cut = tokens.size

        var yearIdx = -1
        for (i in 1 until cut) {
            if (yearRegex.matches(tokens[i])) yearIdx = i
        }
        val titleTokens: List<String>
        var year: Int? = null
        if (yearIdx >= 1) {
            titleTokens = tokens.subList(0, yearIdx)
            year = tokens[yearIdx].toInt()
        } else {
            titleTokens = tokens.subList(0, cut)
        }
        var title = titleTokens.joinToString(" ").trim().trim('-', ' ')
        if (title.isBlank()) title = fileName.substringBeforeLast('.')
        return Parsed(title, year)
    }

    /** Lower-case, accent-free, letters+digits only — used to compare titles. */
    fun normalize(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return n.lowercase().replace("&", "and").replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), "")
    }
}
