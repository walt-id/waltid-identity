package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.presentation.PresentationReview
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.credentials.status.CredentialStatus

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceOfferReview
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.WalletEvent
import id.walt.wallet2.mobile.WalletEventPhase
import id.walt.wallet2.mobile.WalletEventStatus
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.credentials.CredentialWithStatus
import id.walt.wallet2.mobile.credentials.WalletDatabaseKey
import id.walt.wallet2.mobile.credentials.WalletPersistence
import id.walt.wallet2.mobile.identity.WalletKeyType
import id.walt.wallet2.mobile.issuance.IssuanceCredentialSelection
import id.walt.wallet2.mobile.issuance.IssuanceHolderBinding
import id.walt.wallet2.mobile.issuance.IssuanceRequest
import id.walt.wallet2.mobile.issuance.WalletAttestationConfig
import id.walt.wallet2.mobile.platformPreferredLocales
import id.walt.wallet2.mobile.presentation.PresentationClientIdScheme
import id.walt.wallet2.mobile.presentation.MetadataDisplay
import id.walt.wallet2.mobile.presentation.PresentationCredentialOption
import id.walt.wallet2.mobile.presentation.PresentationCredentialRequirement
import id.walt.wallet2.mobile.presentation.PresentationCredentialSelection
import id.walt.wallet2.mobile.presentation.PresentationDisclosureSelection
import id.walt.wallet2.mobile.presentation.PresentationErrorCode
import id.walt.wallet2.mobile.presentation.PresentationPreview
import id.walt.wallet2.mobile.presentation.PresentationPreviewHandle
import id.walt.wallet2.mobile.presentation.PresentationPreviewResult
import id.walt.wallet2.mobile.presentation.PartialPresentationRequest
import id.walt.wallet2.mobile.presentation.PresentationRequest
import id.walt.wallet2.mobile.presentation.PresentationResult
import id.walt.wallet2.mobile.presentation.PresentationSelection
import id.walt.wallet2.mobile.presentation.PresentationRequestAuthentication
import id.walt.wallet2.mobile.presentation.PresentationResponseEncryption
import id.walt.wallet2.mobile.presentation.TransactionDataProfile
import id.walt.wallet2.mobile.presentation.VerifierMetadata
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

        assertIs<WalletBridgeResult.Success<List<WalletCredential>>>(result)
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
    fun bridgeDeletesCredentialAsSuccessResult() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.deleteCredential("credential-1")

        assertIs<WalletBridgeResult.Success<Boolean>>(result)
        assertEquals(true, result.value)
        assertEquals(1, operations.deleteCredentialCalls)
        assertEquals(listOf("credential-1"), operations.deletedCredentialIds)
    }

    @Test
    fun bridgePresentationPreviewReturnsSwiftSafeDtos() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.previewPresentation("openid4vp://request")

        assertIs<WalletBridgeResult.Success<PresentationPreviewResult>>(result)
        val preview = assertIs<PresentationPreviewResult.Ready>(result.value).preview
        assertEquals("https://verifier.example", preview.request.clientId)
        assertEquals(
            PresentationResponseEncryption.Required(
                keyManagementAlgorithm = "ECDH-ES",
                contentEncryptionAlgorithm = "A256GCM",
                verifierKeyId = "verifier-key-1",
                verifierKeyThumbprint = "thumbprint-1",
            ),
            preview.request.responseEncryption,
        )
        assertEquals("credential-1", preview.credentialOptions.single().credential.id)
        assertEquals(true, preview.credentialOptions.single().multiple)
        assertEquals(listOf(listOf("pid")), preview.credentialRequirements.single().options)
        assertEquals("openid4vp://request", operations.previewRequestUrl)
    }

    @Test
    fun bridgePresentationPreviewPreservesAuthenticatedRequestFacts() = runTest {
        val operations = FakeWalletSdkBridgeOperations(
            requestAuthentication = PresentationRequestAuthentication.Authenticated(
                compactRequestObject = "signed-request-object",
                algorithm = "ES256",
                keyId = "verifier-kid",
                clientIdScheme = PresentationClientIdScheme.PRE_REGISTERED,
            ),
        )
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.previewPresentation("openid4vp://request")

        val preview = assertIs<PresentationPreviewResult.Ready>(
            assertIs<WalletBridgeResult.Success<PresentationPreviewResult>>(result).value,
        ).preview
        assertEquals(
            PresentationRequestAuthentication.Authenticated(
                compactRequestObject = "signed-request-object",
                algorithm = "ES256",
                keyId = "verifier-kid",
                clientIdScheme = PresentationClientIdScheme.PRE_REGISTERED,
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
            ).toWalletConfiguration()

            val signer = requireNotNull(configured.issuance.metadataTrustResolver).verify(
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
        val expected = PresentationPreviewResult.Invalid(
            previewHandle = PresentationPreviewHandle("presentation-preview"),
            request = PartialPresentationRequest(
                clientId = "https://verifier.example",
                verifierMetadata = VerifierMetadata(
                    display = MetadataDisplay(
                        name = "Example Verifier",
                        locale = "en",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    clientUri = null,
                    policyUri = null,
                    termsOfServiceUri = null,
                ),
                requestAuthentication = PresentationRequestAuthentication.Unauthenticated,
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = null,
                responseEncryption = PresentationResponseEncryption.NotRequired,
            ),
            errorCode = PresentationErrorCode.invalidTransactionData,
            message = "Unsupported transaction data type",
        )
        val operations = FakeWalletSdkBridgeOperations(previewResult = expected)
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.previewPresentation("openid4vp://request")

        assertIs<WalletBridgeResult.Success<PresentationPreviewResult>>(result)
        assertEquals(expected, result.value)
    }

    @Test
    fun bridgeReviewForwardsSelectedCredentialOptions() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.reviewPresentation(
            previewHandle = PresentationPreviewHandle("presentation-preview"),
            selection = PresentationSelection(
                credentials = listOf(PresentationCredentialSelection("pid", "credential-1")),
                disclosures = listOf(PresentationDisclosureSelection("pid", "credential-1", "$.given_name")),
            ),
            did = "did:jwk:issuer",
        )

        assertIs<WalletBridgeResult.Failure>(result)
        assertEquals(listOf(PresentationCredentialSelection("pid", "credential-1")), operations.submittedCredentialOptions)
        assertEquals(listOf(PresentationDisclosureSelection("pid", "credential-1", "$.given_name")), operations.submittedDisclosureOptions)
        assertEquals(PresentationPreviewHandle("presentation-preview"), operations.submittedPreviewHandle)
        assertEquals("did:jwk:issuer", operations.submittedDid)
    }

    @Test
    fun bridgeRejectPresentationForwardsErrorDetails() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)
        val handle = PresentationPreviewHandle("presentation-preview")

        val result = bridge.rejectPresentation(
            previewHandle = handle,
            errorCode = PresentationErrorCode.accessDenied,
            errorDescription = "User declined",
        )

        val success = assertIs<WalletBridgeResult.Success<PresentationResult>>(result)
        assertIs<PresentationResult.Transmitted.Succeeded>(success.value)
        assertEquals(handle, operations.rejectedPreviewHandle)
        assertEquals(PresentationErrorCode.accessDenied, operations.rejectedErrorCode)
        assertEquals("User declined", operations.rejectedErrorDescription)
    }

    @Test
    fun bridgeRejectPresentationCanUseWalletDetectedError() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)
        val handle = PresentationPreviewHandle("presentation-preview")

        bridge.rejectPresentation(previewHandle = handle)

        assertEquals(handle, operations.rejectedPreviewHandle)
        assertNull(operations.rejectedErrorCode)
        assertNull(operations.rejectedErrorDescription)
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun factoryMapsSwiftFriendlyConfigurationToWalletConfiguration() = runTest {
        var capturedConfig: WalletConfiguration? = null
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }
        val expectedTrustAnchor = X509CertificateUtil.parseCertificatePem(validTrustAnchorPem)

        val result = factory.create(
            WalletBridgeConfiguration(
                walletId = "consumer-wallet",
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
                    x509TrustAnchorsPem = listOf(validTrustAnchorPem),
                    preRegisteredClientMetadataJson = mapOf(
                        "demo-verifier" to """{"jwks":{"keys":[{"kty":"EC","crv":"P-256","x":"x","y":"y"}]}}""",
                    ),
                ),
                preferredLocales = listOf("de-AT", "en"),
                transactionDataProfiles = listOf(
                    TransactionDataProfile(
                        type = "example.transaction",
                        displayName = "Example Transaction",
                        fields = listOf("amount"),
                    )
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        assertEquals("consumer-wallet", capturedConfig?.walletId)
        assertEquals(
            WalletPersistence(),
            capturedConfig?.persistence,
        )
        assertEquals("https://attestation.example", capturedConfig?.issuance?.attestation?.baseUrl)
        assertEquals("/wallet-attestation", capturedConfig?.issuance?.attestation?.attesterPath)
        assertEquals("token", capturedConfig?.issuance?.attestation?.bearerToken)
        assertEquals("attestation.example", capturedConfig?.issuance?.attestation?.hostHeader)
        val capturedTrustConfiguration = capturedConfig?.presentation?.verifierTrust
        val capturedTrustStore = assertNotNull(capturedTrustConfiguration?.x509TrustAnchors)
        val capturedTrustAnchor = capturedTrustStore
            .findCertificateBySubjectDn(expectedTrustAnchor.data.subjectDn)
            .single()
        assertEquals(expectedTrustAnchor.encodedDer, capturedTrustAnchor.encodedDer)
        assertEquals(setOf("demo-verifier"), capturedTrustConfiguration?.preRegisteredClients?.keys)
        assertEquals(listOf("de-AT", "en"), capturedConfig?.preferredLocales)
        assertEquals(
            listOf(
                TransactionDataProfile(
                    type = "example.transaction",
                    displayName = "Example Transaction",
                    fields = listOf("amount"),
                )
            ),
            capturedConfig?.presentation?.transactionDataProfiles,
        )

        val credentials = result.value.credentials()
        assertIs<WalletBridgeResult.Success<List<WalletCredential>>>(credentials)
        assertEquals("credential-1", credentials.value.single().id)
    }

    @Test
    fun factoryMapsProvidedDatabaseKeyProviderToWalletConfiguration() = runTest {
        var capturedConfig: WalletConfiguration? = null
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
        val databaseKey = assertIs<WalletDatabaseKey.Provided>(persistence?.databaseKey)
        val key = databaseKey.provider.getOrCreateKey("swift-managed-wallet", "wallet_swift-managed-wallet")

        assertEquals(DatabaseEncryptionKey("swift-key", byteArrayOf(1, 2, 3)), key)
        databaseKey.provider.deleteKey("swift-managed-wallet", "wallet_swift-managed-wallet")
        assertEquals(listOf("swift-managed-wallet:wallet_swift-managed-wallet"), bridgeKeyProvider.deletedKeys)
    }

    @Test
    fun factoryMapsSwiftCredentialStoreOverrideToWalletConfiguration() = runTest {
        var capturedConfig: WalletConfiguration? = null
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
        assertIs<WalletDatabaseKey.Managed>(persistence?.databaseKey)
        assertNull(persistence?.didStore)
        val credentialStore = persistence?.credentialStore
        assertEquals(true, credentialStore?.removeCredential("credential-1"))
        assertEquals(listOf("credential-1"), bridgeCredentialStore.removedCredentialIds)
    }

    @Test
    fun swiftCredentialStorePersistsSdJwtDisclosuresForReloadableDisplay() = runTest {
        var capturedConfig: WalletConfiguration? = null
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
    fun factoryMapsSwiftDidStoreOverrideToWalletConfiguration() = runTest {
        var capturedConfig: WalletConfiguration? = null
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
        assertIs<WalletDatabaseKey.Managed>(persistence.databaseKey)

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
        var capturedConfig: WalletConfiguration? = null
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
        val databaseKey = assertIs<WalletDatabaseKey.Provided>(persistence?.databaseKey)
        val key = databaseKey.provider.getOrCreateKey("swift-combined-wallet", "wallet_swift-combined-wallet")

        assertEquals(DatabaseEncryptionKey("swift-key", byteArrayOf(4, 5, 6)), key)
        assertNull(persistence?.didStore)
        assertEquals(true, persistence?.credentialStore?.removeCredential("credential-1"))
        assertEquals(listOf("credential-1"), bridgeCredentialStore.removedCredentialIds)
    }

    @Test
    fun factoryUsesStableSwiftSdkDefaults() {
        val config = WalletBridgeConfiguration().toWalletConfiguration()

        assertEquals("default", config.walletId)
        assertEquals(null, config.issuance.attestation)
        assertEquals(WalletPersistence(), config.persistence)
        assertEquals(platformPreferredLocales(), config.preferredLocales)
        assertEquals(emptyList(), config.presentation.transactionDataProfiles)
    }

    @Test
    fun factoryValidatesBeforePlatformWorkAndPropagatesCancellation() = runTest {
        var opens = 0
        val factory = WalletSdkBridgeFactory.forOperationsFactory {
            opens++
            throw CancellationException("opening cancelled")
        }
        val profile = TransactionDataProfile("example.transaction")
        val invalid = factory.create(WalletBridgeConfiguration(transactionDataProfiles = listOf(profile, profile)))
        assertEquals(WalletBridgeErrorCategory.invalidInput, assertIs<WalletBridgeResult.Failure>(invalid).error.category)
        assertEquals(0, opens)
        assertFailsWith<CancellationException> { factory.create() }
        assertEquals(1, opens)
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
    fun statusStreamSerializesEmptyCollectionsAndReleasesTheNativeCollector() = runTest {
        var released = false
        val operations = FakeWalletSdkBridgeOperations()
        operations.statusFlow = flow {
            try {
                emit(WalletCredentialStatusSnapshot(
                    Instant.parse("2026-11-12T10:00:00Z"), emptyList()))
                awaitCancellation()
            } finally { released = true }
        }
        val bridge = WalletSdkBridge.forOperations(operations, emptyFlow())
        val update = assertIs<WalletBridgeCredentialStatusUpdate.Snapshot>(bridge.credentialStatuses.first())
        val json = Json.parseToJsonElement(update.snapshotJson).jsonObject
        assertEquals("[]", json.getValue("credentials").toString())
        assertEquals("[]", json.getValue("refreshing").toString())
        assertEquals(true, released)
    }

    @Test
    fun statusStreamSanitizesFailuresAndDoesNotConvertCancellationToAnUpdate() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations, emptyFlow())
        operations.statusFlow = flow { throw IllegalStateException("secret-status-url") }
        val failure = assertIs<WalletBridgeCredentialStatusUpdate.Failure>(bridge.credentialStatuses.first())
        assertEquals(WalletBridgeErrorCategory.internalFailure, failure.error.category)
        assertEquals("Credential status observation failed", failure.error.message)
        operations.statusFlow = flow { throw CancellationException("owner ended") }
        assertFailsWith<CancellationException> { bridge.credentialStatuses.first() }
    }

    @Test
    fun bridgeExposesCommonWalletEvents() = runTest {
        val events = MutableSharedFlow<WalletEvent>(replay = 1)
        val bridge = WalletSdkBridge.forOperations(
            operations = FakeWalletSdkBridgeOperations(),
            eventFlow = events,
        )

        events.emit(
            WalletEvent.PresentationCompleted
        )
        val event = bridge.events.first()

        assertEquals(WalletEventPhase.Presentation, event.phase)
        assertEquals(WalletEventStatus.Completed, event.status)
        assertEquals("presentation_completed", event.identifier)
    }

    private class FakeWalletSdkBridgeOperations(
        private val previewResult: PresentationPreviewResult? = null,
        private val requestAuthentication: PresentationRequestAuthentication =
            PresentationRequestAuthentication.Unauthenticated,
    ) : WalletSdkBridgeOperations {
        var deleteWalletCalls = 0
            private set
        var deleteCredentialCalls = 0
            private set
        val deletedCredentialIds = mutableListOf<String>()
        var previewRequestUrl: String? = null
            private set
        var submittedPreviewHandle: PresentationPreviewHandle? = null
            private set
        var submittedCredentialOptions: List<PresentationCredentialSelection>? = null
            private set
        var submittedDisclosureOptions: List<PresentationDisclosureSelection>? = null
            private set
        var submittedDid: String? = null
            private set
        var rejectedPreviewHandle: PresentationPreviewHandle? = null
            private set
        var rejectedErrorCode: PresentationErrorCode? = null
            private set
        var rejectedErrorDescription: String? = null
            private set
        var cancelledIssuanceSessionId: String? = null
            private set
        override suspend fun keyUseAuthorizationPreflight(
            keyType: WalletKeyType,
            policy: KeyUseAuthorizationPolicy,
        ): KeyUseAuthorizationSupport = error("Not used by this test fake")

        override suspend fun createIssuanceHolderKeys(
            count: Int, keyType: WalletKeyType?, didMethod: String, policy: KeyUseAuthorizationPolicy?,
        ): List<IssuanceHolderBinding> = List(count) { IssuanceHolderBinding("holder-$it", "did:key:holder-$it") }

        override suspend fun resumeIssuance(sessionId: String): WalletIssuanceOfferReview? = null

        override suspend fun startIssuance(request: IssuanceRequest) =
            error("Not used by this test fake")

        override suspend fun beginAuthorizationIssuance(sessionId: String, registration: IssuanceClientRegistration, credentials: List<IssuanceCredentialSelection>?): WalletIssuanceAuthorization =
            error("Not used by this test fake")

        override suspend fun continuePreAuthorizedIssuance(
            sessionId: String,
            transactionCode: String?,
            credentials: List<IssuanceCredentialSelection>?,
            clientId: String?,
        ) = error("Not used by this test fake")

        override suspend fun completeAuthorization(
            authorizationId: String,
            callbackUri: String,
        ) = error("Not used by this test fake")

        override suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome {
            cancelledIssuanceSessionId = sessionId
            return WalletIssuanceOutcome.Cancelled(sessionId)
        }

        override suspend fun listDeferredIssuance(): List<id.walt.wallet2.handlers.WalletIssuanceContinuation> = emptyList()

        override suspend fun resumeDeferredIssuance(deferredCredentialId: String) =
            error("Not used by this test fake")

        override suspend fun credentialReads(): List<CredentialWithStatus> = credentials().map {
            CredentialWithStatus(it, CredentialStatus.references(), Clock.System.now())
        }
        var statusFlow: Flow<WalletCredentialStatusSnapshot> = emptyFlow()
        override val credentialStatuses: Flow<WalletCredentialStatusSnapshot>
            get() = statusFlow
        override suspend fun credentialStatusSnapshot(): WalletCredentialStatusSnapshot =
            WalletCredentialStatusSnapshot(Clock.System.now(), emptyList())
        override suspend fun refreshCredentialStatuses(credentialIds: List<String>?, mode: CredentialStatusRefreshMode): List<CredentialStatusRefreshResult> = emptyList()
        override suspend fun close() = Unit

        override suspend fun credentials(): List<WalletCredential> =
            listOf(
                WalletCredential(
                    id = "credential-1",
                    format = "vc+sd-jwt",
                    issuer = "https://issuer.example",
                    subject = null,
                    label = "PID",
                    addedAt = null,
                    credentialDataJson = """{"given_name":"Ada"}""",
                )
            )

        override suspend fun deleteCredential(credentialId: String): Boolean {
            deleteCredentialCalls++
            deletedCredentialIds += credentialId
            return true
        }

        override suspend fun deleteWallet() {
            deleteWalletCalls++
        }

        override suspend fun previewPresentation(requestUrl: String): PresentationPreviewResult {
            previewRequestUrl = requestUrl
            return previewResult ?: PresentationPreviewResult.Ready(PresentationPreview(
                previewHandle = PresentationPreviewHandle("presentation-preview"),
                request = PresentationRequest(
                    clientId = "https://verifier.example",
                    verifierMetadata = VerifierMetadata(
                        display = MetadataDisplay(
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
                    responseEncryption = PresentationResponseEncryption.Required(
                        keyManagementAlgorithm = "ECDH-ES",
                        contentEncryptionAlgorithm = "A256GCM",
                        verifierKeyId = "verifier-key-1",
                        verifierKeyThumbprint = "thumbprint-1",
                    ),
                ),
                credentialOptions = listOf(
                    PresentationCredentialOption(
                        queryId = "pid",
                        credential = WalletCredential(
                            id = "credential-1",
                            format = "vc+sd-jwt",
                            credentialDataJson = """{"given_name":"Ada"}""",
                            issuer = "https://issuer.example",
                            label = "PID",
                        ),
                        multiple = true,
                        disclosures = emptyList(),
                    )
                ),
                credentialRequirements = listOf(
                    PresentationCredentialRequirement(options = listOf(listOf("pid")))
                ),
            ))
        }

        override suspend fun retainPresentationReview(
            previewHandle: PresentationPreviewHandle, selection: PresentationSelection, did: String?,
        ): PresentationReview {
            submittedPreviewHandle = previewHandle
            submittedCredentialOptions = selection.credentials
            submittedDisclosureOptions = selection.disclosures
            submittedDid = did
            throw IllegalArgumentException("Test wallet has no matching credential")
        }

        override suspend fun submitReviewedPresentation(review: PresentationReview): PresentationResult =
            error("No review returned by this fake")

        override suspend fun rejectPresentation(
            previewHandle: PresentationPreviewHandle,
            errorCode: PresentationErrorCode?,
            errorDescription: String?,
        ): PresentationResult {
            rejectedPreviewHandle = previewHandle
            rejectedErrorCode = errorCode
            rejectedErrorDescription = errorDescription
            return PresentationResult.Transmitted.Succeeded(
                verifierResponseJson = """{"accepted":false}""",
                redirectUrl = null,
            )
        }

        override suspend fun discardPresentationPreview(previewHandle: PresentationPreviewHandle) = Unit
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

private val validTrustAnchorPem = """
    -----BEGIN CERTIFICATE-----
    MIICCTCCAY6gAwIBAgINAgPlwGjvYxqccpBQUjAKBggqhkjOPQQDAzBHMQswCQYD
    VQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEUMBIG
    A1UEAxMLR1RTIFJvb3QgUjQwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAwMDAw
    WjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2Vz
    IExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjQwdjAQBgcqhkjOPQIBBgUrgQQAIgNi
    AATzdHOnaItgrkO4NcWBMHtLSZ37wWHO5t5GvWvVYRg1rkDdc/eJkTBa6zzuhXyi
    QHY7qca4R9gq55KRanPpsXI5nymfopjTX15YhmUPoYRlBtHci8nHc8iMai/lxKvR
    HYqjQjBAMA4GA1UdDwEB/wQEAwIBhjAPBgNVHRMBAf8EBTADAQH/MB0GA1UdDgQW
    BBSATNbrdP9JNqPV2Py1PsVq8JQdjDAKBggqhkjOPQQDAwNpADBmAjEA6ED/g94D
    9J+uHXqnLrmvT/aDHQ4thQEd0dlq7A/Cr8deVl5c1RxYIigL9zC2L7F8AjEA8GE8
    p/SgguMh1YQdc4acLa/KNJvxn7kjNuK8YAOdgLOaVsjh4rsUecrNIdSUtUlD
    -----END CERTIFICATE-----
""".trimIndent()
