package id.walt.crypto2.signum

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import kotlin.test.*

/** Opt-in physical-device regression; requires an entitled application host. */
class IosSecureEnclaveKeyTest {
    @Test
    fun unauthenticatedKeyRetainsRequestedPolicyAndSignsAfterReopening() = runTest {
        if ("--test-secure-enclave" !in NSProcessInfo.processInfo.arguments) {
            println("SKIP Secure Enclave regression: pass --test-secure-enclave on a physical device")
            return@runTest
        }
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        for (accessibility in SignumKeychainAccessibility.entries) {
            val alias = "enclave-no-auth-${NSUUID().UUIDString}"
            val policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED,
                platform = SignumPlatformPolicy.IosKeychain(accessibility))
            val backend = IosSignumKeyBackend()
            try {
                val created = backend.create(alias, spec, usages, policy)
                assertEquals(SignumSecurityLevel.SECURE_ENCLAVE, created.securityLevel)
                assertNull(created.privateKeyExporter)
                val reopened = assertNotNull(IosSignumKeyBackend().load(alias, spec, usages, policy))
                assertEquals(created.publicKey, reopened.publicKey)
                val challenge = "Secure Enclave: $accessibility".encodeToByteArray()
                val signature = reopened.sign(challenge, algorithm)
                assertTrue(created.verify(challenge, signature, algorithm))
                assertFalse(created.verify(challenge + 1, signature, algorithm))
                assertFailsWith<SignumKeyPolicyMismatchException> {
                    backend.load(alias, spec, usages, policy.copy(authentication = SignumAuthenticationPolicy.UserPresence()))
                }
            } finally { backend.delete(alias, policy) }
            assertNull(backend.load(alias, spec, usages, policy))
        }
    }
}
