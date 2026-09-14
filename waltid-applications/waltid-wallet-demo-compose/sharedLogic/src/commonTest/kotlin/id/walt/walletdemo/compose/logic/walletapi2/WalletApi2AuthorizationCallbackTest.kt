package id.walt.walletdemo.compose.logic.walletapi2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WalletApi2AuthorizationCallbackTest {

    @Test
    fun matchingCallbackReturnsCode() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?code=auth-code&state=state-1",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106/",
        )
        assertEquals(WalletApi2AuthorizationCallback.Code("auth-code"), parsed)
    }

    @Test
    fun blankAndSlashPathsAreEquivalent() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?code=auth-code&state=state-1",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106",
        )
        assertEquals(WalletApi2AuthorizationCallback.Code("auth-code"), parsed)
    }

    @Test
    fun wrongStateDoesNotReturnCode() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?code=auth-code&state=WRONG",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106/",
        )
        val invalid = assertIs<WalletApi2AuthorizationCallback.Invalid>(parsed)
        assertEquals("Authorization callback state does not match the issuance session", invalid.message)
    }

    @Test
    fun missingStateDoesNotReturnCode() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?code=auth-code",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106/",
        )
        assertIs<WalletApi2AuthorizationCallback.Invalid>(parsed)
    }

    @Test
    fun missingStoredStateDoesNotReturnCode() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?code=auth-code&state=state-1",
            expectedState = null,
            expectedRedirectUri = "http://localhost:7106/",
        )
        val invalid = assertIs<WalletApi2AuthorizationCallback.Invalid>(parsed)
        assertEquals("Authorization session is missing state", invalid.message)
    }

    @Test
    fun redirectMismatchDoesNotReturnCode() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://evil.example/?code=auth-code&state=state-1",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106/",
        )
        val invalid = assertIs<WalletApi2AuthorizationCallback.Invalid>(parsed)
        assertEquals(
            "Authorization callback redirect URI does not match the issuance session",
            invalid.message,
        )
    }

    @Test
    fun accessDeniedCancels() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?error=access_denied&state=state-1",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106/",
        )
        assertEquals(WalletApi2AuthorizationCallback.Denied, parsed)
    }

    @Test
    fun accessDeniedWithWrongStateIsInvalid() {
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = "http://localhost:7106/?error=access_denied&state=WRONG",
            expectedState = "state-1",
            expectedRedirectUri = "http://localhost:7106/",
        )
        assertIs<WalletApi2AuthorizationCallback.Invalid>(parsed)
    }
}
