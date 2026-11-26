import Foundation

/// Names one issuer-signed element without exposing a credential model.
public struct ProximityElementReference: Sendable, Hashable {
    /// Issuer namespace.
    public let namespace: String
    /// Element identifier within ``namespace``.
    public let elementIdentifier: String

    /// Creates an issuer-signed element reference.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - namespace: Issuer namespace.
    ///   - elementIdentifier: Element identifier within the namespace.
    public init(namespace: String, elementIdentifier: String) throws {
        try requireProximityInput(isProximityNonBlank(namespace) && isProximityNonBlank(elementIdentifier))
        self.namespace = namespace
        self.elementIdentifier = elementIdentifier
    }
}

/// One requested issuer-signed element and its disclosure constraints.
public struct ProximityRequestedElement: Sendable, Equatable {
    /// Issuer namespace.
    public let namespace: String
    /// Element identifier within ``namespace``.
    public let elementIdentifier: String
    /// Reader assertion that it intends to retain the value.
    public let intentToRetain: Bool
    /// Request elements satisfied by this projected element.
    public let satisfiesRequestedElements: [ProximityElementReference]
}

/// Original request context when the final request cannot be satisfied overall.
/// No credential values are included and reader authentication has not been evaluated.
public struct ProximityUnsatisfiedRequest: Sendable, Equatable {
    /// All requested documents in original order, including repeated document types.
    public let documents: [ProximityUnsatisfiedDocument]

    /// Creates a request summary without retaining credential or cryptographic data.
    /// - Parameter documents: Nonempty list of original requested documents.
    public init(documents: [ProximityUnsatisfiedDocument]) throws {
        try requireProximityInput(!documents.isEmpty)
        self.documents = documents
    }
}

/// One original requested document; its presence does not mean it individually had no match.
public struct ProximityUnsatisfiedDocument: Sendable, Equatable {
    /// Original zero-based document request index.
    public let requestIndex: Int
    /// Requested mdoc document type.
    public let documentType: String
    /// Original requested fields and reader-declared retention intent.
    public let requestedElements: [ProximityRequestedElement]

    /// Creates one document's request context.
    /// - Parameters:
    ///   - requestIndex: Original nonnegative document index.
    ///   - documentType: Nonblank document type, unchanged from the request.
    ///   - requestedElements: Nonempty list of original requested fields, without disclosure alternatives.
    public init(requestIndex: Int, documentType: String, requestedElements: [ProximityRequestedElement]) throws {
        try requireProximityInput(requestIndex >= 0 && isKotlinNonBlank(documentType))
        try requireProximityInput(!requestedElements.isEmpty && requestedElements.allSatisfy { $0.satisfiesRequestedElements.isEmpty })
        self.requestIndex = requestIndex
        self.documentType = documentType
        self.requestedElements = requestedElements
    }
}

/// One holder credential that can satisfy a document request.
public struct ProximityCredentialOption: Sendable, Equatable, Identifiable {
    /// Stable identity equal to ``credentialID``.
    public var id: String { credentialID }
    /// Stable wallet credential identifier.
    public let credentialID: String
    /// Optional display label.
    public let label: String?
    /// Optional display-safe issuer label or identifier.
    public let issuer: String?
    /// Credential validity end.
    public let validUntil: Date
    /// Holder authentication the response will use.
    public let deviceAuthentication: ProximityDeviceAuthenticationMethod
    /// Requested elements available from the credential.
    public let requestedElements: [ProximityRequestedElement]
}

/// Holder authentication selected for a reviewed document response.
public enum ProximityDeviceAuthenticationMethod: Sendable, Equatable {
    /// Authenticate the response with a device signature.
    case signature
    /// Authenticate the response with a session MAC.
    case mac
}

/// One requested document and its satisfying credential choices.
public struct ProximityDocumentReview: Sendable, Equatable, Identifiable {
    /// Stable identity equal to ``requestIndex``.
    public var id: Int { requestIndex }
    /// Zero-based document-request index.
    public let requestIndex: Int
    /// Requested ISO mdoc document type.
    public let documentType: String
    /// Credentials that can satisfy the request.
    public let credentialOptions: [ProximityCredentialOption]
    /// Profile-defined data required when sharing this document.
    public let requiredElements: Set<ProximityElementReference>

    init(requestIndex: Int, documentType: String, credentialOptions: [ProximityCredentialOption], requiredElements: Set<ProximityElementReference> = []) {
        self.requestIndex = requestIndex
        self.documentType = documentType
        self.credentialOptions = credentialOptions
        self.requiredElements = requiredElements
    }
}

/// Reader-asserted purpose hint preserved as untrusted request data.
public struct ProximityPurposeHint: Sendable, Equatable {
    /// Purpose-hint type.
    public let type: String
    /// Purpose-hint numeric code.
    public let code: Int
    /// Always indicates that the hint is a reader assertion, not a wallet fact.
    public let readerAsserted: Bool
}

/// ISO use-case metadata associated with one or more document requests.
public struct ProximityUseCase: Sendable, Equatable, Identifiable {
    /// Stable identity equal to ``index``.
    public var id: Int { index }
    /// Zero-based use-case index.
    public let index: Int
    /// Whether the reader marked the use case mandatory.
    public let mandatory: Bool
    /// Document requests associated with the use case.
    public let documentRequestIndices: [Int]
    /// Untrusted reader-asserted purpose hints.
    public let purposeHints: [ProximityPurposeHint]
}

/// Opaque identifier issued for exactly one review across session instances.
public struct ProximityReviewID: Sendable, Equatable, Hashable {
    internal let value: String
}

/// Frozen, display-safe review model for one exchange.
public struct ProximityReview: Sendable, Equatable {
    /// Identity required when approving or declining this review.
    public let reviewID: ProximityReviewID
    /// One-based exchange number.
    public let exchange: Int
    /// Requested documents and credential choices.
    public let documents: [ProximityDocumentReview]
    /// Reader-authentication results.
    public let readerAuthentication: [ProximityReaderAuthentication]
    /// Shared summary accounting for whole-request and document authentication coverage.
    public let readerAuthenticationSummary: ProximityReaderAuthenticationSummary
    /// Parsed ISO use-case metadata.
    public let useCases: [ProximityUseCase]
    /// Recognized application-profile contributions.
    public let applicationAuthorizations: [ProximityApplicationAuthorization]
}
