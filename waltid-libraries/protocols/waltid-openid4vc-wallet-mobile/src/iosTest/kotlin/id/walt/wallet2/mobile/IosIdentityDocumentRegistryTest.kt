package id.walt.wallet2.mobile

import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentProjectionRecord
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentProjectionResult
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentProjectionState
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentRegistrationStatus
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentRegistry
import id.walt.wallet2.mobile.digitalcredentials.RegistryField
import id.walt.wallet2.mobile.digitalcredentials.RegistryEntry
import id.walt.wallet2.mobile.digitalcredentials.RegistryEntryDisplay
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialFormat
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialProtocols
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import id.walt.wallet2.mobile.digitalcredentials.CredentialRegistrationResult
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

class IosIdentityDocumentRegistryTest {
    @Test
    fun cancelledWalletCannotReplaceTheActiveWalletProjection() = runTest {
        withSuite { suite ->
            val active = IosIdentityDocumentRegistry(suite, "active-wallet")
            active.replace("active", emptyList())
            val stale = launch {
                currentCoroutineContext().cancel()
                IosIdentityDocumentRegistry(suite, "old-wallet").replace("old", emptyList())
            }
            stale.join()
            assertEquals("active-wallet", assertIs<IosIdentityDocumentProjectionResult.Published>(
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite)).state.walletId)
        }
    }

    @Test
    fun capabilitiesExposeOnlyAnnexCAndRequireSharedRegistrationConfiguration() {
        val capabilities = registry(null).capabilities

        assertFalse(capabilities.registrationAvailable)
        assertEquals("iOS/iPadOS 26", capabilities.minimumOsVersion)
        assertEquals(
            capabilities.platformAvailable,
            capabilities.capabilities.single {
                it.protocol == DigitalCredentialProtocols.ISO_MDOC_ANNEX_C
            }.supported,
        )
        assertEquals(
            setOf(
                DigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                DigitalCredentialProtocols.OPENID4VP_SIGNED,
                DigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
            ),
            capabilities.capabilities
                .filter { it.protocol != DigitalCredentialProtocols.ISO_MDOC_ANNEX_C }
                .filterNot { it.supported }
                .mapTo(mutableSetOf()) { it.protocol },
        )
    }

    @Test
    fun registrationFailsClosedWithoutAnAppGroup() = runTest {
        val result = registry(null).replace("registry", emptyList())

        assertFalse(result.available)
        assertEquals("An App Group is required", result.reason)
    }

    @Test
    fun capabilitiesFollowTheReportedIdentityDocumentServicesRuntimeStatus() {
        val suite = newSuite()
        val defaults = NSUserDefaults(suiteName = suite)
        val registry = registry(suite)
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
                    it.protocol == DigitalCredentialProtocols.ISO_MDOC_ANNEX_C
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
            val result = registry(suite).replace(
                "registry-1",
                listOf(
                    registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL),
                    registryRecord("dc-b", "cred-b", DigitalCredentialFormat.MDOC, MDL),
                ),
            )

            assertTrue(result.available)
            assertEquals(2, result.entryCount)
            assertContentEquals(
                listOf(
                    IosIdentityDocumentProjectionRecord("dc-a", MDL),
                    IosIdentityDocumentProjectionRecord("dc-b", MDL),
                ),
                published(suite).registrations,
            )
        }
    }

    @Test
    fun theProjectionNamesTheWalletTheExtensionHasToOpen() = runTest {
        withSuite { suite ->
            // The extension gets no wallet id from Apple's request context, so the only thing that keeps
            // a host started with a non-default wallet id presentable is this field.
            registry(suite, walletId = "test-123").replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL)),
            )

            assertEquals("test-123", published(suite).walletId)
        }
    }

    @Test
    fun onlyMdocCredentialsEnterTheAppleProjection() = runTest {
        withSuite { suite ->
            registry(suite).replace(
                "registry-1",
                listOf(
                    registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL),
                    registryRecord("dc-b", "cred-b", DigitalCredentialFormat.SD_JWT_VC, "urn:eudi:pid:1"),
                ),
            )

            assertContentEquals(
                listOf("dc-a"),
                published(suite).registrations.map { it.documentIdentifier },
            )
        }
    }

    @Test
    fun refreshingAnUnchangedWalletKeepsTheSameDocumentIdentifiers() = runTest {
        withSuite { suite ->
            val registry = registry(suite)
            val records = listOf(registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL))

            registry.replace("registry-1", records)
            val first = published(suite).registrations
            registry.replace("registry-1", records)

            assertContentEquals(first, published(suite).registrations)
        }
    }

    @Test
    fun deletingOneCredentialRemovesOnlyItsDesiredRegistration() = runTest {
        withSuite { suite ->
            val registry = registry(suite)
            registry.replace(
                "registry-1",
                listOf(
                    registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL),
                    registryRecord("dc-b", "cred-b", DigitalCredentialFormat.MDOC, PID),
                ),
            )

            registry.replace(
                "registry-1",
                listOf(registryRecord("dc-b", "cred-b", DigitalCredentialFormat.MDOC, PID)),
            )

            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-b", PID)),
                published(suite).registrations,
            )
        }
    }

    @Test
    fun desiredRegistrationsSurviveAnUnauthorizedPlatform() = runTest {
        withSuite { suite ->
            report(suite, IosIdentityDocumentRegistrationStatus.NOT_AUTHORIZED)

            val result = registry(suite).replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL)),
            )

            val staged = assertIs<CredentialRegistrationResult.ProjectionSaved>(result)
            assertEquals(1, staged.entryCount)
            assertFalse(staged.available)
            assertEquals("IdentityDocumentServices registration is not authorized", staged.reason)
            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-a", MDL)),
                published(suite).registrations,
            )
        }
    }

    @Test
    fun authorizationArrivingLaterNeedsNoCredentialReissuance() = runTest {
        withSuite { suite ->
            report(suite, IosIdentityDocumentRegistrationStatus.NOT_DETERMINED)
            val registry = registry(suite)
            registry.replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL)),
            )

            report(suite, IosIdentityDocumentRegistrationStatus.AUTHORIZED)

            assertTrue(registry.capabilities.registrationAvailable)
            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-a", MDL)),
                published(suite).registrations,
            )
        }
    }

    @Test
    fun anEmptyWalletPublishesAnEmptyProjectionRatherThanNone() = runTest {
        withSuite { suite ->
            val registry = registry(suite)
            registry.replace(
                "registry-1",
                listOf(registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL)),
            )

            val result = registry.replace("registry-1", emptyList())

            assertEquals(0, result.entryCount)
            // Published-and-empty, not Missing: the reconciler unregisters on the first and leaves Apple's
            // store alone on the second, and a wallet whose last mdoc was deleted means the former.
            assertEquals(emptyList(), published(suite).registrations)
        }
    }

    @Test
    fun theLatestWalletToRefreshReplacesTheProjectionInsteadOfMergingIntoIt() = runTest {
        withSuite { suite ->
            // A merged projection would describe registrations the extension could not fulfil: Apple
            // passes it no documentIdentifier, so it cannot tell which wallet a request belongs to and
            // can only serve the one wallet the projection names.
            registry(suite, walletId = "wallet-a").replace(
                "registry-a",
                listOf(registryRecord("dc-a", "cred-a", DigitalCredentialFormat.MDOC, MDL)),
            )
            registry(suite, walletId = "wallet-b").replace(
                "registry-b",
                listOf(registryRecord("dc-b", "cred-b", DigitalCredentialFormat.MDOC, PID)),
            )

            val state = published(suite)
            assertEquals("wallet-b", state.walletId)
            assertContentEquals(
                listOf(IosIdentityDocumentProjectionRecord("dc-b", PID)),
                state.registrations,
            )
        }
    }

    @Test
    fun anUnwrittenAppGroupReadsAsMissingRatherThanAsAnEmptyWallet() = runTest {
        withSuite { suite ->
            assertIs<IosIdentityDocumentProjectionResult.Missing>(
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
            )
        }
    }

    @Test
    fun anUndecodableProjectionReadsAsMalformedRatherThanAsAnEmptyWallet() = runTest {
        withSuite { suite ->
            // Decoded to empty, this would instruct the reconciler to unregister every managed document
            // the wallet can still present - so the corrupt case has to stay distinguishable.
            NSUserDefaults(suiteName = suite).setObject(
                "{\"walletId\":\"test-123\",\"registrations\":",
                forKey = IosIdentityDocumentRegistry.PROJECTION_STATE_KEY,
            )

            val result = IosIdentityDocumentRegistry.readDesiredRegistrations(suite)

            assertIs<IosIdentityDocumentProjectionResult.Malformed>(result)
            assertTrue(result.reason.isNotEmpty(), "a malformed projection has to carry a loggable reason")
        }
    }

    @Test
    fun aProjectionMissingTheWalletIdReadsAsMalformed() = runTest {
        withSuite { suite ->
            // Well-formed JSON but not this contract: an older build's projection carried no wallet id,
            // and guessing one would open the wrong database.
            NSUserDefaults(suiteName = suite).setObject(
                "{\"registrations\":[]}",
                forKey = IosIdentityDocumentRegistry.PROJECTION_STATE_KEY,
            )

            assertIs<IosIdentityDocumentProjectionResult.Malformed>(
                IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
            )
        }
    }

    @Test
    fun theProjectionCarriesNoClaimValues() = runTest {
        withSuite { suite ->
            val defaults = NSUserDefaults(suiteName = suite)
            registry(suite).replace(
                "registry-1",
                listOf(
                    RegistryEntry(
                        registryEntryId = "dc-a",
                        credentialId = "cred-a",
                        format = DigitalCredentialFormat.MDOC,
                        type = MDL,
                        fields = listOf(
                            RegistryField(
                                path = listOf("org.iso.18013.5.1", "family_name"),
                                valueJson = "\"Mustermann\"",
                                selectivelyDisclosable = true,
                            ),
                        ),
                        display = RegistryEntryDisplay("Driving Licence"),
                    ),
                ),
            )

            val stored = defaults.stringForKey(IosIdentityDocumentRegistry.PROJECTION_STATE_KEY)
            assertTrue(stored != null && "Mustermann" !in stored, "claim values must not reach the App Group")
        }
    }

    private fun newSuite() = "id.walt.wallet.registry-test.${NSUUID().UUIDString}"

    private fun registry(appGroupIdentifier: String?, walletId: String = "default") =
        IosIdentityDocumentRegistry(appGroupIdentifier = appGroupIdentifier, walletId = walletId)

    private fun published(suite: String): IosIdentityDocumentProjectionState =
        assertIs<IosIdentityDocumentProjectionResult.Published>(
            IosIdentityDocumentRegistry.readDesiredRegistrations(suite),
        ).state

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
        format: DigitalCredentialFormat,
        type: String,
    ) = RegistryEntry(
        registryEntryId = registryEntryId,
        credentialId = credentialId,
        format = format,
        type = type,
        fields = emptyList(),
        display = RegistryEntryDisplay(credentialId),
    )

    private companion object {
        const val MDL = "org.iso.18013.5.1.mDL"
        const val PID = "eu.europa.ec.eudi.pid.1"
    }
}
