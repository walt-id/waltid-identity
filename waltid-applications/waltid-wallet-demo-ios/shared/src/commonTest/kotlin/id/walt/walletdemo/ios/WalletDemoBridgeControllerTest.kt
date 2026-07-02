package id.walt.walletdemo.ios

import id.walt.wallet2.mobile.iosbridge.WalletBridgeBootstrapResult
import id.walt.wallet2.mobile.iosbridge.WalletBridgeCredential
import id.walt.wallet2.mobile.iosbridge.WalletBridgeError
import id.walt.wallet2.mobile.iosbridge.WalletBridgeErrorCategory
import id.walt.wallet2.mobile.iosbridge.WalletBridgePresentationResult
import id.walt.wallet2.mobile.iosbridge.WalletBridgeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletDemoBridgeControllerTest {

    @Test
    fun bootstrapCachesDidFromSdkBridge() = kotlinx.coroutines.test.runTest {
        val operations = FakeWalletDemoBridgeOperations(
            bootstrapResult = WalletBridgeResult.Success(
                WalletBridgeBootstrapResult(
                    keyId = "key-1",
                    did = "did:key:wallet",
                )
            )
        )
        val controller = WalletDemoBridgeController(operations)

        val first = controller.bootstrap()
        val second = controller.bootstrap()

        assertTrue(first.success)
        assertEquals("did:key:wallet", first.message)
        assertEquals(first, second)
        assertEquals(1, operations.bootstrapCalls)
    }

    @Test
    fun receiveAndListUseSdkBridgeResults() = kotlinx.coroutines.test.runTest {
        val operations = FakeWalletDemoBridgeOperations(
            receiveResult = WalletBridgeResult.Success(listOf("credential-1", "credential-2")),
            credentialsResult = WalletBridgeResult.Success(
                listOf(
                    WalletBridgeCredential(
                        id = "credential-1",
                        format = "vc+sd-jwt",
                        issuer = null,
                        subject = null,
                        label = null,
                        addedAt = "2026-07-02T10:00:00Z",
                    )
                )
            )
        )
        val controller = WalletDemoBridgeController(operations)

        val receive = controller.receiveCredential("openid-credential-offer://credential_offer")
        val credentials = controller.listCredentials()

        assertTrue(receive.success)
        assertEquals("Received 2 credential(s)", receive.message)
        assertEquals(1, credentials.size)
        assertEquals("credential-1", credentials.single().id)
        assertEquals("Unknown", credentials.single().issuer)
        assertEquals("vc+sd-jwt", credentials.single().label)
    }

    @Test
    fun presentMapsSdkBridgeResultMessage() = kotlinx.coroutines.test.runTest {
        val operations = FakeWalletDemoBridgeOperations(
            presentResult = WalletBridgeResult.Success(
                WalletBridgePresentationResult(
                    success = false,
                    redirectTo = null,
                    verifierResponseJson = null,
                )
            )
        )
        val controller = WalletDemoBridgeController(operations)

        val present = controller.presentCredential("openid4vp://verifier", did = "did:key:wallet")

        assertFalse(present.success)
        assertEquals("Presentation finished without verifier confirmation", present.message)
    }

    @Test
    fun bridgeFailuresBecomeDemoOperationFailures() = kotlinx.coroutines.test.runTest {
        val operations = FakeWalletDemoBridgeOperations(
            receiveResult = WalletBridgeResult.Failure(
                WalletBridgeError(
                    category = WalletBridgeErrorCategory.invalidInput,
                    message = "missing offer",
                )
            )
        )
        val controller = WalletDemoBridgeController(operations)

        val receive = controller.receiveCredential("bad")

        assertFalse(receive.success)
        assertEquals("Receive failed: missing offer", receive.message)
    }
}

private class FakeWalletDemoBridgeOperations(
    var bootstrapResult: WalletBridgeResult<WalletBridgeBootstrapResult> =
        WalletBridgeResult.Success(WalletBridgeBootstrapResult("key", "did:key:wallet")),
    var receiveResult: WalletBridgeResult<List<String>> =
        WalletBridgeResult.Success(emptyList()),
    var credentialsResult: WalletBridgeResult<List<WalletBridgeCredential>> =
        WalletBridgeResult.Success(emptyList()),
    var presentResult: WalletBridgeResult<WalletBridgePresentationResult> =
        WalletBridgeResult.Success(WalletBridgePresentationResult(success = true, redirectTo = null, verifierResponseJson = null)),
) : WalletDemoBridgeOperations {
    var bootstrapCalls = 0

    override suspend fun bootstrap() = bootstrapResult.also { bootstrapCalls += 1 }

    override suspend fun receiveCredential(offerUrl: String) = receiveResult

    override suspend fun listCredentials() = credentialsResult

    override suspend fun presentCredential(
        requestUrl: String,
        did: String?,
    ) = presentResult
}
