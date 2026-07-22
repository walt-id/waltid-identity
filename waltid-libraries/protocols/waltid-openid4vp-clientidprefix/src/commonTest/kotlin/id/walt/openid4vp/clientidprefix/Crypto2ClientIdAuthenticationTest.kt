@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.openid4vp.clientidprefix.prefixes.DecentralizedIdentifier
import id.walt.openid4vp.clientidprefix.prefixes.PreRegistered
import id.walt.openid4vp.clientidprefix.prefixes.VerifierAttestation
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class Crypto2ClientIdAuthenticationTest {
    @Test
    fun `X509 client authentication fails closed without trust anchors`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val requestObject = key.signJws(
            "{}".encodeToByteArray(),
            mapOf("x5c" to JsonArray(listOf(JsonPrimitive("AQ==")))),
        )
        val clientId = X509Hash("AQ", "AQ")

        val result = assertIs<ClientValidationResult.Failure>(
            clientId.authenticateX509Hash(
                clientId,
                RequestContext(
                    clientId = "AQ",
                    clientMetadata = ClientMetadata(),
                    requestObjectJws = requestObject,
                ),
                ClientIdTrustConfiguration(),
            )
        )

        assertEquals(ClientIdError.MissingX509TrustAnchors, result.error)
    }

    @Test
    fun `verifier attestation requires trusted issuer and validates proof of possession`() = runTest {
        DidService.minimalInit()
        val attesterKey = JWKKey.generate(KeyType.Ed25519)
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val attesterDid = DidService.registerByKey("key", attesterKey).did
        val attesterKid = DidService.resolveAuthenticationMethodId(attesterDid, attesterKey.getKeyId())
        val clientIdValue = "verifier.example"
        val responseUri = "https://verifier.example/response"
        val attestation = attesterKey.signJws(
            Json.encodeToString(
                buildJsonObject {
                    put("iss", attesterDid)
                    put("sub", clientIdValue)
                    put("exp", Clock.System.now().epochSeconds + 300)
                    put("redirect_uris", buildJsonArray { add(JsonPrimitive(responseUri)) })
                    put("cnf", buildJsonObject { put("jwk", verifierKey.getPublicKey().exportJWKObject()) })
                }
            ).encodeToByteArray(),
            mapOf(
                "typ" to JsonPrimitive("verifier-attestation+jwt"),
                "kid" to JsonPrimitive(attesterKid),
            ),
        )
        val requestObject = verifierKey.signJws(
            "{}".encodeToByteArray(),
            mapOf("jwt" to JsonPrimitive(attestation)),
        )
        val clientId = VerifierAttestation(clientIdValue, clientIdValue)
        val context = RequestContext(
            clientId = clientIdValue,
            clientMetadata = ClientMetadata(),
            requestObjectJws = requestObject,
            responseUri = responseUri,
        )
        val trust = ClientIdTrustConfiguration(trustedVerifierAttestationIssuers = setOf(attesterDid))

        assertIs<ClientValidationResult.Success>(clientId.authenticateVerifierAttestation(clientId, context, trust))
        assertIs<ClientValidationResult.Failure>(
            clientId.authenticateVerifierAttestation(
                clientId,
                context,
                ClientIdTrustConfiguration(),
            )
        )
    }

    @Test
    fun `DID request object verifies through crypto2 with exact verification method kid`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val kid = DidService.resolveAuthenticationMethodId(did, key.getKeyId())
        val requestObject = key.signJws(
            plaintext = "{}".encodeToByteArray(),
            headers = mapOf("kid" to JsonPrimitive(kid)),
        )
        val clientId = DecentralizedIdentifier(did, did)
        val context = RequestContext(
            clientId = did,
            clientMetadata = ClientMetadata(),
            requestObjectJws = requestObject,
        )

        assertIs<ClientValidationResult.Success>(
            clientId.authenticateDecentralizedIdentifier(clientId, context)
        )
        val parts = requestObject.split('.').toMutableList()
        parts[2] = (if (parts[2].first() == 'A') "B" else "A") + parts[2].drop(1)
        assertIs<ClientValidationResult.Failure>(
            clientId.authenticateDecentralizedIdentifier(
                clientId,
                context.copy(requestObjectJws = parts.joinToString(".")),
            )
        )
        assertIs<ClientValidationResult.Failure>(
            clientId.authenticateDecentralizedIdentifier(
                clientId,
                context.copy(
                    requestObjectJws = key.signJws(
                        plaintext = "{}".encodeToByteArray(),
                        headers = mapOf("kid" to JsonPrimitive("$did#missing")),
                    )
                ),
            )
        )
    }

    @Test
    fun `pre-registered request object must verify with trusted metadata JWK`() = runTest {
        val trustedKey = JWKKey.generate(KeyType.Ed25519)
        val attackerKey = JWKKey.generate(KeyType.Ed25519)
        val clientId = PreRegistered("registered-client")
        val metadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(listOf(trustedKey.getPublicKey().exportJWKObject())),
        )
        val metadataProvider: suspend (String) -> String? = {
            Json.encodeToString(ClientMetadata.serializer(), metadata)
        }
        val validRequest = trustedKey.signJws("{}".encodeToByteArray())
        val invalidRequest = attackerKey.signJws("{}".encodeToByteArray())

        assertIs<ClientValidationResult.Success>(
            ClientIdPrefixAuthenticator.authenticate(
                clientId,
                RequestContext(clientId.rawValue, requestObjectJws = validRequest),
                metadataProvider,
            )
        )
        val invalid = assertIs<ClientValidationResult.Failure>(
            ClientIdPrefixAuthenticator.authenticate(
                clientId,
                RequestContext(clientId.rawValue, requestObjectJws = invalidRequest),
                metadataProvider,
            )
        )
        assertEquals(ClientIdError.InvalidSignature, invalid.error)
    }

    @Test
    fun `pre-registered metadata without verification keys is rejected`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val clientId = PreRegistered("registered-client")

        val result = assertIs<ClientValidationResult.Failure>(
            ClientIdPrefixAuthenticator.authenticate(
                clientId,
                RequestContext(clientId.rawValue, requestObjectJws = key.signJws("{}".encodeToByteArray())),
                preRegisteredMetadataProvider = {
                    Json.encodeToString(ClientMetadata.serializer(), ClientMetadata())
                },
            )
        )

        assertIs<ClientIdError.InvalidMetadata>(result.error)
    }
}
