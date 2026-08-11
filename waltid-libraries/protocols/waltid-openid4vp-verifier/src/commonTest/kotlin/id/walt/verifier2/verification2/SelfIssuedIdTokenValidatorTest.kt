package id.walt.verifier2.verification2

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.did.dids.resolver.Crypto2DidKeyResolver
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

class SelfIssuedIdTokenValidatorTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))
    private val now = Instant.parse("2026-07-18T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@SelfIssuedIdTokenValidatorTest.now
    }

    @Test
    fun `valid P256 thumbprint subject verifies`() = runTest {
        val key = generateKey()
        val token = token(key)

        SelfIssuedIdTokenValidator.validate(
            idToken = token,
            expectedNonce = "nonce",
            expectedAudience = "client",
            clockSkewSeconds = 0,
            clock = clock,
        )
    }

    @Test
    fun `wrong signature thumbprint nonce audience and expiry fail`() = runTest {
        val key = generateKey()
        val valid = token(key)
        val signatureParts = valid.split('.').toMutableList().apply {
            this[2] = (if (this[2].first() == 'A') "B" else "A") + this[2].drop(1)
        }
        val invalidTokens = listOf(
            signatureParts.joinToString("."),
            token(key, subject = "urn:ietf:params:oauth:jwk-thumbprint:sha-256:wrong"),
            token(key, nonce = "wrong"),
            token(key, audience = "wrong"),
            token(key, expiresAt = now.epochSeconds - 1),
        )

        invalidTokens.forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                SelfIssuedIdTokenValidator.validate(
                    idToken = invalid,
                    expectedNonce = "nonce",
                    expectedAudience = "client",
                    clockSkewSeconds = 0,
                    clock = clock,
                )
            }
        }
    }

    @Test
    fun `malformed JWS and private sub_jwk fail`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            SelfIssuedIdTokenValidator.validate("not-a-jws", "nonce", "client", clock = clock)
        }

        val key = generateKey()
        val privateJwk = assertIsJwk(key, privateKey = true)
        val publicJwk = assertIsJwk(key, privateKey = false)
        val subject = "urn:ietf:params:oauth:jwk-thumbprint:sha-256:${Jwk.sha256Thumbprint(publicJwk)}"
        val payload = claims(subject, privateJwk.toJsonObject(), "nonce", "client", now.epochSeconds + 60)
        val token = CompactJws.sign(Json.encodeToString(payload).encodeToByteArray(), key, JwsAlgorithm.ES256)

        assertFailsWith<IllegalArgumentException> {
            SelfIssuedIdTokenValidator.validate(token, "nonce", "client", clockSkewSeconds = 0, clock = clock)
        }
    }

    @Test
    fun `DID subject requires matching resolved verification method`() = runTest {
        val signingKey = generateKey()
        val publicMaterial = signingKey.capabilities.publicKeyExporter!!.exportPublicKey()
        val publicKey = runtime.restore(
            signingKey.storedKey.copy(
                id = KeyId("did:example:123#key-1"),
                usages = setOf(KeyUsage.VERIFY),
                material = publicMaterial,
            )
        )
        val subject = "did:example:123"
        val payload = buildJsonObject {
            put("iss", subject)
            put("sub", subject)
            put("aud", "client")
            put("nonce", "nonce")
            put("exp", now.epochSeconds + 60)
        }
        val token = CompactJws.sign(
            Json.encodeToString(payload).encodeToByteArray(),
            signingKey,
            JwsAlgorithm.ES256,
            buildJsonObject { put("kid", publicKey.id.value) },
        )
        val resolver = Crypto2DidKeyResolver { setOf(publicKey) }

        SelfIssuedIdTokenValidator.validate(
            token,
            "nonce",
            "client",
            clockSkewSeconds = 0,
            clock = clock,
            didKeyResolver = resolver,
        )
        assertFailsWith<IllegalArgumentException> {
            SelfIssuedIdTokenValidator.validate(token, "nonce", "client", clock = clock)
        }
        assertFailsWith<IllegalArgumentException> {
            SelfIssuedIdTokenValidator.validate(
                token,
                "nonce",
                "client",
                clock = clock,
                didKeyResolver = Crypto2DidKeyResolver { emptySet() },
            )
        }
    }

    private suspend fun token(
        key: Key,
        subject: String? = null,
        nonce: String = "nonce",
        audience: String = "client",
        expiresAt: Long = now.epochSeconds + 60,
    ): String {
        val publicJwk = assertIsJwk(key, privateKey = false)
        val actualSubject = subject
            ?: "urn:ietf:params:oauth:jwk-thumbprint:sha-256:${Jwk.sha256Thumbprint(publicJwk)}"
        val payload = claims(actualSubject, publicJwk.toJsonObject(), nonce, audience, expiresAt)
        return CompactJws.sign(Json.encodeToString(payload).encodeToByteArray(), key, JwsAlgorithm.ES256)
    }

    private fun claims(
        subject: String,
        jwk: JsonObject,
        nonce: String,
        audience: String,
        expiresAt: Long,
    ): JsonObject = buildJsonObject {
        put("iss", subject)
        put("sub", subject)
        put("aud", audience)
        put("nonce", nonce)
        put("exp", expiresAt)
        put("sub_jwk", jwk)
    }

    private suspend fun generateKey(): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("siop-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun assertIsJwk(key: Key, privateKey: Boolean): EncodedKey.Jwk = if (privateKey) {
        key.capabilities.privateKeyExporter?.exportPrivateKey() as EncodedKey.Jwk
    } else {
        key.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
    }

    private fun EncodedKey.Jwk.toJsonObject(): JsonObject = Json.parseToJsonElement(data.toByteArray().decodeToString()) as JsonObject
}
