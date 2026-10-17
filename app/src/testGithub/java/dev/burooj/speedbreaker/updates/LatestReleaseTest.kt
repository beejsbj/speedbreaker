package dev.burooj.speedbreaker.updates

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestReleaseTest {
    /** GitHub's actual `releases/latest` answer for v1.0.3, trimmed to release and asset fields. */
    private val fixture: String =
        javaClass.getResource("/releases/latest-v1.0.3.json")!!.readText()

    private fun edited(edit: JSONObject.() -> Unit): String = JSONObject(fixture).apply(edit).toString()

    @Test
    fun publishedRelease_yieldsVersionNotesAndBothAssets() {
        val release = parseLatestRelease(fixture)!!

        assertEquals(ReleaseVersion(1, 0, 3), release.version)
        assertTrue(release.notes.startsWith("See how much you've breathed."))
        assertEquals(
            ReleaseAsset(
                "Speedbreaker-1.0.3.apk",
                "https://github.com/beejsbj/speedbreaker/releases/download/v1.0.3/Speedbreaker-1.0.3.apk",
                8_009_627L,
            ),
            release.apk,
        )
        assertEquals(
            "https://github.com/beejsbj/speedbreaker/releases/download/v1.0.3/Speedbreaker-1.0.3.apk.sha256",
            release.checksum!!.url,
        )
    }

    @Test
    fun draftsPrereleasesAndOtherTagsAreNeverOffered() {
        assertNull(parseLatestRelease(edited { put("draft", true) }))
        assertNull(parseLatestRelease(edited { put("prerelease", true) }))
        assertNull(parseLatestRelease(edited { put("tag_name", "1.0.3") }))
        assertNull(parseLatestRelease(edited { put("tag_name", "v1.0.4-rc1") }))
        assertNull(parseLatestRelease(edited { put("tag_name", "nightly") }))
    }

    @Test
    fun assetsOutsideThisRepositoryOrTagAreIgnored() {
        val elsewhere = edited {
            val assets = getJSONArray("assets")
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                asset.put(
                    "browser_download_url",
                    asset.getString("browser_download_url").replace("beejsbj/speedbreaker", "someone/else"),
                )
            }
        }
        val release = parseLatestRelease(elsewhere)!!
        assertNull(release.apk)
        assertNull(release.checksum)

        // A newer tag whose assets still carry the old file names offers nothing to install.
        val renamed = parseLatestRelease(edited { put("tag_name", "v1.0.4") })!!
        assertEquals(ReleaseVersion(1, 0, 4), renamed.version)
        assertNull(renamed.apk)
    }

    @Test
    fun unfinishedUploadsAreIgnored() {
        val uploading = edited {
            val assets = getJSONArray("assets")
            for (index in 0 until assets.length()) assets.getJSONObject(index).put("state", "starter")
        }
        assertNull(parseLatestRelease(uploading)!!.apk)
    }

    @Test
    fun longNotesAreCapped() {
        val release = parseLatestRelease(edited { put("body", "a".repeat(10_000)) })!!
        assertEquals(4_001, release.notes.length)
        assertTrue(release.notes.endsWith("…"))
    }

    @Test
    fun malformedAnswersThrow() {
        assertThrows(JSONException::class.java) { parseLatestRelease("<html>rate limited</html>") }
        assertThrows(JSONException::class.java) { parseLatestRelease("""{"message":"Not Found"}""") }
    }
}
