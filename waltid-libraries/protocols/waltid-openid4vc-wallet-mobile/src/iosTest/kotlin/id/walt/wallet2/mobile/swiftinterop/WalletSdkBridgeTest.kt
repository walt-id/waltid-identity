package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletEventPhase
import id.walt.wallet2.mobile.MobileWalletEventStatus
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.MobileWalletPersistenceConfig
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.wallet2.mobile.toKeyType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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
        assertEquals(KeyType.secp256r1, operations.bootstrapKeyType)
        assertEquals("jwk", operations.bootstrapDidMethod)
    }

    @Test
    fun bridgeCredentialsMapToSwiftSafeDtos() = runTest {
        val operations = FakeWalletSdkBridgeOperations()
        val bridge = WalletSdkBridge.forOperations(operations)

        val result = bridge.credentials()

        assertIs<WalletBridgeResult.Success<List<MobileWalletCredential>>>(result)
        assertEquals("credential-1", result.value.single().id)
        assertEquals("https://issuer.example", result.value.single().issuer)
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
        assertEquals(true, result.value.success)
        assertEquals("wallet://return", result.value.redirectTo)
        assertEquals("""{"accepted":true}""", result.value.verifierResponseJson)
        assertEquals("openid4vp://request", operations.presentationRequestUrl)
        assertEquals("did:jwk:issuer", operations.presentationDid)
        assertEquals(true, operations.presentationRunPolicies)
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
                persistence = WalletBridgePersistenceConfiguration.SdkManagedEncrypted,
                attestation = WalletAttestationConfig(
                    baseUrl = "https://attestation.example",
                    attesterPath = "/wallet-attestation",
                    bearerToken = "token",
                    hostHeader = "attestation.example",
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        assertEquals("consumer-wallet", capturedConfig?.walletId)
        assertEquals(MobileWalletKeyType.Ed25519, capturedConfig?.defaultKeyType)
        assertEquals(
            MobileWalletPersistenceConfig.SdkManagedEncrypted,
            capturedConfig?.persistence,
        )
        assertEquals("https://attestation.example", capturedConfig?.attestationConfig?.baseUrl)
        assertEquals("/wallet-attestation", capturedConfig?.attestationConfig?.attesterPath)
        assertEquals("token", capturedConfig?.attestationConfig?.bearerToken)
        assertEquals("attestation.example", capturedConfig?.attestationConfig?.hostHeader)

        val credentials = result.value.credentials()
        assertIs<WalletBridgeResult.Success<List<MobileWalletCredential>>>(credentials)
        assertEquals("credential-1", credentials.value.single().id)
    }

    @Test
    fun factoryMapsSwiftManagedDatabaseKeyProviderToMobileWalletConfig() = runTest {
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
                persistence = WalletBridgePersistenceConfiguration.IntegratorManagedKey,
                databaseKeyProvider = bridgeKeyProvider,
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        val persistence = assertIs<MobileWalletPersistenceConfig.IntegratorManagedKey>(capturedConfig?.persistence)
        val key = persistence.keyProvider.getOrCreateKey("swift-managed-wallet", "wallet_swift-managed-wallet")

        assertEquals(DatabaseEncryptionKey("swift-key", byteArrayOf(1, 2, 3)), key)
        persistence.keyProvider.deleteKey("swift-managed-wallet", "wallet_swift-managed-wallet")
        assertEquals(listOf("swift-managed-wallet:wallet_swift-managed-wallet"), bridgeKeyProvider.deletedKeys)
    }

    @Test
    fun factoryUsesStableSwiftSdkDefaults() {
        val config = WalletBridgeConfiguration().toMobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
        assertEquals(MobileWalletPersistenceConfig.SdkManagedEncrypted, config.persistence)
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
        var presentationRequestUrl: String? = null
            private set
        var presentationDid: String? = null
            private set
        var presentationRunPolicies: Boolean? = null
            private set
        var deleteWalletCalls = 0
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
            return MobileWalletPresentationResult(
                success = true,
                redirectTo = "wallet://return",
                verifierResponseJson = """{"accepted":true}""",
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
}
