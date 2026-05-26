package id.walt.walletdemo

import id.walt.wallet2.client.WalletEndpointRewriter
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletEndpointRewriterTest {

    @Test
    fun quickstartLocal_rewritesHostLocalhostUrlsForAndroidEmulator() {
        val original =
            "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Fwaltid.enterprise.localhost%2Fv2%2Fissuer"

        val rewritten = WalletEndpointRewriter.androidEmulatorLocalhost().rewrite(original)

        assertEquals(
            "openid-credential-offer://?credential_offer_uri=http%3A%2F%2F10.0.2.2%2Fv2%2Fissuer",
            rewritten,
        )
    }
}
