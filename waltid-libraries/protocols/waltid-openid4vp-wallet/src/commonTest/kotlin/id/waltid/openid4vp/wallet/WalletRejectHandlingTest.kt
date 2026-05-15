package id.waltid.openid4vp.wallet

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class WalletRejectHandlingTest {

    // --- FRAGMENT -----------------------------------------------------------

    @Test
    fun fragmentErrorResponse_includesErrorDescriptionAndState() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.FRAGMENT,
                state = "state-123",
            ),
            error = "access_denied",
            errorDescription = "User denied",
        ).getOrThrow()

        assertEquals(
            "https://wallet.example/callback#error=access_denied&error_description=User+denied&state=state-123",
            result.getUrl,
        )
    }

    @Test
    fun fragmentErrorResponse_typedEnum_producesSameUrl() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.FRAGMENT,
                state = "state-123",
            ),
            error = WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED,
            errorDescription = "User denied",
        ).getOrThrow()

        assertEquals(
            "https://wallet.example/callback#error=access_denied&error_description=User+denied&state=state-123",
            result.getUrl,
        )
    }

    @Test
    fun fragmentErrorResponse_omitsStateWhenRequestHasNone() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.FRAGMENT,
            ),
            error = "access_denied",
        ).getOrThrow()

        assertEquals("https://wallet.example/callback#error=access_denied", result.getUrl)
    }

    // --- QUERY --------------------------------------------------------------

    @Test
    fun queryErrorResponse_appendsErrorParametersAsQueryString() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.QUERY,
                state = "state-abc",
            ),
            error = WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
            errorDescription = "bad request",
        ).getOrThrow()

        val url = assertNotNull(result.getUrl)
        assertTrue(url.startsWith("https://wallet.example/callback?"))
        assertTrue("error=invalid_request" in url)
        assertTrue("error_description=bad+request" in url || "error_description=bad%20request" in url)
        assertTrue("state=state-abc" in url)
    }

    // --- FORM_POST ----------------------------------------------------------

    @Test
    fun formPostErrorResponse_generatesSelfSubmittingHtmlWithEscapedFields() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.FORM_POST,
                state = "state-<123>",
            ),
            error = "access_denied",
            errorDescription = "User \"denied\"",
        ).getOrThrow()

        val html = assertNotNull(result.formPostHtml)
        assertTrue("<form method=\"POST\"" in html)
        assertTrue("action=\"https://wallet.example/callback\"" in html)
        assertTrue("name=\"error\" value=\"access_denied\"" in html)
        // error_description should be HTML-escaped
        assertTrue("name=\"error_description\" value=\"User &quot;denied&quot;\"" in html)
        // state should be HTML-escaped
        assertTrue("name=\"state\" value=\"state-&lt;123&gt;\"" in html)
        // auto-submit behavior present
        assertTrue("document.forms[0].submit()" in html)
    }

    @Test
    fun dcApi_isUnsupported() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.DC_API,
                state = "s",
            ),
            error = "access_denied",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }
}
