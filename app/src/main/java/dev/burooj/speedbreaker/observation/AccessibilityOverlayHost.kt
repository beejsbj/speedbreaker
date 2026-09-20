package dev.burooj.speedbreaker.observation

import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.burooj.speedbreaker.enforcement.Breaker
import dev.burooj.speedbreaker.enforcement.Choice
import dev.burooj.speedbreaker.presentation.BreakerOverlay
import dev.burooj.speedbreaker.presentation.RedirectDestination

internal data class OverlayState(
    val breaker: Breaker,
    val targetLabel: String,
    val nowElapsedMs: Long,
    val pauseTokensLeft: Int,
    val pauseResetLabel: String,
    val pauseAvailable: Boolean,
    val redirects: List<RedirectDestination>,
)

/** Hosts Compose in an accessibility window with the owners an Activity normally supplies. */
internal class AccessibilityOverlayHost(
    private val service: SpeedbreakerService,
    private val onChoice: (Choice) -> Unit,
    private val onBack: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val state = mutableStateOf<OverlayState?>(null)
    private var composeView: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var backRegistration: BackRegistration? = null
    private var choicesHapticSent = false
    private var breakerIdentity: Pair<String, Long>? = null

    val isShowing: Boolean get() = composeView != null

    fun showOrUpdate(value: OverlayState) {
        val wasShowing = isShowing
        val nextIdentity = value.breaker.packageName to value.breaker.startedElapsedMs
        val isNewBreaker = nextIdentity != breakerIdentity
        if (isNewBreaker) {
            breakerIdentity = nextIdentity
            choicesHapticSent = false
        }
        state.value = value
        if (!wasShowing) show()
        else if (isNewBreaker) composeView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        val choicesUnlocked = value.nowElapsedMs - value.breaker.startedElapsedMs >= HARD_LOCK_MS
        if (choicesUnlocked && !choicesHapticSent) {
            choicesHapticSent = true
            composeView?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    fun dismiss() {
        val view = composeView ?: return
        val lifecycleOwner = owner
        composeView = null
        owner = null
        state.value = null
        choicesHapticSent = false
        breakerIdentity = null

        runCatching { backRegistration?.unregister() }
        backRegistration = null
        lifecycleOwner?.destroy()
        runCatching { windowManager.removeViewImmediate(view) }
        view.disposeComposition()
        lifecycleOwner?.viewModelStore?.clear()
    }

    private fun show() {
        if (composeView != null) return
        val lifecycleOwner = OverlayOwner().also { it.restore() }
        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onBack()
                    true
                } else {
                    false
                }
            }
        }
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.setContent {
            state.value?.let { current ->
                BreakerOverlay(
                    breaker = current.breaker,
                    targetLabel = current.targetLabel,
                    nowElapsedMs = current.nowElapsedMs,
                    pauseTokensLeft = current.pauseTokensLeft,
                    pauseResetLabel = current.pauseResetLabel,
                    pauseAvailable = current.pauseAvailable,
                    redirects = current.redirects,
                    motionEnabled = ValueAnimator.areAnimatorsEnabled(),
                    onChoice = onChoice,
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Speedbreaker"
        }

        try {
            windowManager.addView(view, params)
            composeView = view
            owner = lifecycleOwner
            lifecycleOwner.start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backRegistration = BackRegistration.create(view, onBack)
            }
            ServiceStatus.reportError(null)
            view.requestFocus()
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (error: RuntimeException) {
            runCatching { backRegistration?.unregister() }
            backRegistration = null
            composeView = null
            owner = null
            lifecycleOwner.destroy()
            runCatching { windowManager.removeViewImmediate(view) }
            view.disposeComposition()
            lifecycleOwner.viewModelStore.clear()
            ServiceStatus.reportError("Unable to show Speedbreaker: ${error.javaClass.simpleName}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class BackRegistration private constructor(
        private val dispatcher: OnBackInvokedDispatcher,
        private val callback: OnBackInvokedCallback,
    ) {
        fun unregister() {
            dispatcher.unregisterOnBackInvokedCallback(callback)
        }

        companion object {
            fun create(view: ComposeView, onBack: () -> Unit): BackRegistration? {
                val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
                val callback = OnBackInvokedCallback { onBack() }
                dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    callback,
                )
                return BackRegistration(dispatcher, callback)
            }
        }
    }

    private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun restore() {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun start() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private companion object {
        const val HARD_LOCK_MS = 8_000L
    }
}
