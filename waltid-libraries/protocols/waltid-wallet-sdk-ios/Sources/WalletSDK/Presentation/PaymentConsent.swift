import Foundation

/// Independent issuer verification material, configured outside the credential and its metadata.
public struct WalletPaymentCredentialIssuer: Equatable, Sendable {
    /// Expected signed issuer identifier.
    public let issuer: String
    /// Public asymmetric JWK encoded as JSON.
    public let publicJWKJSON: String
    /// Allowed JWS algorithm identifier for this key.
    public let algorithm: String
    /// Creates a pinned issuer key. A key rollover can be configured with multiple entries.
    /// - Parameters:
    ///   - issuer: Expected issuer identifier in the signed credential.
    ///   - publicJWKJSON: Independently trusted public key encoded as JWK JSON.
    ///   - algorithm: Allowed JWS algorithm identifier for this key.
    public init(issuer: String, publicJWKJSON: String, algorithm: String = "ES256") {
        self.issuer = issuer; self.publicJWKJSON = publicJWKJSON; self.algorithm = algorithm
    }
}

/// One immutable, localized payment review prepared and validated by the shared wallet core.
public struct PaymentConsent: Equatable, Sendable {
    /// Opaque acknowledgment to pass only after displaying this review.
    public let revision: String
    /// Coherent language selected for all instructions.
    public let locale: String
    /// Issuer title; use wallet-owned wording when absent.
    public let title: String?
    /// Optional issuer security hint. It never replaces wallet-owned warnings.
    public let securityHint: String?
    /// Prescribed affirmative action.
    public let affirmativeAction: String
    /// Prescribed denial action; use wallet-owned wording when absent.
    public let denialAction: String?
    /// Show an unsigned-request warning alongside explicit confirmation.
    public let requiresUnsignedRequestWarning: Bool
    /// All validated fields, including fields intentionally omitted from display.
    public let fields: [PaymentConsentField]
}

/// Resolved plain-text payment value and issuer-provided display instructions.
public struct PaymentConsentField: Equatable, Sendable {
    /// Localized label, preserved without truncation.
    public let label: String
    /// Optional localized description.
    public let description: String?
    /// Exact decimal or string value; never rounded through floating point.
    public let value: String
    /// Placement resolved in shared code.
    public let placement: PaymentConsentFieldPlacement
    /// Payload-relative semantic path. Interpret known field types by path, never by localized labels.
    public let path: [String]

    init(label: String, description: String?, value: String, placement: PaymentConsentFieldPlacement, path: [String] = []) {
        self.label = label
        self.description = description
        self.value = value
        self.placement = placement
        self.path = path
    }
}

/// Display placement, already resolved from attestation metadata by shared code.
public enum PaymentConsentFieldPlacement: Sendable {
    /// Prominent on the main screen.
    case prominent
    /// Visible on the main screen.
    case main
    /// Available in accessible additional details.
    case details
    /// Not displayed, but still included in the signed transaction bytes.
    case omitted
}
