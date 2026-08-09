package id.walt.wallet2.mobile

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        val suite = newSuite()
        val defaults = NSUserDefaults(suiteName = suite)
        val registry = IosIdentityDocumentRegistry(suite)
        try {
            assertFalse(registry.capabilities.platformAvailable)
            assertFalse(registry.capabilities.registrationAvailable)

            report(suite, IosIdentityDocumentRegistrationStatus.NOT_SUPPORTED)
            assertFalse(registry.capabilities.platformAvailable)

            report(suite, IosIdentityDocumentRegistrationStatus.AUTHORIZED)
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
    fun everyMdocCredentialBecomesItsOwnDesiredRegistration() = runTest {
        withSuite { suite ->
            report(suite, IosIdentityDocumentRegistrationStatus.AUTHORIZED)
            val result = IosIdentityDocumentRegistry(suite).replace(
                "registry-1",
                listOf(
                    registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL),
                    registryRecord("dc-b", "cred-b", MobileWalletDigitalCredentialFormat.MDOC, MDL),
                ),
            )

            assertTrue(result.available)
            assertEquals(2, result.registeredEntryCount)
            assertContentEquals(
                listOf(
                    IosIdentityDocumentProjectionRecord("dc-a", "cred-a", MDL),
                    IosIdentityDocumentProjectionRecord("dc-b", "cred-b", MDL),
                ),
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
            )
        }
    }

    @Test
    fun onlyMdocCredentialsEnterTheAppleProjection() = runTest {
        withSuite { suite ->
            IosIdentityDocumentRegistry(suite).replace(
                "registry-1",
                listOf(
                    registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL),
                    registryRecord("dc-b", "cred-b", MobileWalletDigitalCredentialFormat.SD_JWT_VC, "urn:eudi:pid:1"),
                ),
            )

            assertContentEquals(
                listOf("dc-a"),
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite).map { it.documentIdentifier },
            )
        }
    }

    @Test
    fun refreshingAnUnchangedWalletKeepsTheSameDocumentIdentifiers() = runTest {
        withSuite { suite ->
            val registry = IosIdentityDocumentRegistry(suite)
            val records = listOf(registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL))

            registry.replace("registry-1", records)
            val first = IosIdentityDocumentRegistry.readDesiredRegistrations(suite)
            registry.replace("registry-1", records)

            assertContentEquals(first, IosIdentityDocumentRegistry.readDesiredRegistrations(suite))
        }
    }

    @Test
    fun deletingOneCredentialRemovesOnlyItsDesiredRegistration() = runTest {
        withSuite { suite ->
            val registry = IosIdentityDocumentRegistry(suite)
            registry.replace(
                "registry-1",
                listOf(
                    registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL),
                    registryRecord("dc-b", "cred-b", MobileWalletDigitalCredentialFormat.MDOC, PID),
                ),
            )

            registry.replace(
                "registry-1",
                listOf(registryRecord("dc-b", "cred-b", MobileWalletDigitalCredentialFormat.MDOC, PID)),
            )

            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-b", "cred-b", PID)),
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
            )
        }
    }

    @Test
    fun desiredRegistrationsSurviveAnUnauthorizedPlatform() = runTest {
        withSuite { suite ->
            report(suite, IosIdentityDocumentRegistrationStatus.NOT_AUTHORIZED)

            val result = IosIdentityDocumentRegistry(suite).replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL)),
            )

            assertFalse(result.available)
            assertEquals("IdentityDocumentServices registration is not authorized", result.reason)
            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-a", "cred-a", MDL)),
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
            )
        }
    }

    @Test
    fun authorizationArrivingLaterNeedsNoCredentialReissuance() = runTest {
        withSuite { suite ->
            report(suite, IosIdentityDocumentRegistrationStatus.NOT_DETERMINED)
            val registry = IosIdentityDocumentRegistry(suite)
            registry.replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL)),
            )

            report(suite, IosIdentityDocumentRegistrationStatus.AUTHORIZED)

            assertTrue(registry.capabilities.registrationAvailable)
            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-a", "cred-a", MDL)),
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
            )
        }
    }

    @Test
    fun anEmptyWalletClearsTheDesiredRegistrations() = runTest {
        withSuite { suite ->
            val registry = IosIdentityDocumentRegistry(suite)
            registry.replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL)),
            )

            val result = registry.replace("registry-1", emptyList())

            assertEquals(0, result.registeredEntryCount)
            assertTrue(IosIdentityDocumentRegistry.readDesiredRegistrations(suite).isEmpty())
        }
    }

    @Test
    fun oneRegistryDoesNotOverwriteAnother() = runTest {
        withSuite { suite ->
            val registry = IosIdentityDocumentRegistry(suite)
            registry.replace(
                "registry-a",
                listOf(registryRecord("dc-a", "cred-a", MobileWalletDigitalCredentialFormat.MDOC, MDL)),
            )
            registry.replace(
                "registry-b",
                listOf(registryRecord("dc-b", "cred-b", MobileWalletDigitalCredentialFormat.MDOC, PID)),
            )

            registry.replace(
                "registry-a",
                listOf(registryRecord("dc-a2", "cred-a2", MobileWalletDigitalCredentialFormat.MDOC, MDL)),
            )

            assertEquals(
                setOf("dc-a2", "dc-b"),
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite)
                    .mapTo(mutableSetOf()) { it.documentIdentifier },
            )
        }
    }

    @Test
    fun theProjectionCarriesNoClaimValues() = runTest {
        withSuite { suite ->
            val defaults = NSUserDefaults(suiteName = suite)
            IosIdentityDocumentRegistry(suite).replace(
                "registry-1",
                listOf(
                    MobileWalletCredentialRegistryRecord(
                        registryEntryId = "dc-a",
                        credentialId = "cred-a",
                        format = MobileWalletDigitalCredentialFormat.MDOC,
                        type = MDL,
                        fields = listOf(
                            MobileWalletCredentialRegistryField(
                                path = listOf("org.iso.18013.5.1", "family_name"),
                                valueJson = "\"Mustermann\"",
                                selectivelyDisclosable = true,
                            ),
                        ),
                        displayName = "Driving Licence",
                    ),
                ),
            )

            val stored = defaults.stringForKey(IosIdentityDocumentRegistry.PROJECTION_STATE_KEY)
            assertTrue(stored != null && "Mustermann" !in stored, "claim values must not reach the App Group")
        }
    }

    private fun newSuite() = "id.walt.wallet.registry-test.${NSUUID().UUIDString}"

    private suspend fun withSuite(block: suspend (String) -> Unit) {
        val suite = newSuite()
        try {
            block(suite)
        } finally {
            NSUserDefaults(suiteName = suite).removePersistentDomainForName(suite)
        }
    }

    private fun report(suite: String, status: IosIdentityDocumentRegistrationStatus) =
        IosIdentityDocumentRegistry.reportRegistrationStatus(appGroupIdentifier = suite, status = status)

    private fun registryRecord(
        registryEntryId: String,
        credentialId: String,
        format: MobileWalletDigitalCredentialFormat,
        type: String,
    ) = MobileWalletCredentialRegistryRecord(
        registryEntryId = registryEntryId,
        credentialId = credentialId,
        format = format,
        type = type,
        fields = emptyList(),
        displayName = credentialId,
    )

    private companion object {
        const val MDL = "org.iso.18013.5.1.mDL"
        const val PID = "eu.europa.ec.eudi.pid.1"
    }
}
