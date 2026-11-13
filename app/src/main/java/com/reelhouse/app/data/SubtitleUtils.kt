package com.reelhouse.app.data

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

object SubtitleUtils {
    /**
     * Decodes subtitle bytes into text. Tries BOM -> strict UTF-8 -> Windows-1256 (Arabic) so Arabic
     * subtitles never show as garbage. The result is later saved as UTF-8.
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            // not UTF-8
        }
        val cs = try {
            Charset.forName("windows-1256")
        } catch (e: Exception) {
            Charsets.ISO_8859_1
        }
        return String(bytes, cs)
    }

    private val timeLine = Regex(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
    )

    private fun toMs(h: String, m: String, s: String, ms: String): Long =
        h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1000 + ms.padEnd(3, '0').toLong()

    private fun fmt(msIn: Long): String {
        val ms = if (msIn < 0) 0 else msIn
        return String.format(
            Locale.US, "%02d:%02d:%02d,%03d",
            ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000
        )
    }

    /** Shifts every cue by [offsetMs] (positive = later, negative = earlier). */
    fun shiftSrt(text: String, offsetMs: Long): String {
        if (offsetMs == 0L) return text
        return timeLine.replace(text) { m ->
            val g = m.groupValues
            val a = toMs(g[1], g[2], g[3], g[4]) + offsetMs
            val b = toMs(g[5], g[6], g[7], g[8]) + offsetMs
            "${fmt(a)} --> ${fmt(b)}"
        }
    }

    /** OpenSubtitles "moviehash": file size + checksum of first and last 64 KB. */
    fun movieHash(ctx: Context, uri: Uri): String? {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use { p ->
            FileInputStream(p.fileDescriptor).channel.use { ch ->
                val size = ch.size()
                val chunk = 65536
                if (size < chunk * 2L) {
                    null
                } else {
                    var hash = size
                    val buf = ByteBuffer.allocate(chunk).order(ByteOrder.LITTLE_ENDIAN)
                    for (pos in longArrayOf(0L, size - chunk)) {
                        buf.clear()
                        ch.position(pos)
                        while (buf.hasRemaining()) {
                            if (ch.read(buf) < 0) break
                        }
                        buf.flip()
                        val lb = buf.asLongBuffer()
                        while (lb.hasRemaining()) hash += lb.get()
                    }
                    String.format(Locale.US, "%016x", hash)
                }
            }
        }
    }
}
