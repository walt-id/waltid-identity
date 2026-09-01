package id.walt.walletdemo.compose.logic

import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [NoneEnrolledAndroidXBiometricManager::class])
class AndroidDemoBiometricAuthenticatorTest {
    @Test
    fun authenticateFromBackgroundDispatcherCompletesWithoutMainThreadCrash() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        val authenticator = createAndroidDemoBiometricAuthenticator { activity }
        val result = authenticateFromDefault(authenticator)

        assertTrue(result == DemoBiometricResult.Succeeded || result == DemoBiometricResult.Failed)
    }

    @Test
    fun authenticateFailsWhenHostIsNotResumed() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create().start()
        val activity = controller.get()
        assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        assertTrue(!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        val authenticator = createAndroidDemoBiometricAuthenticator { activity }
        val result = authenticateFromDefault(authenticator)

        assertEquals(DemoBiometricResult.Failed, result)
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
