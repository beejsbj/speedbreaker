package dev.burooj.speedbreaker.observation

import android.Manifest
import android.app.Application
import android.app.AppOpsManager
import android.os.Process
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.telecom.TelecomManager
import dev.burooj.speedbreaker.enforcement.EnforcementEngine
import dev.burooj.speedbreaker.enforcement.Choice
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

    @Test fun alreadyActiveCallPreventsNewBreakerAndStaleChoicesHaveNoSideEffects() {
        shadowOf(telecom).setIsInCall(true)
        assertNull(EnforcementEngine().update(settings, observation(0)).breaker)
        shadowOf(telecom).setIsInCall(false)
        for (choice in listOf(Choice.Continue, Choice.Leave, Choice.Pause, Choice.Redirect("elsewhere"))) {
            val engine = EnforcementEngine()
            engine.update(settings, observation(0))
            shadowOf(telecom).setIsInCall(true)
            val update = engine.choose(choice, settings, observation(12_000))
            assertNull(update.breaker)
            assertTrue(update.effects.isEmpty())
            assertTrue(update.pauses.isEmpty())
            shadowOf(telecom).setIsInCall(false)
        }
    }

    @Test fun appOpsDenialYieldsEvenWithRuntimePermissionGranted() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_READ_PHONE_STATE, Process.myUid(), context.packageName,
            AppOpsManager.MODE_IGNORED,
        )
        assertEquals(CallSafety.Status.PermissionRequired, CallSafety(context).status())
        assertTrue(policy.safetyUiVisible(setOf("target.app")))
    }

    @Test fun missingManagerOrExposedServiceFailureSuspendsBreaths() {
        val missing = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.TELECOM_SERVICE) null else super.getSystemService(name)
        }
        assertEquals(CallSafety.Status.Unavailable, CallSafety(missing).status())
        val failing = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.TELECOM_SERVICE) throw IllegalStateException("Unavailable")
                return super.getSystemService(name)
            }
        }
        assertEquals(CallSafety.Status.Unavailable, CallSafety(failing).status())
    }

    @Test fun communicationAudioProvidesAnAdditionalYield() {
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(policy.safetyUiVisible(setOf("target.app")))
    }

    private fun observation(elapsed: Long) = Observation(
        visiblePackages = setOf("target.app"), interactive = true,
        safetyUiVisible = policy.safetyUiVisible(setOf("target.app")),
        elapsedMs = elapsed, epochMs = 1_800_000_000_000L + elapsed,
        zoneId = ZoneId.of("UTC"),
    )
}
