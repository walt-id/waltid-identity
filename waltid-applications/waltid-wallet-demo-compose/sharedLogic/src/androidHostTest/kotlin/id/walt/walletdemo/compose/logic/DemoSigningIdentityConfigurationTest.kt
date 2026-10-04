package id.walt.walletdemo.compose.logic

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoSigningIdentityConfigurationTest {
    @Test
    fun perUseBiometricsCanBeSelectedAndReopenedWithoutChangingTimedSigning() {
        val policy = WalletDemoSigningProtection.BiometricPerUse.toKeyUseAuthorizationPolicy()
        assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, policy)
        assertEquals(WalletDemoSigningProtection.BiometricPerUse, policy.toDemoSigningProtection())
        assertEquals(KeyUseAuthorizationPolicy.BiometricTimedReuse(10), WalletDemoSigningProtection.Biometric.toKeyUseAuthorizationPolicy())
        assertEquals(WalletDemoSigningProtection.BiometricPerUse, WalletDemoSigningProtection.parse("BiometricPerUse"))
    }

    @Test
    fun configuredChoicesRetainTheirAuthorizationConstraints() {
        val perUse = KeyUseAuthorizationPolicy.BiometricCurrentSet
        assertTrue(perUse in WalletDemoSigningProtectionMode.Required.alternativeAuthorizations())
        assertTrue(perUse in WalletDemoSigningProtectionMode.Optional.alternativeAuthorizations())
        assertFalse(KeyUseAuthorizationPolicy.None in WalletDemoSigningProtectionMode.Required.alternativeAuthorizations())
        assertTrue(WalletDemoSigningProtectionMode.Disabled.alternativeAuthorizations().isEmpty())
        assertEquals(WalletDemoSigningProtection.BiometricPerUse,
            WalletDemoSigningProtectionMode.Required.resolve(WalletDemoSigningProtection.BiometricPerUse))
    }
}
