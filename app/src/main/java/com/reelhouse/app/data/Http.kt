package com.reelhouse.app.data

import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking HTTP helper. Always call from Dispatchers.IO. */
object Http {
    class Result(val code: Int, val body: ByteArray) {
        val ok: Boolean get() = code in 200..299
        val text: String get() = String(body, Charsets.UTF_8)
    }

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        timeoutMs: Int = 20_000
    ): Result {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return Result(code, bytes)
        } finally {
            conn.disconnect()
        }
    }
}
