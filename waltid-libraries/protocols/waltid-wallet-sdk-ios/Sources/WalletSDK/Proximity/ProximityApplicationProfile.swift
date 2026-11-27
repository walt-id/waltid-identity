import Foundation

/// Minimal candidate-credential facts supplied to an application profile.
public struct ProximityApplicationCredential: Sendable, Equatable {
    /// Stable wallet credential identifier.
    public let credentialID: String
    /// ISO mdoc document type.
    public let documentType: String
    /// Optional display label.
    public let label: String?
}

/// Complete dependency-free request context supplied to an application profile.
public struct ProximityApplicationProfileInput: Sendable, Equatable {
    /// Exact encoded DeviceRequest bytes.
    public let deviceRequest: Data
    /// Candidate credentials available for the request.
    public let credentials: [ProximityApplicationCredential]
    /// Parsed document requests and element facts.
    public let requestedDocuments: [ProximityApplicationDocumentRequest]
    /// Verified reader-authentication facts, including absent scopes.
    public let readerAuthentication: [ProximityReaderAuthentication]
}

/// Dependency-free parsed request facts supplied to an application profile.
public struct ProximityApplicationDocumentRequest: Sendable, Equatable, Identifiable {
    /// Stable identity equal to ``requestIndex``.
    public var id: Int { requestIndex }
    /// Zero-based document-request index.
    public let requestIndex: Int
    /// Requested ISO mdoc document type.
    public let documentType: String
    /// Requested issuer-signed elements.
    public let requestedElements: [ProximityRequestedElement]
}

/// Display-safe application-profile detail shown during holder review.
public struct ProximityApplicationAuthorizationDetail: Sendable, Equatable, Identifiable {
    /// Stable profile-defined detail identifier.
    public let id: String
    /// Display-safe detail label.
    public let label: String
    /// Display-safe detail value.
    public let value: String

    /// Creates one display-safe authorization detail.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - id: Stable profile-defined detail identifier.
    ///   - label: Display-safe detail label.
    ///   - value: Display-safe detail value.
    public init(id: String, label: String, value: String) throws {
        try requireProximityInput(isProximityNonBlank(id) && isProximityNonBlank(label) && isProximityNonBlank(value))
        self.id = id
        self.label = label
        self.value = value
    }
}

/// Application-profile element that is bound through device authentication.
public struct ProximityDeviceSignedElement: Sendable, Equatable {
    /// Credential whose device key authenticates the element.
    public let credentialID: String
    /// Device namespace containing the element.
    public let namespace: String
    /// Element identifier within ``namespace``.
    public let elementIdentifier: String
    /// Exact encoded CBOR value.
    public let valueCBOR: Data

    /// Creates an exact device-signed element.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - credentialID: Credential whose device key authenticates the value.
    ///   - namespace: Device namespace.
    ///   - elementIdentifier: Element identifier.
    ///   - valueCBOR: Exact encoded CBOR value.
    public init(credentialID: String, namespace: String, elementIdentifier: String, valueCBOR: Data) throws {
        try requireProximityInput(isProximityNonBlank(credentialID))
        try requireProximityInput(
            isProximityNonBlank(namespace) && isProximityNonBlank(elementIdentifier) && !valueCBOR.isEmpty
        )
        self.credentialID = credentialID
        self.namespace = namespace
        self.elementIdentifier = elementIdentifier
        self.valueCBOR = valueCBOR
    }
}

/// Recognized application-profile contribution to holder review and response binding.
public struct ProximityApplicationAuthorization: Sendable, Equatable {
    /// Stable identifier of the recognizing profile.
    public let profileID: String
    /// Display-safe title for holder review.
    public let displayTitle: String
    /// Display-safe profile details.
    public let details: [ProximityApplicationAuthorizationDetail]
    /// Credential identifiers compatible with the recognized profile.
    public let compatibleCredentialIDs: Set<String>
    /// Profile-defined elements authenticated by the selected holder key.
    public let deviceSignedElements: [ProximityDeviceSignedElement]
    /// Profile-owned SHA-256 binding contribution.
    public let resultBindingDigest: Data

    /// Creates a recognized application-profile authorization.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - profileID: Stable profile identifier.
    ///   - displayTitle: Display-safe review title.
    ///   - details: Display-safe review details.
    ///   - compatibleCredentialIDs: Credentials permitted by the profile.
    ///   - resultBindingDigest: Profile-owned 32-byte SHA-256 binding contribution.
    ///   - deviceSignedElements: Profile-defined device-signed elements.
    public init(
        profileID: String,
        displayTitle: String,
        details: [ProximityApplicationAuthorizationDetail],
        compatibleCredentialIDs: Set<String>,
        resultBindingDigest: Data,
        deviceSignedElements: [ProximityDeviceSignedElement] = []
    ) throws {
        try requireProximityInput(isProximityNonBlank(profileID) && isProximityNonBlank(displayTitle))
        try requireProximityInput(!details.isEmpty && Set(details.map(\.id)).count == details.count)
        try requireProximityInput(
            !compatibleCredentialIDs.isEmpty && compatibleCredentialIDs.allSatisfy(isProximityNonBlank)
        )
        try requireProximityInput(deviceSignedElements.allSatisfy { compatibleCredentialIDs.contains($0.credentialID) })
        try requireProximityInput(
            Set(deviceSignedElements.map {
                "\($0.credentialID)\u{0}\($0.namespace)\u{0}\($0.elementIdentifier)"
            }).count == deviceSignedElements.count
        )
        try requireProximityInput(resultBindingDigest.count == 32, "Application-profile binding must be SHA-256")
        self.profileID = profileID
        self.displayTitle = displayTitle
        self.details = details
        self.compatibleCredentialIDs = compatibleCredentialIDs
        self.deviceSignedElements = deviceSignedElements
        self.resultBindingDigest = resultBindingDigest
    }
}

/// Outcome of application-profile recognition and validation.
public enum ProximityApplicationProfileResult: Sendable, Equatable {
    /// The profile does not recognize the request.
    case notRecognized
    /// The profile recognizes and authorizes the request facts.
    case recognized(ProximityApplicationAuthorization)
    /// The profile recognizes but rejects the request with a display-safe reason.
    case rejected(reason: String)
}

/// Versioned Swift-owned interpreter for application-specific request semantics.
public protocol ProximityApplicationProfile: Sendable {
    /// Stable versioned profile identifier.
    var id: String { get }
    /// Interprets request facts without an implicit SDK lookup.
    /// - Parameter input: Exact request, credential, and reader-authentication facts.
    /// - Returns: Recognition and authorization result.
    func evaluate(_ input: ProximityApplicationProfileInput) async throws -> ProximityApplicationProfileResult
}
