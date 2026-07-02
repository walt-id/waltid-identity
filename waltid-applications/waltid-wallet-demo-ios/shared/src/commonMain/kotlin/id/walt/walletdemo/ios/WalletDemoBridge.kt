package id.walt.walletdemo.ios

import id.walt.wallet2.mobile.iosbridge.WalletBridgeAttestationConfiguration
import id.walt.wallet2.mobile.iosbridge.WalletBridgeBootstrapResult
import id.walt.wallet2.mobile.iosbridge.WalletBridgeConfiguration
import id.walt.wallet2.mobile.iosbridge.WalletBridgeCredential
import id.walt.wallet2.mobile.iosbridge.WalletBridgePresentationResult
import id.walt.wallet2.mobile.iosbridge.WalletBridgeResult
import id.walt.wallet2.mobile.iosbridge.WalletSdkBridge
import id.walt.wallet2.mobile.iosbridge.WalletSdkBridgeFactory

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

class WalletDemoBridgeController private constructor(
    private val operationsResult: Result<WalletDemoBridgeOperations>,
) {
    constructor(
        walletId: String = "default",
        attestationBaseUrl: String? = null,
        attestationAttesterPath: String? = null,
        attestationBearerToken: String? = null,
        attestationHostHeader: String? = null,
    ) : this(
        operationsResult = runCatching {
            val bridge = WalletSdkBridgeFactory().create(
                WalletBridgeConfiguration(
                    walletId = walletId,
                    attestation = attestationBaseUrl?.takeIf { it.isNotBlank() }?.let {
                        WalletBridgeAttestationConfiguration(
                            enterpriseBaseUrl = it,
                            attesterPath = attestationAttesterPath ?: "",
                            bearerToken = attestationBearerToken ?: "",
                            enterpriseHostHeader = attestationHostHeader ?: "",
                        )
                    },
                )
            ).successOrThrow()

            SdkWalletDemoBridgeOperations(bridge)
        }
    )

    constructor(
        attestationBaseUrl: String? = null,
        attestationAttesterPath: String? = null,
        attestationBearerToken: String? = null,
        attestationHostHeader: String? = null,
    ) : this(
        walletId = "default",
        attestationBaseUrl = attestationBaseUrl,
        attestationAttesterPath = attestationAttesterPath,
        attestationBearerToken = attestationBearerToken,
        attestationHostHeader = attestationHostHeader,
    )

    internal constructor(operations: WalletDemoBridgeOperations) : this(Result.success(operations))

    private val operations: WalletDemoBridgeOperations
        get() = operationsResult.getOrThrow()

    private var did = ""

    suspend fun bootstrap(): BridgeOperationResult {
        if (did.isNotBlank()) {
            return BridgeOperationResult(success = true, message = did)
        }

        return try {
            val result = operations.bootstrap().successOrThrow()
            did = result.did
            BridgeOperationResult(success = true, message = result.did)
        } catch (e: Throwable) {
            BridgeOperationResult(success = false, message = "Bootstrap failed: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun receiveCredential(offerUrl: String): BridgeOperationResult {
        return try {
            val ids = operations.receiveCredential(offerUrl = offerUrl).successOrThrow()
            BridgeOperationResult(success = true, message = "Received ${ids.size} credential(s)")
        } catch (e: Throwable) {
            BridgeOperationResult(success = false, message = "Receive failed: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun listCredentials(): List<BridgeCredential> =
        try {
            operations.listCredentials().successOrThrow().map { credential ->
                BridgeCredential(
                    id = credential.id,
                    format = credential.format,
                    issuer = credential.issuer ?: "Unknown",
                    label = credential.label ?: credential.format,
                    addedAt = credential.addedAt ?: "",
                )
            }
        } catch (e: Throwable) {
            println("WalletDemoBridgeController.listCredentials failed: ${e.message ?: e::class.simpleName}")
            emptyList()
        }

    suspend fun presentCredential(requestUrl: String, did: String? = null): BridgeOperationResult {
        return try {
            val result = operations.presentCredential(requestUrl = requestUrl, did = did).successOrThrow()
            BridgeOperationResult(
                success = result.success,
                message = if (result.success) "Presentation sent" else "Presentation finished without verifier confirmation",
            )
        } catch (e: Throwable) {
            BridgeOperationResult(success = false, message = "Present failed: ${e.message ?: e::class.simpleName}")
        }
    }
}

internal interface WalletDemoBridgeOperations {
    suspend fun bootstrap(): WalletBridgeResult<WalletBridgeBootstrapResult>

    suspend fun receiveCredential(offerUrl: String): WalletBridgeResult<List<String>>

    suspend fun listCredentials(): WalletBridgeResult<List<WalletBridgeCredential>>

    suspend fun presentCredential(
        requestUrl: String,
        did: String?,
    ): WalletBridgeResult<WalletBridgePresentationResult>
}

private class SdkWalletDemoBridgeOperations(
    private val bridge: WalletSdkBridge,
) : WalletDemoBridgeOperations {
    override suspend fun bootstrap(): WalletBridgeResult<WalletBridgeBootstrapResult> =
        bridge.bootstrap()

    override suspend fun receiveCredential(offerUrl: String): WalletBridgeResult<List<String>> =
        bridge.receive(offerUrl = offerUrl)

    override suspend fun listCredentials(): WalletBridgeResult<List<WalletBridgeCredential>> =
        bridge.credentials()

    override suspend fun presentCredential(
        requestUrl: String,
        did: String?,
    ): WalletBridgeResult<WalletBridgePresentationResult> =
        bridge.present(requestUrl = requestUrl, did = did)
}

private fun <T> WalletBridgeResult<T>.successOrThrow(): T = when (this) {
    is WalletBridgeResult.Success -> value
    is WalletBridgeResult.Failure -> throw WalletDemoBridgeException(error.message)
}

private class WalletDemoBridgeException(message: String) : RuntimeException(message)
