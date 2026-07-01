package id.walt.openid4vci.clientauth.attestation

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

    private suspend fun authenticate(
        requestClientId: String?,
        attestationSubject: String,
        popAudience: JsonElement,
    ): ClientAuthenticationResult {
        val attesterKey = JWKKey.generate(KeyType.secp256r1)
        val clientInstanceKey = JWKKey.generate(KeyType.secp256r1)
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
        val popJwt = clientInstanceKey.signJws(
            buildJsonObject {
                put("aud", popAudience)
                put("iat", now)
                put("jti", "proof-$now")
            }.toString().encodeToByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP),
            ),
        )
        val method = AttestationBasedClientAuthenticationMethod(
            trustResolver = StaticJwkClientAttestationTrustResolver(attesterKey.getPublicKey()),
        )

        return method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = requestClientId?.let { mapOf("client_id" to listOf(it)) } ?: emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )
    }

    private companion object {
        const val issuer = "https://issuer.example/openid4vci"
    }
}
