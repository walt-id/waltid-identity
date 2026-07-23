package id.waltid.openid4vci.wallet.authorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorizationResponseParserTest {
    private val redirect = "com.example.wallet:/oauth/callback?tenant=one"

    @Test
    fun validatesStateAndExactRedirectBinding() {
        val result = AuthorizationResponseParser.parseAuthorizationResponse(
            redirectUri = "$redirect&code=code-1&state=state-1",
            expectedState = "state-1",
            expectedRedirectUri = redirect,
        )

        assertEquals("code-1", result.code)
    }

    @Test
    fun rejectsWrongStateRedirectAndDuplicateParameters() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationResponseParser.parseAuthorizationResponse(
                redirectUri = "$redirect&code=code-1&state=wrong",
                expectedState = "state-1",
                expectedRedirectUri = redirect,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorizationResponseParser.parseAuthorizationResponse(
                redirectUri = "com.example.other:/oauth/callback?tenant=one&code=code-1&state=state-1",
                expectedState = "state-1",
                expectedRedirectUri = redirect,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorizationResponseParser.parseAuthorizationResponse(
                redirectUri = "$redirect&code=one&code=two&state=state-1",
                expectedState = "state-1",
                expectedRedirectUri = redirect,
            )
        }
    }

    @Test
    fun rejectsFragmentCallbacks() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationResponseParser.parseAuthorizationResponse(
                redirectUri = "com.example.wallet:/oauth/callback#code=code-1&state=state-1",
                expectedState = "state-1",
                expectedRedirectUri = "com.example.wallet:/oauth/callback",
            )
        }
    }
}
