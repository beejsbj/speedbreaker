package dev.burooj.speedbreaker.updates

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** GitHub answered, but not with something usable (an HTTP error, rate limit or oversized body). */
internal class GitHubResponseException(val code: Int, message: String) : IOException(message)

/**
 * Plain HTTPS GETs to GitHub with no account, token, cookie or identifier: the only header
 * Speedbreaker sets is `User-Agent: Speedbreaker/<version>`, which GitHub's API requires.
 * Callers run it on a background dispatcher.
 */
internal class GitHubClient(installedVersion: String) {
    private val userAgent = "Speedbreaker/$installedVersion"

    fun latestReleaseJson(): String = String(fetchBytes(LATEST_RELEASE_URL, MAX_JSON_BYTES), Charsets.UTF_8)

    fun text(url: String, maxBytes: Long): String = String(fetchBytes(url, maxBytes), Charsets.UTF_8)

    /** Streams [url] into [target], reporting bytes so far, and fails unless exactly [expectedSize] arrive. */
    suspend fun download(url: String, target: File, expectedSize: Long, onProgress: (Long) -> Unit) {
        require(expectedSize in 1..MAX_APK_BYTES) { "Unexpected update size" }
        val connection = open(url)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > expectedSize) throw GitHubResponseException(200, "Download is larger than announced")
                        output.write(buffer, 0, count)
                        onProgress(total)
                    }
                    if (total != expectedSize) throw IOException("Download ended early")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchBytes(url: String, maxBytes: Long): ByteArray {
        val connection = open(url)
        try {
            return connection.inputStream.use { it.readLimited(maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Updates use HTTPS only" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.useCaches = false
        // Release downloads redirect from github.com to GitHub's asset host, HTTPS to HTTPS.
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw GitHubResponseException(code, "GitHub answered $code")
        }
        return connection
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw GitHubResponseException(200, "GitHub's answer was too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_JSON_BYTES = 1L shl 20
        const val MAX_CHECKSUM_BYTES = 4L * 1024
        const val MAX_APK_BYTES = 128L shl 20
    }
}
