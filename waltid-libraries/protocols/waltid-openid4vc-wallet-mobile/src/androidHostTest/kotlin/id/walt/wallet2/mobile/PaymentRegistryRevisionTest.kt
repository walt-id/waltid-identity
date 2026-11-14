package id.walt.wallet2.mobile

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.consent.PaymentConsentMetadata
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.wallet2.persistence.keys.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.test.*

class PaymentRegistryRevisionTest {
    @Test
    fun changedOrUnavailableIssuerInstructionsRejectThePreviousSystemSelection() = runTest {
        withContext(Dispatchers.Default) {
            val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
            val key = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId("registry-holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            val registry = object : MobileWalletPaymentCredentialRegistry {
                var records = emptyList<MobileWalletCredentialRegistryRecord>()
                var instructions = emptyMap<String, PaymentConsentMetadata>()
                override val capabilities = UnavailableMobileWalletCredentialRegistry.capabilities
                override suspend fun replace(
                    registryId: String,
                    records: List<MobileWalletCredentialRegistryRecord>,
                ): MobileWalletCredentialRegistrationResult = error("Payment capability must be used")

                override suspend fun replaceWithPaymentMetadata(
                    registryId: String,
                    records: List<MobileWalletCredentialRegistryRecord>,
                    paymentMetadata: Map<String, PaymentConsentMetadata>,
                    preferredLocales: List<String>,
                ): MobileWalletCredentialRegistrationResult {
                    this.records = records
                    instructions = paymentMetadata
                    assertEquals(listOf("en"), preferredLocales)
                    return MobileWalletCredentialRegistrationResult(true, records.size)
                }
            }
            val unusedProvider = object : PlatformManagedKeyProvider {
                override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy = error("Unused")
                override suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts = error("Unused")
                override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport = error("Unused")
                override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = error("Unused")
                override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration = error("Unused")
                override suspend fun deleteManagedKey(stored: StoredKey.Managed): Unit = error("Unused")
            }
            var changed = false
            var unavailable = false
            var cancelled = false
            val fixture = scaWalletFixture(key, unusedProvider, registry) { metadata ->
                if (cancelled) throw CancellationException("Metadata retrieval cancelled")
                check(!unavailable) { "Fixture metadata is unavailable" }
                if (changed) {
                    Json.parseToJsonElement(metadata.toString().replace("\"Pay\"", "\"Authorize\"")).jsonObject
                } else metadata
            }
            fixture.wallet.refreshDigitalCredentialRegistration()
            val originalId = registry.records.single().registryEntryId
            assertTrue(originalId.length < 64, "AndroidX entry IDs must be shorter than 64 characters")
            assertEquals(setOf(originalId), registry.instructions.keys)
            val request = MobileWalletDigitalCredentialRequest(
                MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, fixture.request.toString(),
                "https://verifier.example", selectedRegistryEntryIds = listOf(originalId),
            )
            val preview = fixture.wallet.previewDigitalCredentialPresentation(request)
            assertEquals("synthetic-sca", preview.credentialOptions.single().credentialId)
            val retainedBeforeRejection = retainedPreviewIds()
            changed = true
            assertFailsWith<MobileWalletStaleRegistryEntryException> {
                fixture.wallet.previewDigitalCredentialPresentation(request)
            }
            assertNoNewPreviews(retainedBeforeRejection, preview.requestId)
            fixture.wallet.refreshDigitalCredentialRegistration()
            assertNotEquals(originalId, registry.records.single().registryEntryId)
            unavailable = true
            val current = request.copy(selectedRegistryEntryIds = listOf(registry.records.single().registryEntryId))
            assertFailsWith<MobileWalletStaleRegistryEntryException> {
                fixture.wallet.previewDigitalCredentialPresentation(current)
            }
            assertNoNewPreviews(retainedBeforeRejection, preview.requestId)
            cancelled = true
            assertFailsWith<CancellationException> {
                fixture.wallet.previewDigitalCredentialPresentation(current)
            }
            assertNoNewPreviews(retainedBeforeRejection, preview.requestId)
            cancelled = false
            fixture.wallet.refreshDigitalCredentialRegistration()
            assertEquals(1, registry.records.size) // Ordinary disclosure remains registered.
            assertTrue(registry.instructions.isEmpty())
            fixture.wallet.discardDigitalCredentialPreview(preview.requestId)
            runtime.close()
        }
    }

    private fun assertNoNewPreviews(before: Set<*>, validPreviewId: String) {
        val retained = retainedPreviewIds()
        // Creating the rejected preview can evict an unrelated entry from the bounded store.
        // It must not leak a new handle or discard this wallet's existing, valid preview.
        assertTrue((retained - before).isEmpty(), "Rejected or cancelled selections must not retain new previews")
        assertTrue(validPreviewId in retained, "The existing valid preview must remain available")
    }

    // Observe resource retention without adding a production diagnostic API solely for this test.
    private fun retainedPreviewIds(): Set<*> {
        val store = WalletPresentationHandler::class.java.getDeclaredField("previewedDcApiRequests")
            .apply { isAccessible = true }.get(WalletPresentationHandler)
        val entries = store.javaClass.getDeclaredField("entries").apply { isAccessible = true }.get(store) as Map<*, *>
        return entries.keys.toSet()
    }
}
