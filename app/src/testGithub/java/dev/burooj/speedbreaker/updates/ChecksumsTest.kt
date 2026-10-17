package dev.burooj.speedbreaker.updates

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChecksumsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun file(content: String): File = folder.newFile().apply { writeText(content) }

    @Test
    fun sha256_matchesKnownDigest() {
        assertEquals(abc, sha256Hex(file("abc")))
    }

    @Test
    fun verification_acceptsOnlyTheExactPublishedDigest() {
        val apk = file("abc")
        assertTrue(matchesSha256(apk, abc))
        assertTrue(matchesSha256(apk, abc.uppercase()))
        assertFalse(matchesSha256(apk, abc.replaceFirst('b', 'c')))
        assertFalse(matchesSha256(file("abd"), abc))
        assertFalse(matchesSha256(apk, ""))
        assertFalse(matchesSha256(apk, abc.take(63)))
    }

    @Test
    fun checksumFile_readsSha256sumOutputForTheNamedApk() {
        val name = "Speedbreaker-1.0.4.apk"
        assertEquals(abc, parseChecksumFile("$abc  $name\n", name))
        assertEquals(abc, parseChecksumFile("$abc *$name", name))
        assertEquals(abc, parseChecksumFile(abc.uppercase(), name))
        assertNull(parseChecksumFile("$abc  Speedbreaker-1.0.3.apk\n", name))
        assertNull(parseChecksumFile("not a digest  $name", name))
        assertNull(parseChecksumFile("", name))
        assertNull(parseChecksumFile("$abc  $name extra", name))
    }
}
