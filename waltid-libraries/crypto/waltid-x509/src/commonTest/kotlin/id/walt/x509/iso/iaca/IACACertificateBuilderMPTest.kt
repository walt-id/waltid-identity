@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.assertIACABuilderDataEqualsCertificateData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class IACACertificateBuilderMPTest {

    @Test
    fun `build should succeed when IACA signing key is of valid keyType`() = runTest {
        IsoSharedTestHarnessValidResources
            .iacaSigningKeyMap()
            .values
            .forEach { validSigningKey ->

                IsoSharedTestHarnessValidResources.iacaBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                    signingKey = validSigningKey,
                ).run {
                    assertIACABuilderDataEqualsCertificateData(
                        profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                        decodedCert = this.decodedCertificate,
                    )
                }
            }
    }

    @Test
    fun `build should be safe when called concurrently`() = runTest {
        val bundles = List(20) {
            async {
                IsoSharedTestHarnessValidResources.iacaBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                    signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey(),
                )
            }
        }.awaitAll()

        assertTrue {
            bundles.all { it.certificateDer.bytes.size != 0 }
        }
        //all serial numbers are unique -> hence all generated certificates different
        assertEquals(
            expected = bundles.size,
            actual = bundles.map { it.decodedCertificate.serialNumber }.toSet().size,
        )
    }

    @Test
    fun `builder should throw when IACA signing key is of invalid keyType`() = runTest {
        listOf(
            KeyType.Ed25519,
            KeyType.RSA,
            KeyType.RSA3072,
            KeyType.RSA4096,
            KeyType.secp256k1,
        ).forEach { invalidKeyType ->
            val signingKey = KeyManager.createKey(
                generationRequest = KeyGenerationRequest(
                    backend = "jwk",
                    keyType = invalidKeyType,
                )
            )

            assertFails {
                IsoSharedTestHarnessValidResources.iacaBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                    signingKey = signingKey,
                )
            }
        }
    }
}
