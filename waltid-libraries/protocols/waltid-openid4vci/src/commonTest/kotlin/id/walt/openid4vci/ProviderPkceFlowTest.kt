package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandler
import id.walt.openid4vci.pkce.Pkce
import id.walt.openid4vci.pkce.PkceBinding
import id.walt.openid4vci.pkce.PkceServiceConfig
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.repository.refresh.RefreshTokenRecord
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.authorization.toDefaultAuthorizationRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponse
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.responses.token.TokenCredentialAuthorization
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderPkceFlowTest {
    private val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    private val redirect = "https://wallet.example/callback"
    private val session = DefaultSession(subject = "same-issuance-session")

    @Test
    fun validPkcePersistsWithCodeAndTokensIssueAfterVerification() = runTest {
        val fixture = Fixture()
        val code = authorize(fixture.provider, verifier)
        val stored = Json.decodeFromString<DefaultAuthorizationCodeRecord>(fixture.codes.records.getValue(code))
        assertEquals(PkceBinding.Challenge(challenge, "S256"), stored.pkceBinding)
        // Recreate the repository and provider, retaining only the serialized code record.
        val restarted = Fixture(SerializedCodes(fixture.codes.records))
        val success = assertIs<AccessTokenResponseResult.Success>(redeem(restarted, code, listOf(verifier)))
        assertEquals(1, restarted.resolverCalls)
        assertEquals(1, restarted.tokenCalls)
        assertEquals(1, restarted.refreshWrites)
        assertNotNull(success.response.refreshToken)
        assertNull(restarted.codes.records[code])
        assertEquals(OAuthErrorCodes.INVALID_GRANT,
            assertIs<AccessTokenResponseResult.Failure>(redeem(restarted, code, listOf(verifier))).error.error)
        assertTrue(success.request.requestForm.containsKey("code_verifier"))
    }

    @Test
    fun invalidPkceHasNoIssuanceSideEffectsAndKeepsFailureSession() = runTest {
        val invalid = listOf(
            null to OAuthErrorCodes.INVALID_REQUEST,
            emptyList<String>() to OAuthErrorCodes.INVALID_REQUEST,
            listOf(verifier, "") to OAuthErrorCodes.INVALID_REQUEST,
            listOf("", verifier) to OAuthErrorCodes.INVALID_REQUEST,
            listOf("") to OAuthErrorCodes.INVALID_GRANT,
            listOf("x".repeat(43)) to OAuthErrorCodes.INVALID_GRANT,
            listOf("a".repeat(42)) to OAuthErrorCodes.INVALID_GRANT,
            listOf("a".repeat(129)) to OAuthErrorCodes.INVALID_GRANT,
            listOf("é".repeat(43)) to OAuthErrorCodes.INVALID_GRANT,
        )
        for ((values, errorCode) in invalid) {
            val fixture = Fixture()
            val code = authorize(fixture.provider, verifier)
            val failure = assertIs<AccessTokenResponseResult.Failure>(redeem(fixture, code, values))
            assertEquals(errorCode, failure.error.error)
            assertEquals(session.subject, failure.request.session?.subject)
            assertEquals(0, fixture.resolverCalls)
            assertEquals(0, fixture.tokenCalls)
            assertEquals(0, fixture.refreshWrites)
            assertNull(fixture.codes.records[code])
        }
    }

    private fun authorizationForm(verifier: String?) = mapOf(
        "response_type" to listOf("code"), "client_id" to listOf("wallet"), "redirect_uri" to listOf(redirect),
    ) + (verifier?.let { mapOf("code_challenge" to listOf(Pkce.generateS256CodeChallenge(it)), "code_challenge_method" to listOf("S256")) } ?: emptyMap())

    private suspend fun authorize(provider: OAuth2Provider, verifier: String?): String {
        val request = assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(authorizationForm(verifier))).request
        return assertIs<AuthorizationResponseResult.Success>(provider.createAuthorizationResponse(request, session)).response.code
    }

    private suspend fun redeem(fixture: Fixture, code: String, verifiers: List<String>?): AccessTokenResponseResult {
        val form = mapOf("grant_type" to listOf("authorization_code"), "client_id" to listOf("wallet"),
            "redirect_uri" to listOf(redirect), "code" to listOf(code)) + (verifiers?.let { mapOf("code_verifier" to it) } ?: emptyMap())
        val request = assertIs<AccessTokenRequestResult.Success>(fixture.provider.createAccessTokenRequest(form)).request
        return fixture.provider.createAccessTokenResponse(request, TokenResponseOptions(credentialAuthorizationResolver = { _, _ ->
            fixture.resolverCalls++
            TokenCredentialAuthorization(emptyList(), includeInResponse = false)
        }))
    }

    private class SerializedCodes(val records: MutableMap<String, String> = mutableMapOf()) : AuthorizationCodeRepository {
        override suspend fun save(record: AuthorizationCodeRecord) {
            records[record.code] = Json.encodeToString(record as DefaultAuthorizationCodeRecord)
        }
        override suspend fun consume(code: String): AuthorizationCodeRecord? =
            records.remove(code)?.let { Json.decodeFromString<DefaultAuthorizationCodeRecord>(it) }
    }

    private class Fixture(val codes: SerializedCodes = SerializedCodes(), required: Boolean = false) {
        var resolverCalls = 0
        var tokenCalls = 0
        var refreshWrites = 0
        val config = createTestConfig(
            accessTokenIssuer = object : AccessTokenIssuer {
                override suspend fun issue(claims: Map<String, Any?>): String { tokenCalls++; return "access-token" }
            },
            refreshTokenRepository = object : RefreshTokenRepository by InMemoryRefreshTokenRepository() {
                override suspend fun save(record: RefreshTokenRecord) { refreshWrites++ }
            },
        ).copy(authorizationCodeRepository = codes, pkceServiceConfig = PkceServiceConfig(requirePkce = required),
            pushedAuthorizationConfig = PushedAuthorizationConfig(InMemoryPARRepository()))
        val provider = buildOAuth2Provider(config)
    }
}
