package dev.burooj.speedbreaker.observation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Choice
import dev.burooj.speedbreaker.enforcement.EnforcementEngine
import dev.burooj.speedbreaker.enforcement.EngineUpdate
import dev.burooj.speedbreaker.enforcement.Observation
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.model.Settings
import dev.burooj.speedbreaker.persistence.SpeedbreakerRepository
import dev.burooj.speedbreaker.presentation.RedirectDestination
import dev.burooj.speedbreaker.systemaction.SystemActionController
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class SpeedbreakerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SpeedbreakerRepository
    private lateinit var safetyPolicy: SafetyPolicy
    private lateinit var windowReader: WindowObservationReader
    private lateinit var overlay: AccessibilityOverlayHost
    private lateinit var notifications: PauseNotifications
    private lateinit var systemActions: SystemActionController

    private var engine: EnforcementEngine? = null
    private var enginePauses: Map<String, PauseState> = emptyMap()
    private var lastRepositoryPauses: Map<String, PauseState> = emptyMap()
    private var pauseWriteRunning = false
    private var pendingPauseWrite: Map<String, PauseState>? = null
    private val ownPauseWriteSnapshots = mutableListOf<Map<String, PauseState>>()
    private var navigationAwayLatched = false
    private val labelCache = mutableMapOf<String, String>()

    private val ticker = object : Runnable {
        override fun run() {
            reconcile()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50L
        }
        repository = SpeedbreakerRepository.get(this)
        safetyPolicy = SafetyPolicy(this)
        windowReader = WindowObservationReader(this)
        notifications = PauseNotifications(this)
        systemActions = SystemActionController(this)
        overlay = AccessibilityOverlayHost(
            service = this,
            onChoice = ::handleChoice,
            onBack = ::handleBack,
        )
        activeService = WeakReference(this)
        ServiceStatus.setConnected(true)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::repository.isInitialized) return
        val packageName = event.packageName?.toString()
        val windowTransition = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (overlay.isShowing && windowTransition && safetyPolicy.isNavigationPackage(packageName)) {
            navigationAwayLatched = true
        }
        val forceSafetyYield = overlay.isShowing && safetyPolicy.isImmediateYieldPackage(packageName)
        reconcile(forceSafetyYield = forceSafetyYield)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDownConnection()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDownConnection()
        scope.cancel()
        super.onDestroy()
    }

    private fun reconcile(
        navigationAway: Boolean = navigationAwayLatched,
        forceSafetyYield: Boolean = false,
    ): EngineUpdate? {
        if (!::repository.isInitialized) return null
        val persistenceError = repository.error.value
        if (!repository.ready.value || persistenceError != null) {
            suspendEnforcement(persistenceError)
            return null
        }

        val settings = safetyPolicy.filter(repository.settings.value)
        if (!settings.consentAccepted) {
            suspendEnforcement(null)
            return null
        }

        val storedPauses = repository.pauses.value
        if (engine == null) {
            engine = EnforcementEngine(storedPauses)
            enginePauses = storedPauses
            lastRepositoryPauses = storedPauses
        } else if (storedPauses != lastRepositoryPauses) {
            lastRepositoryPauses = storedPauses
            val acknowledgementIndex = ownPauseWriteSnapshots.indexOf(storedPauses)
            val ownAcknowledgement = acknowledgementIndex >= 0
            if (ownAcknowledgement) {
                repeat(acknowledgementIndex + 1) { ownPauseWriteSnapshots.removeAt(0) }
            }
            if (!ownAcknowledgement && storedPauses != enginePauses) {
                engine = EnforcementEngine(storedPauses)
                enginePauses = storedPauses
                overlay.dismiss()
            }
        }

        val observation = observation(navigationAway, forceSafetyYield)
        val currentEngine = engine ?: return null
        var update = currentEngine.update(settings, observation)
        if (!notifications.isAvailable()) {
            update.pauses
                .filterValues { it.untilEpochMs > observation.epochMs }
                .keys
                .sorted()
                .forEach { packageName ->
                    update = currentEngine.endPause(packageName, settings, observation)
                }
        }
        apply(update, settings, observation)
        if (update.breaker == null) navigationAwayLatched = false
        return update
    }

    private fun handleChoice(choice: Choice) {
        val currentEngine = engine ?: return
        if (repository.error.value != null || !repository.ready.value) {
            suspendEnforcement(repository.error.value)
            return
        }
        if (choice == Choice.Pause && !notifications.isAvailable()) return
        val settings = safetyPolicy.filter(repository.settings.value)
        val observation = observation(navigationAway = false, forceSafetyYield = false)
        apply(currentEngine.choose(choice, settings, observation), settings, observation)
    }

    private fun handleBack() {
        val wasShowing = overlay.isShowing
        val update = reconcile(navigationAway = true) ?: return
        if (wasShowing && update.breaker == null) {
            systemActions.performBack()
        }
    }

    private fun handleEndPause(packageName: String) {
        val currentEngine = engine ?: return
        if (!repository.ready.value || repository.error.value != null) return
        val settings = safetyPolicy.filter(repository.settings.value)
        val observation = observation(navigationAway = false, forceSafetyYield = false)
        apply(currentEngine.endPause(packageName, settings, observation), settings, observation)
    }

    private fun apply(update: EngineUpdate, settings: Settings, observation: Observation) {
        enginePauses = update.pauses
        persistPausesIfChanged(update.pauses)
        notifications.reconcile(update.pauses, observation.epochMs)
        update.effects.forEach(systemActions::execute)
        val breaker = update.breaker
        if (breaker == null) {
            overlay.dismiss()
        } else {
            overlay.showOrUpdate(overlayState(breaker, settings, update.pauses, observation))
        }
    }

    private fun persistPausesIfChanged(pauses: Map<String, PauseState>) {
        if (
            pauses == repository.pauses.value ||
            pauses == pendingPauseWrite ||
            pauses in ownPauseWriteSnapshots
        ) return
        pendingPauseWrite = pauses
        if (pauseWriteRunning) return
        pauseWriteRunning = true
        scope.launch {
            while (true) {
                val next = pendingPauseWrite ?: break
                pendingPauseWrite = null
                ownPauseWriteSnapshots += next
                repository.savePauses(next)
                val error = repository.error.value
                if (error != null) {
                    pauseWriteRunning = false
                    suspendEnforcement(error)
                    return@launch
                }
            }
            pauseWriteRunning = false
        }
    }

    private fun overlayState(
        breaker: Breaker,
        settings: Settings,
        pauses: Map<String, PauseState>,
        observation: Observation,
    ): OverlayState {
        val policy = settings.apps[breaker.packageName]
        val redirectPackages = (policy?.redirects ?: settings.redirects)
            .takeIf { it.size == REDIRECT_COUNT && it.toSet().size == REDIRECT_COUNT }
            .orEmpty()
        val today = Instant.ofEpochMilli(observation.epochMs)
            .atZone(observation.zoneId)
            .toLocalDate()
            .toString()
        val used = pauses[breaker.packageName]?.takeIf { it.day == today }?.used ?: 0
        return OverlayState(
            breaker = breaker,
            targetLabel = appLabel(breaker.packageName),
            nowElapsedMs = observation.elapsedMs,
            pauseTokensLeft = (PAUSES_PER_DAY - used).coerceIn(0, PAUSES_PER_DAY),
            pauseResetLabel = "resets tomorrow",
            pauseAvailable = notifications.isAvailable(),
            redirects = redirectPackages.map { packageName ->
                RedirectDestination(packageName, appLabel(packageName))
            },
        )
    }

    private fun observation(navigationAway: Boolean, forceSafetyYield: Boolean): Observation {
        val elapsedMs = SystemClock.elapsedRealtime()
        val visiblePackages = windowReader.visiblePackages(elapsedMs)
        val power = getSystemService(PowerManager::class.java)
        val interactive = power?.isInteractive == true
        return Observation(
            visiblePackages = visiblePackages,
            interactive = interactive,
            safetyUiVisible = forceSafetyYield || !interactive ||
                safetyPolicy.safetyUiVisible(visiblePackages),
            navigationAway = navigationAway,
            elapsedMs = elapsedMs,
            epochMs = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault(),
        )
    }

    private fun appLabel(packageName: String): String = labelCache.getOrPut(packageName) {
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun suspendEnforcement(error: String?) {
        engine = null
        enginePauses = emptyMap()
        pendingPauseWrite = null
        ownPauseWriteSnapshots.clear()
        navigationAwayLatched = false
        overlay.dismiss()
        repository.pauses.value.keys.forEach(notifications::cancel)
        notifications.cancelAll()
        ServiceStatus.reportError(error)
    }

    private fun tearDownConnection() {
        handler.removeCallbacks(ticker)
        if (::overlay.isInitialized) overlay.dismiss()
        engine = null
        enginePauses = emptyMap()
        navigationAwayLatched = false
        if (activeService.get() === this) activeService.clear()
        ServiceStatus.setConnected(false)
    }

    internal companion object {
        private const val TICK_MS = 250L
        private const val REDIRECT_COUNT = 4
        private const val PAUSES_PER_DAY = 2
        private var activeService = WeakReference<SpeedbreakerService>(null)

        fun requestEndPause(packageName: String): Boolean {
            val service = activeService.get() ?: return false
            if (
                service.engine == null ||
                !service.repository.ready.value ||
                service.repository.error.value != null
            ) return false
            service.handler.post { service.handleEndPause(packageName) }
            return true
        }
    }
}
