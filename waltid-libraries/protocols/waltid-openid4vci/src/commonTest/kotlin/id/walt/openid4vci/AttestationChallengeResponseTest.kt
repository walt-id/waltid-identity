package id.walt.openid4vci

import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeConfig
import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeService
import kotlinx.coroutines.test.runTest
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.responses.challenge.AttestationChallengeResponse
import kotlinx.serialization.json.*
import kotlin.test.*

class AttestationChallengeResponseTest {
    @Test
    fun `challenge headers survive HTTP writers without leaking into JSON`() {
        val error = OAuthError("use_attestation_challenge", "Retry", mapOf("OAuth-Client-Attestation-Challenge" to "fresh"))
        val json = Json { encodeDefaults = true }
        assertEquals(setOf("error", "error_description"), json.encodeToJsonElement(error).jsonObject.keys)
        val provider = buildOAuth2Provider(createTestConfig())
        val par = provider.writePushedAuthorizationError(error)
        val token = provider.writeAccessTokenError(error)
        for ((status, headers, payload) in listOf(Triple(par.status, par.headers, par.payload), Triple(token.status, token.headers, token.payload))) {
            assertEquals(400, status)
            assertEquals("fresh", headers["OAuth-Client-Attestation-Challenge"])
            assertEquals("no-store", headers["Cache-Control"])
            assertEquals(setOf("error", "error_description"), payload.keys)
        }
        val response = AttestationChallengeResponse("fresh").toHttp()
        assertEquals(200, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals("""{"attestation_challenge":"fresh"}""", json.encodeToString(response.payload))
    }

    @Test
    fun `optional challenge configuration controls enablement and round trips`() {
        val base = Json.parseToJsonElement("""{"config":{"verificationMethod":{"type":"static-jwk","jwk":{}}}}""").jsonObject
        for ((value, expected) in listOf(
            null to null,
            JsonNull to null,
            buildJsonObject {} to AttestationChallengeConfig(),
            buildJsonObject { put("lifetimeSeconds", 600) } to AttestationChallengeConfig(600),
        )) {
            val input = JsonObject(base + if (value == null) emptyMap() else mapOf("challenge" to value))
            val method = Json.decodeFromJsonElement<ClientAuthenticationMethodConfig.ClientAttestation>(input)
            assertEquals(expected, method.challenge)
            assertEquals(method, Json.decodeFromString<ClientAuthenticationMethodConfig.ClientAttestation>(Json.encodeToString(method)))
        }
        assertEquals(300L, AttestationChallengeConfig().lifetimeSeconds)
        for (lifetime in listOf(0L, -1L)) {
            assertFailsWith<IllegalArgumentException> { AttestationChallengeConfig(lifetime) }
            assertFailsWith<IllegalArgumentException> {
                Json.decodeFromString<AttestationChallengeConfig>("""{"lifetimeSeconds":$lifetime}""")
            }
        }
    }

    @Test
    fun `configuration conversion rejects missing and unexpected services`() = runTest {
        val method = Json.decodeFromString<ClientAuthenticationMethodConfig.ClientAttestation>(
            """{"config":{"verificationMethod":{"type":"static-jwk","jwk":{}}}}""",
        )
        assertFailsWith<IllegalArgumentException> {
            ClientAuthenticationConfig(listOf(method.copy(challenge = AttestationChallengeConfig())))
                .toClientAuthenticationServiceConfig()
        }
        assertFailsWith<IllegalArgumentException> {
            ClientAuthenticationConfig(listOf(method)).toClientAuthenticationServiceConfig(challengeService = service)
        }
        assertFailsWith<IllegalArgumentException> {
            ClientAuthenticationConfig().toClientAuthenticationServiceConfig(challengeService = service)
        }
        assertTrue(ClientAuthenticationConfig().toClientAuthenticationServiceConfig().methods.isEmpty())
    }

    private val service = object : AttestationChallengeService {
        override suspend fun issue(authorizationServerIssuer: String) = "challenge:$authorizationServerIssuer"
        override suspend fun validate(challenge: String, authorizationServerIssuer: String) =
            challenge == "challenge:$authorizationServerIssuer"
    }

}
