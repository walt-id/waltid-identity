package id.walt.verifier2.handlers.authrequest

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Verifier2RequestUriPostCrypto2Test {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val migration = V1KeyMigration()

    @Test
    fun `direct crypto2 re-sign verifies existing JAR and preserves protected headers`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val crypto2Key = runtime.restore(
            migration.migrate(
                recordId = KeyId(legacyKey.getKeyId()),
                serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val headers = buildJsonObject {
            put("alg", "ES256")
            put("typ", "oauth-authz-req+jwt")
            put("kid", legacyKey.getKeyId())
            put("x5c", JsonArray(listOf(JsonPrimitive("certificate"))))
        }
        val original = Verifier2RequestUriPostHandler.signRequestObject(
            crypto2Key,
            buildJsonObject { put("aud", "https://self-issued.me/v2") },
            headers,
        )
        Verifier2RequestUriPostHandler.verifyExistingRequestObject(original, crypto2Key)
        val resigned = Verifier2RequestUriPostHandler.signRequestObject(
            crypto2Key,
            buildJsonObject { put("wallet_nonce", "nonce") },
            CompactJws.decodeUnverified(original).protectedHeader,
        )
        val decoded = CompactJws.decodeUnverified(resigned)

        assertEquals("oauth-authz-req+jwt", decoded.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("certificate", decoded.protectedHeader["x5c"]?.let { (it as JsonArray).single().jsonPrimitive.content })

        val replacement = JWKKey.generate(KeyType.secp256r1)
        val replacementWithSameId = runtime.restore(
            migration.migrate(
                recordId = KeyId(legacyKey.getKeyId()),
                serialized = KeySerialization.serializeKeyToJson(replacement).jsonObject,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.verifyExistingRequestObject(original, replacementWithSameId)
        }
    }

    @Test
    fun `local JWK request object re-signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val token = Verifier2RequestUriPostHandler.signRequestObject(
            signingKey = legacyKey,
            payload = buildJsonObject { put("wallet_nonce", "nonce") },
            headers = buildJsonObject {
                put("alg", "ES256")
                put("typ", "oauth-authz-req+jwt")
                put("kid", legacyKey.getKeyId())
            },
        )
        val publicKey = legacyKey.getPublicKey()
        val verificationStoredKey = V1KeyMigration().migrate(
            recordId = KeyId(publicKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(publicKey).jsonObject,
            usages = setOf(KeyUsage.VERIFY),
        )
        val verificationKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).restore(verificationStoredKey)
        val verified = CompactJws.verify(token, verificationKey, JwsAlgorithm.ES256)

        assertEquals("oauth-authz-req+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals(
            "nonce",
            Json.parseToJsonElement(verified.payload.decodeToString())
                .jsonObject["wallet_nonce"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `public local JWK cannot re-sign request object`() = runTest {
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()

        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.signRequestObject(
                signingKey = publicKey,
                payload = buildJsonObject { put("wallet_nonce", "nonce") },
                headers = buildJsonObject {
                    put("alg", "ES256")
                    put("kid", publicKey.getKeyId())
                },
            )
        }
    }

    @Test
    fun `resolved key must match original request kid`() = runTest {
        val originalKey = JWKKey.generate(KeyType.secp256r1)
        val replacementKey = JWKKey.generate(KeyType.secp256r1)

        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.signRequestObject(
                signingKey = replacementKey,
                payload = buildJsonObject { put("wallet_nonce", "nonce") },
                headers = buildJsonObject {
                    put("alg", "ES256")
                    put("kid", originalKey.getKeyId())
                },
            )
        }
    }

    @Test
    fun `existing request signature rejects replacement key with same kid`() = runTest {
        val originalKey = JWKKey.generate(KeyType.secp256r1)
        val originalToken = originalKey.signJws(
            plaintext = buildJsonObject { put("aud", "https://self-issued.me/v2") }.toString().encodeToByteArray(),
            headers = mapOf(
                "alg" to JsonPrimitive("ES256"),
                "kid" to JsonPrimitive(originalKey.getKeyId()),
            ),
        )
        Verifier2RequestUriPostHandler.verifyExistingRequestObject(originalToken, originalKey)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val replacementWithSameKid = JWKKey.importJWK(
            JsonObject(replacement.exportJWKObject() + ("kid" to JsonPrimitive(originalKey.getKeyId()))).toString()
        ).getOrThrow()

        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.verifyExistingRequestObject(originalToken, replacementWithSameKid)
        }
    }
}
