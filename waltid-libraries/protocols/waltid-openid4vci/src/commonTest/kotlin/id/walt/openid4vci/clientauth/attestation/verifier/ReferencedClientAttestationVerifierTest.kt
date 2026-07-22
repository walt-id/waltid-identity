package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("DEPRECATION")
class ReferencedClientAttestationVerifierTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `crypto2-only key reference verifies`() = runTest {
        val crypto2Key = crypto2Key("trusted")
        val jwt = CompactJws.sign("{}".encodeToByteArray(), crypto2Key, JwsAlgorithm.ES256)
        val decoded = CompactJws.decodeUnverified(jwt)
        val resolver = Crypto2ClientAttestationKeyReferenceResolver { _, _, _ -> listOf(crypto2Key) }
        keyReferenceConfig().toClientAttestationConfig(crypto2KeyReferenceResolver = resolver)
        val verifier = ReferencedClientAttestationVerifier(
            reference = "kms.key",
            crypto2Resolver = resolver,
        )

        assertEquals(
            ClientAttestationVerificationResult.Verified,
            verifier.verifyAttestationJwt(jwt, decoded.protectedHeader, payload(decoded.payload)),
        )
    }

    @Test
    fun `legacy-only key reference verifies`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val jwt = key.signJws("{}".encodeToByteArray(), emptyMap())
        val decoded = CompactJws.decodeUnverified(jwt)
        val resolver = ClientAttestationKeyReferenceResolver { _, _, _ -> listOf(key.getPublicKey()) }
        keyReferenceConfig().toClientAttestationConfig(keyReferenceResolver = resolver)
        val verifier = ReferencedClientAttestationVerifier(
            reference = "legacy.key",
            legacyResolver = resolver,
        )

        assertEquals(
            ClientAttestationVerificationResult.Verified,
            verifier.verifyAttestationJwt(jwt, decoded.protectedHeader, payload(decoded.payload)),
        )
    }

    @Test
    fun `crypto2 key reference is authoritative when both are configured`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val crypto2Key = crypto2Key("trusted")
        val jwt = CompactJws.sign("{}".encodeToByteArray(), crypto2Key, JwsAlgorithm.ES256)
        val decoded = CompactJws.decodeUnverified(jwt)
        var legacyInvocations = 0
        val verifier = ReferencedClientAttestationVerifier(
            reference = "kms.key",
            crypto2Resolver = Crypto2ClientAttestationKeyReferenceResolver { _, _, _ -> listOf(crypto2Key) },
            legacyResolver = ClientAttestationKeyReferenceResolver { _, _, _ ->
                legacyInvocations++
                listOf(legacyKey.getPublicKey())
            },
        )

        assertEquals(
            ClientAttestationVerificationResult.Verified,
            verifier.verifyAttestationJwt(jwt, decoded.protectedHeader, payload(decoded.payload)),
        )
        assertEquals(0, legacyInvocations)
    }

    @Test
    fun `invalid crypto2 signature does not downgrade to legacy key`() = runTest {
        val attacker = JWKKey.generate(KeyType.secp256r1)
        val attackerCrypto2 = crypto2Key("attacker")
        val jwt = CompactJws.sign("{}".encodeToByteArray(), attackerCrypto2, JwsAlgorithm.ES256)
        val decoded = CompactJws.decodeUnverified(jwt)
        var legacyInvocations = 0
        val verifier = ReferencedClientAttestationVerifier(
            reference = "kms.key",
            crypto2Resolver = Crypto2ClientAttestationKeyReferenceResolver { _, _, _ -> listOf(crypto2Key("trusted")) },
            legacyResolver = ClientAttestationKeyReferenceResolver { _, _, _ ->
                legacyInvocations++
                listOf(attacker.getPublicKey())
            },
        )

        assertEquals(
            ClientAttestationVerificationResult.Rejected("Client attestation signature is invalid"),
            verifier.verifyAttestationJwt(jwt, decoded.protectedHeader, payload(decoded.payload)),
        )
        assertEquals(0, legacyInvocations)
    }

    @Test
    fun `empty crypto2 resolution rejects without legacy fallback`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val jwt = key.signJws("{}".encodeToByteArray(), emptyMap())
        val decoded = CompactJws.decodeUnverified(jwt)
        var legacyInvocations = 0
        val verifier = ReferencedClientAttestationVerifier(
            reference = "kms.key",
            crypto2Resolver = Crypto2ClientAttestationKeyReferenceResolver { _, _, _ -> emptyList() },
            legacyResolver = ClientAttestationKeyReferenceResolver { _, _, _ ->
                legacyInvocations++
                listOf(key.getPublicKey())
            },
        )

        assertEquals(
            ClientAttestationVerificationResult.Rejected("Client attester is not trusted"),
            verifier.verifyAttestationJwt(jwt, decoded.protectedHeader, payload(decoded.payload)),
        )
        assertEquals(0, legacyInvocations)
    }

    @Test
    fun `key-reference config rejects when neither resolver is configured`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            keyReferenceConfig().toClientAttestationConfig()
        }
        assertEquals(
            "key-reference client attestation verification requires at least one key reference resolver",
            failure.message,
        )
    }

    private suspend fun crypto2Key(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private fun keyReferenceConfig() = ClientAttestationVerifierConfig(
        ClientAttestationVerificationMethod.KeyReference("kms.key")
    )

    private fun payload(bytes: ByteArray): JsonObject =
        Json.parseToJsonElement(bytes.decodeToString()).jsonObject
}
