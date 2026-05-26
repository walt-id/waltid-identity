package id.walt.wallet2.client

import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletClientTest {

    @Test
    fun receiveRewritesOfferUrlAndChecksAttestationWhenRequired() = runTest {
        val adapter = RecordingWalletClientAdapter()
        val attestationProvider = RecordingAttestationProvider()
        val client = WalletClient(
            adapter = adapter,
            endpointRewriter = WalletEndpointRewriter.HostRewriter(
                mapOf("waltid.enterprise.localhost" to "10.0.2.2")
            ),
            attestationProvider = attestationProvider,
        )

        val ids = client.receiveCredential(
            offerUrl = "openid-credential-offer://?credential_offer_uri=http%3A%2F%2Fwaltid.enterprise.localhost%2Foffer",
            requireAttestation = true,
        )

        assertEquals(listOf("credential-1"), ids)
        assertEquals(1, attestationProvider.ensureReadyCalls)
        assertEquals(
            "openid-credential-offer://?credential_offer_uri=http%3A%2F%2F10.0.2.2%2Foffer",
            adapter.receivedOfferUrl,
        )
    }

    @Test
    fun presentRewritesRequestUrl() = runTest {
        val adapter = RecordingWalletClientAdapter()
        val client = WalletClient(
            adapter = adapter,
            endpointRewriter = WalletEndpointRewriter.HostRewriter(
                mapOf("waltid.enterprise.localhost" to "10.0.2.2")
            ),
        )

        client.presentCredential(
            requestUrl = "openid4vp://authorize?request_uri=http%3A%2F%2Fwaltid.enterprise.localhost%2Frequest"
        )

        assertEquals(
            "openid4vp://authorize?request_uri=http%3A%2F%2F10.0.2.2%2Frequest",
            adapter.presentedRequestUrl,
        )
    }

    @Test
    fun receiveSkipsAttestationWhenNotRequired() = runTest {
        val adapter = RecordingWalletClientAdapter()
        val attestationProvider = RecordingAttestationProvider()
        val client = WalletClient(adapter = adapter, attestationProvider = attestationProvider)

        client.receiveCredential("offer", requireAttestation = false)

        assertEquals(0, attestationProvider.ensureReadyCalls)
    }
}

private class RecordingWalletClientAdapter : WalletClientAdapter {
    var receivedOfferUrl: String? = null
    var presentedRequestUrl: String? = null

    override suspend fun bootstrapWallet(keyType: KeyType, didMethod: String): WalletBootstrapResult =
        WalletBootstrapResult(keyId = "key", did = "did:key:test")

    override suspend fun receiveCredential(
        offerUrl: String,
        txCode: String?,
        clientId: String,
    ): List<String> {
        receivedOfferUrl = offerUrl
        return listOf("credential-1")
    }

    override suspend fun listCredentials(): List<WalletCredentialSummary> = emptyList()

    override suspend fun presentCredential(
        requestUrl: String,
        did: String?,
        runPolicies: Boolean?,
    ): WalletPresentationResult {
        presentedRequestUrl = requestUrl
        return WalletPresentationResult(
            getUrl = null,
            formPostHtml = null,
            transmissionSuccess = true,
            redirectTo = null,
        )
    }
}

private class RecordingAttestationProvider : WalletClientAttestationProvider {
    var ensureReadyCalls = 0

    override suspend fun ensureReady(): WalletClientAttestationStatus {
        ensureReadyCalls += 1
        return WalletClientAttestationStatus.Present(expiresAt = 1)
    }
}
