package id.walt.wallet2.mobile.swiftinterop

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletEventPhase
import id.walt.wallet2.mobile.MobileWalletEventStatus
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletOfferResolution
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletDatabaseKey
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialRequirement
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.MobileWalletPresentationRequestInfo
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.MobileWalletPersistence
import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.wallet2.mobile.toKeyType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class WalletSdkBridgeTest {

    @Test
    fun wrapsSuccessfulSuspendCallsInBridgeResult() = runTest {
        val result = walletBridgeCall {
            listOf("credential-1")
        }

        assertIs<WalletBridgeResult.Success<List<String>>>(result)
        assertEquals(listOf("credential-1"), result.value)
    }

    @Test
    fun mapsOperationFailuresToTypedBridgeFailures() = runTest {
        val result = walletBridgeCall<String> {
            throw IllegalArgumentException("bad offer")
        }

        assertIs<WalletBridgeResult.Failure>(result)
        assertEquals(WalletBridgeErrorCategory.invalidInput, result.error.category)
        assertEquals("bad offer", result.error.message)
    }

    @Test
    fun preservesStructuredCoroutineCancellation() = runTest {
        val cancellation = assertFailsWith<CancellationException> {
            walletBridgeCall<String> {
                throw CancellationException("cancelled")
            }
        }

        assertEquals("cancelled", cancellation.message)
    }

    @Test
    fun storedKeyStringRepresentationRedactsSerializedKeyJson() {
        val key = WalletBridgeStoredKey(
            keyId = "key-1",
            keyType = "secp256r1",
            algorithm = "ES256",
            serializedKeyJson = """{"type":"jwk","jwk":{"kid":"key-1","d":"secret"}}""",
        )

        assertEquals(
            "WalletBridgeStoredKey(keyId=key-1, keyType=secp256r1, algorithm=ES256, serializedKeyJson=<redacted>)",
            key.toString(),
        )
        assertFalse(key.toString().contains("secret"))
    }

    @Test
    fun bridgeBootstrapMapsKeyTypeAndResultDto() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.bootstrap(
            keyType = MobileWalletKeyType.secp256r1,
            didMethod = "jwk",
        )

        assertIs<WalletBridgeResult.Success<MobileWalletBootstrapResult>>(result)
        assertEquals("key-1", result.value.keyId)
        assertEquals("did:jwk:issuer", result.value.did)
        assertEquals(KeyType.secp256r1, operations.bootstrapKeyType)
        assertEquals("jwk", operations.bootstrapDidMethod)
    }

    @Test
    fun bridgeResolvesCredentialOffers() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.resolveOffer("openid-credential-offer://issuer.example")

        assertIs<WalletBridgeResult.Success<MobileWalletOfferResolution>>(result)
        assertEquals(true, result.value.transactionCodeRequired)
        assertEquals("openid-credential-offer://issuer.example", operations.resolvedOfferUrl)
    }

    @Test
    fun bridgeCredentialsMapToSwiftSafeDtos() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.credentials()

        assertIs<WalletBridgeResult.Success<List<MobileWalletCredential>>>(result)
        assertEquals("credential-1", result.value.single().id)
        assertEquals("https://issuer.example", result.value.single().issuer)
        assertEquals("""{"given_name":"Ada"}""", result.value.single().credentialDataJson)
    }

    @Test
    fun bridgeDeletesWalletAsSuccessResult() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.deleteWallet()

        assertIs<WalletBridgeResult.Success<Unit>>(result)
        assertEquals(1, operations.deleteWalletCalls)
    }

    @Test
    fun bridgePresentationMapsJsonElementToJsonString() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.present(
            requestUrl = "openid4vp://request",
            did = "did:jwk:issuer",
            runPolicies = true,
        )

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationResult>>(result)
        assertEquals(
            MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = """{"accepted":true}""",
                redirectUrl = "wallet://return",
            ),
            result.value,
        )
        assertEquals("openid4vp://request", operations.presentationRequestUrl)
        assertEquals("did:jwk:issuer", operations.presentationDid)
        assertEquals(true, operations.presentationRunPolicies)
    }

    @Test
    fun bridgePresentationPreviewReturnsSwiftSafeDtos() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.previewPresentation("openid4vp://request")

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationPreview>>(result)
        assertEquals("https://verifier.example", result.value.request.clientId)
        assertEquals("credential-1", result.value.credentialOptions.single().credentialId)
        assertEquals(true, result.value.credentialOptions.single().multiple)
        assertEquals(listOf(listOf("pid")), result.value.credentialRequirements.single().options)
        assertEquals("openid4vp://request", operations.previewRequestUrl)
    }

    @Test
    fun bridgeSubmitPresentationForwardsSelectedCredentialOptions() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.submitPresentation(
            requestUrl = "openid4vp://request",
            selectedCredentialOptions = listOf(MobileWalletPresentationCredentialSelection("pid", "credential-1")),
            selectedDisclosureOptions = listOf(MobileWalletPresentationDisclosureSelection("pid", "credential-1", "$.given_name")),
            did = "did:jwk:issuer",
            runPolicies = false,
        )

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationResult>>(result)
        assertEquals(listOf(MobileWalletPresentationCredentialSelection("pid", "credential-1")), operations.submittedCredentialOptions)
        assertEquals(listOf(MobileWalletPresentationDisclosureSelection("pid", "credential-1", "$.given_name")), operations.submittedDisclosureOptions)
        assertEquals("openid4vp://request", operations.submittedRequestUrl)
        assertEquals("did:jwk:issuer", operations.submittedDid)
        assertEquals(false, operations.submittedRunPolicies)
    }

    @Test
    fun bridgeRejectPresentationForwardsErrorDetails() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.rejectPresentation(
            requestUrl = "openid4vp://request",
            errorCode = MobileWalletPresentationErrorCode.accessDenied,
            errorDescription = "User declined",
        )

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationResult>>(result)
        assertEquals(true, result.value.success)
        assertEquals("openid4vp://request", operations.rejectedRequestUrl)
        assertEquals(MobileWalletPresentationErrorCode.accessDenied, operations.rejectedErrorCode)
        assertEquals("User declined", operations.rejectedErrorDescription)
    }

    @Test
    fun bridgeMethodsReturnTypedFailures() = runTest {
        val operations = FakeWalletSdkBridgeOperations(
            receiveFailure = IllegalArgumentException("bad offer"),
        )
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.receive(offerUrl = "not-a-url")

        assertIs<WalletBridgeResult.Failure>(result)
        assertEquals(WalletBridgeErrorCategory.invalidInput, result.error.category)
        assertEquals("bad offer", result.error.message)
    }

    @Test
    fun factoryMapsSwiftFriendlyConfigurationToMobileWalletConfig() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "consumer-wallet",
                defaultKeyType = MobileWalletKeyType.Ed25519,
                persistence = WalletBridgePersistence(
                    databaseKey = WalletBridgeDatabaseKeyConfiguration.Managed,
                ),
                attestation = WalletAttestationConfig(
                    baseUrl = "https://attestation.example",
                    attesterPath = "/wallet-attestation",
                    bearerToken = "token",
                    hostHeader = "attestation.example",
                ),
                transactionDataProfiles = listOf(
                    MobileWalletTransactionDataProfile(
                        type = "example.transaction",
                        displayName = "Example Transaction",
                        fields = listOf("amount"),
                    )
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        assertEquals("consumer-wallet", capturedConfig?.walletId)
        assertEquals(MobileWalletKeyType.Ed25519, capturedConfig?.defaultKeyType)
        assertEquals(
            MobileWalletPersistence(),
            capturedConfig?.persistence,
        )
        assertEquals("https://attestation.example", capturedConfig?.attestationConfig?.baseUrl)
        assertEquals("/wallet-attestation", capturedConfig?.attestationConfig?.attesterPath)
        assertEquals("token", capturedConfig?.attestationConfig?.bearerToken)
        assertEquals("attestation.example", capturedConfig?.attestationConfig?.hostHeader)
        assertEquals(
            listOf(
                MobileWalletTransactionDataProfile(
                    type = "example.transaction",
                    displayName = "Example Transaction",
                    fields = listOf("amount"),
                )
            ),
            capturedConfig?.transactionDataProfiles,
        )

        val credentials = result.value.credentials()
        assertIs<WalletBridgeResult.Success<List<MobileWalletCredential>>>(credentials)
        assertEquals("credential-1", credentials.value.single().id)
    }

    @Test
    fun factoryMapsProvidedDatabaseKeyProviderToMobileWalletConfig() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val bridgeKeyProvider = RecordingBridgeDatabaseKeyProvider(
            WalletBridgeDatabaseEncryptionKey(
                keyId = "swift-key",
                material = byteArrayOf(1, 2, 3),
            )
        )
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "swift-managed-wallet",
                persistence = WalletBridgePersistence(
                    databaseKey = WalletBridgeDatabaseKeyConfiguration.Provided,
                ),
                databaseKeyProvider = bridgeKeyProvider,
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = capturedConfig?.persistence
        val databaseKey = assertIs<MobileWalletDatabaseKey.Provided>(persistence?.databaseKey)
        val key = databaseKey.provider.getOrCreateKey("swift-managed-wallet", "wallet_swift-managed-wallet")

        assertEquals(DatabaseEncryptionKey("swift-key", byteArrayOf(1, 2, 3)), key)
        databaseKey.provider.deleteKey("swift-managed-wallet", "wallet_swift-managed-wallet")
        assertEquals(listOf("swift-managed-wallet:wallet_swift-managed-wallet"), bridgeKeyProvider.deletedKeys)
    }

    @Test
    fun factoryMapsSwiftCredentialStoreOverrideToMobileWalletConfig() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val bridgeCredentialStore = RecordingBridgeCredentialStore()
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "swift-store-wallet",
                persistence = WalletBridgePersistence(
                    stores = WalletBridgeStores(credentials = bridgeCredentialStore),
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = capturedConfig?.persistence
        assertIs<MobileWalletDatabaseKey.Managed>(persistence?.databaseKey)
        assertNull(persistence?.stores?.dids)
        assertNull(persistence?.stores?.keys)
        val credentialStore = persistence?.stores?.credentials
        assertEquals(true, credentialStore?.removeCredential("credential-1"))
        assertEquals(listOf("credential-1"), bridgeCredentialStore.removedCredentialIds)
    }

    @Test
    fun swiftCredentialStorePersistsSdJwtDisclosuresForReloadableDisplay() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val bridgeCredentialStore = RecordingBridgeCredentialStore()
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "swift-sd-jwt-store-wallet",
                persistence = WalletBridgePersistence(
                    stores = WalletBridgeStores(credentials = bridgeCredentialStore),
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val (_, parsedCredential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        val credentialStore = requireNotNull(capturedConfig)
            .persistence
            .stores
            .credentials
        requireNotNull(credentialStore).addCredential(
            StoredCredential(
                id = "credential-sd-jwt",
                credential = parsedCredential,
                label = "PID",
            )
        )

        val bridgeEntry = bridgeCredentialStore.addedCredentials.single()
        assertEquals(SdJwtExamples.sdJwtVcSignedExample2, bridgeEntry.serializedCredential)

        val (_, reloadedCredential) = CredentialParser.detectAndParse(bridgeEntry.serializedCredential)
        assertEquals("Inga", reloadedCredential.credentialData["given_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Silverstone", reloadedCredential.credentialData["family_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("1991-11-06", reloadedCredential.credentialData["birthdate"]?.jsonPrimitive?.contentOrNull)
        assertFalse(reloadedCredential.credentialData.containsKey("_sd"))
    }

    @Test
    fun factoryMapsSwiftDidAndKeyStoreOverridesToMobileWalletConfig() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val generatedKey = KeyManager.resolveSerializedKey(ED25519_SERIALIZED_KEY)
        val generatedBridgeKey = WalletBridgeStoredKey(
            keyId = generatedKey.getKeyId(),
            keyType = "Ed25519",
            algorithm = "EdDSA",
            serializedKeyJson = ED25519_SERIALIZED_KEY,
        )
        val storedBridgeKey = WalletBridgeStoredKey(
            keyId = P256_KEY_ID,
            keyType = "secp256r1",
            algorithm = "ES256",
            serializedKeyJson = P256_SERIALIZED_KEY,
        )
        val bridgeDidStore = RecordingBridgeDidStore(
            WalletBridgeStoredDid(
                did = "did:key:swift",
                documentJson = """{"id":"did:key:swift"}""",
            )
        )
        val bridgeKeyStore = RecordingBridgeKeyStore(storedBridgeKey)
        val bridgeKeyGenerator = RecordingBridgeKeyGenerator(generatedBridgeKey)
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "swift-full-store-wallet",
                persistence = WalletBridgePersistence(
                    stores = WalletBridgeStores(
                        dids = bridgeDidStore,
                        keys = WalletBridgeKeys(
                            store = bridgeKeyStore,
                            generate = bridgeKeyGenerator,
                        ),
                    ),
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = requireNotNull(capturedConfig).persistence
        assertIs<MobileWalletDatabaseKey.Managed>(persistence.databaseKey)

        val didStore = requireNotNull(persistence.stores.dids)
        assertEquals(
            WalletDidEntry("did:key:swift", Json.parseToJsonElement("""{"id":"did:key:swift"}""").jsonObject),
            didStore.getDid("did:key:swift"),
        )
        didStore.addDid(WalletDidEntry("did:key:new", Json.parseToJsonElement("""{"id":"did:key:new"}""").jsonObject))
        assertEquals(listOf("did:key:new"), bridgeDidStore.addedDids.map { it.did })
        assertEquals("""{"id":"did:key:new"}""", bridgeDidStore.addedDids.single().documentJson)
        assertEquals(true, didStore.removeDid("did:key:swift"))
        assertEquals(listOf("did:key:swift"), bridgeDidStore.removedDids)

        val keys = requireNotNull(persistence.stores.keys)
        assertEquals(listOf(WalletKeyInfo(P256_KEY_ID, "secp256r1", "ES256")), keys.store.listKeys().toList())
        assertEquals(P256_KEY_ID, keys.store.getKey(P256_KEY_ID)?.getKeyId())
        assertEquals(P256_KEY_ID, keys.store.addKey(generatedKey))
        assertEquals(generatedBridgeKey.keyId, bridgeKeyStore.addedKeys.single().keyId)
        assertEquals(true, keys.store.removeKey(P256_KEY_ID))
        assertEquals(listOf(P256_KEY_ID), bridgeKeyStore.removedKeyIds)

        val generated = keys.generate(KeyType.Ed25519)
        assertEquals(generatedBridgeKey.keyId, generated.getKeyId())
        assertEquals(listOf(MobileWalletKeyType.Ed25519), bridgeKeyGenerator.requestedTypes)
    }

    @Test
    fun factoryCombinesSwiftDatabaseKeyProviderAndCredentialStoreOverride() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val bridgeKeyProvider = RecordingBridgeDatabaseKeyProvider(
            WalletBridgeDatabaseEncryptionKey(
                keyId = "swift-key",
                material = byteArrayOf(4, 5, 6),
            )
        )
        val bridgeCredentialStore = RecordingBridgeCredentialStore()
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "swift-combined-wallet",
                persistence = WalletBridgePersistence(
                    databaseKey = WalletBridgeDatabaseKeyConfiguration.Provided,
                    stores = WalletBridgeStores(credentials = bridgeCredentialStore),
                ),
                databaseKeyProvider = bridgeKeyProvider,
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = capturedConfig?.persistence
        val databaseKey = assertIs<MobileWalletDatabaseKey.Provided>(persistence?.databaseKey)
        val key = databaseKey.provider.getOrCreateKey("swift-combined-wallet", "wallet_swift-combined-wallet")

        assertEquals(DatabaseEncryptionKey("swift-key", byteArrayOf(4, 5, 6)), key)
        assertNull(persistence?.stores?.dids)
        assertNull(persistence?.stores?.keys)
        assertEquals(true, persistence?.stores?.credentials?.removeCredential("credential-1"))
        assertEquals(listOf("credential-1"), bridgeCredentialStore.removedCredentialIds)
    }

    @Test
    fun factoryUsesStableSwiftSdkDefaults() {
        val config = WalletBridgeConfiguration().toMobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
        assertEquals(MobileWalletPersistence(), config.persistence)
        assertEquals(emptyList(), config.transactionDataProfiles)
    }

    @Test
    fun factoryReturnsTypedFailureWhenWalletCreationFails() = runTest {
        val factory = WalletSdkBridgeFactory.forOperationsFactory {
            throw IllegalArgumentException("bad wallet config")
        }

        val result = factory.create()

        assertIs<WalletBridgeResult.Failure>(result)
        assertEquals(WalletBridgeErrorCategory.invalidInput, result.error.category)
        assertEquals("bad wallet config", result.error.message)
    }

    @Test
    fun bridgeExposesCommonMobileWalletEvents() = runTest {
        val events = MutableSharedFlow<MobileWalletEvent>(replay = 1)
        val bridge = WalletSdkBridge.forOperations(
            operations = FakeWalletSdkBridgeOperations(),
            eventFlow = events,
        )

        events.emit(
            MobileWalletEvent(
                name = "presentation_completed",
                phase = MobileWalletEventPhase.presentation,
                status = MobileWalletEventStatus.completed,
            )
        )
        val event = bridge.events.first()

        assertEquals(MobileWalletEventPhase.presentation, event.phase)
        assertEquals(MobileWalletEventStatus.completed, event.status)
        assertEquals("presentation_completed", event.name)
    }

    private class FakeWalletSdkBridgeOperations(
        private val receiveFailure: Throwable? = null,
    ) : WalletSdkBridgeOperations {
        var bootstrapKeyType: KeyType? = null
            private set
        var bootstrapDidMethod: String? = null
            private set
        var resolvedOfferUrl: String? = null
            private set
        var presentationRequestUrl: String? = null
            private set
        var presentationDid: String? = null
            private set
        var presentationRunPolicies: Boolean? = null
            private set
        var deleteWalletCalls = 0
            private set
        var previewRequestUrl: String? = null
            private set
        var submittedRequestUrl: String? = null
            private set
        var submittedCredentialOptions: List<MobileWalletPresentationCredentialSelection>? = null
            private set
        var submittedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null
            private set
        var submittedDid: String? = null
            private set
        var submittedRunPolicies: Boolean? = null
            private set
        var rejectedRequestUrl: String? = null
            private set
        var rejectedErrorCode: MobileWalletPresentationErrorCode? = null
            private set
        var rejectedErrorDescription: String? = null
            private set

        override suspend fun bootstrap(
            keyType: MobileWalletKeyType?,
            didMethod: String,
        ): MobileWalletBootstrapResult {
            bootstrapKeyType = keyType?.toKeyType()
            bootstrapDidMethod = didMethod
            return MobileWalletBootstrapResult(
                keyId = "key-1",
                did = "did:jwk:issuer",
            )
        }

        override suspend fun resolveOffer(offerUrl: String): MobileWalletOfferResolution {
            resolvedOfferUrl = offerUrl
            return MobileWalletOfferResolution(
                transactionCodeRequired = true,
                credentialIssuer = "https://issuer.example",
                offeredCredentials = listOf("ExampleCredential"),
            )
        }

        override suspend fun receive(
            offerUrl: String,
            txCode: String?,
            clientId: String,
        ): List<String> {
            receiveFailure?.let { throw it }
            return listOf("credential-1")
        }

        override suspend fun credentials(): List<MobileWalletCredential> =
            listOf(
                MobileWalletCredential(
                    id = "credential-1",
                    format = "vc+sd-jwt",
                    issuer = "https://issuer.example",
                    subject = null,
                    label = "PID",
                    addedAt = null,
                    credentialDataJson = """{"given_name":"Ada"}""",
                )
            )

        override suspend fun deleteWallet() {
            deleteWalletCalls++
        }

        override suspend fun present(
            requestUrl: String,
            did: String?,
            runPolicies: Boolean?,
        ): MobileWalletPresentationResult {
            presentationRequestUrl = requestUrl
            presentationDid = did
            presentationRunPolicies = runPolicies
            return MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = """{"accepted":true}""",
                redirectUrl = "wallet://return",
            )
        }

        override suspend fun previewPresentation(requestUrl: String): MobileWalletPresentationPreview {
            previewRequestUrl = requestUrl
            return MobileWalletPresentationPreview(
                request = MobileWalletPresentationRequestInfo(
                    clientId = "https://verifier.example",
                    verifierName = "Example Verifier",
                    responseUri = "https://verifier.example/direct-post",
                    state = "state-1",
                    nonce = "nonce-1",
                ),
                credentialOptions = listOf(
                    MobileWalletPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = "credential-1",
                        multiple = true,
                        format = "vc+sd-jwt",
                        issuer = "https://issuer.example",
                        subject = null,
                        label = "PID",
                        credentialDataJson = """{"given_name":"Ada"}""",
                        disclosures = emptyList(),
                    )
                ),
                credentialRequirements = listOf(
                    MobileWalletPresentationCredentialRequirement(options = listOf(listOf("pid")))
                ),
            )
        }

        override suspend fun submitPresentation(
            requestUrl: String,
            selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
            selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>?,
            did: String?,
            runPolicies: Boolean?,
        ): MobileWalletPresentationResult {
            submittedRequestUrl = requestUrl
            submittedCredentialOptions = selectedCredentialOptions
            submittedDisclosureOptions = selectedDisclosureOptions
            submittedDid = did
            submittedRunPolicies = runPolicies
            return MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = """{"accepted":true}""",
                redirectUrl = "wallet://return",
            )
        }

        override suspend fun rejectPresentation(
            requestUrl: String,
            errorCode: MobileWalletPresentationErrorCode,
            errorDescription: String?,
        ): MobileWalletPresentationResult {
            rejectedRequestUrl = requestUrl
            rejectedErrorCode = errorCode
            rejectedErrorDescription = errorDescription
            return MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = """{"accepted":false}""",
                redirectUrl = "wallet://return",
            )
        }
    }

    private class RecordingBridgeDatabaseKeyProvider(
        private val key: WalletBridgeDatabaseEncryptionKey,
    ) : WalletBridgeDatabaseEncryptionKeyProvider {
        val deletedKeys = mutableListOf<String>()

        override suspend fun getOrCreateKey(walletId: String, databaseName: String): WalletBridgeDatabaseEncryptionKey =
            key

        override suspend fun deleteKey(walletId: String, databaseName: String) {
            deletedKeys += "$walletId:$databaseName"
        }
    }

    private class RecordingBridgeCredentialStore : WalletBridgeCredentialStore {
        val removedCredentialIds = mutableListOf<String>()
        val addedCredentials = mutableListOf<WalletBridgeStoredCredential>()

        override suspend fun getCredential(id: String): WalletBridgeStoredCredential? = null

        override suspend fun listCredentials(): List<WalletBridgeStoredCredential> = emptyList()

        override suspend fun addCredential(entry: WalletBridgeStoredCredential) {
            addedCredentials += entry
        }

        override suspend fun removeCredential(id: String): Boolean {
            removedCredentialIds += id
            return true
        }
    }

    private class RecordingBridgeDidStore(
        private val did: WalletBridgeStoredDid,
    ) : WalletBridgeDidStore {
        val addedDids = mutableListOf<WalletBridgeStoredDid>()
        val removedDids = mutableListOf<String>()

        override suspend fun getDid(did: String): WalletBridgeStoredDid? =
            this.did.takeIf { it.did == did }

        override suspend fun listDids(): List<WalletBridgeStoredDid> =
            listOf(did)

        override suspend fun addDid(entry: WalletBridgeStoredDid) {
            addedDids += entry
        }

        override suspend fun removeDid(did: String): Boolean {
            removedDids += did
            return true
        }
    }

    private class RecordingBridgeKeyStore(
        private val key: WalletBridgeStoredKey,
    ) : WalletBridgeKeyStore {
        val addedKeys = mutableListOf<WalletBridgeStoredKey>()
        val removedKeyIds = mutableListOf<String>()

        override suspend fun getKey(keyId: String): WalletBridgeStoredKey? =
            key.takeIf { it.keyId == keyId }

        override suspend fun listKeys(): List<WalletBridgeKeyInfo> =
            listOf(WalletBridgeKeyInfo(key.keyId, key.keyType, key.algorithm))

        override suspend fun addKey(entry: WalletBridgeStoredKey): String {
            addedKeys += entry
            return key.keyId
        }

        override suspend fun removeKey(keyId: String): Boolean {
            removedKeyIds += keyId
            return true
        }
    }

    private class RecordingBridgeKeyGenerator(
        private val key: WalletBridgeStoredKey,
    ) : WalletBridgeKeyGenerator {
        val requestedTypes = mutableListOf<MobileWalletKeyType>()

        override suspend fun generateKey(keyType: MobileWalletKeyType): WalletBridgeStoredKey {
            requestedTypes += keyType
            return key
        }
    }

    private companion object {
        private const val ED25519_SERIALIZED_KEY =
            """{"type":"jwk","jwk":{"kty":"OKP","d":"lPR4XjW-9_rI4hLjvdjmjoGC6ozblm9juDv4OHYdm5M","crv":"Ed25519","kid":"sryFIxLJ7aIqTsXo0QCnNUR9TG6jmHOQa9CFhxg5OIA","x":"LRHvL7I9utgSl47JksY0-uY21TlIxp_queROJJzknNM"}}"""

        private const val P256_KEY_ID = "_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug"

        private const val P256_SERIALIZED_KEY =
            """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""
    }
}
