@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.cose.Cose
import id.walt.cose.coseCompliantCbor
import id.walt.cose.protectedAlgorithm
import id.walt.cose.verifyDetached
import id.walt.cose.toCoseKey
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletCrypto2PresentationSigningTest {
    @Test
    fun `persisted crypto2 key signs when legacy platform adapter cannot`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.Ed25519)
        val crypto2Key = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("platform-key"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val issuerJwt = issuerKey.signJws(
            buildJsonObject { put("_sd_alg", "sha-256") }.toString().encodeToByteArray()
        )

        val token = WalletPresentFunctionality2.createKeyBindingJwt(
            disclosed = "$issuerJwt~",
            nonce = "nonce",
            audience = "verifier",
            selectedDisclosures = emptyList(),
            holderKey = crypto2Key,
        )

        assertEquals(
            "nonce",
            Json.parseToJsonElement(
                CompactJws.verify(token, crypto2Key, JwsAlgorithm.ED25519).payload.decodeToString()
            ).jsonObject["nonce"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `SD-JWT key binding signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        val signingKey = assertNotNull(WalletCrypto2KeyAdapter.signingKey(legacyKey))
        val issuerJwt = legacyKey.signJws(
            buildJsonObject { put("_sd_alg", "sha-256") }.toString().encodeToByteArray()
        )
        val token = WalletPresentFunctionality2.createKeyBindingJwt(
            disclosed = "$issuerJwt~",
            nonce = "nonce",
            audience = "verifier",
            selectedDisclosures = emptyList(),
            holderKey = legacyKey,
        )
        val publicJwk = signingKey.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val storedVerificationKey = V1KeyMigration().migrate(
            recordId = KeyId("kb-jwt-verification"),
            serialized = buildJsonObject {
                put("type", "jwk")
                put("jwk", Jwk.parse(publicJwk))
            },
            usages = setOf(KeyUsage.VERIFY),
        )
        val verificationKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).restore(storedVerificationKey)
        val verified = CompactJws.verify(token, verificationKey, JwsAlgorithm.ED25519)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject

        assertEquals("kb+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertEquals("verifier", payload["aud"]?.jsonPrimitive?.content)
        assertNotNull(payload["sd_hash"])
    }

    @Test
    fun `direct post response encrypts with strict crypto2 JWE`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val recipientKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("response-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val publicJwk = recipientKey.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val verifierJwk = JsonObject(
            Jwk.parse(publicJwk) + mapOf(
                "kid" to JsonPrimitive(recipientKey.id.value),
                "use" to JsonPrimitive("enc"),
                "alg" to JsonPrimitive("ECDH-ES"),
            )
        )
        val payload = buildJsonObject { put("state", "state") }
        val recipientPublicKey = EncodedKey.Jwk(
            BinaryData(verifierJwk.toString().encodeToByteArray()),
            privateMaterial = false,
        )
        val encrypted = WalletPresentFunctionality2.encryptDirectPostResponse(
            payload,
            recipientPublicKey,
            JweContentEncryption.A128GCM,
        )
        val decrypted = CompactJwe.decrypt(
            encrypted,
            recipientKey,
            setOf(JweContentEncryption.A128GCM),
        )

        assertEquals(recipientKey.id.value, decrypted.protectedHeader["kid"]?.jsonPrimitive?.content)
        assertEquals(payload, Json.parseToJsonElement(decrypted.plaintext.decodeToString()))
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.encryptDirectPostResponse(
                payload,
                EncodedKey.Jwk(
                    BinaryData(JsonObject(verifierJwk - "kid").toString().encodeToByteArray()),
                    privateMaterial = false,
                ),
                JweContentEncryption.A128GCM,
            )
        }
    }

    @Test
    fun `direct post response rejects private material even when mislabeled public`() = runTest {
        val privateJwk = buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("kid", "response-key")
            put("alg", "ECDH-ES")
            put("x", "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30")
            put("y", "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg")
            put("d", "AQ")
        }

        listOf(true, false).forEach { privateMaterial ->
            assertFailsWith<IllegalArgumentException> {
                WalletPresentFunctionality2.encryptDirectPostResponse(
                    payload = buildJsonObject { put("state", "state") },
                    recipientPublicKey = EncodedKey.Jwk(
                        BinaryData(privateJwk.toString().encodeToByteArray()),
                        privateMaterial = privateMaterial,
                    ),
                    contentEncryption = JweContentEncryption.A128GCM,
                )
            }
        }
    }

    @Test
    fun `direct post response selects supported content encryption and ECDH key operations`() {
        assertEquals(
            JweContentEncryption.A256GCM,
            WalletPresentFunctionality2.selectDirectPostContentEncryption(listOf("A192GCM", "A256GCM")),
        )
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.selectDirectPostContentEncryption(listOf("A192GCM"))
        }

        val verifierJwk = buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("kid", "response-key")
            put("use", "enc")
            put("alg", "ECDH-ES")
            put("key_ops", JsonArray(listOf(JsonPrimitive("deriveKey"))))
        }
        assertEquals(true, WalletPresentFunctionality2.isSupportedVerifierEncryptionJwk(verifierJwk))
        assertEquals(
            false,
            WalletPresentFunctionality2.isSupportedVerifierEncryptionJwk(
                JsonObject(verifierJwk + ("key_ops" to JsonArray(listOf(JsonPrimitive("sign")))))
            ),
        )
    }

    @Test
    fun `encrypted mdoc transcript binds verifier encryption key thumbprint`() {
        val request = AuthorizationRequest(clientId = "verifier", nonce = "nonce")
        val cleartextTranscript = MdocPresenter.buildSessionTranscript(request, "https://verifier/response", null)
        val encryptedTranscript = MdocPresenter.buildSessionTranscript(
            request,
            "https://verifier/response",
            "AQID",
        )

        assertFalse(
            coseCompliantCbor.encodeToByteArray(cleartextTranscript)
                .contentEquals(coseCompliantCbor.encodeToByteArray(encryptedTranscript))
        )
    }

    @Test
    fun `mdoc device authentication signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val crypto2Key = assertNotNull(WalletCrypto2KeyAdapter.signingKey(legacyKey))
        val publicJwk = crypto2Key.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val verificationKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).restore(
            V1KeyMigration().migrate(
                recordId = KeyId("mdoc-device-auth-verification"),
                serialized = buildJsonObject {
                    put("type", "jwk")
                    put("jwk", Jwk.parse(publicJwk))
                },
                usages = setOf(KeyUsage.VERIFY),
            )
        )
        val credential = MdocsCredential(
            credentialData = buildJsonObject { },
            signed = null,
            docType = "org.iso.18013.5.1.mDL",
        )
        val namespaces = DeviceNameSpaces(emptyMap())
        val transcript = MdocPresenter.buildSessionTranscript(
            AuthorizationRequest(clientId = "verifier", nonce = "nonce"),
            "https://verifier/response",
            null,
        )
        val deviceAuth = MdocPresenter.buildDeviceAuth(transcript, credential, namespaces, legacyKey)
        val signature = assertNotNull(deviceAuth.deviceSignature)
        val detachedPayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            transcript,
            credential.docType,
            ByteStringWrapper(namespaces),
        )

        assertEquals(Cose.Algorithm.ES256, signature.protectedAlgorithm())
        assertTrue(signature.verifyDetached(verificationKey, detachedPayload, Cose.Algorithm.ES256))
        assertFails {
            MdocPresenter.buildDeviceAuth(
                transcript,
                credential,
                namespaces,
                legacyKey,
                allowedAlgorithms = setOf(Cose.Algorithm.ES384),
            )
        }

        val deviceCoseKey = publicJwk.toCoseKey()
        assertFails {
            MdocCrypto.coseKeyToCrypto2Key(
                deviceCoseKey.copy(alg = Cose.Algorithm.ES384),
                Cose.Algorithm.ES256,
            )
        }
        assertFails {
            MdocCrypto.coseKeyToCrypto2Key(
                deviceCoseKey.copy(key_ops = emptyList()),
                Cose.Algorithm.ES256,
            )
        }
    }
}
