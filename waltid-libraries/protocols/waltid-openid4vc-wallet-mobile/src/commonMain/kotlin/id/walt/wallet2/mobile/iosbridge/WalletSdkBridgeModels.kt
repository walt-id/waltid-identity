package id.walt.wallet2.mobile.iosbridge

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.WalletAttestationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
enum class WalletBridgeKeyType {
    Ed25519,
    secp256k1,
    secp256r1,
    secp384r1,
    secp521r1,
    RSA,
    RSA3072,
    RSA4096,
}

fun WalletBridgeKeyType.toKeyType(): KeyType = when (this) {
    WalletBridgeKeyType.Ed25519 -> KeyType.Ed25519
    WalletBridgeKeyType.secp256k1 -> KeyType.secp256k1
    WalletBridgeKeyType.secp256r1 -> KeyType.secp256r1
    WalletBridgeKeyType.secp384r1 -> KeyType.secp384r1
    WalletBridgeKeyType.secp521r1 -> KeyType.secp521r1
    WalletBridgeKeyType.RSA -> KeyType.RSA
    WalletBridgeKeyType.RSA3072 -> KeyType.RSA3072
    WalletBridgeKeyType.RSA4096 -> KeyType.RSA4096
}

@Serializable
data class WalletBridgeConfiguration(
    val walletId: String = "default",
    val defaultKeyType: WalletBridgeKeyType = WalletBridgeKeyType.secp256r1,
    val attestation: WalletBridgeAttestationConfiguration? = null,
)

fun WalletBridgeConfiguration.toMobileWalletConfig(
    onEvent: suspend (WalletSessionEvent) -> Unit = {},
) = MobileWalletConfig(
    walletId = walletId,
    defaultKeyType = defaultKeyType.toKeyType(),
    attestationConfig = attestation?.toWalletAttestationConfig(),
    onEvent = onEvent,
)

@Serializable
data class WalletBridgeAttestationConfiguration(
    val enterpriseBaseUrl: String,
    val attesterPath: String,
    val bearerToken: String = "",
    val enterpriseHostHeader: String = "",
)

fun WalletBridgeAttestationConfiguration.toWalletAttestationConfig() = WalletAttestationConfig(
    enterpriseBaseUrl = enterpriseBaseUrl,
    attesterPath = attesterPath,
    bearerToken = bearerToken,
    enterpriseHostHeader = enterpriseHostHeader,
)

@Serializable
data class WalletBridgeBootstrapResult(
    val keyId: String,
    val did: String,
)

fun MobileWalletBootstrapResult.toWalletBridgeBootstrapResult() = WalletBridgeBootstrapResult(
    keyId = keyId,
    did = did,
)

@Serializable
data class WalletBridgeCredential(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: String?,
)

fun MobileWalletCredential.toWalletBridgeCredential() = WalletBridgeCredential(
    id = id,
    format = format,
    issuer = issuer,
    subject = subject,
    label = label,
    addedAt = addedAt,
)

@Serializable
data class WalletBridgePresentationResult(
    val success: Boolean,
    val redirectTo: String?,
    val verifierResponseJson: String?,
)

fun MobileWalletPresentationResult.toWalletBridgePresentationResult() = WalletBridgePresentationResult(
    success = success,
    redirectTo = redirectTo,
    verifierResponseJson = verifierResponse?.let { Json.encodeToString(JsonElement.serializer(), it) },
)

@Serializable
enum class WalletBridgeEventPhase {
    issuance,
    presentation,
}

@Serializable
enum class WalletBridgeEventStatus {
    progress,
    completed,
    failed,
}

@Serializable
data class WalletBridgeEvent(
    val name: String,
    val phase: WalletBridgeEventPhase,
    val status: WalletBridgeEventStatus,
)

fun WalletSessionEvent.toWalletBridgeEvent() = WalletBridgeEvent(
    name = name,
    phase = when {
        name.startsWith("issuance_") -> WalletBridgeEventPhase.issuance
        else -> WalletBridgeEventPhase.presentation
    },
    status = when (this) {
        WalletSessionEvent.issuance_completed,
        WalletSessionEvent.presentation_completed -> WalletBridgeEventStatus.completed

        WalletSessionEvent.issuance_failed,
        WalletSessionEvent.presentation_failed -> WalletBridgeEventStatus.failed

        else -> WalletBridgeEventStatus.progress
    },
)

@Serializable
enum class WalletBridgeErrorCategory {
    invalidInput,
    network,
    issuer,
    verifier,
    storage,
    crypto,
    credentialNotFound,
    cancelled,
    internalFailure,
}

@Serializable
data class WalletBridgeError(
    val category: WalletBridgeErrorCategory,
    val message: String,
    val causeClass: String? = null,
) {
    companion object {
        fun fromThrowable(throwable: Throwable): WalletBridgeError {
            val category = when (throwable) {
                is CancellationException -> WalletBridgeErrorCategory.cancelled
                is IllegalArgumentException -> WalletBridgeErrorCategory.invalidInput
                else -> WalletBridgeErrorCategory.internalFailure
            }

            return WalletBridgeError(
                category = category,
                message = throwable.message ?: throwable::class.simpleName ?: "Unknown wallet error",
                causeClass = throwable::class.simpleName,
            )
        }
    }
}

sealed interface WalletBridgeResult<out T> {
    data class Success<T>(val value: T) : WalletBridgeResult<T>
    data class Failure(val error: WalletBridgeError) : WalletBridgeResult<Nothing>
}
