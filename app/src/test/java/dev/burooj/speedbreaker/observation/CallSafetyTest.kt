package dev.burooj.speedbreaker.observation

import android.Manifest
import android.app.Application
import android.media.AudioManager
import android.telecom.TelecomManager
import dev.burooj.speedbreaker.enforcement.EnforcementEngine
import dev.burooj.speedbreaker.enforcement.Observation
import dev.burooj.speedbreaker.model.AppPolicy
import dev.burooj.speedbreaker.model.Settings
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class CallSafetyTest {
    private lateinit var context: Application
    private lateinit var telecom: TelecomManager
    private lateinit var policy: SafetyPolicy
    private val settings = Settings(consentAccepted = true, apps = mapOf("target.app" to AppPolicy()))

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        telecom = context.getSystemService(TelecomManager::class.java)
        shadowOf(telecom).setReadPhoneStatePermission(true)
        policy = SafetyPolicy(context)
    }

    @Test fun silentIncomingCallDismissesHardLockAndCannotReappearWhileRinging() {
        val engine = EnforcementEngine()
        assertNotNull(engine.update(settings, observation(0)).breaker)
        // The target remains the only application window. Call controls are SystemUI,
        // and silent ringing need not change the audio mode or launch the dialer.
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        shadowOf(telecom).setIsInCall(true)
        assertNull(engine.update(settings, observation(1_000)).breaker)
        assertNull(engine.update(settings, observation(9_000)).breaker)
        shadowOf(telecom).setIsInCall(false)
        assertNotNull(engine.update(settings, observation(9_250)).breaker)
    }

    @Test fun ordinarySystemUiDoesNotReleaseHardLock() {
        val engine = EnforcementEngine()
        engine.update(settings, observation(0))
        assertNotNull(engine.update(settings, observation(1_000).copy(navigationAway = true)).breaker)
        assertFalse(policy.safetyUiVisible(setOf("target.app", "com.android.systemui")))
    }

    @Test fun permissionRevocationDismissesAnExistingBreaker() {
        val engine = EnforcementEngine()
        assertNotNull(engine.update(settings, observation(0)).breaker)
        shadowOf(context).denyPermissions(Manifest.permission.READ_PHONE_STATE)
        shadowOf(telecom).setReadPhoneStatePermission(false)
        assertNull(engine.update(settings, observation(1_000)).breaker)
        assertNull(engine.update(settings, observation(9_000)).breaker)
    }

    @Test fun deniedPermissionDoesNotAllowANewBreaker() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_PHONE_STATE)
        shadowOf(telecom).setReadPhoneStatePermission(false)
        assertNull(EnforcementEngine().update(settings, observation(0)).breaker)
    }

    private fun observation(elapsed: Long) = Observation(
        visiblePackages = setOf("target.app"), interactive = true,
        safetyUiVisible = policy.safetyUiVisible(setOf("target.app")),
        elapsedMs = elapsed, epochMs = 1_800_000_000_000L + elapsed,
        zoneId = ZoneId.of("UTC"),
    )
}
