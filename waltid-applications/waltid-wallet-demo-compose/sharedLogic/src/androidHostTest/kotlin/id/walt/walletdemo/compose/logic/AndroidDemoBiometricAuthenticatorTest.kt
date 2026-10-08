package id.walt.walletdemo.compose.logic

import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [NoneEnrolledAndroidXBiometricManager::class])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDemoBiometricAuthenticatorTest {
    @Test
    fun availabilityPreservesEnrollmentAndHardwareReasons() {
        assertEquals(DemoBiometricAvailability.Available, androidBiometricAvailability(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(DemoBiometricAvailability.NotEnrolled, androidBiometricAvailability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(DemoBiometricAvailability.Unsupported, androidBiometricAvailability(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        assertEquals(DemoBiometricAvailability.Unavailable, androidBiometricAvailability(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
        assertEquals(DemoBiometricAvailability.Unavailable, androidBiometricAvailability(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))
    }

    @Test
    fun authenticateFromBackgroundDispatcherCompletesWithoutMainThreadCrash() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        val authenticator = createAndroidDemoBiometricAuthenticator { activity }
        val result = authenticateFromDefault(authenticator)

        assertEquals(DemoBiometricResult.Unavailable, result)
    }

    @Test
    fun authenticateWaitsForHostReadiness() = runTest {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create().start()
        val activity = controller.get()
        assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        assertTrue(!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        val authenticator = createAndroidDemoBiometricAuthenticator { activity }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = async { authenticator.authenticate("Unlock the wallet") }
            runCurrent()
            assertFalse(pending.isCompleted)
            controller.resume()
            runCurrent()
            assertEquals(DemoBiometricResult.Unavailable, pending.await())
        } finally { controller.pause().stop().destroy(); Dispatchers.resetMain() }
    }

    @Test
    fun destroyingHostResolvesWaitingAttempt() = runTest {
        val host = Robolectric.buildActivity(FragmentActivity::class.java).create().start()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = async { createAndroidDemoBiometricAuthenticator { host.get() }.authenticate("Unlock") }
            runCurrent()
            assertFalse(pending.isCompleted)
            host.stop().destroy()
            runCurrent()
            assertEquals(DemoBiometricResult.Unavailable, pending.await())
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun cancellingReadinessWaitDoesNotRestartOnResume() = runTest {
        val host = Robolectric.buildActivity(FragmentActivity::class.java).create().start()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = async { createAndroidDemoBiometricAuthenticator { host.get() }.authenticate("Unlock") }
            runCurrent()
            pending.cancelAndJoin()
            host.resume()
            runCurrent()
            assertTrue(pending.isCancelled)
        } finally { host.pause().stop().destroy(); Dispatchers.resetMain() }
    }

    private fun authenticateFromDefault(
        authenticator: DemoBiometricAuthenticator,
    ): DemoBiometricResult {
        val result = CompletableFuture<DemoBiometricResult>()
        Thread {
            try {
                result.complete(
                    runBlocking {
                        withContext(Dispatchers.Default) {
                            authenticator.authenticate("Unlock the wallet")
                        }
                    },
                )
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }.start()

        val deadline = System.currentTimeMillis() + 5_000
        while (!result.isDone && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
        }
        return result.get(1, TimeUnit.SECONDS)
    }
}

@Implements(BiometricManager::class)
class NoneEnrolledAndroidXBiometricManager {
    @Suppress("unused")
    @Implementation
    fun canAuthenticate(authenticators: Int): Int =
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}
