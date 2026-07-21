package id.walt.wallet2.mobile

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            capabilities.capabilities
                .filter { it.protocol != MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C }
                .filterNot { it.supported }
                .mapTo(mutableSetOf()) { it.protocol },
        )
    }

    @Test
    fun registrationFailsClosedWithoutAnAppGroup() = runTest {
        val result = IosIdentityDocumentRegistry(null).replace("registry", emptyList())

        assertFalse(result.available)
        assertEquals("An App Group is required", result.reason)
    }

    @Test
    fun capabilitiesFollowTheReportedIdentityDocumentServicesRuntimeStatus() {
        val suite = "id.walt.wallet.registry-test.${NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suite)
        val registry = IosIdentityDocumentRegistry(suite)
        try {
            assertFalse(registry.capabilities.platformAvailable)
            assertFalse(registry.capabilities.registrationAvailable)

            defaults.setObject("notSupported", forKey = IosIdentityDocumentRegistry.REGISTRATION_STATUS_KEY)
            assertFalse(registry.capabilities.platformAvailable)

            defaults.setObject("authorized", forKey = IosIdentityDocumentRegistry.REGISTRATION_STATUS_KEY)
            assertTrue(registry.capabilities.platformAvailable)
            assertTrue(registry.capabilities.registrationAvailable)
            assertTrue(
                registry.capabilities.capabilities.single {
                    it.protocol == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C
                }.supported,
            )
        } finally {
            defaults.removePersistentDomainForName(suite)
        }
    }

    @Test
    fun registrationStoresOnlyDistinctMdocDocumentTypesInTheAppGroup() = runTest {
        val suite = "id.walt.wallet.registry-test.${NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suite)
        defaults.setObject("authorized", forKey = IosIdentityDocumentRegistry.REGISTRATION_STATUS_KEY)
        val registry = IosIdentityDocumentRegistry(suite)
        val records = listOf(
            registryRecord("mdoc-1", MobileWalletDigitalCredentialFormat.MDOC, "org.iso.18013.5.1.mDL"),
            registryRecord("mdoc-2", MobileWalletDigitalCredentialFormat.MDOC, "org.iso.18013.5.1.mDL"),
            registryRecord("sd-jwt", MobileWalletDigitalCredentialFormat.SD_JWT_VC, "urn:eudi:pid:1"),
        )
        try {
            val result = registry.replace("registry-1", records)

            assertTrue(result.available)
            assertEquals(1, result.registeredEntryCount)
            assertEquals(
                listOf("org.iso.18013.5.1.mDL"),
                defaults.stringArrayForKey(IosIdentityDocumentRegistry.DOCUMENT_TYPES_KEY),
            )
            assertEquals("registry-1", defaults.stringForKey(IosIdentityDocumentRegistry.REGISTRY_ID_KEY))
        } finally {
            defaults.removePersistentDomainForName(suite)
        }
    }

    private fun registryRecord(
        id: String,
        format: MobileWalletDigitalCredentialFormat,
        type: String,
    ) = MobileWalletCredentialRegistryRecord(
        registryEntryId = id,
        credentialId = id,
        format = format,
        type = type,
        fields = emptyList(),
        displayName = id,
    )
}
