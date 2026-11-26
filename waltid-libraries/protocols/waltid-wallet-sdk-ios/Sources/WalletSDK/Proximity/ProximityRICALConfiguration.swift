import Foundation

/// Exact RICAL signer evidence passed to an application-owned certificate-status source.
public struct ProximityRICALSignerEvidence: Sendable, Equatable {
    /// Stable identifier of the configured RICAL provider.
    public let providerID: String
    /// DER certificates in leaf-first order.
    public let certificateChainDER: [Data]

    /// Creates exact signer evidence for one configured RICAL provider.
    /// - Parameters:
    ///   - providerID: Stable identifier of the configured provider.
    ///   - certificateChainDER: Nonempty DER certificate chain in leaf-first order.
    public init(providerID: String, certificateChainDER: [Data]) {
        precondition(isProximityNonBlank(providerID))
        precondition(!certificateChainDER.isEmpty && certificateChainDER.allSatisfy { !$0.isEmpty })
        self.providerID = providerID
        self.certificateChainDER = certificateChainDER
    }
}

/// Application boundary for RICAL-signer OCSP, CRL, or another status source.
public protocol ProximityRICALSignerRevocationEvaluator: Sendable {
    /// Evaluates current revocation status for a verified RICAL signer chain.
    /// - Parameter evidence: Exact signer evidence for the selected provider.
    /// - Returns: The configured source's immutable certificate-status result.
    func evaluate(
        _ evidence: ProximityRICALSignerEvidence
    ) async throws -> ProximityCertificateRevocationResult
}

/// Explicit revocation behavior selected for one RICAL provider's signer certificate.
public enum ProximityRICALSignerRevocationPolicy: Sendable {
    /// Do not perform a signer-revocation lookup.
    case notChecked
    /// Require the supplied source to establish that the RICAL signer is not revoked.
    case check(any ProximityRICALSignerRevocationEvaluator)
}

/// Explicit application-provisioned root for one RICAL provider.
public struct ProximityRICALProviderTrustAnchor: Sendable, Equatable {
    /// DER-encoded trust anchor for this RICAL provider's signer.
    public let certificateDER: Data

    /// Creates an explicitly application-provisioned RICAL signer trust anchor.
    /// - Parameter certificateDER: DER-encoded trust-anchor certificate.
    public init(certificateDER: Data) {
        precondition(isProximityX509Certificate(certificateDER))
        self.certificateDER = certificateDER
    }
}

/// Immutable active-RICAL result from an application-owned provider boundary.
public struct ProximityRICALProviderResult: Sendable, Equatable {
    /// Supplies exact untagged COSE_Sign1 bytes.
    public static func available(signedRICAL: Data) -> Self {
        precondition(!signedRICAL.isEmpty)
        return Self(storage: .available(signedRICAL: signedRICAL))
    }

    /// No active list is available.
    public static func unavailable(reason: String) -> Self {
        precondition(isProximityNonBlank(reason))
        return Self(storage: .unavailable(reason: reason))
    }

    /// The application detected conflicting active-list state.
    public static func conflict(reason: String) -> Self {
        precondition(isProximityNonBlank(reason))
        return Self(storage: .conflict(reason: reason))
    }

    enum Storage: Sendable, Equatable {
        case available(signedRICAL: Data)
        case unavailable(reason: String)
        case conflict(reason: String)
    }

    let storage: Storage
}

/// Supplies the latest application-selected RICAL without implicit SDK networking.
public protocol ProximityRICALProvider: Sendable {
    /// Returns the application's current active-list result for this provider.
    /// - Returns: Exact signed RICAL bytes or a display-safe unavailable/conflict result.
    func current() async throws -> ProximityRICALProviderResult
}

/// One RICAL trust constraint. Values contain CBOR-encoded constraint data.
public struct ProximityRICALTrustConstraint: Sendable, Equatable {
    /// Constraint name to CBOR-encoded value.
    public let valuesCBOR: [String: Data]

