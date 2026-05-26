package id.walt.wallet2.client

import id.walt.crypto.keys.KeyType

/** Result of wallet initialization — contains the generated key ID and DID. */
data class WalletBootstrapResult(
    val keyId: String,
    val did: String,
)

data class WalletCredentialSummary(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: String?,
)

data class WalletPresentationResult(
    val getUrl: String?,
    val formPostHtml: String?,
    val transmissionSuccess: Boolean?,
    val redirectTo: String?,
)

/** Platform-specific adapter implementing wallet operations against local or remote storage. */
interface WalletClientAdapter {
    suspend fun bootstrapWallet(
        keyType: KeyType = KeyType.secp256r1,
        didMethod: String = "key",
    ): WalletBootstrapResult

    suspend fun receiveCredential(
        offerUrl: String,
        txCode: String? = null,
        clientId: String = "wallet-client",
    ): List<String>

    suspend fun listCredentials(): List<WalletCredentialSummary>

    suspend fun presentCredential(
        requestUrl: String,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): WalletPresentationResult
}
