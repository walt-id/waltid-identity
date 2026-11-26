import Foundation

/// Holder-approved credential and element selection for one document request.
public struct ProximityDocumentSubmission: Sendable, Equatable {
    /// Zero-based document-request index from the frozen review.
    public let requestIndex: Int
    /// Selected wallet credential identifier.
    public let credentialID: String
    /// Nonempty set of approved issuer-signed elements.
    public let disclosedElements: Set<ProximityElementReference>

    /// Creates a document submission tied to a frozen review.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - requestIndex: Document-request index.
    ///   - credentialID: Selected credential identifier.
    ///   - disclosedElements: Nonempty approved element set.
    public init(requestIndex: Int, credentialID: String, disclosedElements: Set<ProximityElementReference>) throws {
        try requireProximityInput((0...Int(Int32.max)).contains(requestIndex) && isProximityNonBlank(credentialID))
        try requireProximityInput(!disclosedElements.isEmpty)
        self.requestIndex = requestIndex
        self.credentialID = credentialID
        self.disclosedElements = disclosedElements
    }
}

/// Complete holder-approved submission for the current exchange.
public struct ProximitySubmission: Sendable, Equatable {
    /// Nonempty document submissions.
    public let documents: [ProximityDocumentSubmission]
    /// Whether to keep the transport alive for another device request.
    public let continueAfterResponse: Bool

    /// Creates a complete exchange submission.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - documents: Nonempty approved document submissions.
    ///   - continueAfterResponse: Whether another exchange may follow.
    public init(documents: [ProximityDocumentSubmission], continueAfterResponse: Bool = false) throws {
        try requireProximityInput(!documents.isEmpty)
        try requireProximityInput(Set(documents.map(\.requestIndex)).count == documents.count)
        self.documents = documents
        self.continueAfterResponse = continueAfterResponse
    }
}
