import Foundation

/// Credential metadata visible to Swift consumers.
public struct Credential: Equatable, Identifiable, Sendable {
    /// Stable local credential identifier.
    public let id: String

    /// Credential format, for example `vc+sd-jwt` or `jwt_vc_json`.
    public let format: String

    /// Issuer identifier or URL when available.
    public let issuer: String?

    /// Credential subject identifier when available.
    public let subject: String?

    /// User-facing credential label when available.
    public let label: String?

    /// Date the credential was added to the wallet when available.
    public let addedAt: Date?

    /// Parsed credential data encoded as JSON for app-side display.
    public let credentialDataJSON: String

    /// Optional sidecar metadata JSON persisted with the credential (e.g. `issuerDisplay`).
    public let metadataJSON: String?

    /// Local, dated status observation, independent of credential validity and acceptance.
    public let status: CredentialStatus?

    /// Creates credential metadata visible to SDK consumers.
    ///
    /// - Parameters:
    ///   - id: Stable local credential identifier.
    ///   - format: Credential format, for example `vc+sd-jwt` or
    ///     `jwt_vc_json`.
    ///   - credentialDataJSON: Parsed credential data encoded as JSON for
    ///     app-side display.
    ///   - issuer: Issuer identifier or URL when available.
    ///   - subject: Credential subject identifier when available.
    ///   - label: User-facing credential label when available.
    ///   - addedAt: Date the credential was added to the wallet when
    ///     available.
    ///   - metadataJSON: Optional sidecar metadata JSON persisted with the
    ///     credential.
    ///   - status: Optional local dated status observation, independent of credential validity.
    public init(
        id: String,
        format: String,
        credentialDataJSON: String,
        issuer: String? = nil,
        subject: String? = nil,
        label: String? = nil,
        addedAt: Date? = nil,
        metadataJSON: String? = nil,
        status: CredentialStatus? = nil
    ) {
        self.id = id
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.addedAt = addedAt
        self.credentialDataJSON = credentialDataJSON
        self.metadataJSON = metadataJSON
        self.status = status
    }
}
