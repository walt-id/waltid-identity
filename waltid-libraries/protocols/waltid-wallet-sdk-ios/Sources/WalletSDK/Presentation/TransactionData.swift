import Foundation

/// Decoded transaction_data item from an OpenID4VP presentation request.
public struct PresentationTransactionData: Equatable, Sendable {
    /// Transaction data type.
    public let type: String

    /// Human-readable label from the accepted wallet profile.
    public let displayName: String

    /// Related DCQL credential query identifiers.
    public let credentialQueryIDs: [String]

    /// Profile-declared transaction-data fields the wallet accepts.
    public let supportedFields: [String]

    /// Decoded raw transaction data JSON.
    public let rawJSON: String

    /// Transaction type-specific details encoded as JSON.
    public let detailsJSON: String

    /// Creates transaction data for display.
    ///
    /// - Parameters:
    ///   - type: Transaction data type.
    ///   - displayName: Human-readable label from the accepted wallet profile.
    ///   - credentialQueryIDs: Related DCQL credential query identifiers.
    ///   - supportedFields: Profile-declared transaction-data fields.
    ///   - rawJSON: Decoded raw transaction data JSON.
    ///   - detailsJSON: Transaction type-specific details encoded as JSON.
    public init(
        type: String,
        displayName: String,
        credentialQueryIDs: [String],
        supportedFields: [String],
        rawJSON: String,
        detailsJSON: String
    ) {
        precondition(
            !credentialQueryIDs.isEmpty,
            "Transaction data must reference at least one credential query ID."
        )
        precondition(
            credentialQueryIDs.allSatisfy(isPresentationIdentifierNonBlank),
            "Transaction data must contain non-blank credential query IDs."
        )
        self.type = type
        self.displayName = displayName
        self.credentialQueryIDs = credentialQueryIDs
        self.supportedFields = supportedFields
        self.rawJSON = rawJSON
        self.detailsJSON = detailsJSON
    }
}
