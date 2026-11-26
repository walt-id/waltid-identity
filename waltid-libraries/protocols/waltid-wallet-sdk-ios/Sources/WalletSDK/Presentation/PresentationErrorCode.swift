import Foundation

/// OAuth 2.0 and OpenID4VP 1.0 authorization error codes supported by the wallet.
///
/// Use ``accessDenied`` when the user declines, the wallet has no requested
/// credential, or user authentication fails. Other cases describe protocol or
/// availability failures and should not be presented as end-user choices.
public enum PresentationErrorCode: String, Equatable, Sendable {
    /// The user or wallet denied the presentation request.
    case accessDenied = "access_denied"

    /// The authorization request is malformed or missing a required parameter.
    case invalidRequest = "invalid_request"

    /// The request's client identification is invalid.
    case invalidClient = "invalid_client"

    /// The requested scope is invalid, unknown, or unsupported.
    case invalidScope = "invalid_scope"

    /// The client is not authorized to make this presentation request.
    case unauthorizedClient = "unauthorized_client"

    /// The wallet does not support the requested response type.
    case unsupportedResponseType = "unsupported_response_type"

    /// The wallet cannot fulfill the request because of an unexpected error.
    case serverError = "server_error"

    /// The wallet cannot fulfill the request because it is temporarily unavailable.
    case temporarilyUnavailable = "temporarily_unavailable"

    /// The wallet does not support any requested verifiable-presentation format.
    case vpFormatsNotSupported = "vp_formats_not_supported"

    /// The wallet does not support the request's `request_uri_method`.
    case invalidRequestURIMethod = "invalid_request_uri_method"

    /// The request contains invalid or unsupported transaction data.
    case invalidTransactionData = "invalid_transaction_data"

    /// The requested wallet is unavailable.
    case walletUnavailable = "wallet_unavailable"

    /// OpenID4VP error code sent for this reason.
    public var errorCode: String { rawValue }
}
