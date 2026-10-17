package dev.burooj.speedbreaker.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    private fun v(text: String) = ReleaseVersion.parse(text)!!

    @Test
    fun parse_acceptsTagsAndVersionNames() {
        assertEquals(ReleaseVersion(1, 0, 4), v("v1.0.4"))
        assertEquals(ReleaseVersion(1, 0, 4), v("1.0.4"))
        assertEquals(ReleaseVersion(12, 3, 45), v(" v12.3.45 "))
    }

    @Test
    fun parse_rejectsAnythingThatIsNotAPlainRelease() {
        for (text in listOf(null, "", "1.0", "1.0.4.1", "v1.0.4-rc1", "1.0.4-debug", "vv1.0.4", "latest", "1.a.4")) {
            assertNull(text, ReleaseVersion.parse(text))
        }
    }

    @Test
    fun compare_isNumericPerField() {
        assertTrue(v("1.0.10") > v("1.0.9"))
        assertTrue(v("1.1.0") > v("1.0.99"))
        assertTrue(v("2.0.0") > v("1.9.9"))
        assertTrue(v("1.0.3") < v("1.0.4"))
        assertEquals(0, v("v1.0.3").compareTo(v("1.0.3")))
    }

    @Test
    fun toString_isTheVersionName() {
        assertEquals("1.0.4", v("v1.0.4").toString())
    }
}
