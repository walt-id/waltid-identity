package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.preauthorized.InvalidPreAuthorizedCodeLifetimeException
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeConfig
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ProviderPreAuthorizedCodeConfigTest {
    @Test
    fun `provider uses configured lifetime and attempt limit and keeps token expiry independent`() = runTest {
        val settings = PreAuthorizedCodeConfig(
            defaultLifetimeSeconds = 40, maxLifetimeSeconds = 50, maxFailedTxCodeAttempts = 2,
        )
        val provider = buildOAuth2Provider(createTestConfig().copy(preAuthorizedCodeConfig = settings))
        val tokenDeadline = Clock.System.now() + 120.seconds
        val request = PreAuthorizedCodeIssueRequest(
            session = DefaultSession(subject = "holder").withExpiresAt(TokenType.ACCESS_TOKEN, tokenDeadline),
            txCode = TxCode(length = 6),
            txCodeValue = "123456",
        )

        val before = Clock.System.now()
        val issued = provider.preAuthorizedCodeIssuer.issue(request)
        assertTrue(issued.expiresAt in (before + 40.seconds)..(Clock.System.now() + 40.seconds))
        assertFailsWith<InvalidPreAuthorizedCodeLifetimeException> {
            provider.preAuthorizedCodeIssuer.issue(request.copy(lifetimeSeconds = 51))
        }
        val redeemable = provider.preAuthorizedCodeIssuer.issue(request.copy(lifetimeSeconds = 50))

        suspend fun exchange(code: String, pin: String): AccessTokenResponseResult {
            val parsed = assertIs<AccessTokenRequestResult.Success>(provider.createAccessTokenRequest(mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf(code),
                "tx_code" to listOf(pin),
            )))
            return provider.createAccessTokenResponse(parsed.request)
        }

        for (pin in listOf("000000", "000000", "123456")) {
            val failure = assertIs<AccessTokenResponseResult.Failure>(exchange(issued.code, pin))
            assertEquals("invalid_grant", failure.error.error)
        }
        assertIs<AccessTokenResponseResult.Failure>(exchange(redeemable.code, "000000"))
        val success = assertIs<AccessTokenResponseResult.Success>(exchange(redeemable.code, "123456"))
        assertEquals(tokenDeadline, success.request.session?.expiresAt?.get(TokenType.ACCESS_TOKEN))
        assertTrue((success.response.expiresIn ?: 0) > 50)
    }

    @Test
    fun `settings reject a default beyond the maximum and a disabled attempt limit`() {
        assertFailsWith<IllegalArgumentException> { PreAuthorizedCodeConfig(defaultLifetimeSeconds = 301) }
        assertFailsWith<IllegalArgumentException> { PreAuthorizedCodeConfig(maxFailedTxCodeAttempts = 0) }
    }
}
