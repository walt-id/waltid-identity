package id.walt.walletdemo.compose.android

import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DigitalCredentialCreateCallbackActivityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val callback = Uri.parse("${DigitalCredentialCreateAuthHandoff.REDIRECT_URI}?code=abc&state=expected")

    @After fun tearDown() {
        DigitalCredentialCreateAuthHandoff.clear(context, "callback-test")
        while (OrphanAuthorizationCallback.take() != null) { /* drain owned callbacks */ }
    }

    @Test fun unmatchedCallbackClosesWithoutOpeningTheWallet() {
        launch { activity ->
            assertNull(shadowOf(activity).nextStartedActivity)
            assertNull(OrphanAuthorizationCallback.take())
        }
    }

    @Test fun liveCallbackKeepsTheOriginalHostResponsibleForItsResult() {
        var delivered = 0
        register { delivered++ }
        launch { activity ->
            assertEquals(1, delivered)
            assertNull(shadowOf(activity).nextStartedActivity)
            assertNull(OrphanAuthorizationCallback.take())
        }
    }

    @Test fun processLossOpensTheWalletWithOneQueuedContinuation() {
        register { fail("A lost process cannot keep its live continuation") }
        DigitalCredentialCreateAuthHandoff.dropLiveContinuation()
        launch { activity ->
            val recovery = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals(MainActivity::class.java.name, recovery.component?.className)
            assertEquals(callback, recovery.data)
            assertEquals(DigitalCredentialCreateAuthHandoff.Delivery.Duplicate,
                DigitalCredentialCreateAuthHandoff.deliver(context, callback))
            assertEquals("callback-test" to callback.toString(), OrphanAuthorizationCallback.take())
            assertNull(OrphanAuthorizationCallback.take())
        }
    }

    private fun register(continuation: (String) -> Unit) = DigitalCredentialCreateAuthHandoff.register(
        context, "callback-test", "expected", DigitalCredentialCreateAuthHandoff.REDIRECT_URI, continuation,
    )

    private fun launch(assertions: (DigitalCredentialCreateCallbackActivity) -> Unit) {
        val host = Robolectric.buildActivity(DigitalCredentialCreateCallbackActivity::class.java,
            Intent(Intent.ACTION_VIEW, callback)).create()
        try {
            assertTrue(host.get().isFinishing)
            assertions(host.get())
        } finally { host.destroy() }
    }
}
