import Foundation

/// Result of resolving and validating an OpenID4VP request for presentation preview.
public enum PresentationPreviewResult: Equatable, Sendable {
    /// The request is valid and can be reviewed, submitted, or declined.
    case ready(PresentationPreview)

    /// The request cannot be fulfilled, but its protocol error can be returned after user interaction.
    case invalid(PresentationPreviewError)
}

/// Protocol error detected while previewing a presentation request.
public struct PresentationPreviewError: Equatable, Sendable {
    /// Opaque handle required to reject or discard this reviewed request.
    public let previewHandle: PresentationPreviewHandle

    /// Validated response destination and partial request context to show before returning the error.
    public let request: PartialPresentationRequest

    /// OpenID4VP or OAuth authorization error code selected by the wallet.
    public let code: PresentationErrorCode

    /// Local diagnostic intended for wallet UI; it is not sent to the verifier automatically.
    public let message: String

    /// Creates a presentation preview error.
    ///
    /// - Parameters:
    ///   - previewHandle: Opaque handle required to reject or discard this reviewed request.
    ///   - request: Validated response destination and request context shown before responding.
    ///   - code: OpenID4VP or OAuth authorization error code selected by the wallet.
    ///   - message: Local diagnostic that is not sent to the verifier automatically.
    public init(
        previewHandle: PresentationPreviewHandle,
        request: PartialPresentationRequest,
        code: PresentationErrorCode,
        message: String
    ) {
        self.previewHandle = previewHandle
        self.request = request
        self.code = code
        self.message = message
    }
}

/// Opaque handle for one reviewed OpenID4VP presentation request.
public struct PresentationPreviewHandle: Equatable, Sendable, CustomStringConvertible {
    /// Creates a handle for bridge adapters and test fixtures. Production handles come from preview operations.
    ///
    /// - Parameter value: Nonempty opaque handle value supplied by wallet core.
    public init(value: String) throws {
        guard isPresentationIdentifierNonBlank(value) else {
            throw WalletError.invalidInput("A presentation preview handle must contain a non-blank value.")
        }
        self.value = value
    }

    /// Redacted representation that does not expose the opaque handle value.
    public var description: String { "PresentationPreviewHandle(<redacted>)" }

    let value: String
}

/// Reviewed OpenID4VP request metadata bound to an opaque presentation handle.
public struct PresentationPreview: Equatable, Sendable {
    /// Opaque handle required to submit, reject, or discard this reviewed request.
    public let previewHandle: PresentationPreviewHandle

    /// Verifier/request information shown to the user.
    public let request: PresentationRequest

    /// Credentials that satisfy the request's DCQL queries.
    public let credentialOptions: [PresentationCredentialOption]

    /// Required DCQL credential query combinations that must be satisfied before submission.
    public let credentialRequirements: [PresentationCredentialRequirement]

    /// Creates a presentation preview.
    ///
    /// - Parameters:
    ///   - previewHandle: Opaque handle required to act on this reviewed request.
    ///   - request: Verifier and request metadata extracted from the
    ///     presentation request.
    ///   - credentialOptions: Wallet credentials that can satisfy the
    ///     requested credential queries.
    ///   - credentialRequirements: Required DCQL credential query combinations
    ///     that must be satisfied before submission.
    public init(
        previewHandle: PresentationPreviewHandle,
        request: PresentationRequest,
        credentialOptions: [PresentationCredentialOption],
        credentialRequirements: [PresentationCredentialRequirement] = []
    ) {
        self.previewHandle = previewHandle
        self.request = request
        self.credentialOptions = credentialOptions
        self.credentialRequirements = credentialRequirements
    }
}

/// A required presentation credential-query combination.
public struct PresentationCredentialRequirement: Equatable, Sendable {
    /// Alternative query-id combinations that can satisfy this requirement.
    ///
    /// All query IDs in one option must be selected together. At least one option
    /// must be satisfied for the requirement to be complete.
    public let options: [[String]]

    /// Creates a required presentation credential-query combination.
    ///
    /// - Parameter options: Alternative query-id combinations that can satisfy
    ///   this requirement.
    public init(options: [[String]]) throws {
        guard Self.hasValidOptions(options) else {
            throw WalletError.invalidInput("A presentation credential requirement must contain non-empty options with non-blank query IDs.")
        }
        self.options = options
    }

    static func hasValidOptions(_ options: [[String]]) -> Bool {
        !options.isEmpty && options.allSatisfy { option in
            !option.isEmpty && option.allSatisfy(isPresentationIdentifierNonBlank)
        }
    }
}
