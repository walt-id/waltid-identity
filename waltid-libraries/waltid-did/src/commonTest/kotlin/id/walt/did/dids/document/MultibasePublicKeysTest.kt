package id.walt.did.dids.document

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Decoding `did:key` identifiers without any crypto1 involvement, which is what makes DID-based verification work
 * on every platform - the v1 path fails on JS because it feeds raw key bytes to npm-jose's `importSPKI`.
 *
 * The identifiers are the W3C did:key specification's own examples.
 */
class MultibasePublicKeysTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `decodes Ed25519 did key into a usable verification key`() = runTest {
        val decoded = MultibasePublicKeys.decode(ED25519_DID)

        assertEquals(KeySpec.Edwards(EdwardsCurve.ED25519), decoded.spec)
        assertEquals("OKP", decoded.member("kty"))
        assertEquals("Ed25519", decoded.member("crv"))
        assertTrue(decoded.jwk.privateMaterial.not())
        assertNotNull(restoreForVerification(decoded).capabilities.verifier)
    }

    @Test
    fun `decodes P-256 did key from its compressed point`() = runTest {
        val decoded = MultibasePublicKeys.decode(P256_DID)

        assertEquals(KeySpec.Ec(EcCurve.P256), decoded.spec)
        assertEquals("EC", decoded.member("kty"))
        assertEquals("P-256", decoded.member("crv"))
        // Decompression happened: a compressed point carries no y coordinate.
        assertNotNull(decoded.member("y"))
        val key = restoreForVerification(decoded)
        assertTrue(
            !assertNotNull(key.capabilities.verifier).verify(
                "message".encodeToByteArray(),
                ByteArray(64),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
            ),
            "a zero signature must not verify",
        )
    }

    @Test
    fun `accepts a fragment and rejects unsupported encodings`() = runTest {
        val withFragment = MultibasePublicKeys.decode("$ED25519_DID#${ED25519_DID.removePrefix("did:key:")}")
        assertEquals(KeySpec.Edwards(EdwardsCurve.ED25519), withFragment.spec)

        assertFails { MultibasePublicKeys.decode("did:key:QmUnsupportedBase") }
        assertFails { MultibasePublicKeys.decode("did:key:z") }
    }

    private suspend fun restoreForVerification(decoded: MultibasePublicKeys.DecodedPublicKey) =
        runtime.restore(decoded.jwk.toStoredSoftwareKey(KeyId("multibase-key"), setOf(KeyUsage.VERIFY)))

    private fun MultibasePublicKeys.DecodedPublicKey.member(name: String): String? =
        Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()).jsonObject[name]?.jsonPrimitive?.content

    private companion object {
        const val ED25519_DID = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        const val P256_DID = "did:key:zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169"
    }
}
