@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.time.ExperimentalTime

class IACACertificateBuilderMPTest {

    @Test
    fun `build should succeed when IACA signing key is of valid keyType`() = runTest {
        IsoSharedTestHarnessValidResources
            .iacaSigningKeyMap()
            .values
            .forEach { validSigningKey ->
                //implicit success
                IsoSharedTestHarnessValidResources.iacaBuilder.build(
                    profileData = IsoSharedTestHarnessValidResources.iacaProfileData,
                    signingKey = validSigningKey,
                )
            }
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