package id.walt.wallet2.mobile.iosbridge

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            keyType = WalletBridgeKeyType.secp256r1,
            didMethod = "jwk",
        )

        assertIs<WalletBridgeResult.Success<WalletBridgeBootstrapResult>>(result)
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

        assertIs<WalletBridgeResult.Success<List<WalletBridgeCredential>>>(result)
        assertEquals("credential-1", result.value.single().id)
        assertEquals("https://issuer.example", result.value.single().issuer)
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

        assertIs<WalletBridgeResult.Success<WalletBridgePresentationResult>>(result)
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
                defaultKeyType = WalletBridgeKeyType.Ed25519,
                attestation = WalletBridgeAttestationConfiguration(
                    enterpriseBaseUrl = "https://enterprise.example",
                    attesterPath = "/wallet-attestation",
                    bearerToken = "token",
                    enterpriseHostHeader = "enterprise.example",
                ),
            )
        )

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)
        assertEquals("consumer-wallet", capturedConfig?.walletId)
        assertEquals(KeyType.Ed25519, capturedConfig?.defaultKeyType)
        assertEquals("https://enterprise.example", capturedConfig?.attestationConfig?.enterpriseBaseUrl)
        assertEquals("/wallet-attestation", capturedConfig?.attestationConfig?.attesterPath)
        assertEquals("token", capturedConfig?.attestationConfig?.bearerToken)
        assertEquals("enterprise.example", capturedConfig?.attestationConfig?.enterpriseHostHeader)

        val credentials = result.value.credentials()
        assertIs<WalletBridgeResult.Success<List<WalletBridgeCredential>>>(credentials)
        assertEquals("credential-1", credentials.value.single().id)
    }

    @Test
    fun factoryUsesStableSwiftSdkDefaults() {
        val config = WalletBridgeConfiguration().toMobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(KeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
    }

    @Test
    fun factoryReturnsTypedFailureWhenWalletCreationFails() {
        val factory = WalletSdkBridgeFactory.forOperationsFactory {
            throw IllegalArgumentException("bad wallet config")
        }

        val result = factory.create()

        assertIs<WalletBridgeResult.Failure>(result)
        assertEquals(WalletBridgeErrorCategory.invalidInput, result.error.category)
        assertEquals("bad wallet config", result.error.message)
    }

    @Test
    fun mapsWalletSessionEventsToSwiftSafeBridgeEvents() {
        val progress = WalletSessionEvent.issuance_offer_resolved.toWalletBridgeEvent()
        val completed = WalletSessionEvent.presentation_completed.toWalletBridgeEvent()
        val failed = WalletSessionEvent.issuance_failed.toWalletBridgeEvent()

        assertEquals(WalletBridgeEventPhase.issuance, progress.phase)
        assertEquals(WalletBridgeEventStatus.progress, progress.status)
        assertEquals("issuance_offer_resolved", progress.name)

        assertEquals(WalletBridgeEventPhase.presentation, completed.phase)
        assertEquals(WalletBridgeEventStatus.completed, completed.status)
        assertEquals("presentation_completed", completed.name)

        assertEquals(WalletBridgeEventPhase.issuance, failed.phase)
        assertEquals(WalletBridgeEventStatus.failed, failed.status)
        assertEquals("issuance_failed", failed.name)
    }

    @Test
    fun factoryWiresWalletSessionEventsIntoBridgeEventFlow() = runTest {
        var capturedConfig: MobileWalletConfig? = null
        val factory = WalletSdkBridgeFactory.forOperationsFactory { config ->
            capturedConfig = config
            FakeWalletSdkBridgeOperations()
        }

        val result = factory.create()

        assertIs<WalletBridgeResult.Success<WalletSdkBridge>>(result)

        capturedConfig?.onEvent?.invoke(WalletSessionEvent.presentation_completed)
        val event = result.value.events().first()

        assertEquals(WalletBridgeEventPhase.presentation, event.phase)
        assertEquals(WalletBridgeEventStatus.completed, event.status)
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

        override suspend fun bootstrap(
            keyType: KeyType?,
            didMethod: String,
        ): MobileWalletBootstrapResult {
            bootstrapKeyType = keyType
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
                verifierResponse = JsonObject(mapOf("accepted" to JsonPrimitive(true))),
            )
        }
    }
}
