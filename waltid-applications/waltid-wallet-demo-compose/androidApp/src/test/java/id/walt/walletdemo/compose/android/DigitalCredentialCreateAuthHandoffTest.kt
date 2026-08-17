package id.walt.walletdemo.compose.android

import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DigitalCredentialCreateAuthHandoffTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        DigitalCredentialCreateAuthHandoff.clear(context)
        OrphanAuthorizationCallback.take()
    }

    @Test
    fun deliversOpenIdCallbackToRegisteredCreateActivityContinuation() {
        val delivered = AtomicReference<String?>(null)
        DigitalCredentialCreateAuthHandoff.register(context, sessionId = "session-1") { callback ->
            delivered.set(callback)
        }

        val accepted = DigitalCredentialCreateAuthHandoff.deliver(
            context,
            Uri.parse("openid://?code=abc&state=xyz"),
        )

        assertTrue(accepted)
        assertEquals("openid://?code=abc&state=xyz", delivered.get())
        assertNull(OrphanAuthorizationCallback.take())
    }

    @Test
    fun queuesOrphanCallbackWhenCreateActivityIsGoneButSessionIsPersisted() {
        DigitalCredentialCreateAuthHandoff.register(context, sessionId = "session-orphan") { error("unused") }
        DigitalCredentialCreateAuthHandoff.dropLiveContinuation()

        val accepted = DigitalCredentialCreateAuthHandoff.deliver(
            context,
            Uri.parse("openid://?code=orphan"),
        )

        assertTrue(accepted)
        assertEquals("session-orphan" to "openid://?code=orphan", OrphanAuthorizationCallback.take())
        assertEquals("session-orphan", DigitalCredentialCreateAuthHandoff.pendingSessionId(context))
    }

    @Test
    fun ignoresNonOpenIdDeepLinks() {
        assertFalse(
            DigitalCredentialCreateAuthHandoff.deliver(
                context,
                Uri.parse("openid-credential-offer://?credential_offer_uri=https://issuer.example"),
            ),
        )
    }
}
