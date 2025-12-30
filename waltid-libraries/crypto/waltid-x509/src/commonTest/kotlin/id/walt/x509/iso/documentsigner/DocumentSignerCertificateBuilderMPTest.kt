package id.walt.x509.iso.documentsigner

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DocumentSignerCertificateBuilderMPTest {

    @Test
    fun `build should succeed when Document signer public key is of valid keyType`() = runTest {
        IsoSharedTestHarnessValidResources
            .dsKeyMap()
            .values
            .forEach { validDsKey ->

                //implicit success
                IsoSharedTestHarnessValidResources.dsBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.dsProfileData,
                    publicKey = validDsKey.getPublicKey(),
                    iacaSignerSpec = IACASignerSpecification(
                        profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                        signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey(),
                    ),
                )
            }
    }

    @Test
    fun `build should be safe when called concurrently`() = runTest {
        val iacaSignerSpec = IACASignerSpecification(
            profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
            signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey(),
        )

        val bundles = List(20) {
            async {
                IsoSharedTestHarnessValidResources.dsBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.dsProfileData,
                    publicKey = IsoSharedTestHarnessValidResources.dsSecp256r1PublicKey(),
                    iacaSignerSpec = iacaSignerSpec,
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
    fun `build should throw when Document signer public key is of invalid keyType`() = runTest {

        listOf(
            KeyType.RSA,
            KeyType.RSA3072,
            KeyType.RSA4096,
            KeyType.secp256k1,
        ).forEach { invalidKeyType ->
            val invalidDsKey = KeyManager.createKey(
                generationRequest = KeyGenerationRequest(
                    backend = "jwk",
                    keyType = invalidKeyType,
                )
            )

            assertFails {
                IsoSharedTestHarnessValidResources.dsBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.dsProfileData,
                    publicKey = invalidDsKey.getPublicKey(),
                    iacaSignerSpec = IACASignerSpecification(
                        profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                        signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey(),
                    ),
                )
            }
        }
    }
}
