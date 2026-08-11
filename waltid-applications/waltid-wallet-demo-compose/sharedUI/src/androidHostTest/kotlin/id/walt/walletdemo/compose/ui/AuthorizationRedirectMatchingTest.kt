package id.walt.walletdemo.compose.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthorizationRedirectMatchingTest {
    @Test
    fun matchesOpenIdCustomSchemeCallbacks() {
        assertTrue(
            matchesAuthorizationRedirect(
                url = "openid://?code=abc&state=xyz",
                redirectUri = "openid://",
            ),
        )
        assertTrue(
            matchesAuthorizationRedirect(
                url = "openid://callback?code=abc",
                redirectUri = "openid://",
            ),
        )
    }

    @Test
    fun rejectsUnrelatedUrls() {
        assertFalse(
            matchesAuthorizationRedirect(
                url = "https://keycloak.example/login",
                redirectUri = "openid://",
            ),
        )
        assertFalse(
            matchesAuthorizationRedirect(
                url = "https://issuer.example/callback?code=abc",
                redirectUri = "openid://",
            ),
        )
    }
}
