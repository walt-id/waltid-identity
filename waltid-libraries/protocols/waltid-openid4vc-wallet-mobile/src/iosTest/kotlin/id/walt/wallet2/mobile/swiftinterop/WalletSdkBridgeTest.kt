package id.walt.wallet2.mobile.swiftinterop

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletEventPhase
import id.walt.wallet2.mobile.MobileWalletEventStatus
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletClientIdScheme
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletMetadataDisplay
import id.walt.wallet2.mobile.MobileWalletDatabaseKey
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialRequirement
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewHandle
import id.walt.wallet2.mobile.MobileWalletPresentationRequestContext
import id.walt.wallet2.mobile.MobileWalletPresentationRequestInfo
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.MobileWalletResponseEncryption
import id.walt.wallet2.mobile.MobileWalletPersistence
import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile
import id.walt.wallet2.mobile.MobileWalletVerifierMetadata
import id.walt.wallet2.mobile.MobileWalletRequestAuthentication
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import id.walt.x509.CertificateDer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
        assertEquals(MobileWalletKeyType.secp256r1, operations.bootstrapKeyType)
        assertEquals("jwk", operations.bootstrapDidMethod)
    }

    @Test
    fun bridgeCancelsIssuanceSessionsThroughTypedResults() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.cancelIssuance("issuance-session")

        assertIs<WalletBridgeResult.Success<WalletIssuanceOutcome>>(result)
        assertEquals(WalletIssuanceOutcome.Cancelled("issuance-session"), result.value)
        assertEquals("issuance-session", operations.cancelledIssuanceSessionId)
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

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationPreviewResult>>(result)
        val preview = assertIs<MobileWalletPresentationPreviewResult.Ready>(result.value).preview
        assertEquals("https://verifier.example", preview.request.clientId)
        assertEquals(
            MobileWalletResponseEncryption.Required(
                keyManagementAlgorithm = "ECDH-ES",
                contentEncryptionAlgorithm = "A256GCM",
                verifierKeyId = "verifier-key-1",
                verifierKeyThumbprint = "thumbprint-1",
            ),
            preview.request.responseEncryption,
        )
        assertEquals("credential-1", preview.credentialOptions.single().credentialId)
        assertEquals(true, preview.credentialOptions.single().multiple)
        assertEquals(listOf(listOf("pid")), preview.credentialRequirements.single().options)
        assertEquals("openid4vp://request", operations.previewRequestUrl)
    }

    @Test
    fun bridgePresentationPreviewPreservesAuthenticatedRequestFacts() = runTest {
        val operations = FakeWalletSdkBridgeOperations(
            requestAuthentication = MobileWalletRequestAuthentication.Authenticated(
                compactRequestObject = "signed-request-object",
                algorithm = "ES256",
                keyId = "verifier-kid",
                clientIdScheme = MobileWalletClientIdScheme.PRE_REGISTERED,
            ),
        )
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.previewPresentation("openid4vp://request")

        val preview = assertIs<MobileWalletPresentationPreviewResult.Ready>(
            assertIs<WalletBridgeResult.Success<MobileWalletPresentationPreviewResult>>(result).value,
        ).preview
        assertEquals(
            MobileWalletRequestAuthentication.Authenticated(
                compactRequestObject = "signed-request-object",
                algorithm = "ES256",
                keyId = "verifier-kid",
                clientIdScheme = MobileWalletClientIdScheme.PRE_REGISTERED,
            ),
            preview.request.requestAuthentication,
        )
    }

    @Test
    fun bridgeIssuerMetadataTrustResolverPreservesSignerFacts() = runTest {
        listOf(
            WalletBridgeIssuerMetadataSignerTrustType.TrustedIssuer to MetadataSignerTrustType.TRUSTED_ISSUER,
            WalletBridgeIssuerMetadataSignerTrustType.TrustedDelegate to MetadataSignerTrustType.TRUSTED_DELEGATE,
        ).forEach { (bridgeTrustType, expectedTrustType) ->
            val configured = WalletBridgeConfiguration(
                issuerMetadataTrustResolver = object : WalletBridgeIssuerMetadataTrustResolver {
                    override suspend fun verify(
                        compactJwt: String,
                        expectedCredentialIssuer: String,
                    ) = WalletBridgeIssuerMetadataSigner(
                        keyId = "issuer-key",
                        algorithm = "ES256",
                        trustType = bridgeTrustType,
                    ).also {
                        assertEquals("signed-metadata-jwt", compactJwt)
                        assertEquals("https://issuer.example", expectedCredentialIssuer)
                    }
                },
            ).toMobileWalletConfig()

            val signer = requireNotNull(configured.credentialIssuerMetadataTrustResolver).verify(
                "signed-metadata-jwt",
                "https://issuer.example",
            )

            assertEquals(
                MetadataSigner("issuer-key", "ES256", expectedTrustType),
                signer,
            )
        }
    }

    @Test
    fun bridgePresentationPreviewPreservesDetectedProtocolError() = runTest {
        val expected = MobileWalletPresentationPreviewResult.Invalid(
            previewHandle = MobileWalletPresentationPreviewHandle("presentation-preview"),
            request = MobileWalletPresentationRequestContext(
                clientId = "https://verifier.example",
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
                requestAuthentication = MobileWalletRequestAuthentication.Unauthenticated,
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = null,
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            ),
            errorCode = MobileWalletPresentationErrorCode.invalidTransactionData,
            message = "Unsupported transaction data type",
        )
        val operations = FakeWalletSdkBridgeOperations(previewResult = expected)
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.previewPresentation("openid4vp://request")

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationPreviewResult>>(result)
        assertEquals(expected, result.value)
    }

    @Test
    fun bridgeSubmitPresentationForwardsSelectedCredentialOptions() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.submitPresentation(
            previewHandle = MobileWalletPresentationPreviewHandle("presentation-preview"),
            selectedCredentialOptions = listOf(MobileWalletPresentationCredentialSelection("pid", "credential-1")),
            selectedDisclosureOptions = listOf(MobileWalletPresentationDisclosureSelection("pid", "credential-1", "$.given_name")),
            did = "did:jwk:issuer",
            runPolicies = false,
        )

        assertIs<WalletBridgeResult.Success<MobileWalletPresentationResult>>(result)
        assertEquals(listOf(MobileWalletPresentationCredentialSelection("pid", "credential-1")), operations.submittedCredentialOptions)
        assertEquals(listOf(MobileWalletPresentationDisclosureSelection("pid", "credential-1", "$.given_name")), operations.submittedDisclosureOptions)
        assertEquals(MobileWalletPresentationPreviewHandle("presentation-preview"), operations.submittedPreviewHandle)
        assertEquals("did:jwk:issuer", operations.submittedDid)
        assertEquals(false, operations.submittedRunPolicies)
    }

    @Test
    fun bridgeRejectPresentationForwardsErrorDetails() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)
        val handle = MobileWalletPresentationPreviewHandle("presentation-preview")

        val result = bridge.rejectPresentation(
            previewHandle = handle,
            errorCode = MobileWalletPresentationErrorCode.accessDenied,
            errorDescription = "User declined",
        )

        val success = assertIs<WalletBridgeResult.Success<MobileWalletPresentationResult>>(result)
        assertIs<MobileWalletPresentationResult.Transmitted.Succeeded>(success.value)
        assertEquals(handle, operations.rejectedPreviewHandle)
        assertEquals(MobileWalletPresentationErrorCode.accessDenied, operations.rejectedErrorCode)
        assertEquals("User declined", operations.rejectedErrorDescription)
    }

    @Test
    fun bridgeRejectPresentationCanUseWalletDetectedError() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)
        val handle = MobileWalletPresentationPreviewHandle("presentation-preview")

        bridge.rejectPresentation(previewHandle = handle)

        assertEquals(handle, operations.rejectedPreviewHandle)
        assertNull(operations.rejectedErrorCode)
        assertNull(operations.rejectedErrorDescription)
    }

    @Test
    fun factoryMapsSwiftFriendlyConfigurationToMobileWalletConfig() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        var capturedTrustConfiguration: ClientIdTrustConfiguration? = null
        val factory = WalletSdkBridgeFactory.forOperationsFactoryWithTrust { config, trustConfiguration ->
            capturedConfig = config
            capturedTrustConfiguration = trustConfiguration
            FakeWalletSdkBridgeOperations()
        }
        val trustAnchor = CertificateDer(byteArrayOf(1, 2, 3)).toPEMEncodedString()

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
                clientIdTrustConfiguration = WalletBridgeClientIdTrustConfiguration(
                    x509TrustAnchorsPem = listOf(trustAnchor),
                ),
                preferredLocales = listOf("de-AT", "en"),
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
        assertEquals(listOf(CertificateDer(byteArrayOf(1, 2, 3))), capturedTrustConfiguration?.x509TrustAnchors)
        assertEquals(listOf("de-AT", "en"), capturedConfig?.preferredLocales)
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
                    credentialStore = bridgeCredentialStore,
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = capturedConfig?.persistence
        assertIs<MobileWalletDatabaseKey.Managed>(persistence?.databaseKey)
        assertNull(persistence?.didStore)
        val credentialStore = persistence?.credentialStore
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
                    credentialStore = bridgeCredentialStore,
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val (_, parsedCredential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        val credentialStore = requireNotNull(capturedConfig)
            .persistence
            .credentialStore
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
    fun factoryMapsSwiftDidStoreOverrideToMobileWalletConfig() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val bridgeDidStore = RecordingBridgeDidStore(
            WalletBridgeStoredDid(
                did = "did:key:swift",
                documentJson = """{"id":"did:key:swift"}""",
            )
        )
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "swift-full-store-wallet",
                persistence = WalletBridgePersistence(
                    didStore = bridgeDidStore,
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = requireNotNull(capturedConfig).persistence
        assertIs<MobileWalletDatabaseKey.Managed>(persistence.databaseKey)

        val didStore = requireNotNull(persistence.didStore)
        assertEquals(
            WalletDidEntry("did:key:swift", Json.parseToJsonElement("""{"id":"did:key:swift"}""").jsonObject),
            didStore.getDid("did:key:swift"),
        )
        didStore.addDid(WalletDidEntry("did:key:new", Json.parseToJsonElement("""{"id":"did:key:new"}""").jsonObject))
        assertEquals(listOf("did:key:new"), bridgeDidStore.addedDids.map { it.did })
        assertEquals("""{"id":"did:key:new"}""", bridgeDidStore.addedDids.single().documentJson)
        assertEquals(true, didStore.removeDid("did:key:swift"))
        assertEquals(listOf("did:key:swift"), bridgeDidStore.removedDids)

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
                    credentialStore = bridgeCredentialStore,
                ),
                databaseKeyProvider = bridgeKeyProvider,
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = capturedConfig?.persistence
        val databaseKey = assertIs<MobileWalletDatabaseKey.Provided>(persistence?.databaseKey)
        val key = databaseKey.provider.getOrCreateKey("swift-combined-wallet", "wallet_swift-combined-wallet")

        assertEquals(DatabaseEncryptionKey("swift-key", byteArrayOf(4, 5, 6)), key)
        assertNull(persistence?.didStore)
        assertEquals(true, persistence?.credentialStore?.removeCredential("credential-1"))
        assertEquals(listOf("credential-1"), bridgeCredentialStore.removedCredentialIds)
    }

    @Test
    fun factoryUsesStableSwiftSdkDefaults() {
        val config = WalletBridgeConfiguration().toMobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
        assertEquals(MobileWalletPersistence(), config.persistence)
        assertEquals(emptyList(), config.preferredLocales)
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
            MobileWalletEvent.presentation_completed
        )
        val event = bridge.events.first()

        assertEquals(MobileWalletEventPhase.presentation, event.phase)
        assertEquals(MobileWalletEventStatus.completed, event.status)
        assertEquals("presentation_completed", event.name)
    }

    private class FakeWalletSdkBridgeOperations(
        private val previewResult: MobileWalletPresentationPreviewResult? = null,
        private val requestAuthentication: MobileWalletRequestAuthentication =
            MobileWalletRequestAuthentication.Unauthenticated,
    ) : WalletSdkBridgeOperations {
        var bootstrapKeyType: MobileWalletKeyType? = null
            private set
        var bootstrapDidMethod: String? = null
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
        var submittedPreviewHandle: MobileWalletPresentationPreviewHandle? = null
            private set
        var submittedCredentialOptions: List<MobileWalletPresentationCredentialSelection>? = null
            private set
        var submittedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null
            private set
        var submittedDid: String? = null
            private set
        var submittedRunPolicies: Boolean? = null
            private set
        var rejectedPreviewHandle: MobileWalletPresentationPreviewHandle? = null
            private set
        var rejectedErrorCode: MobileWalletPresentationErrorCode? = null
            private set
        var rejectedErrorDescription: String? = null
            private set
        var cancelledIssuanceSessionId: String? = null
            private set
        override suspend fun bootstrap(
            keyType: MobileWalletKeyType?,
            didMethod: String,
        ): MobileWalletBootstrapResult {
            bootstrapKeyType = keyType
            bootstrapDidMethod = didMethod
            return MobileWalletBootstrapResult(
                keyId = "key-1",
                did = "did:jwk:issuer",
            )
        }

        override suspend fun startIssuance(request: MobileWalletIssuanceRequest) =
            error("Not used by this test fake")

        override suspend fun beginAuthorizationIssuance(sessionId: String): WalletIssuanceAuthorization =
            error("Not used by this test fake")

        override suspend fun continuePreAuthorizedIssuance(
            sessionId: String,
            transactionCode: String?,
        ) = error("Not used by this test fake")

        override suspend fun continueAuthorizationIssuance(
            sessionId: String,
            callbackUri: String,
        ) = error("Not used by this test fake")

        override suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome {
            cancelledIssuanceSessionId = sessionId
            return WalletIssuanceOutcome.Cancelled(sessionId)
        }

        override suspend fun resumeDeferredIssuance(deferredCredentialId: String) =
            error("Not used by this test fake")

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

        override suspend fun previewPresentation(requestUrl: String): MobileWalletPresentationPreviewResult {
            previewRequestUrl = requestUrl
            return previewResult ?: MobileWalletPresentationPreviewResult.Ready(MobileWalletPresentationPreview(
                previewHandle = MobileWalletPresentationPreviewHandle("presentation-preview"),
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
                    requestAuthentication = requestAuthentication,
                    responseUri = "https://verifier.example/direct-post",
                    state = "state-1",
                    nonce = "nonce-1",
                    responseEncryption = MobileWalletResponseEncryption.Required(
                        keyManagementAlgorithm = "ECDH-ES",
                        contentEncryptionAlgorithm = "A256GCM",
                        verifierKeyId = "verifier-key-1",
                        verifierKeyThumbprint = "thumbprint-1",
                    ),
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
            ))
        }

        override suspend fun submitPresentation(
            previewHandle: MobileWalletPresentationPreviewHandle,
            selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
            selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>?,
            did: String?,
            runPolicies: Boolean?,
        ): MobileWalletPresentationResult {
            submittedPreviewHandle = previewHandle
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
            previewHandle: MobileWalletPresentationPreviewHandle,
            errorCode: MobileWalletPresentationErrorCode?,
            errorDescription: String?,
        ): MobileWalletPresentationResult {
            rejectedPreviewHandle = previewHandle
            rejectedErrorCode = errorCode
            rejectedErrorDescription = errorDescription
            return MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = """{"accepted":false}""",
                redirectUrl = null,
            )
        }

        override suspend fun discardPresentationPreview(previewHandle: MobileWalletPresentationPreviewHandle) = Unit
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

}
