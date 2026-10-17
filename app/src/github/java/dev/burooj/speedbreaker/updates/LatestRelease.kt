package dev.burooj.speedbreaker.updates

import org.json.JSONObject

/** A release number as Speedbreaker tags it: `vX.Y.Z`, compared field by field. */
internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d{1,6})\.(\d{1,6})\.(\d{1,6})""")

        /** `v1.0.4` or `1.0.4`; anything else (suffixes, missing parts) is not a release. */
        fun parse(text: String?): ReleaseVersion? {
            val match = text?.trim()?.let(PATTERN::matchEntire) ?: return null
            val (major, minor, patch) = match.destructured
            return ReleaseVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}

internal data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** The parts of GitHub's latest-release answer the updater uses. */
internal data class LatestRelease(
    val version: ReleaseVersion,
    val notes: String,
    /** `Speedbreaker-X.Y.Z.apk`, or null while the release has none. */
    val apk: ReleaseAsset?,
    /** `Speedbreaker-X.Y.Z.apk.sha256`, or null while the release has none. */
    val checksum: ReleaseAsset?,
)

internal const val LATEST_RELEASE_URL = "https://api.github.com/repos/beejsbj/speedbreaker/releases/latest"
private const val DOWNLOAD_PREFIX = "https://github.com/beejsbj/speedbreaker/releases/download/"
private const val MAX_NOTES = 4_000

/**
 * Reads GitHub's `releases/latest` JSON. Returns null for a draft, a prerelease, or a tag that
 * is not `vX.Y.Z`: none of those is offered. Assets count only under this repository's own
 * download path for the same tag, with Speedbreaker's exact file names.
 * Throws [org.json.JSONException] when the answer is not a release object at all.
 */
internal fun parseLatestRelease(json: String): LatestRelease? {
    val release = JSONObject(json)
    if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
    val tag = release.getString("tag_name")
    if (!tag.startsWith("v")) return null
    val version = ReleaseVersion.parse(tag) ?: return null
    val apkName = "Speedbreaker-$version.apk"
    val checksumName = "$apkName.sha256"
    val prefix = "$DOWNLOAD_PREFIX$tag/"
    val assets = release.optJSONArray("assets")
    var apk: ReleaseAsset? = null
    var checksum: ReleaseAsset? = null
    for (index in 0 until (assets?.length() ?: 0)) {
        val asset = assets!!.optJSONObject(index) ?: continue
        val name = asset.optString("name")
        val url = asset.optString("browser_download_url")
        val size = asset.optLong("size", -1L)
        if (url != prefix + name || size <= 0L || asset.optString("state", "uploaded") != "uploaded") continue
        when (name) {
            apkName -> apk = ReleaseAsset(name, url, size)
            checksumName -> checksum = ReleaseAsset(name, url, size)
        }
    }
    val notes = release.optString("body").replace("\r\n", "\n").trim().let {
        if (it.length > MAX_NOTES) it.take(MAX_NOTES).trimEnd() + "…" else it
    }
    return LatestRelease(version, notes, apk, checksum)
}
