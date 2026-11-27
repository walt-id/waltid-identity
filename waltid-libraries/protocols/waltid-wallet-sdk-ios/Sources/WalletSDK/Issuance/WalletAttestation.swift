import Foundation

/// Enterprise Wallet Provider used for wallet instance and key attestations.
public struct WalletProviderConfiguration: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    /// Attestations to obtain from this deployment.
    public enum Composition: Equatable, Sendable {
        /// Wallet instance attestation for authorization-server authentication.
        case walletInstance
        /// Credential holder key attestation.
        case key
        /// Both wallet instance and key attestations.
        case walletInstanceAndKey
    }

    /// Log-safe summary of the selected attestations.
    public var description: String { "WalletProviderConfiguration(\(composition))" }

    /// Redacted debugger representation.
    public var debugDescription: String { description }

    /// Origin of the Enterprise API, without a service path.
    public var baseURL: String
    /// Wallet-provider target, `organization.tenant.service`.
    public var servicePath: String
    /// Bearer token for the wallet-provider API. Empty sends no Authorization header.
    public var bearerToken: String
    /// Optional `Host` header override for tunneled local tests.
    public var hostHeader: String
    /// `wia`, `ka`, or `wia_ka`. Must match a composition that provider is configured to mint.
    public var composition: Composition

    /// Creates wallet-provider settings.
    ///
    /// - Parameters:
    ///   - baseURL: Origin of the Enterprise API, without a service path.
    ///   - servicePath: Wallet-provider target, `organization.tenant.service`.
    ///   - bearerToken: Bearer token for the wallet-provider API. Empty sends no Authorization header.
    ///   - hostHeader: Optional `Host` header override for tunneled local tests.
    ///   - composition: `wia`, `ka`, or `wia_ka`. Defaults to both.
    public init(
        baseURL: String,
        servicePath: String,
        bearerToken: String = "",
        hostHeader: String = "",
        composition: Composition = .walletInstanceAndKey
    ) {
        self.baseURL = baseURL
        self.servicePath = servicePath
        self.bearerToken = bearerToken
        self.hostHeader = hostHeader
        self.composition = composition
    }
}

/// Wallet attestation settings.
public struct WalletAttestationConfiguration: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    /// Log-safe description of the configured service.
    public var description: String { "WalletAttestationConfiguration(<redacted>)" }

    /// Redacted debugger representation.
    public var debugDescription: String { description }

    /// Attestation API base URL.
    public var baseURL: String

    /// Relative attester endpoint path.
    public var attesterPath: String

    /// Optional bearer token for attestation requests.
    public var bearerToken: String

    /// Optional host header override for attestation requests.
    public var hostHeader: String

    /// Creates wallet attestation settings.
    ///
    /// - Parameters:
    ///   - baseURL: Base URL of the attestation service.
    ///   - attesterPath: Relative attester endpoint path.
    ///   - bearerToken: Optional bearer token used for attestation requests.
    ///   - hostHeader: Optional host header override for attestation requests.
    public init(
        baseURL: String,
        attesterPath: String,
        bearerToken: String = "",
        hostHeader: String = ""
    ) {
        self.baseURL = baseURL
        self.attesterPath = attesterPath
        self.bearerToken = bearerToken
        self.hostHeader = hostHeader
    }
}
