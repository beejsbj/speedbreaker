package dev.burooj.speedbreaker.updates

import java.io.File
import java.security.MessageDigest

private val HEX_DIGEST = Regex("[0-9a-fA-F]{64}")

/**
 * The digest in a release's `.apk.sha256` file, written by `sha256sum` as
 * `<64 hex>  <file name>`. A named file must be [fileName]; a bare digest is accepted.
 */
internal fun parseChecksumFile(text: String, fileName: String): String? {
    val parts = text.trim().lines().firstOrNull()?.trim()?.split(Regex("\\s+")) ?: return null
    val digest = parts.firstOrNull()?.takeIf { HEX_DIGEST.matches(it) } ?: return null
    return when (parts.size) {
        1 -> digest.lowercase()
        2 -> if (parts[1].removePrefix("*") == fileName) digest.lowercase() else null
        else -> null
    }
}

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** True only when [file] hashes to the release's published digest. */
internal fun matchesSha256(file: File, expected: String): Boolean =
    HEX_DIGEST.matches(expected) && sha256Hex(file) == expected.lowercase()
