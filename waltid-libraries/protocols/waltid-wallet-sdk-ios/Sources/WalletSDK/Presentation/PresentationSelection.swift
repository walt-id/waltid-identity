import Foundation

/// The same credential and claim choices can be passed to review, payment preparation and submission.
public struct PresentationSelection: Equatable, Sendable {
    /// Credential options selected by the holder.
    public let credentials: [PresentationCredentialSelection]
    /// Optional claims to disclose. Nil uses request-matched defaults. An empty array selects no optional claims.
    public let disclosures: [PresentationDisclosureSelection]?

    /// Captures the holder's selection for a particular presentation preview.
    /// - Parameters:
    ///   - credentials: Selected credential options from this preview.
    ///   - disclosures: Selected claims, or nil for the request-matched defaults.
    public init(credentials: [PresentationCredentialSelection], disclosures: [PresentationDisclosureSelection]? = nil) {
        self.credentials = credentials
        self.disclosures = disclosures
    }
}

/// A wallet credential that satisfies one presentation credential query.
public struct PresentationCredentialOption: Equatable, Identifiable, Sendable {
    /// Stable UI identifier made from query and credential IDs.
    public var id: String { selection.id }

    /// Selection value to pass back when this option is shared.
    public var selection: PresentationCredentialSelection {
        PresentationCredentialSelection(queryID: queryID, credentialID: credentialID)
    }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Whether the DCQL credential query allows sharing multiple matching credentials.
    public let multiple: Bool

    /// Credential format.
    public let format: String

    /// Issuer identifier when available.
    public let issuer: String?

    /// Subject identifier when available.
    public let subject: String?

    /// User-facing label when available.
    public let label: String?

    /// Parsed credential data encoded as JSON.
    public let credentialDataJSON: String

    /// Requested credential values shown for informed consent.
    public let disclosures: [PresentationDisclosure]

    /// Optional stored display metadata encoded as JSON, including issuer and credential card art.
    public let metadataJSON: String?

    /// Creates the review option for one credential that matches the query.
    /// - Parameters:
    ///   - queryID: Non-blank identifier of the matched credential query.
    ///   - credential: Wallet credential with its display data and stored metadata.
    ///   - multiple: Whether the query accepts more than one credential.
    ///   - disclosures: Claims available to review for this match.
    public init(queryID: String, credential: Credential, multiple: Bool = false,
                disclosures: [PresentationDisclosure] = []) throws {
        guard isPresentationIdentifierNonBlank(queryID) else { throw WalletError.invalidInput("A credential query ID is required") }
        self.queryID = queryID
        self.credentialID = credential.id
        self.multiple = multiple
        self.format = credential.format
        self.issuer = credential.issuer
        self.subject = credential.subject
        self.label = credential.label
        self.credentialDataJSON = credential.credentialDataJSON
        self.disclosures = disclosures
        self.metadataJSON = credential.metadataJSON
    }
}

/// A selected presentation credential option.
public struct PresentationCredentialSelection: Equatable, Hashable, Identifiable, Sendable {
    /// Stable UI identifier made from query and credential IDs.
    public var id: String { "\(queryID.count):\(queryID)\(credentialID.count):\(credentialID)" }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Creates a selected presentation credential option.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier from the preview option.
    ///   - credentialID: Wallet-local credential identifier from the preview option.
    public init(queryID: String, credentialID: String) {
        self.queryID = queryID
        self.credentialID = credentialID
    }
}

/// A selected selectively disclosable presentation claim.
public struct PresentationDisclosureSelection: Equatable, Hashable, Identifiable, Sendable {
    /// Stable UI identifier made from query, credential, and disclosure path.
    public var id: String { "\(queryID.count):\(queryID)\(credentialID.count):\(credentialID)\(path.count):\(path)" }

    /// DCQL credential query identifier.
    public let queryID: String

    /// Wallet-local credential identifier.
    public let credentialID: String

    /// Opaque disclosure path token from ``PresentationDisclosure/path``.
    public let path: String

    /// Creates a selected presentation disclosure.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier from the preview option.
    ///   - credentialID: Wallet-local credential identifier from the preview option.
    ///   - path: Opaque disclosure path token from the preview disclosure.
    public init(queryID: String, credentialID: String, path: String) {
        self.queryID = queryID
        self.credentialID = credentialID
        self.path = path
    }
}

/// Inclusion of a claim in this particular presentation.
public enum DisclosureDisposition: Equatable, Sendable {
    /// Cleartext shared with the credential outside the requested claims.
    case additionalCleartext
    /// A required claim carried in cleartext.
    case requiredCleartext
    /// An optional requested claim carried in cleartext.
    case optionalCleartext
    /// A selectively disclosable claim included by the accepted request.
    case requiredSelective
    /// The format supports selective disclosure, but this optional claim has no independent toggle.
    case fixedSelective
    /// An optional claim the holder may independently toggle.
    case selectable

    /// Whether the credential format supports selective disclosure of this claim.
    public var selectivelyDisclosable: Bool { self == .requiredSelective || self == .fixedSelective || self == .selectable }
    /// Whether the accepted request requires the claim.
    public var required: Bool { self == .requiredCleartext || self == .requiredSelective }
    /// Whether the holder may toggle this claim for the current request.
    public var selectable: Bool { self == .selectable }
    /// Whether the verifier requested the claim.
    public var requested: Bool { self != .additionalCleartext }
}

/// Credential value that may be shared.
public struct PresentationDisclosure: Equatable, Identifiable, Sendable {
    /// Stable display identifier.
    public var id: String { path }

    /// Opaque claim path token supplied by the presentation engine.
    public let path: String

    /// Claim name when known.
    public let name: String?

    /// Raw claim value encoded as JSON.
    public let valueJSON: String

    /// Human-readable value when trivially available.
    public let displayValue: String?

    /// The inclusion state for this claim in the current presentation.
    public let disposition: DisclosureDisposition
    /// Whether the credential format supports selective disclosure of this claim.
    public var selectivelyDisclosable: Bool { disposition.selectivelyDisclosable }
    /// Whether the accepted request requires the claim.
    public var required: Bool { disposition.required }
    /// Whether the holder may toggle this claim for the current request.
    public var selectable: Bool { disposition.selectable }
    /// Whether the verifier requested the claim.
    public var requested: Bool { disposition.requested }

    /// Creates the display value and inclusion rules for a reviewed claim.
    /// - Parameters:
    ///   - path: Opaque claim path supplied by the wallet.
    ///   - valueJSON: Claim value encoded as JSON.
    ///   - disposition: Inclusion state for this matched request.
    ///   - name: Optional name suitable for display.
    ///   - displayValue: Optional human-readable representation.
    public init(
        path: String, valueJSON: String, disposition: DisclosureDisposition,
        name: String? = nil, displayValue: String? = nil
    ) {
        self.path = path
        self.name = name
        self.valueJSON = valueJSON
        self.displayValue = displayValue
        self.disposition = disposition
    }
}

func isPresentationIdentifierNonBlank(_ value: String) -> Bool {
    !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}
