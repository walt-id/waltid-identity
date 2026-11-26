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

    /// Creates a presentation credential option.
    ///
    /// - Parameters:
    ///   - queryID: DCQL credential query identifier this option satisfies.
    ///   - credentialID: Wallet-local credential identifier.
    ///   - format: Credential format.
    ///   - credentialDataJSON: Parsed credential data encoded as JSON.
    ///   - multiple: Whether the request allows multiple credentials for this query.
    ///   - issuer: Issuer identifier when available.
    ///   - subject: Subject identifier when available.
    ///   - label: User-facing credential label when available.
    ///   - disclosures: Credential values requested from this credential.
    ///   - metadataJSON: Optional stored display metadata encoded as JSON.
    public init(
        queryID: String,
        credentialID: String,
        format: String,
        credentialDataJSON: String,
        multiple: Bool = false,
        issuer: String? = nil,
        subject: String? = nil,
        label: String? = nil,
        disclosures: [PresentationDisclosure] = [],
        metadataJSON: String? = nil
    ) {
        precondition(
            isPresentationIdentifierNonBlank(queryID),
            "A presentation credential option must contain a non-blank query ID."
        )
        self.queryID = queryID
        self.credentialID = credentialID
        self.multiple = multiple
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.credentialDataJSON = credentialDataJSON
        self.disclosures = disclosures
        self.metadataJSON = metadataJSON
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

    /// Whether this value comes from a selectively disclosable claim.
    public let selectivelyDisclosable: Bool

    /// Whether the presentation request requires this claim for the matched query.
    public let required: Bool

    /// Whether apps may let the user toggle this claim for submission.
    public let selectable: Bool

    /// False for additional clear-text data the credential must transmit even when not requested.
    public let requested: Bool

    /// Creates a presentation disclosure.
    ///
    /// - Parameters:
    ///   - path: Opaque claim path token supplied by the presentation engine.
    ///   - name: Claim name when known.
    ///   - valueJSON: Raw claim value encoded as JSON.
    ///   - displayValue: Human-readable value when trivially available.
    ///   - selectivelyDisclosable: Whether the credential format can selectively
    ///     disclose this claim.
    ///   - required: Whether the request requires this claim.
    ///   - selectable: Whether apps may let the user toggle this claim.
    ///   - requested: Whether the presentation request asks for this claim, rather than additional clear-text data.
    public init(
        path: String,
        name: String?,
        valueJSON: String,
        displayValue: String?,
        selectivelyDisclosable: Bool,
        required: Bool? = nil,
        selectable: Bool? = nil,
        requested: Bool = true
    ) {
        self.path = path
        self.name = name
        self.valueJSON = valueJSON
        self.displayValue = displayValue
        self.selectivelyDisclosable = selectivelyDisclosable
        let resolvedRequired = required ?? !selectivelyDisclosable
        let resolvedSelectable = selectable ?? (selectivelyDisclosable && !resolvedRequired)
        precondition(requested || (!selectivelyDisclosable && !resolvedRequired && !resolvedSelectable),
            "Additional shared data must be immutable and independent of request requirements.")
        self.requested = requested
        precondition(
            Self.hasValidSelectionState(
                selectivelyDisclosable: selectivelyDisclosable,
                required: resolvedRequired,
                selectable: resolvedSelectable
            ),
            "A selectable disclosure must be selectively disclosable and optional."
        )
        self.required = resolvedRequired
        self.selectable = resolvedSelectable
    }

    static func hasValidSelectionState(
        selectivelyDisclosable: Bool,
        required: Bool,
        selectable: Bool
    ) -> Bool {
        !selectable || (selectivelyDisclosable && !required)
    }
}

func isPresentationIdentifierNonBlank(_ value: String) -> Bool {
    !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}
