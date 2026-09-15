package id.walt.crypto2.signum

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import kotlin.test.*

class IosKeyPolicyTest {
    private val spec = KeySpec.Ec(EcCurve.P256)
    private val policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.DISCOURAGED)

    @Test
    fun changingPromptTextDoesNotChangeSecurityPolicy() {
        val original = policy.copy(authentication = SignumAuthenticationPolicy.UserPresence())
        val localized = original.copy(authentication = (original.authentication as SignumAuthenticationPolicy.UserPresence)
            .copy(prompt = "Bitte bestätigen", cancelText = "Abbrechen"))
        assertEquals(original.immutableIosPolicy(), localized.immutableIosPolicy())
    }

    @Test
    fun routingPreservesExportabilityAndPerKeyReuse() {
        assertEquals(IosKeyEngine.APPLE_KEYCHAIN, iosKeyEngine(spec, policy))
        val hardware = policy.copy(hardware = SignumHardwarePolicy.REQUIRED)
        assertEquals(IosKeyEngine.SIGNUM, iosKeyEngine(spec, hardware))
        assertEquals(IosKeyEngine.SIGNUM, iosKeyEngine(spec, hardware.copy(authentication = SignumAuthenticationPolicy.UserPresence())))
        assertEquals(IosKeyEngine.APPLE_KEYCHAIN, iosKeyEngine(spec, hardware.copy(
            authentication = SignumAuthenticationPolicy.UserPresence(timeoutSeconds = 10))))
        assertEquals(IosKeyEngine.APPLE_KEYCHAIN, iosKeyEngine(spec, hardware.copy(
            platform = SignumPlatformPolicy.IosKeychain(accessGroup = "test.group"))))
        assertEquals(IosKeyEngine.APPLE_KEYCHAIN, iosKeyEngine(spec, hardware.copy(
            platform = SignumPlatformPolicy.IosKeychain(SignumKeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY))))
    }
}
