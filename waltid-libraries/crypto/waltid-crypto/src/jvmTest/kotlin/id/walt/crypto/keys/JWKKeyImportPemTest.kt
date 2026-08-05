package id.walt.crypto.keys

import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PEM import must accept a private-key-only PEM.
 *
 * `JWK.parseFromPEMEncodedObjects` can only assemble a JWK from the PEM objects it is handed, so for EC
 * and Edwards keys it needs the public point and rejects a bare PKCS#8 private key with "Missing
 * PEM-encoded public key to construct JWK". That is what `openssl pkcs8 -topk8` and most tooling
 * produce, so it is the common input - the public half is derived from the private key instead.
 */
class JWKKeyImportPemTest {

    @Test
    fun `private key only PEM imports for every supported key type`() = runTest {
        listOf(
            KeyType.secp256r1,
            KeyType.secp384r1,
            KeyType.secp521r1,
            KeyType.secp256k1,
            // Ed25519 and RSA are handled by the import path too, but cannot be fixtured from this
            // library: exportPEM does not support Ed25519, and its RSA output is not re-readable
            // (BouncyCastle rejects it with "malformed sequence in RSA private key") - a separate
            // pre-existing export defect, unrelated to import.
        ).forEach { keyType ->
            val generated = JWKKey.generate(keyType)
            // Keep only the private block, i.e. what `openssl pkcs8 -topk8` emits.
            val privateOnlyPem = requireNotNull(
                Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", RegexOption.DOT_MATCHES_ALL)
                    .find(generated.exportPEM())
            ) { "$keyType PEM export has no private key block" }.value

            assertTrue(
                "PUBLIC KEY" !in privateOnlyPem,
                "$keyType fixture must contain only the private key",
            )
            val imported = JWKKey.importPEM(privateOnlyPem)
            assertTrue(imported.isSuccess, "$keyType private-only PEM import failed: ${imported.exceptionOrNull()}")

            val key = imported.getOrThrow()
            assertEquals(keyType, key.keyType, "Imported $keyType key changed type")
            assertTrue(key.hasPrivateKey, "Imported $keyType key lost its private material")
            // The derived public half must be the generated key's, not an unrelated one.
            assertEquals(
                generated.getPublicKey().getThumbprint(),
                key.getPublicKey().getThumbprint(),
                "Derived public key does not match the private key for $keyType",
            )
        }
    }

    @Test
    fun `combined private and public PEM still imports`() = runTest {
        val generated = JWKKey.generate(KeyType.secp256r1)
        val imported = JWKKey.importPEM(generated.exportPEM()).getOrThrow()

        assertEquals(generated.getThumbprint(), imported.getThumbprint())
        assertTrue(imported.hasPrivateKey)
    }

    @Test
    fun `public key only PEM still imports without private material`() = runTest {
        val generated = JWKKey.generate(KeyType.secp256r1)
        val publicPem = generated.getPublicKey().exportPEM()

        val imported = JWKKey.importPEM(publicPem).getOrThrow()

        assertEquals(generated.getPublicKey().getThumbprint(), imported.getThumbprint())
        assertTrue(!imported.hasPrivateKey)
    }
}
