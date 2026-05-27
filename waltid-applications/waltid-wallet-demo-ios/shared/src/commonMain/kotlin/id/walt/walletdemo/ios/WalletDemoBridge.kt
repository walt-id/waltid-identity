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

        return try {
            val result = client.bootstrap()
            did = result.did
            BridgeOperationResult(success = true, message = result.did)
        } catch (e: Throwable) {
            BridgeOperationResult(success = false, message = "Bootstrap failed: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun receiveCredential(offerUrl: String): BridgeOperationResult {
        return try {
            val ids = client.receive(offerUrl = offerUrl)
            BridgeOperationResult(success = true, message = "Received ${ids.size} credential(s)")
        } catch (e: Throwable) {
            BridgeOperationResult(success = false, message = "Receive failed: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun listCredentials(): List<BridgeCredential> =
        try {
            client.credentials().map { credential ->
                BridgeCredential(
                    id = credential.id,
                    format = credential.format,
                    issuer = credential.issuer ?: "Unknown",
                    label = credential.label ?: credential.format,
                    addedAt = credential.addedAt ?: "",
                )
            }
        } catch (e: Throwable) {
            emptyList() // TODO: log error for debugging
        }

    suspend fun presentCredential(requestUrl: String): BridgeOperationResult {
        return try {
            val result = client.present(requestUrl = requestUrl)
            BridgeOperationResult(
                success = result.success,
                message = if (result.success) "Presentation sent" else "Presentation finished without verifier confirmation",
            )
        } catch (e: Throwable) {
            BridgeOperationResult(success = false, message = "Present failed: ${e.message ?: e::class.simpleName}")
        }
    }
}
