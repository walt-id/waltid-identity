package id.waltid.openid4vp.wallet

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletPresentResponseTest {

    @Test
    fun `empty successful direct-post response is accepted`() {
        val result = WalletPresentFunctionality2.directPostResult(success = true, responseBody = "")

        assertTrue(result.transmissionSuccess == true)
        assertEquals(JsonObject(emptyMap()), result.verifierResponse)
        assertNull(result.redirectTo)
    }

    @Test
    fun `successful direct-post response preserves redirect URI`() {
        val result = WalletPresentFunctionality2.directPostResult(
            success = true,
            responseBody = """{"redirect_uri":"https://verifier.example/complete"}""",
        )

        assertEquals("https://verifier.example/complete", result.redirectTo)
    }

    @Test
    fun `plain-text failure response preserves transmission result`() {
        val result = WalletPresentFunctionality2.directPostResult(success = false, responseBody = "upstream failure")

        assertEquals(false, result.transmissionSuccess)
        assertNull(result.redirectTo)
    }
}
