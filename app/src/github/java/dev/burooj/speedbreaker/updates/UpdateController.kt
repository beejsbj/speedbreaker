package dev.burooj.speedbreaker.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.burooj.speedbreaker.observation.ServiceStatus
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val installed: String) : UpdateState
    data class Available(val release: LatestRelease, val installed: String, val note: String? = null) : UpdateState
    data class NeedsInstallPermission(val release: LatestRelease, val installed: String) : UpdateState
    data class Downloading(val release: LatestRelease, val fraction: Float) : UpdateState
    data class Verifying(val release: LatestRelease) : UpdateState
    data class Installing(val release: LatestRelease) : UpdateState
    data class Failed(val message: String, val release: LatestRelease? = null, val installed: String = "") : UpdateState
}

/**
 * The GitHub build's updater, used only from the app UI. It checks when asked (or once a day
 * on opening, if the person turned that on), downloads the release APK to the app cache,
 * verifies its SHA-256 against the release's `.apk.sha256`, and hands it to PackageInstaller.
 * Nothing runs in the background and nothing starts, downloads or prompts during a breath.
 */
internal class UpdateController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheDir = File(context.cacheDir, "updates")
    private var job: Job? = null
    private var sessionId: Int? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, false))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    /** Android's install confirmation, held until the app UI can show it outside a breath. */
    private val _confirmation = MutableStateFlow<Intent?>(null)
    val confirmation: StateFlow<Intent?> = _confirmation.asStateFlow()

    // A previous process may have left a download behind (or installed it and been replaced).
    private val cleanup = scope.launch(Dispatchers.IO) { cacheDir.deleteRecursively() }

    init {
        scope.launch { ServiceStatus.breathShowing.collect { showing -> if (showing) stopForBreath() } }
    }

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _autoCheck.value = enabled
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Every time the app UI resumes: continue after Install unknown apps, or the daily check. */
    fun onAppResumed() {
        val current = _state.value
        if (current is UpdateState.NeedsInstallPermission) {
            if (canInstall()) update()
            return
        }
        if (!_autoCheck.value || current != UpdateState.Idle || ServiceStatus.breathShowing.value) return
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_AUTO_CHECK, null) == today) return
        prefs.edit().putString(KEY_LAST_AUTO_CHECK, today).apply()
        check(manual = false)
    }

    /** Asks GitHub for the latest release. An automatic check stays invisible unless it finds one. */
    fun check(manual: Boolean = true) {
        if (job?.isActive == true || _state.value is UpdateState.Installing) return
        if (ServiceStatus.breathShowing.value) return
        _state.value = if (manual) UpdateState.Checking else UpdateState.Idle
        job = scope.launch {
            val installed = installedVersion()
            val outcome = try {
                val release = withContext(Dispatchers.IO) {
                    parseLatestRelease(GitHubClient(installed).latestReleaseJson())
                }
                val current = ReleaseVersion.parse(installed)
                when {
                    release != null && current != null && release.version > current ->
                        UpdateState.Available(release, installed)
                    manual -> UpdateState.UpToDate(installed)
                    else -> UpdateState.Idle
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: GitHubResponseException) {
                if (manual) UpdateState.Failed(responseMessage(error)) else UpdateState.Idle
            } catch (_: IOException) {
                if (manual) UpdateState.Failed(UNREACHABLE) else UpdateState.Idle
            } catch (_: JSONException) {
                if (manual) UpdateState.Failed("GitHub’s answer couldn’t be read. Try again later.") else UpdateState.Idle
            }
            _state.value = outcome
        }
    }

    /** Download, verify and install the offered release, asking for Install unknown apps first if needed. */
    fun update() {
        val (release, installed) = offered(_state.value) ?: return
        if (job?.isActive == true || ServiceStatus.breathShowing.value) return
        val apk = release.apk
        val checksum = release.checksum
        if (apk == null || checksum == null) {
            _state.value = UpdateState.Failed(
                "The ${release.version} release has no APK on GitHub yet. Try again later.", release, installed,
            )
            return
        }
        if (!canInstall()) {
            _state.value = UpdateState.NeedsInstallPermission(release, installed)
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        job = scope.launch {
            val file = File(cacheDir, apk.name)
            try {
                cleanup.join()
                withContext(Dispatchers.IO) {
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs()
                    val client = GitHubClient(installed)
                    val expected = parseChecksumFile(client.text(checksum.url, GitHubClient.MAX_CHECKSUM_BYTES), apk.name)
                        ?: throw UpdateProblem("The release’s checksum file couldn’t be read. Try again later.")
                    var percent = 0
                    client.download(apk.url, file, apk.size) { bytes ->
                        val now = (bytes * 100 / apk.size).toInt()
                        if (now != percent) {
                            percent = now
                            _state.value = UpdateState.Downloading(release, now / 100f)
                        }
                    }
                    _state.value = UpdateState.Verifying(release)
                    if (!matchesSha256(file, expected)) {
                        throw UpdateProblem("The download didn’t match GitHub’s checksum, so it was deleted.")
                    }
                    checkArchive(file, release)
                    ensureActive()
                    _state.value = UpdateState.Installing(release)
                    sessionId = UpdateInstaller.install(context, file)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateProblem) {
                _state.value = UpdateState.Failed(error.message, release, installed)
            } catch (error: GitHubResponseException) {
                _state.value = UpdateState.Failed(responseMessage(error), release, installed)
            } catch (_: IOException) {
                _state.value = UpdateState.Failed(UNREACHABLE, release, installed)
            } catch (_: RuntimeException) {
                _state.value = UpdateState.Failed("Android couldn’t start the install. Try again later.", release, installed)
            } finally {
                // The install session holds its own copy; nothing stays in the cache.
                withContext(NonCancellable + Dispatchers.IO) { cacheDir.deleteRecursively() }
            }
        }
    }

    /** Stop a download or a pending install; the offer stays so Update can be tapped again. */
    fun cancel() {
        val release = releaseIn(_state.value)
        job?.cancel()
        sessionId?.let { UpdateInstaller.abandon(context, it) }
        sessionId = null
        _confirmation.value = null
        _state.value = release?.let { UpdateState.Available(it, installedVersion()) } ?: UpdateState.Idle
    }

    /** Close the card (Not now, OK). An update in progress is not dismissed. */
    fun dismiss() {
        if (job?.isActive == true || _state.value is UpdateState.Installing) return
        _state.value = UpdateState.Idle
    }

    fun confirmationShown() {
        _confirmation.value = null
    }

    fun onInstallStatus(status: Int, confirmation: Intent?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmation != null) {
                    _confirmation.value = confirmation
                } else {
                    sessionFinished(UpdateState.Failed("Android couldn’t show its installer. Try again later.", release, installedVersion()))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> sessionFinished(UpdateState.Idle)
            PackageInstaller.STATUS_FAILURE_ABORTED -> sessionFinished(
                release?.let { UpdateState.Available(it, installedVersion()) } ?: UpdateState.Idle,
            )
            PackageInstaller.STATUS_FAILURE_STORAGE -> sessionFinished(
                UpdateState.Failed("There isn’t enough space to install the update.", release, installedVersion()),
            )
            else -> sessionFinished(
                UpdateState.Failed("Android didn’t install the update. Your current version is unchanged.", release, installedVersion()),
            )
        }
    }

    private fun sessionFinished(next: UpdateState) {
        sessionId = null
        _confirmation.value = null
        _state.value = next
    }

    private fun stopForBreath() {
        when (val current = _state.value) {
            UpdateState.Idle, UpdateState.Checking -> {
                job?.cancel()
                _state.value = UpdateState.Idle
            }
            is UpdateState.Downloading, is UpdateState.Verifying -> {
                job?.cancel()
                _state.value = UpdateState.Available(
                    releaseIn(current)!!, installedVersion(), note = "Stopped for a breath. Tap Update to try again.",
                )
            }
            // A pending confirmation waits: the UI holds Android's prompt until the breath ends.
            else -> Unit
        }
    }

    private fun checkArchive(file: File, release: LatestRelease) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw UpdateProblem("The download isn’t an app Android can read, so it was deleted.")
        if (info.packageName != context.packageName || ReleaseVersion.parse(info.versionName) != release.version) {
            throw UpdateProblem("The download isn’t Speedbreaker ${release.version}, so it was deleted.")
        }
    }

    private fun installedVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    private fun offered(state: UpdateState): Pair<LatestRelease, String>? = when (state) {
        is UpdateState.Available -> state.release to state.installed
        is UpdateState.NeedsInstallPermission -> state.release to state.installed
        is UpdateState.Failed -> state.release?.let { it to state.installed.ifEmpty { installedVersion() } }
        else -> null
    }

    private fun releaseIn(state: UpdateState): LatestRelease? = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.NeedsInstallPermission -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Verifying -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }

    private fun responseMessage(error: GitHubResponseException): String = when (error.code) {
        403, 429 -> "GitHub is limiting requests right now. Try again later."
        else -> UNREACHABLE
    }

    private class UpdateProblem(override val message: String) : Exception(message)

    companion object {
        private const val PREFS = "updates"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_LAST_AUTO_CHECK = "last_auto_check_day"
        private const val UNREACHABLE = "Couldn’t reach GitHub. Try again later."

        @Volatile
        private var instance: UpdateController? = null

        fun get(context: Context): UpdateController =
            instance ?: synchronized(this) {
                instance ?: UpdateController(context.applicationContext).also { instance = it }
            }
    }
}
