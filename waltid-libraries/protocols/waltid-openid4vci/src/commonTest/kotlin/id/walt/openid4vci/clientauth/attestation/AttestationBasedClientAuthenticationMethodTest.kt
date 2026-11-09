package id.walt.openid4vci.clientauth.attestation

import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeService
import id.walt.openid4vci.clientauth.attestation.verifier.KeyBasedClientAttestationVerifier
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

class AttestationBasedClientAuthenticationMethodTest {

    @Test
    fun `authenticates when request client_id matches attestation subject`() = runTest {
        val result = authenticate(
            requestClientId = "wallet-client",
            attestationSubject = "wallet-client",
            popAudience = JsonPrimitive(issuer),
        )

        val authenticated = assertIs<ClientAuthenticationResult.Authenticated>(result)
        assertEquals("wallet-client", authenticated.client.id)
    }

    @Test
    fun `rejects request client_id that does not match attestation subject`() = runTest {
        val result = authenticate(
            requestClientId = "other-client",
            attestationSubject = "wallet-client",
            popAudience = JsonPrimitive(issuer),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals("client_id does not match client attestation subject", failure.error.description)
    }

    @Test
    fun `accepts pop audience as issuer string`() = runTest {
        val result = authenticate(
            requestClientId = null,
            attestationSubject = "wallet-client",
            popAudience = JsonPrimitive(issuer),
        )

        assertIs<ClientAuthenticationResult.Authenticated>(result)
    }

    @Test
    fun `accepts pop audience as single issuer array`() = runTest {
        val result = authenticate(
            requestClientId = null,
            attestationSubject = "wallet-client",
            popAudience = JsonArray(listOf(JsonPrimitive(issuer))),
        )

        assertIs<ClientAuthenticationResult.Authenticated>(result)
    }

    @Test
    fun `rejects pop audience with additional audiences`() = runTest {
        val result = authenticate(
            requestClientId = null,
            attestationSubject = "wallet-client",
            popAudience = JsonArray(listOf(JsonPrimitive(issuer), JsonPrimitive("https://other.example"))),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals(
            "Client attestation PoP aud claim must identify only the authorization server issuer",
            failure.error.description,
        )
    }

    private val challenges = object : AttestationChallengeService {
        override suspend fun issue(authorizationServerIssuer: String): String = "fresh-challenge"
        override suspend fun validate(challenge: String, authorizationServerIssuer: String): Boolean = challenge == "valid"
    }

    @Test
    fun `required missing malformed and invalid challenges return replacement headers`() = runTest {
        for (challenge in listOf(null, JsonPrimitive(""), JsonPrimitive("expired"), JsonPrimitive(123), JsonArray(emptyList()))) {
            val failure = assertIs<ClientAuthenticationResult.Failure>(authenticate(
                null, "wallet-client", JsonPrimitive(issuer), challenges, challenge,
            ))
            assertEquals("use_attestation_challenge", failure.error.error)
            assertEquals("fresh-challenge", failure.error.responseHeaders[ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE])
        }
        assertIs<ClientAuthenticationResult.Authenticated>(authenticate(
            null, "wallet-client", JsonPrimitive(issuer), challenges, JsonPrimitive("valid"),
        ))
    }

    @Test
    fun `invalid signatures remain authentication failures even with missing challenges`() = runTest {
        val failure = assertIs<ClientAuthenticationResult.Failure>(authenticate(
            null, "wallet-client", JsonPrimitive(issuer), challenges, invalidSignature = true,
        ))
        assertEquals("invalid_client", failure.error.error)
        assertEquals(emptyMap(), failure.error.responseHeaders)
    }

    @Test
    fun `challenge service failures are server errors`() = runTest {
        val unavailable = object : AttestationChallengeService {
            override suspend fun issue(authorizationServerIssuer: String): String = error("Signing unavailable")
            override suspend fun validate(challenge: String, authorizationServerIssuer: String) = false
        }
        for (service in listOf(unavailable)) {
            val failure = assertIs<ClientAuthenticationResult.Failure>(authenticate(
                null, "wallet-client", JsonPrimitive(issuer), service,
            ))
            assertEquals("server_error", failure.error.error)
        }
    }

    @Test
    fun `explicit expected challenge validation remains available when enforcement is disabled`() = runTest {
        val failure = assertIs<ClientAuthenticationResult.Failure>(authenticate(
            null, "wallet-client", JsonPrimitive(issuer), expectedChallenge = "expected",
        ))
        assertEquals("invalid_client", failure.error.error)
        assertIs<ClientAuthenticationResult.Authenticated>(authenticate(
            null, "wallet-client", JsonPrimitive(issuer), challenge = JsonPrimitive("expected"), expectedChallenge = "expected",
        ))
    }

    private suspend fun authenticate(
        requestClientId: String?,
        attestationSubject: String,
        popAudience: JsonElement,
        challengeService: AttestationChallengeService? = null,
        challenge: JsonElement? = null,
        invalidSignature: Boolean = false,
        expectedChallenge: String? = null,

    ): ClientAuthenticationResult {
        val attesterKey = JWKKey.generate(KeyType.Ed25519)
        val clientInstanceKey = JWKKey.generate(KeyType.Ed25519)
        val now = Clock.System.now().epochSeconds
        val attestationJwt = attesterKey.signJws(
            buildJsonObject {
                put("sub", attestationSubject)
                put("iat", now)
                put("exp", now + 300)
                put("cnf", buildJsonObject {
                    put("jwk", clientInstanceKey.getPublicKey().exportJWKObject())
                })
            }.toString().encodeToByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION),
            ),
        )
        val popJwt = (if (invalidSignature) JWKKey.generate(KeyType.Ed25519) else clientInstanceKey).signJws(
            buildJsonObject {
                challenge?.let { put("challenge", it) }
                put("aud", popAudience)
                put("iat", now)
                put("jti", "proof-$now")
            }.toString().encodeToByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP),
            ),
        )
        val method = AttestationBasedClientAuthenticationMethod(
            attestationVerifier = KeyBasedClientAttestationVerifier { _, _ -> listOf(attesterKey.getPublicKey()) },
            challengeService = challengeService,
        )

        return method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = requestClientId?.let { mapOf("client_id" to listOf(it)) } ?: emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer, challenge = expectedChallenge),
        )
    }

    @Test
    fun `wrapped verifier keeps rejected algorithms and the pop age window`() = runTest {
        val attesterKey = JWKKey.generate(KeyType.Ed25519)
        val clientInstanceKey = JWKKey.generate(KeyType.Ed25519)
        val now = Clock.System.now().epochSeconds
        val method = AttestationBasedClientAuthenticationMethod(
            trustedAttesterKeys = { _, _ -> listOf(attesterKey.getPublicKey()) },
            acceptedAttestationSigningAlgorithms = setOf("none"),
            popMaxAgeSeconds = 1,
            clockSkewSeconds = 0,
        ).withAttestationVerifier { inner ->
            object : id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifier {
                override suspend fun verifyAttestationJwt(
                    jwt: String,
                    header: kotlinx.serialization.json.JsonObject,
                    payload: kotlinx.serialization.json.JsonObject,
                ) = inner.verifyAttestationJwt(jwt, header, payload)
            }
        }
        val attestationJwt = attesterKey.signJws(
            buildJsonObject {
                put("sub", "wallet-client")
                put("iat", now)
                put("exp", now + 300)
                put("cnf", buildJsonObject {
                    put("jwk", clientInstanceKey.getPublicKey().exportJWKObject())
                })
            }.toString().encodeToByteArray(),
            headers = mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION)),
        )
        val popJwt = clientInstanceKey.signJws(
            buildJsonObject {
                put("aud", issuer)
                put("iat", now - 120)
                put("jti", "proof-$now")
            }.toString().encodeToByteArray(),
            headers = mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP)),
        )
        val result = method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )
        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("Client attestation alg is not supported", failure.error.description)
    }

    @Test
    fun `wrapped verifier keeps the pop age window`() = runTest {
        val attesterKey = JWKKey.generate(KeyType.Ed25519)
        val clientInstanceKey = JWKKey.generate(KeyType.Ed25519)
        val now = Clock.System.now().epochSeconds
        val method = AttestationBasedClientAuthenticationMethod(
            trustedAttesterKeys = { _, _ -> listOf(attesterKey.getPublicKey()) },
            popMaxAgeSeconds = 1,
            clockSkewSeconds = 0,
        ).withAttestationVerifier { it }
        val attestationJwt = attesterKey.signJws(
            buildJsonObject {
                put("sub", "wallet-client")
                put("iat", now)
                put("exp", now + 300)
                put("cnf", buildJsonObject {
                    put("jwk", clientInstanceKey.getPublicKey().exportJWKObject())
                })
            }.toString().encodeToByteArray(),
            headers = mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION)),
        )
        val popJwt = clientInstanceKey.signJws(
            buildJsonObject {
                put("aud", issuer)
                put("iat", now - 120)
                put("jti", "proof-$now")
            }.toString().encodeToByteArray(),
            headers = mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP)),
        )
        val result = method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )
        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals(
            "Client attestation PoP iat claim is outside the accepted age window",
            failure.error.description,
        )
    }

    private companion object {
        const val issuer = "https://issuer.example/openid4vci"
    }
}
