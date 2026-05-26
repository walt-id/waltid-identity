package id.walt.wallet2.client

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletEndpointRewriterTest {

    @Test
    fun androidEmulatorLocalhostRewritesQuickstartHostsInsideEncodedUrls() {
        val original =
            "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Fwaltid.enterprise.localhost%2Fv2%2Fissuer"

        val rewritten = WalletEndpointRewriter.androidEmulatorLocalhost().rewrite(original)

        assertEquals(
            "openid-credential-offer://?credential_offer_uri=http%3A%2F%2F10.0.2.2%2Fv2%2Fissuer",
            rewritten,
        )
    }

    @Test
    fun noopRewriterLeavesValuesUnchanged() {
        val value = "openid4vp://authorize?request_uri=https%3A%2F%2Fexample.com%2Frequest"

        assertEquals(value, WalletEndpointRewriter.Noop.rewrite(value))
    }
}
