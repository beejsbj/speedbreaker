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
    private data class PauseWrite(
        val base: Map<String, PauseState>,
        val desired: Map<String, PauseState>,
    )

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
    private var pendingPauseWrite: PauseWrite? = null
    private val ownPauseWriteSnapshots = mutableListOf<Map<String, PauseState>>()
    private val expectedPauseEnds = mutableSetOf<String>()
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

        val currentObservation = observation(navigationAway, forceSafetyYield)
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
            val appliedExpectedEnd = if (!ownAcknowledgement) {
                applyExpectedPauseEnds(
                    current = storedPauses,
                    settings = settings,
                    observation = currentObservation,
                )
            } else {
                false
            }
            if (!ownAcknowledgement && !appliedExpectedEnd && storedPauses != enginePauses) {
                engine = EnforcementEngine(storedPauses)
                enginePauses = storedPauses
                overlay.dismiss()
            }
        }

        val currentEngine = engine ?: return null
        var update = currentEngine.update(settings, currentObservation)
        if (!notifications.isAvailable()) {
            update.pauses
                .filterValues { it.untilEpochMs > currentObservation.epochMs }
                .keys
                .sorted()
                .forEach { packageName ->
                    update = currentEngine.endPause(packageName, settings, currentObservation)
                }
        }
        apply(update, settings, currentObservation)
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
        expectedPauseEnds -= packageName
        val currentEngine = engine ?: return
        if (!repository.ready.value || repository.error.value != null) return
        val settings = safetyPolicy.filter(repository.settings.value)
        val observation = observation(navigationAway = false, forceSafetyYield = false)
        apply(currentEngine.endPause(packageName, settings, observation), settings, observation)
    }

    private fun applyExpectedPauseEnds(
        current: Map<String, PauseState>,
        settings: Settings,
        observation: Observation,
    ): Boolean {
        val currentEngine = engine ?: return false
        val endedPackages = expectedPauseEnds
            .filter { packageName ->
                // The repository may coalesce a newly persisted grant and its End now
                // transaction before the service observes either snapshot. The engine's
                // pause generation is the authoritative pre-End state in that case.
                val before = enginePauses[packageName]
                val after = current[packageName]
                before != null &&
                    after != null &&
                    before.day == after.day &&
                    before.used == after.used &&
                    before.untilEpochMs > 0L &&
                    after.untilEpochMs == 0L
            }
            .sorted()
        if (endedPackages.isEmpty()) return false
        endedPackages.forEach { packageName ->
            val update = currentEngine.endPause(packageName, settings, observation)
            enginePauses = update.pauses
            expectedPauseEnds -= packageName
        }
        return true
    }

    private fun apply(update: EngineUpdate, settings: Settings, observation: Observation) {
        val pauseWriteBase = enginePauses
        enginePauses = update.pauses
        persistPausesIfChanged(pauseWriteBase, update.pauses)
        notifications.reconcile(repository.pauses.value, observation.epochMs)
        update.effects.forEach(systemActions::execute)
        val breaker = update.breaker
        if (breaker == null) {
            overlay.dismiss()
        } else {
            overlay.showOrUpdate(overlayState(breaker, settings, update.pauses, observation))
        }
    }

    private fun persistPausesIfChanged(
        base: Map<String, PauseState>,
        desired: Map<String, PauseState>,
    ) {
        if (
            desired == repository.pauses.value ||
            desired == pendingPauseWrite?.desired ||
            desired in ownPauseWriteSnapshots
        ) return
        pendingPauseWrite = PauseWrite(
            base = pendingPauseWrite?.base ?: base,
            desired = desired,
        )
        if (pauseWriteRunning) return
        pauseWriteRunning = true
        scope.launch {
            while (true) {
                val next = pendingPauseWrite ?: break
                pendingPauseWrite = null
                val persisted = PausePersistenceCoordinator.withLock {
                    mergePauseDelta(
                        base = next.base,
                        desired = next.desired,
                        current = repository.pauses.value,
                    ).also { merged ->
                        if (merged != repository.pauses.value) {
                            ownPauseWriteSnapshots += merged
                            repository.savePauses(merged)
                        }
                    }
                }
                val error = repository.error.value
                if (error != null) {
                    pauseWriteRunning = false
                    suspendEnforcement(error)
                    return@launch
                }
                if (persisted == repository.pauses.value) {
                    notifications.reconcile(persisted, System.currentTimeMillis())
                }
            }
            pauseWriteRunning = false
        }
    }

    private fun mergePauseDelta(
        base: Map<String, PauseState>,
        desired: Map<String, PauseState>,
        current: Map<String, PauseState>,
    ): Map<String, PauseState> = current.toMutableMap().apply {
        (base.keys + desired.keys).forEach { packageName ->
            val before = base[packageName]
            val after = desired[packageName]
            if (before == after) return@forEach
            when {
                after == null -> remove(packageName)
                before == null -> put(packageName, after)
                else -> {
                    val latest = current[packageName]
                    put(
                        packageName,
                        PauseState(
                            day = if (after.day != before.day) after.day else latest?.day ?: after.day,
                            used = if (after.used != before.used) after.used else latest?.used ?: after.used,
                            untilEpochMs = if (after.untilEpochMs != before.untilEpochMs) {
                                after.untilEpochMs
                            } else {
                                latest?.untilEpochMs ?: after.untilEpochMs
                            },
                        ),
                    )
                }
            }
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
        expectedPauseEnds.clear()
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
        expectedPauseEnds.clear()
        navigationAwayLatched = false
        if (activeService.get() === this) activeService.clear()
        ServiceStatus.setConnected(false)
    }

    internal companion object {
        private const val TICK_MS = 250L
        private const val REDIRECT_COUNT = 4
        private const val PAUSES_PER_DAY = 2
        private var activeService = WeakReference<SpeedbreakerService>(null)

        fun notifyPauseEnded(packageName: String) {
            val service = activeService.get() ?: return
            service.handler.post { service.handleEndPause(packageName) }
        }

        fun expectPauseEnd(packageName: String) {
            activeService.get()?.expectedPauseEnds?.add(packageName)
        }

        fun cancelExpectedPauseEnd(packageName: String) {
            val service = activeService.get() ?: return
            service.handler.post { service.expectedPauseEnds -= packageName }
        }
    }
}
