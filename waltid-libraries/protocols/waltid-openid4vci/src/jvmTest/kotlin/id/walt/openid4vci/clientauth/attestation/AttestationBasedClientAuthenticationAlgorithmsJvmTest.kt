package id.walt.openid4vci.clientauth.attestation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock

class AttestationBasedClientAuthenticationAlgorithmsJvmTest {

    @Test
    fun `authenticates supported JWK signing algorithms`() = runTest {
        val keyTypes = listOf(
            KeyType.secp256r1,
            KeyType.secp384r1,
            KeyType.secp521r1,
            KeyType.secp256k1,
            KeyType.RSA,
            KeyType.RSA3072,
            KeyType.RSA4096,
            KeyType.Ed25519,
        )

        keyTypes.forEach { keyType ->
            val attesterKey = JWKKey.generate(keyType)
            val clientInstanceKey = JWKKey.generate(keyType)

            val result = authenticate(
                attesterKey = attesterKey,
                clientInstanceKey = clientInstanceKey,
            )

            assertIs<ClientAuthenticationResult.Authenticated>(result, "Expected $keyType to authenticate")
        }
    }

    private suspend fun authenticate(
        attesterKey: JWKKey,
        clientInstanceKey: JWKKey,
    ): ClientAuthenticationResult {
        val now = Clock.System.now().epochSeconds
        val attestationPayload = buildJsonObject {
            put("sub", clientId)
            put("iat", now)
            put("exp", now + 300)
            put("cnf", buildJsonObject {
                put("jwk", clientInstanceKey.getPublicKey().exportJWKObject())
            })
        }
        val popPayload = buildJsonObject {
            put("aud", issuer)
            put("iat", now)
            put("jti", "proof-$now")
        }
        val method = AttestationBasedClientAuthenticationMethod(
            trustResolver = StaticJwkClientAttestationTrustResolver(attesterKey.getPublicKey()),
        )

        return method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = mapOf("client_id" to listOf(clientId)),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(
                    signWithWaltKey(attesterKey, attestationPayload, ClientAttestationJwtTypes.CLIENT_ATTESTATION),
                ),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(
                    signWithWaltKey(clientInstanceKey, popPayload, ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP),
                ),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )
    }

    private suspend fun signWithWaltKey(
        key: JWKKey,
        payload: JsonObject,
        type: String,
    ): String =
        key.signJws(
            payload.toString().encodeToByteArray(),
            headers = mapOf("typ" to JsonPrimitive(type)),
        )

    private companion object {
        const val issuer = "https://issuer.example/openid4vci"
        const val clientId = "wallet-client"
    }
}
