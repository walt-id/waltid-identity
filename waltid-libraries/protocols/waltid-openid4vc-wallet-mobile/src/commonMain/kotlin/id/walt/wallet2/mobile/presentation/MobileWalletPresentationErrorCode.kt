package id.walt.wallet2.mobile.presentation

/**
 * OAuth 2.0 and OpenID4VP 1.0 authorization error codes supported by the wallet.
 *
 * Apps should use [accessDenied] when the user declines, the wallet has no requested credential,
 * or user authentication fails. The remaining values describe protocol or availability failures
 * and should not be presented as end-user choices.
 *
 * @property errorCode Error code returned to the verifier.
 */
public enum class MobileWalletPresentationErrorCode(
    public val errorCode: String,
) {
    accessDenied("access_denied"),
    invalidRequest("invalid_request"),
    invalidClient("invalid_client"),
    invalidScope("invalid_scope"),
    unauthorizedClient("unauthorized_client"),
    unsupportedResponseType("unsupported_response_type"),
    serverError("server_error"),
    temporarilyUnavailable("temporarily_unavailable"),
    vpFormatsNotSupported("vp_formats_not_supported"),
    invalidRequestUriMethod("invalid_request_uri_method"),
    invalidTransactionData("invalid_transaction_data"),
    walletUnavailable("wallet_unavailable"),
}
