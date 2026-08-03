package id.walt.wallet2.mobile

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.GeneratedPlatformKey
import id.walt.wallet2.persistence.keys.PlatformKeyPreflight
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyRequest
import id.walt.wallet2.persistence.stores.MobileWalletKeyRecord
import id.walt.wallet2.persistence.stores.MobileWalletKeyStore
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MobileWalletTest {

    @Test
    fun presentationErrorCodesMatchOAuthAndOpenId4VpValues() {
        assertEquals(
            listOf(
                "access_denied",
                "invalid_request",
                "invalid_client",
                "invalid_scope",
                "unauthorized_client",
                "unsupported_response_type",
                "server_error",
                "temporarily_unavailable",
                "vp_formats_not_supported",
                "invalid_request_uri_method",
                "invalid_transaction_data",
                "wallet_unavailable",
            ),
            MobileWalletPresentationErrorCode.entries.map { it.errorCode },
        )
    }

    @Test
    fun mobileWalletConfigUsesStableDefaults() {
        val config = MobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(emptyList(), config.preferredLocales)
        assertEquals(KeyUseAuthorizationPolicy.None, config.defaultKeyUseAuthorizationPolicy)
        assertEquals(KeyUseAuthorizationPrompt(), config.keyUseAuthorizationPrompt)
        assertEquals(null, config.attestationConfig)
        assertEquals(MobileWalletPersistence(), config.persistence)
        assertEquals(emptyList(), config.transactionDataProfiles)
        assertIs<MobileWalletDatabaseKey.Managed>(config.persistence.databaseKey)
        assertEquals(MobileWalletStores(), config.persistence.stores)
    }

    @Test
    fun protectedBootstrapFailsPreflightWithoutInvokingLegacyOrFallbackGeneration() = runTest {
        val keyStore = EmptyKeyStore()
        var generationCalls = 0
        val wallet = MobileWallet(
            walletId = "protected-wallet",
            keyStore = keyStore,
            didStore = EmptyDidStore(),
            credentialStore = RecordingCredentialStore(),
            keyProvider = TestProvider(
                preflight = PlatformKeyPreflight(
                    supported = false,
                    failure = KeyUseAuthorizationFailure.UnsupportedCombination,
                ),
                onGenerate = { generationCalls++ },
            ),
        )

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            wallet.bootstrap(keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
        }

        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
        assertEquals(0, generationCalls)
        assertEquals(0, keyStore.addKeyCalls)
    }

    @Test
    fun protectedBootstrapFailsPreflightWhenCustomStoreCannotPreservePolicy() = runTest {
        val keyStore = EmptyKeyStore()
        var generationCalls = 0
        val wallet = MobileWallet(
            walletId = "custom-store-wallet",
            keyStore = keyStore,
            didStore = EmptyDidStore(),
            credentialStore = RecordingCredentialStore(),
            keyProvider = TestProvider(
                preflight = PlatformKeyPreflight(
                    supported = false,
                    failure = KeyUseAuthorizationFailure.UnsupportedCombination,
                ),
                onGenerate = { generationCalls++ },
            ),
        )

        val preflight = wallet.keyUseAuthorizationPreflight(
            keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
        )
        val failure = assertFailsWith<KeyUseAuthorizationException> {
            wallet.bootstrap(keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
        }

        assertFalse(preflight.supported)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, preflight.failure)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
        assertEquals(0, generationCalls)
        assertEquals(0, keyStore.addKeyCalls)
    }

    @Test
    fun bootstrapValidationFailureDeletesGeneratedKeyExactlyOnce() = runTest {
        val key = testKey()
        val provider = LifecycleProvider(key, recordPolicyOverride = KeyUseAuthorizationPolicy.None)
        val keyStore = LifecycleKeyStore()
        val didStore = LifecycleDidStore()
        val wallet = lifecycleWallet(provider, keyStore, didStore)

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            wallet.bootstrap(keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
        }

        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
        assertEquals(1, provider.deleteCalls)
        assertTrue(keyStore.records.isEmpty())
        assertTrue(didStore.records.isEmpty())
    }

    @Test
    fun bootstrapDidRegistrationFailureDeletesGeneratedKeyExactlyOnce() = runTest {
        val key = testKey()
        val provider = LifecycleProvider(key)
        val keyStore = LifecycleKeyStore()
        val didStore = LifecycleDidStore()
        val wallet = lifecycleWallet(provider, keyStore, didStore)

        assertFailsWith<Throwable> { wallet.bootstrap(didMethod = "unsupported") }

        assertEquals(1, provider.deleteCalls)
        assertTrue(keyStore.records.isEmpty())
        assertTrue(didStore.records.isEmpty())
    }

    @Test
    fun bootstrapKeyStoreFailureDeletesGeneratedKeyExactlyOnce() = runTest {
        val key = testKey()
        val provider = LifecycleProvider(key)
        val keyStore = LifecycleKeyStore(addFailure = IllegalStateException("key store insertion"))
        val didStore = LifecycleDidStore()
        val wallet = lifecycleWallet(provider, keyStore, didStore)

        assertFailsWith<IllegalStateException> { wallet.bootstrap() }

        assertEquals(1, provider.deleteCalls)
        assertEquals(1, keyStore.addCalls)
        assertEquals(0, keyStore.removeCalls)
        assertTrue(keyStore.records.isEmpty())
        assertTrue(didStore.records.isEmpty())
    }

    @Test
    fun bootstrapDidStoreFailureLetsStoreOwnPersistedKeyDeletion() = runTest {
        val key = testKey()
        val provider = LifecycleProvider(key)
        val keyStore = LifecycleKeyStore(onPersistedKeyDelete = { provider.delete(it) })
        val didStore = LifecycleDidStore(addFailure = IllegalStateException("did store insertion"))
        val wallet = lifecycleWallet(provider, keyStore, didStore)

        assertFailsWith<IllegalStateException> { wallet.bootstrap() }

        assertEquals(1, keyStore.removeCalls)
        assertEquals(1, provider.deleteCalls)
        assertTrue(keyStore.records.isEmpty())
        assertTrue(didStore.records.isEmpty())
    }

    @Test
    fun bootstrapCleanupFailureIsSuppressedOnTheOriginalFailure() = runTest {
        val key = testKey()
        val cleanupFailure = IllegalStateException("cleanup")
        val provider = LifecycleProvider(key, deleteFailure = cleanupFailure)
        val keyStore = LifecycleKeyStore(addFailure = IllegalStateException("key store insertion"))
        val wallet = lifecycleWallet(provider, keyStore, LifecycleDidStore())

        val failure = assertFailsWith<IllegalStateException> { wallet.bootstrap() }

        assertEquals("key store insertion", failure.message)
        assertEquals(listOf(cleanupFailure), failure.suppressedExceptions)
    }

    @Test
    fun successfulBootstrapDoesNotRunCompensation() = runTest {
        val key = testKey()
        val provider = LifecycleProvider(key)
        val keyStore = LifecycleKeyStore(onPersistedKeyDelete = { provider.delete(it) })
        val didStore = LifecycleDidStore()
        val wallet = lifecycleWallet(provider, keyStore, didStore)

        val result = wallet.bootstrap()

        assertEquals(key.getKeyId(), result.keyId)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, keyStore.removeCalls)
        assertEquals(1, keyStore.records.size)
        assertEquals(1, didStore.records.size)
    }

    @Test
    fun changingDefaultDoesNotReclassifyOrReplaceExistingKey() = runTest {
        val existing = MobileWalletKeyRecord(
            keyId = "existing-key",
            keyType = KeyType.secp256r1,
            isPlatformBacked = true,
        )
        val wallet = MobileWallet(
            walletId = "existing-wallet",
            keyStore = PreloadedKeyStore(existing),
            didStore = PreloadedDidStore(WalletDidEntry("did:key:existing", JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            keyProvider = TestProvider(onPreflight = { error("An existing wallet must not preflight a replacement key") }),
            defaultKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
        )

        val bootstrap = wallet.bootstrap()
        val persisted = wallet.keys().single()

        assertEquals("existing-key", bootstrap.keyId)
        assertEquals(KeyUseAuthorizationPolicy.None, persisted.keyUseAuthorizationPolicy)
    }

    @Test
    fun mobileWalletConfigAcceptsCustomTransactionDataProfiles() {
        val profiles = listOf(
            MobileWalletTransactionDataProfile(
                type = "example.transaction",
                displayName = "Example Transaction",
                fields = listOf("amount"),
            )
        )

        val config = MobileWalletConfig(transactionDataProfiles = profiles)

        assertEquals(profiles, config.transactionDataProfiles)
        val registry = profiles.toTransactionDataTypeRegistry()
        assertEquals(setOf("example.transaction"), registry.types)
    }

    @Test
    fun persistenceCanCombineProvidedDatabaseKeyWithIndependentStoreOverrides() {
        val keyStore = PreloadedKeyStore(record("custom-key"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider()
        val keys = MobileWalletKeys(
            store = keyStore,
            provider = TestProvider(onPreflight = { error("Existing custom-store wallets should not preflight") }),
        )

        val persistence = MobileWalletPersistence(
            databaseKey = MobileWalletDatabaseKey.Provided(databaseKeyProvider),
            stores = MobileWalletStores(
                credentials = credentialStore,
                dids = didStore,
                keys = keys,
            ),
        )

        assertSame(databaseKeyProvider, assertIs<MobileWalletDatabaseKey.Provided>(persistence.databaseKey).provider)
        assertSame(credentialStore, persistence.stores.credentials)
        assertSame(didStore, persistence.stores.dids)
        assertSame(keyStore, persistence.stores.keys?.store)
        assertSame(keys.provider, persistence.stores.keys?.provider)
    }

    @Test
    fun walletCanUseInjectedStoresAndAtomicKeyConfiguration() = runTest {
        val keyStore = PreloadedKeyStore(record("custom-key"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val keys = MobileWalletKeys(
            store = keyStore,
            provider = TestProvider(onPreflight = { error("Existing custom-store wallets should not preflight") }),
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keys.store,
            didStore = didStore,
            credentialStore = credentialStore,
            keyProvider = keys.provider,
        )

        val bootstrap = wallet.bootstrap()

        assertEquals("custom-key", bootstrap.keyId)
        assertEquals("did:key:custom", bootstrap.did)
        assertEquals(1, keyStore.listKeyRecordsCalls)
        assertEquals(1, didStore.listDidsCalls)
    }

    @Test
    fun deleteWalletRemovesEntriesFromActiveStores() = runTest {
        val keyStore = PreloadedKeyStore(record("custom-key"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keyStore,
            didStore = didStore,
            credentialStore = credentialStore,
            keyProvider = TestProvider(),
        )

        wallet.deleteWallet()

        assertEquals(listOf("custom-key"), keyStore.removedKeyIds)
        assertEquals(listOf("did:key:custom"), didStore.removedDids)
        assertEquals(emptyList(), credentialStore.removedCredentialIds)
    }

    @Test
    fun mobileWalletKeyTypeMapsToCryptoKeyTypeInternally() {
        assertEquals(KeyType.Ed25519, MobileWalletKeyType.Ed25519.toKeyType())
        assertEquals(KeyType.secp256k1, MobileWalletKeyType.secp256k1.toKeyType())
        assertEquals(KeyType.secp256r1, MobileWalletKeyType.secp256r1.toKeyType())
        assertEquals(KeyType.secp384r1, MobileWalletKeyType.secp384r1.toKeyType())
        assertEquals(KeyType.secp521r1, MobileWalletKeyType.secp521r1.toKeyType())
        assertEquals(KeyType.RSA, MobileWalletKeyType.RSA.toKeyType())
        assertEquals(KeyType.RSA3072, MobileWalletKeyType.RSA3072.toKeyType())
        assertEquals(KeyType.RSA4096, MobileWalletKeyType.RSA4096.toKeyType())
    }

    @Test
    fun walletSessionEventsMapExhaustivelyToMobileWalletEvents() {
        assertEquals(WalletSessionEvent.entries.size, MobileWalletEvent.entries.size)
        WalletSessionEvent.entries.forEach { event ->
            assertEquals(event.name, event.toMobileWalletEvent().name)
        }

        assertEquals(MobileWalletEventPhase.issuance, MobileWalletEvent.issuance_offer_resolved.phase)
        assertEquals(MobileWalletEventStatus.progress, MobileWalletEvent.issuance_offer_resolved.status)
        assertEquals(MobileWalletEventPhase.presentation, MobileWalletEvent.presentation_completed.phase)
        assertEquals(MobileWalletEventStatus.completed, MobileWalletEvent.presentation_completed.status)
        assertEquals(MobileWalletEventStatus.failed, MobileWalletEvent.issuance_failed.status)
    }

    @Test
    fun presentationCredentialRequirementsRejectEmptyCombinations() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(listOf(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(listOf(listOf(" ")))
        }
    }

    @Test
    fun presentationOutputModelsRejectMissingCredentialQueryIds() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialOption(
                queryId = " ",
                credentialId = "credential-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = null,
                credentialDataJson = "{}",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            presentationTransactionData(credentialQueryIds = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            presentationTransactionData(credentialQueryIds = listOf(" "))
        }
    }

    @Test
    fun presentationRequestInfoRequiresClientIdAndNonce() {
        assertFailsWith<IllegalArgumentException> {
            presentationRequestInfo(clientId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            presentationRequestInfo(nonce = " ")
        }
    }

    @Test
    fun presentationRequestContextRequiresClientIdButAllowsMissingNonce() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationRequestContext(
                clientId = " ",
                verifierMetadata = null,
                responseUri = null,
                state = null,
                nonce = null,
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            )
        }

        val context = MobileWalletPresentationRequestContext(
            clientId = "https://verifier.example",
            verifierMetadata = null,
            responseUri = null,
            state = null,
            nonce = null,
            responseEncryption = MobileWalletResponseEncryption.NotRequired,
        )
        assertEquals("https://verifier.example", context.clientId)
        assertEquals(null, context.nonce)
    }

    @Test
    fun presentationDisclosuresRejectImpossibleSelectableStates() {
        assertFailsWith<IllegalArgumentException> {
            presentationDisclosure(selectivelyDisclosable = false, required = false, selectable = true)
        }
        assertFailsWith<IllegalArgumentException> {
            presentationDisclosure(selectivelyDisclosable = true, required = true, selectable = true)
        }
    }

    @Test
    fun presentationResultCarriesVerifierResponseAsJsonString() {
        val result = MobileWalletPresentationResult.Transmitted.Succeeded(
            verifierResponseJson = """{"accepted":true}""",
            redirectUrl = "wallet://return",
        )

        assertEquals("""{"accepted":true}""", result.verifierResponseJson)
        assertEquals("wallet://return", result.redirectUrl)
    }

    @Test
    fun presentationResultPreservesFrontChannelResponseArtifacts() {
        val responseUrl = WalletPresentResult(getUrl = "https://verifier.example/callback?error=access_denied")
            .toMobilePresentationResult()
        val formPost = WalletPresentResult(formPostHtml = "<form></form>").toMobilePresentationResult()

        assertEquals(
            MobileWalletPresentationResult.Prepared.OpenUrl(
                "https://verifier.example/callback?error=access_denied"
            ),
            responseUrl,
        )
        assertEquals(MobileWalletPresentationResult.Prepared.SubmitForm("<form></form>"), formPost)
    }

    @Test
    fun presentationResultHonorsExplicitFailedTransmission() {
        val result = WalletPresentResult(
            transmissionSuccess = false,
            verifierResponse = buildJsonObject { put("error", "server_error") },
        ).toMobilePresentationResult()

        assertEquals(
            MobileWalletPresentationResult.Transmitted.Failed("""{"error":"server_error"}"""),
            result,
        )
    }

    @Test
    fun presentationResultRejectsIncompatibleCoreArtifacts() {
        assertFailsWith<IllegalArgumentException> {
            WalletPresentResult(
                getUrl = "https://verifier.example/callback",
                formPostHtml = "<form></form>",
            ).toMobilePresentationResult()
        }
        assertFailsWith<IllegalArgumentException> {
            WalletPresentResult(transmissionSuccess = true).toMobilePresentationResult()
        }
    }

    @Test
    fun credentialsExposeStoredCredentialDataAsJsonString() = runTest {
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-1",
                credential = SdJwtCredential(
                    dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                    credentialData = buildJsonObject {
                        put("given_name", "Ada")
                    },
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    signature = null,
                    signed = null,
                ),
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = PreloadedKeyStore(record("custom-key")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            keyProvider = TestProvider(),
        )

        val credential = wallet.credentials().single()

        assertEquals("credential-1", credential.id)
        assertEquals("dc+sd-jwt", credential.format)
        assertEquals("https://issuer.example", credential.issuer)
        assertEquals("did:key:subject", credential.subject)
        assertEquals("PID", credential.label)
        assertEquals("""{"given_name":"Ada"}""", credential.credentialDataJson)
    }

    @Test
    fun credentialsExposeResolvedSdJwtClaimsWhenDisclosuresAreAvailable() = runTest {
        val (_, parsedCredential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-sd-jwt",
                credential = parsedCredential,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = PreloadedKeyStore(record("custom-key")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            keyProvider = TestProvider(),
        )

        val displayData = displayJson.parseToJsonElement(wallet.credentials().single().credentialDataJson).jsonObject

        assertEquals("Inga", displayData["given_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Silverstone", displayData["family_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("1991-11-06", displayData["birthdate"]?.jsonPrimitive?.contentOrNull)
        assertFalse(displayData.containsKey("_sd"), "resolved SD-JWT display data should not expose digest commitments as the primary content")
    }

    @Test
    fun presentationPreviewUsesSwiftFriendlyCredentialAndClaimDtos() {
        val preview = MobileWalletPresentationPreview(
            previewHandle = MobileWalletPresentationPreviewHandle("preview-1"),
            request = MobileWalletPresentationRequestInfo(
                clientId = "https://verifier.example",
                verifierMetadata = MobileWalletVerifierMetadata(
                    display = MobileWalletMetadataDisplay(
                        name = "Example Verifier",
                        locale = "en",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    clientUri = "https://verifier.example",
                    policyUri = null,
                    termsOfServiceUri = null,
                ),
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = "nonce-1",
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            ),
            credentialOptions = listOf(
                MobileWalletPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "credential-1",
                    multiple = true,
                    format = "vc+sd-jwt",
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    label = "PID",
                    credentialDataJson = """{"given_name":"Ada"}""",
                    disclosures = listOf(
                        MobileWalletPresentationDisclosure(
                            path = "$.given_name",
                            name = "given_name",
                            valueJson = """"Ada"""",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        )
                    ),
                )
            ),
            credentialRequirements = listOf(
                MobileWalletPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )

        assertEquals("https://verifier.example", preview.request.clientId)
        assertEquals("credential-1", preview.credentialOptions.single().credentialId)
        assertEquals(true, preview.credentialOptions.single().multiple)
        assertEquals("Ada", preview.credentialOptions.single().disclosures.single().displayValue)
        assertEquals(listOf(listOf("pid")), preview.credentialRequirements.single().options)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mobileWalletEventStreamDoesNotBackpressureSlowCollectors() = runTest {
        val stream = MobileWalletEventStream(replay = 1, extraBufferCapacity = 1)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            stream.events.collect {
                delay(Long.MAX_VALUE)
            }
        }

        runCurrent()

        repeat(100) { index ->
            val emitted = stream.tryEmit(MobileWalletEvent.issuance_offer_resolved)

            assertTrue(emitted, "Progress event $index should not suspend or fail when the buffer is full")
        }

        collector.cancel()
    }

    private fun presentationRequestInfo(
        clientId: String = "https://verifier.example",
        nonce: String = "nonce-1",
    ) = MobileWalletPresentationRequestInfo(
        clientId = clientId,
        verifierMetadata = MobileWalletVerifierMetadata(
            display = MobileWalletMetadataDisplay(
                name = "Example Verifier",
                locale = "en",
                logoUri = null,
                logoAltText = null,
            ),
            clientUri = null,
            policyUri = null,
            termsOfServiceUri = null,
        ),
        responseUri = "https://verifier.example/direct-post",
        state = null,
        nonce = nonce,
        responseEncryption = MobileWalletResponseEncryption.NotRequired,
    )

    private fun presentationTransactionData(
        credentialQueryIds: List<String>,
    ) = MobileWalletTransactionDataItem(
        type = "example",
        displayName = "Example",
        credentialQueryIds = credentialQueryIds,
        supportedFields = emptyList(),
        rawJson = "{}",
        detailsJson = "{}",
    )

    private fun presentationDisclosure(
        selectivelyDisclosable: Boolean,
        required: Boolean,
        selectable: Boolean,
    ) = MobileWalletPresentationDisclosure(
        path = "$.claim",
        name = "claim",
        valueJson = "true",
        displayValue = "true",
        selectivelyDisclosable = selectivelyDisclosable,
        required = required,
        selectable = selectable,
    )

    private val displayJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun record(keyId: String) = MobileWalletKeyRecord(
        keyId = keyId,
        keyType = KeyType.secp256r1,
        isPlatformBacked = true,
    )

    private suspend fun testKey(): Key = KeyManager.resolveSerializedKey(
        """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"lifecycle-key","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""
    )

    private fun lifecycleWallet(
        provider: LifecycleProvider,
        keyStore: LifecycleKeyStore,
        didStore: LifecycleDidStore,
    ) = MobileWallet(
        walletId = "lifecycle-wallet",
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = RecordingCredentialStore(),
        keyProvider = provider,
    )

    private class TestProvider(
        private val preflight: PlatformKeyPreflight = PlatformKeyPreflight(true),
        private val onGenerate: () -> Unit = {},
        private val onPreflight: suspend () -> Unit = {},
    ) : PlatformKeyProvider {
        override suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight {
            onPreflight()
            return preflight
        }

        override suspend fun generate(request: PlatformKeyRequest): GeneratedPlatformKey {
            onGenerate()
            error("Test provider generation is not configured")
        }

        override suspend fun load(record: MobileWalletKeyRecord): id.walt.crypto.keys.Key? = null

        override suspend fun delete(record: MobileWalletKeyRecord) = Unit

        override suspend fun loadSoftwareKey(
            keyId: String,
            keyType: KeyType,
            jwkMaterial: ByteArray,
        ): id.walt.crypto.keys.Key? = null

        override suspend fun exportSoftwareKeyMaterial(key: id.walt.crypto.keys.Key): ByteArray =
            error("Test provider software export is not configured")
    }

    private class LifecycleProvider(
        private val key: Key,
        private val recordPolicyOverride: KeyUseAuthorizationPolicy? = null,
        private val deleteFailure: Throwable? = null,
    ) : PlatformKeyProvider {
        var deleteCalls = 0

        override suspend fun preflight(request: PlatformKeyRequest) = PlatformKeyPreflight(true)

        override suspend fun generate(request: PlatformKeyRequest) = GeneratedPlatformKey(
            key = key,
            record = MobileWalletKeyRecord(
                keyId = key.getKeyId(),
                keyType = key.keyType,
                keyUseAuthorizationPolicy = recordPolicyOverride ?: request.keyUseAuthorizationPolicy,
                isPlatformBacked = true,
            ),
        )

        override suspend fun load(record: MobileWalletKeyRecord): Key? = key

        override suspend fun delete(record: MobileWalletKeyRecord) {
            deleteCalls++
            deleteFailure?.let { throw it }
        }

        override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = null

        override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray = error("not used")
    }

    private class LifecycleKeyStore(
        private val addFailure: Throwable? = null,
        private val onPersistedKeyDelete: suspend (MobileWalletKeyRecord) -> Unit = {},
    ) : MobileWalletKeyStore {
        val records = linkedMapOf<String, MobileWalletKeyRecord>()
        var addCalls = 0
        var removeCalls = 0

        override suspend fun getKey(keyId: String): Key? = null

        override suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord> = records.values.toList().asFlow()

        override suspend fun addKey(key: Key, record: MobileWalletKeyRecord): String {
            addCalls++
            addFailure?.let { throw it }
            records[record.keyId] = record
            return record.keyId
        }

        override suspend fun removeKey(keyId: String): Boolean {
            removeCalls++
            val record = records.remove(keyId) ?: return false
            onPersistedKeyDelete(record)
            return true
        }
    }

    private class LifecycleDidStore(
        private val addFailure: Throwable? = null,
    ) : WalletDidStore {
        val records = linkedMapOf<String, WalletDidEntry>()

        override suspend fun getDid(did: String): WalletDidEntry? = records[did]

        override suspend fun listDids(): Flow<WalletDidEntry> = records.values.toList().asFlow()

        override suspend fun addDid(entry: WalletDidEntry) {
            addFailure?.let { throw it }
            records[entry.did] = entry
        }

        override suspend fun removeDid(did: String): Boolean = records.remove(did) != null
    }

    private class RecordingDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey =
            DatabaseEncryptionKey("$walletId:$databaseName", ByteArray(32))

        override suspend fun deleteKey(walletId: String, databaseName: String) = Unit
    }

    private class PreloadedKeyStore(private val record: MobileWalletKeyRecord) : MobileWalletKeyStore {
        var listKeyRecordsCalls = 0
        val removedKeyIds = mutableListOf<String>()

        override suspend fun getKey(keyId: String) = null

        override suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord> {
            listKeyRecordsCalls++
            return listOf(record).asFlow()
        }

        override suspend fun addKey(key: id.walt.crypto.keys.Key, record: MobileWalletKeyRecord): String =
            error("Preloaded test key store should not add keys")

        override suspend fun removeKey(keyId: String): Boolean {
            removedKeyIds += keyId
            return true
        }
    }

    private class EmptyKeyStore : MobileWalletKeyStore {
        var addKeyCalls = 0

        override suspend fun getKey(keyId: String) = null

        override suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord> = emptyList<MobileWalletKeyRecord>().asFlow()

        override suspend fun addKey(key: id.walt.crypto.keys.Key, record: MobileWalletKeyRecord): String {
            addKeyCalls++
            return record.keyId
        }

        override suspend fun removeKey(keyId: String): Boolean = false
    }

    private class PreloadedDidStore(private val did: WalletDidEntry) : WalletDidStore {
        var listDidsCalls = 0
        val removedDids = mutableListOf<String>()

        override suspend fun getDid(did: String): WalletDidEntry? = this.did.takeIf { it.did == did }

        override suspend fun listDids(): Flow<WalletDidEntry> {
            listDidsCalls++
            return listOf(did).asFlow()
        }

        override suspend fun addDid(entry: WalletDidEntry) =
            error("Preloaded test DID store should not add DIDs")

        override suspend fun removeDid(did: String): Boolean {
            removedDids += did
            return true
        }
    }

    private class EmptyDidStore : WalletDidStore {
        override suspend fun getDid(did: String): WalletDidEntry? = null

        override suspend fun listDids(): Flow<WalletDidEntry> = emptyList<WalletDidEntry>().asFlow()

        override suspend fun addDid(entry: WalletDidEntry) = Unit

        override suspend fun removeDid(did: String): Boolean = false
    }

    private class RecordingCredentialStore(
        private vararg val credentials: StoredCredential,
    ) : WalletCredentialStore {
        val removedCredentialIds = mutableListOf<String>()

        override suspend fun getCredential(id: String): StoredCredential? = null

        override suspend fun listCredentials(): Flow<StoredCredential> =
            credentials.toList().asFlow()

        override suspend fun addCredential(entry: StoredCredential) =
            error("Recording credential store should not add credentials in this test")

        override suspend fun removeCredential(id: String): Boolean {
            removedCredentialIds += id
            return true
        }
    }
}
