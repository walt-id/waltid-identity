package id.walt.wallet2.client

import id.walt.crypto.keys.KeyType

/** Orchestrates wallet operations (bootstrap, issuance, presentation) through a platform-specific adapter. */
class WalletClient(
    private val adapter: WalletClientAdapter,
    private val endpointRewriter: WalletEndpointRewriter = WalletEndpointRewriter.Noop,
    private val attestationProvider: WalletClientAttestationProvider = WalletClientAttestationProvider.Noop,
) {
    suspend fun bootstrapWallet(
        keyType: KeyType = KeyType.secp256r1,
        didMethod: String = "key",
    ): WalletBootstrapResult =
        adapter.bootstrapWallet(keyType = keyType, didMethod = didMethod)

    suspend fun receiveCredential(
        offerUrl: String,
        txCode: String? = null,
        clientId: String = "wallet-client",
        requireAttestation: Boolean = false,
    ): List<String> {
        if (requireAttestation) {
            attestationProvider.ensureReady()
        }

        return adapter.receiveCredential(
            offerUrl = endpointRewriter.rewrite(offerUrl),
            txCode = txCode,
            clientId = clientId,
        )
    }

    suspend fun listCredentials(): List<WalletCredentialSummary> =
        adapter.listCredentials()

    suspend fun presentCredential(
        requestUrl: String,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): WalletPresentationResult =
        adapter.presentCredential(
            requestUrl = endpointRewriter.rewrite(requestUrl),
            did = did,
            runPolicies = runPolicies,
        )
}
