package id.walt.x509.iso.documentsigner

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFails

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