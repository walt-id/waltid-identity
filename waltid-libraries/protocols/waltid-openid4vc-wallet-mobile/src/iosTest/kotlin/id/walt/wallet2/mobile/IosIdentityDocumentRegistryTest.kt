package id.walt.wallet2.mobile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosIdentityDocumentRegistryTest {
    @Test
    fun capabilitiesExposeOnlyAnnexCAndRequireSharedRegistrationConfiguration() {
        val capabilities = IosIdentityDocumentRegistry(null).capabilities

        assertFalse(capabilities.registrationAvailable)
        assertEquals("iOS/iPadOS 26", capabilities.minimumOsVersion)
        assertEquals(
            capabilities.platformAvailable,
            capabilities.capabilities.single {
                it.protocol == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C
            }.supported,
        )
        assertEquals(
            setOf(
                MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
                MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
            ),
            capabilities.capabilities.filterNot { it.supported }.mapTo(mutableSetOf()) { it.protocol },
        )
    }

    @Test
    fun registrationFailsClosedWithoutAnAppGroup() = runTest {
        val result = IosIdentityDocumentRegistry(null).replace("registry", emptyList())

        assertFalse(result.available)
        assertEquals("An App Group is required", result.reason)
    }
}
