import Foundation

/// Trust configuration used to authenticate verifier Request Objects.
public struct WalletClientIDTrustConfiguration: Sendable, Equatable {
    /// PEM-encoded X.509 trust anchors pinned by the hosting application.
    ///
    /// Required for OpenID4VP `x509_san_dns` and `x509_hash` clients; an empty list fails those
    /// prefixes closed. Append additional PEMs to trust more verifiers. `decentralized_identifier`
    /// clients still authenticate through DID resolution.
    public var x509TrustAnchorsPEM: [String]

    /// Explicit pre-registered client metadata JSON, keyed by client ID.
    public var preRegisteredClientMetadataJSON: [String: String]

    /// Creates client-ID trust configuration.
    ///
    /// - Parameters:
    ///   - x509TrustAnchorsPEM: PEM-encoded X.509 trust anchors pinned
    ///     by the hosting application. Supply the trust anchors for the verifier deployment.
    ///   - preRegisteredClientMetadataJSON: Client metadata JSON for explicit pre-registered verifiers.
    public init(
        x509TrustAnchorsPEM: [String] = [],
        preRegisteredClientMetadataJSON: [String: String] = [:]
    ) {
        self.x509TrustAnchorsPEM = x509TrustAnchorsPEM
        self.preRegisteredClientMetadataJSON = preRegisteredClientMetadataJSON
    }
}

/// OpenID4VP transaction data profile accepted by the wallet.
public struct WalletTransactionDataProfile: Equatable, Sendable {
    /// Collision-resistant OpenID4VP `transaction_data.type` value.
    public let type: String

    /// Human-readable label for consent UI.
    public let displayName: String

    /// Supported transaction type-specific fields.
    public let fields: [String]

    /// Creates a transaction data profile.
    ///
    /// - Parameters:
    ///   - type: Collision-resistant OpenID4VP `transaction_data.type` value.
    ///   - displayName: Human-readable label for consent UI. Defaults to `type`.
    ///   - fields: Supported transaction type-specific fields.
    public init(type: String, displayName: String? = nil, fields: [String] = []) {
        self.type = type
        self.displayName = displayName ?? type
        self.fields = fields
    }

}
