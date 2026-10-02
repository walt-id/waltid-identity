package id.walt.walletdemo.compose.android

import android.net.Uri
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DigitalCredentialCreateAuthHandoffTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val delivered = mutableListOf<String>()

    @After fun tearDown() {
        listOf("one", "two").forEach { DigitalCredentialCreateAuthHandoff.clear(context, it) }
        DigitalCredentialCreateAuthHandoff.dropLiveContinuation()
        while (OrphanAuthorizationCallback.take() != null) { /* drain owned test callbacks */ }
    }

    private fun register(id: String = "one", state: String = "expected") {
        DigitalCredentialCreateAuthHandoff.register(context, id, state, "openid://") { delivered += it }
    }
    private fun deliver(uri: String) = DigitalCredentialCreateAuthHandoff.deliver(context, Uri.parse(uri))

    @Test fun unrelatedOrMalformedCallbacksDoNotConsumeTheRegisteredRequest() {
        register()
        listOf(
            "openid://?code=abc&state=wrong", "openid://?code=abc", "openid://?state=expected",
            "openid://?code=abc&state=expected&state=expected", "openid://?code=abc&error=denied&state=expected",
            "openid://?code=&state=expected", "openid://other?code=abc&state=expected",
            "openid://?code=abc&state=expected#fragment", "https://?code=abc&state=expected",
            "openid-credential-offer://?credential_offer_uri=https://issuer.example",
        ).forEach { assertFalse(it, deliver(it)) }
        assertTrue(deliver("openid://?code=abc&state=expected"))
        assertEquals(listOf("openid://?code=abc&state=expected"), delivered)
        assertNull(OrphanAuthorizationCallback.take())
    }

    @Test fun correlatedDenialIsTerminalAndDuplicateReturnDoesNotRunAgain() {
        register()
        repeat(2) { assertTrue(deliver("openid://?error=access_denied&state=expected")) }
        assertEquals(1, delivered.size)
        assertNull(OrphanAuthorizationCallback.take())
    }

    @Test fun processLossQueuesOnlyCorrelatedCallbacksOnceWithoutOverwritingAnotherSession() {
        register()
        register("two", "second")
        DigitalCredentialCreateAuthHandoff.dropLiveContinuation()
        assertFalse(deliver("openid://?code=orphan&state=wrong"))
        repeat(2) { assertTrue(deliver("openid://?code=orphan&state=expected")) }
        assertTrue(deliver("openid://?code=other&state=second"))
        assertEquals("one" to "openid://?code=orphan&state=expected", OrphanAuthorizationCallback.take())
        assertEquals("two" to "openid://?code=other&state=second", OrphanAuthorizationCallback.take())
        assertNull(OrphanAuthorizationCallback.take())
        assertTrue(delivered.isEmpty())
    }

    @Test fun oldOwnerCleanupPreservesNewerRegistration() {
        register()
        register("two", "second")
        DigitalCredentialCreateAuthHandoff.clear(context, "one")
        assertFalse(deliver("openid://?code=old&state=expected"))
        assertTrue(deliver("openid://?code=new&state=second"))
        assertEquals(listOf("openid://?code=new&state=second"), delivered)
    }
}
