package id.walt.walletdemo.ios

import id.walt.wallet2.client.NativeWalletClient

data class BridgeCredential(
    val id: String,
    val format: String,
    val issuer: String,
    val label: String,
    val addedAt: String,
)

data class BridgeOperationResult(
    val success: Boolean,
    val message: String,
)

class WalletDemoBridgeController {
    private val client = NativeWalletClient()
    private var did = ""

    suspend fun bootstrap(): BridgeOperationResult {
        if (did.isNotBlank()) {
            return BridgeOperationResult(success = true, message = did)
        }

        val result = client.bootstrap()
        did = result.did
        return BridgeOperationResult(success = true, message = result.did)
    }

    suspend fun receiveCredential(offerUrl: String): BridgeOperationResult {
        val ids = client.receive(offerUrl = offerUrl)
        return BridgeOperationResult(
            success = true,
            message = "Received ${ids.size} credential(s)",
        )
    }

    suspend fun listCredentials(): List<BridgeCredential> =
        client.credentials().map { credential ->
            BridgeCredential(
                id = credential.id,
                format = credential.format,
                issuer = credential.issuer ?: "Unknown",
                label = credential.label ?: credential.format,
                addedAt = credential.addedAt ?: "",
            )
        }

    suspend fun presentCredential(requestUrl: String): BridgeOperationResult {
        val result = client.present(requestUrl = requestUrl)
        return BridgeOperationResult(
            success = result.success,
            message = if (result.success) "Presentation sent" else "Presentation finished without verifier confirmation",
        )
    }
}
