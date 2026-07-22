@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.waltid.openid4vp.wallet.WalletCrypto2KeyAdapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelfIssuedIdTokenCrypto2Test {
    @Test
    fun `SPKI-exporting managed key builds JWK thumbprint subject`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("managed-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicKey = assertNotNull(generated.capabilities.publicKeyExporter).exportPublicKey()
        val spkiExportingKey = object : Crypto2Key {
            override val id = generated.id
            override val spec = generated.spec
            override val usages = generated.usages
            override val capabilities = generated.capabilities.copy(
                publicKeyExporter = PublicKeyExporter { publicKey.toSpkiDer(generated.spec) }
            )
        }
        val token = SelfIssuedIdTokenBuilder.build(
            authorizationRequest = AuthorizationRequest(
                clientId = "verifier",
                nonce = "nonce",
                clientMetadata = ClientMetadata(
                    subjectSyntaxTypesSupported = listOf("urn:ietf:params:oauth:jwk-thumbprint"),
                    idTokenSignedResponseAlg = "ES256",
                ),
            ),
            holderKey = spkiExportingKey,
            holderDid = null,
        )

        val verified = CompactJws.verify(token, generated, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject

        assertNotNull(payload["sub_jwk"])
        assertTrue(payload["sub"]?.jsonPrimitive?.content?.startsWith("urn:ietf:params:oauth:jwk-thumbprint:") == true)
    }

    @Test
    fun `local JWK self-issued ID token signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        assertNotNull(WalletCrypto2KeyAdapter.signingKey(legacyKey))
        val token = SelfIssuedIdTokenBuilder.build(
            authorizationRequest = AuthorizationRequest(
                clientId = "verifier",
                nonce = "nonce",
                clientMetadata = ClientMetadata(
                    subjectSyntaxTypesSupported = listOf("urn:ietf:params:oauth:jwk-thumbprint"),
                    idTokenSignedResponseAlg = "Ed25519",
                ),
            ),
            holderKey = legacyKey,
            holderDid = null,
        )
        val decoded = CompactJws.decodeUnverified(token)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
        val publicJwk = requireNotNull(payload["sub_jwk"]).jsonObject
        val verificationStoredKey = V1KeyMigration().migrate(
            recordId = KeyId("self-issued-verification"),
            serialized = buildJsonObject {
                put("type", "jwk")
                put("jwk", publicJwk)
            },
            usages = setOf(KeyUsage.VERIFY),
        )
        val verificationKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).restore(verificationStoredKey)
        val verified = CompactJws.verify(token, verificationKey, JwsAlgorithm.ED25519)

        assertEquals("JWT", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("verifier", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertFalse("d" in publicJwk)
        val encodedPublicJwk = EncodedKey.Jwk(
            BinaryData(Json.encodeToString(publicJwk).encodeToByteArray()),
            privateMaterial = false,
        )
        assertEquals(
            "urn:ietf:params:oauth:jwk-thumbprint:sha-256:${Jwk.sha256Thumbprint(encodedPublicJwk)}",
            payload["sub"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `public local JWK cannot sign self-issued ID token`() = runTest {
        val publicKey = JWKKey.generate(KeyType.Ed25519).getPublicKey()
        assertNull(WalletCrypto2KeyAdapter.signingKey(publicKey))

        assertFails {
            SelfIssuedIdTokenBuilder.build(
                authorizationRequest = AuthorizationRequest(
                    clientId = "verifier",
                    nonce = "nonce",
                    clientMetadata = ClientMetadata(
                        subjectSyntaxTypesSupported = listOf("urn:ietf:params:oauth:jwk-thumbprint"),
                        idTokenSignedResponseAlg = "Ed25519",
                    ),
                ),
                holderKey = publicKey,
                holderDid = null,
            )
        }
    }

    @Test
    fun `DID subject uses full authentication method kid`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val token = SelfIssuedIdTokenBuilder.build(
            authorizationRequest = AuthorizationRequest(
                clientId = "verifier",
                nonce = "nonce",
                clientMetadata = ClientMetadata(
                    subjectSyntaxTypesSupported = listOf("did:key"),
                    idTokenSignedResponseAlg = "Ed25519",
                ),
            ),
            holderKey = key,
            holderDid = did,
        )
        val decoded = CompactJws.decodeUnverified(token)

        assertEquals(did, Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject["sub"]?.jsonPrimitive?.content)
        assertEquals("$did#${did.removePrefix("did:key:")}", decoded.protectedHeader["kid"]?.jsonPrimitive?.content)
    }

    @Test
    fun `required ID token algorithm must match holder key`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)

        assertFails {
            SelfIssuedIdTokenBuilder.build(
                authorizationRequest = AuthorizationRequest(
                    clientId = "verifier",
                    nonce = "nonce",
                    clientMetadata = ClientMetadata(
                        subjectSyntaxTypesSupported = listOf("urn:ietf:params:oauth:jwk-thumbprint"),
                        idTokenSignedResponseAlg = "ES256",
                    ),
                ),
                holderKey = key,
                holderDid = null,
            )
        }
    }

    @Test
    fun `compatible requested ID token algorithm overrides key default`() = runTest {
        val key = JWKKey.generate(KeyType.RSA)
        val token = SelfIssuedIdTokenBuilder.build(
            authorizationRequest = AuthorizationRequest(
                clientId = "verifier",
                nonce = "nonce",
                clientMetadata = ClientMetadata(
                    subjectSyntaxTypesSupported = listOf("urn:ietf:params:oauth:jwk-thumbprint"),
                    idTokenSignedResponseAlg = "PS256",
                ),
            ),
            holderKey = key,
            holderDid = null,
        )

        assertEquals(JwsAlgorithm.PS256, CompactJws.decodeUnverified(token).algorithm)
    }

    @Test
    fun `JWK subject syntax is used when verifier does not support holder DID method`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val token = SelfIssuedIdTokenBuilder.build(
            authorizationRequest = AuthorizationRequest(
                clientId = "verifier",
                nonce = "nonce",
                clientMetadata = ClientMetadata(
                    subjectSyntaxTypesSupported = listOf("urn:ietf:params:oauth:jwk-thumbprint"),
                    idTokenSignedResponseAlg = "Ed25519",
                ),
            ),
            holderKey = key,
            holderDid = "did:key:unsupported-for-this-verifier",
        )
        val decoded = CompactJws.decodeUnverified(token)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject

        assertEquals(true, payload["sub"]?.jsonPrimitive?.content?.startsWith("urn:ietf:params:oauth:jwk-thumbprint:"))
        assertNotNull(payload["sub_jwk"])
        assertEquals(null, decoded.protectedHeader["kid"])
    }

    @Test
    fun `self-issued ID token rejects missing or incompatible subject syntax metadata`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        assertFails {
            SelfIssuedIdTokenBuilder.build(
                AuthorizationRequest(clientId = "verifier", nonce = "nonce"),
                key,
                null,
            )
        }
        assertFails {
            SelfIssuedIdTokenBuilder.build(
                AuthorizationRequest(
                    clientId = "verifier",
                    nonce = "nonce",
                    clientMetadata = ClientMetadata(subjectSyntaxTypesSupported = listOf("did:web")),
                ),
                key,
                null,
            )
        }
        assertTrue(
            ClientMetadata.fromJson(
                buildJsonObject { put("subject_syntax_types_supported", "did:key") }
            ).isFailure
        )
        assertFails {
            AuthorizationRequest(responseType = OpenID4VPResponseType.VP_TOKEN_ID_TOKEN)
        }
        AuthorizationRequest(
            responseType = OpenID4VPResponseType.VP_TOKEN_ID_TOKEN,
            scope = "openid",
            idTokenType = "subject_signed",
        )
    }
}
