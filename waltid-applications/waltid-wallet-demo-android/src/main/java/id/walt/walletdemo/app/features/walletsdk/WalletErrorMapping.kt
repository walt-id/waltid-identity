package id.walt.walletdemo.app.features.walletsdk

import java.io.IOException

enum class WalletErrorCategory {
    NETWORK,
    AUTH,
    PROOF,
    CREDENTIAL,
    PRESENTATION,
    ATTESTATION,
    VALIDATION,
    UNKNOWN,
}

data class WalletErrorInfo(
    val category: WalletErrorCategory,
    val operation: String,
    val detail: String,
)

object WalletErrorMapper {
    fun map(operation: String, throwable: Throwable): WalletErrorInfo {
        val detail = throwable.message ?: (throwable::class.simpleName ?: "Unexpected error")
        val msg = detail.lowercase()

        val category = when {
            throwable is IOException ||
                    msg.contains("timeout") ||
                    msg.contains("connection") ||
                    msg.contains("network") ||
                    msg.contains("unreachable") -> WalletErrorCategory.NETWORK

            msg.contains("401") ||
                    msg.contains("403") ||
                    msg.contains("unauthorized") ||
                    msg.contains("forbidden") ||
                    msg.contains("auth") -> WalletErrorCategory.AUTH

            msg.contains("attestation") ||
                    msg.contains("client_assertion") ||
                    msg.contains("cnf") -> WalletErrorCategory.ATTESTATION

            msg.contains("proof") ||
                    msg.contains("nonce") ||
                    msg.contains("key") ||
                    msg.contains("did") -> WalletErrorCategory.PROOF

            msg.contains("presentation") ||
                    msg.contains("openid4vp") ||
                    msg.contains("vp_token") ||
                    msg.contains("verifier") -> WalletErrorCategory.PRESENTATION

            msg.contains("credential") ||
                    msg.contains("offer") ||
                    msg.contains("issuer") ||
                    msg.contains("token_endpoint") -> WalletErrorCategory.CREDENTIAL

            msg.contains("invalid") ||
                    msg.contains("missing") ||
                    msg.contains("required") ||
                    msg.contains("must") -> WalletErrorCategory.VALIDATION

            else -> WalletErrorCategory.UNKNOWN
        }

        return WalletErrorInfo(
            category = category,
            operation = operation,
            detail = detail,
        )
    }
}