    /// Creates one nonempty ecosystem-specific RICAL trust constraint.
    /// - Parameter valuesCBOR: Constraint names mapped to nonempty CBOR-encoded values.
    public init(valuesCBOR: [String: Data]) {
        precondition(!valuesCBOR.isEmpty)
        precondition(valuesCBOR.allSatisfy { isProximityNonBlank($0.key) && !$0.value.isEmpty })
        self.valuesCBOR = valuesCBOR
    }
}

/// Application evaluator for ecosystem-specific RICAL trust-constraint semantics.
public protocol ProximityRICALConstraintEvaluator: Sendable {
    /// Returns true only when at least one complete constraint is understood and satisfied.
    /// - Parameters:
    ///   - constraints: Complete constraints carried by the matched RICAL authority.
    ///   - reader: Verified reader evidence to which the constraints apply.
    /// - Returns: Whether an understood complete constraint is satisfied.
    func accepts(
        _ constraints: [ProximityRICALTrustConstraint],
        reader: ProximityReaderEvidence
    ) async throws -> Bool
}

/// Immutable policy for one explicitly configured RICAL provider.
public struct ProximityRICALConfiguration: Sendable {
    /// Stable application-configured provider identifier.
    public let providerID: String
    /// RICAL type identifiers accepted from this provider.
    public let acceptedTypes: Set<String>
    /// Explicit X.509 trust anchors accepted for this provider's signer.
    public let providerTrustAnchors: [ProximityRICALProviderTrustAnchor]
    /// Certificate-policy OIDs accepted on this provider's signer certificate.
    public let acceptedSignerCertificatePolicyOIDs: Set<String>
    /// Revocation behavior for this provider's verified signer certificate.
    public let signerRevocationPolicy: ProximityRICALSignerRevocationPolicy
    /// Whether an accepted matching authority may establish product reader trust.
    public let establishReaderTrust: Bool
    /// Application-owned source of the current signed RICAL.
    public let provider: any ProximityRICALProvider
    /// Nil rejects nonempty ecosystem-specific constraints as unsupported.
    public let constraintEvaluator: (any ProximityRICALConstraintEvaluator)?

    /// Creates immutable policy for one explicitly configured RICAL provider.
    /// - Parameters:
    ///   - providerID: Stable application-configured provider identifier.
    ///   - provider: Application-owned source of the current signed RICAL.
    ///   - acceptedTypes: Nonempty set of accepted RICAL type identifiers.
    ///   - providerTrustAnchors: Nonempty set of explicit signer trust anchors.
    ///   - acceptedSignerCertificatePolicyOIDs: Nonempty set of accepted signer policy OIDs.
    ///   - signerRevocationPolicy: Revocation behavior for the verified signer certificate.
    ///   - establishReaderTrust: Whether an accepted authority may establish product reader trust.
    ///   - constraintEvaluator: Optional evaluator for ecosystem-specific trust constraints.
    public init(
        providerID: String,
        provider: any ProximityRICALProvider,
        acceptedTypes: Set<String>,
        providerTrustAnchors: [ProximityRICALProviderTrustAnchor],
        acceptedSignerCertificatePolicyOIDs: Set<String>,
        signerRevocationPolicy: ProximityRICALSignerRevocationPolicy = .notChecked,
        establishReaderTrust: Bool = false,
        constraintEvaluator: (any ProximityRICALConstraintEvaluator)? = nil
    ) {
        precondition(isProximityNonBlank(providerID))
        precondition(!acceptedTypes.isEmpty && acceptedTypes.allSatisfy(isProximityNonBlank))
        precondition(
            !providerTrustAnchors.isEmpty &&
                Set(providerTrustAnchors.map(\.certificateDER)).count == providerTrustAnchors.count
        )
        precondition(
            !acceptedSignerCertificatePolicyOIDs.isEmpty &&
                acceptedSignerCertificatePolicyOIDs.allSatisfy(isProximityNonBlank)
        )
        self.providerID = providerID
        self.acceptedTypes = acceptedTypes
        self.providerTrustAnchors = providerTrustAnchors
        self.acceptedSignerCertificatePolicyOIDs = acceptedSignerCertificatePolicyOIDs
        self.signerRevocationPolicy = signerRevocationPolicy
        self.establishReaderTrust = establishReaderTrust
        self.provider = provider
        self.constraintEvaluator = constraintEvaluator
    }
}
