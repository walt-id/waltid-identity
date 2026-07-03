package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.WalletAttestationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

data class WalletBridgeConfiguration(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val attestation: WalletAttestationConfig? = null,
)

fun WalletBridgeConfiguration.toMobileWalletConfig() = MobileWalletConfig(
    walletId = walletId,
    defaultKeyType = defaultKeyType,
    attestationConfig = attestation,
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
