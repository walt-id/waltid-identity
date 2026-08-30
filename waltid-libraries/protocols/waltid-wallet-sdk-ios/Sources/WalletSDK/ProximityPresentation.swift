import Foundation
import Security

/// Versioned mdoc interoperability boundary selected for one proximity session.
public enum ProximityPresentationProfile: String, Sendable, CaseIterable, Equatable {
    /// ISO/IEC 18013-5:2021 behavior.
    case iso1801352021
    /// ISO/IEC 18013-5 second-edition DIS behavior.
    case iso180135Edition2DIS2026
    /// EUDI ARF 3.0 / Common Acceptance Framework profile behavior.
    case eudiARF3FCAF202608
}

/// BLE roles the holder prepares for one session.
public enum ProximityPresentationBLERoles: Sendable, Hashable {
    /// Connect to a reader that advertises the GATT service.
    case centralClient
    /// Advertise a GATT service for a reader to connect to.
    case peripheralServer
    /// Prepare both supported BLE roles and advertise both retrieval options.
    case dual
}

/// BLE bearer selection policy. This is intended for integration and debug configuration, not normal UI.
public enum ProximityPresentationBLEBearerPolicy: Sendable, Hashable {
    /// Use the interoperable GATT bearer only.
    case gattOnly
    /// Prefer L2CAP when negotiated and otherwise use GATT.
    case preferL2CAP
}

/// Complete BLE bearer configuration; it exists only when BLE retrieval is selected.
public struct ProximityPresentationBLEConfiguration: Sendable, Hashable {
    /// BLE roles prepared for the session.
    public let roles: ProximityPresentationBLERoles
    /// BLE bearer-selection policy.
    public let bearerPolicy: ProximityPresentationBLEBearerPolicy

    /// Creates a complete BLE retrieval configuration.
    public init(
        roles: ProximityPresentationBLERoles = .dual,
        bearerPolicy: ProximityPresentationBLEBearerPolicy = .preferL2CAP
    ) {
        self.roles = roles
        self.bearerPolicy = bearerPolicy
    }
}

/// Complete conventional NFC retrieval length contract.
public struct ProximityPresentationNFCRetrievalConfiguration: Sendable, Hashable {
    /// Maximum command-data length accepted from the reader.
    public let maximumCommandDataLength: Int
    /// Maximum response-data length returned to the reader.
    public let maximumResponseDataLength: Int

    /// Creates a validated conventional NFC retrieval configuration.
    public init(
        maximumCommandDataLength: Int = 65_535,
        maximumResponseDataLength: Int = 65_536
    ) {
        precondition((255...65_535).contains(maximumCommandDataLength))
        precondition((256...65_536).contains(maximumResponseDataLength))
        self.maximumCommandDataLength = maximumCommandDataLength
        self.maximumResponseDataLength = maximumResponseDataLength
    }
}

/// NFC Engagement v2 APDU contract kept separate from conventional NFC retrieval lengths.
public struct ProximityPresentationNFCV2Configuration: Sendable, Hashable {
    /// Maximum command-data length accepted by the provisional NFCv2 holder application.
    public let maximumCommandDataLength: Int

    /// Creates a validated provisional NFCv2 configuration.
    public init(maximumCommandDataLength: Int = 65_536) {
        precondition((1...65_536).contains(maximumCommandDataLength))
        self.maximumCommandDataLength = maximumCommandDataLength
    }
}

/// One or both conventional retrieval methods used by QR, Static Handover, or Negotiated Handover.
public struct ProximityPresentationConventionalRetrievalConfiguration: Sendable, Hashable {
    /// Optional BLE role and bearer policy.
    public let bluetoothLowEnergy: ProximityPresentationBLEConfiguration?
    /// Optional conventional NFC command/response contract.
    public let nfc: ProximityPresentationNFCRetrievalConfiguration?

    /// Creates a nonempty conventional retrieval configuration.
    public init(
        bluetoothLowEnergy: ProximityPresentationBLEConfiguration? = .init(),
        nfc: ProximityPresentationNFCRetrievalConfiguration? = nil
    ) {
        precondition(bluetoothLowEnergy != nil || nfc != nil)
        self.bluetoothLowEnergy = bluetoothLowEnergy
        self.nfc = nfc
    }
}

/// Provisional NFCv2 same-channel retrieval plus compatible optional paths.
public struct ProximityPresentationNFCV2RetrievalConfiguration: Sendable, Hashable {
    /// Optional NFCv2 alternate BLE bearer and QR BLE bearer.
    public let bluetoothLowEnergy: ProximityPresentationBLEConfiguration?
    /// Optional conventional NFC retrieval for a concurrently prepared QR path.
    public let qrNFC: ProximityPresentationNFCRetrievalConfiguration?

    /// Creates an NFCv2 retrieval configuration. The same NFCv2 APDU channel is always selected.
    public init(
        bluetoothLowEnergy: ProximityPresentationBLEConfiguration? = nil,
        qrNFC: ProximityPresentationNFCRetrievalConfiguration? = nil
    ) {
        self.bluetoothLowEnergy = bluetoothLowEnergy
        self.qrNFC = qrNFC
    }
}

/// Retrieval configuration whose variant is tied to the selected NFC engagement family.
public enum ProximityPresentationRetrievalConfiguration: Sendable, Hashable {
    /// QR or conventional NFC engagement with one or both conventional retrieval methods.
    case conventional(ProximityPresentationConventionalRetrievalConfiguration = .init())
    /// NFCv2 same-channel retrieval plus optional hybrid/QR-compatible methods.
    case provisionalNFCV2(ProximityPresentationNFCV2RetrievalConfiguration = .init())
}

/// NFC engagement wire profile selected exactly once for a session.
public enum ProximityPresentationNFCEngagementMode: Sendable, Hashable {
    /// NFC Forum Static Handover.
    case staticHandover
    /// NFC Forum Negotiated Handover.
    case negotiatedHandover
    /// Provisional NFC Engagement v2 contract pending authoritative source reconciliation.
    case provisionalV2(ProximityPresentationNFCV2Configuration = .init())
}

/// Engagement configuration in which QR/NFC combinations and NFC tuning cannot drift apart.
public enum ProximityPresentationEngagementConfiguration: Sendable, Hashable {
    /// Display an ISO device-engagement QR code only.
    case qrOnly
    /// Use NFC engagement only.
    case nfcOnly(ProximityPresentationNFCEngagementMode)
    /// Race QR and NFC engagement for the same single-use session.
    case qrAndNFC(ProximityPresentationNFCEngagementMode)
}

private extension ProximityPresentationEngagementConfiguration {
    var includesQR: Bool {
        switch self {
        case .qrOnly, .qrAndNFC:
            return true
        case .nfcOnly:
            return false
        }
    }

    var usesProvisionalNFCV2: Bool {
        switch self {
        case .qrOnly:
            return false
        case let .nfcOnly(mode), let .qrAndNFC(mode):
            if case .provisionalV2 = mode { return true }
            return false
        }
    }
}

private extension ProximityPresentationRetrievalConfiguration {
    var provisionalNFCV2: ProximityPresentationNFCV2RetrievalConfiguration? {
        guard case let .provisionalNFCV2(configuration) = self else { return nil }
        return configuration
    }
}

/// Reader-authentication policy applied before disclosure review.
public enum ProximityPresentationReaderPolicy: Sendable, Equatable {
    /// Allow absent or untrusted reader authentication and expose its exact state for review.
    case allowAnonymousOrUntrusted
    /// Require a reader that the application trust policy accepts.
    case requireTrusted
}

/// Allowed holder-authentication methods and their pre-review preference.
public enum ProximityDeviceAuthenticationPolicy: Sendable, Equatable {
    /// Require device signature.
    case signatureOnly
    /// Require device MAC.
    case macOnly
    /// Prefer signature and fall back to MAC only before review.
    case preferSignature
    /// Prefer MAC and fall back to signature only before review.
    case preferMAC
}

/// Portion of the device request covered by reader authentication.
public enum ProximityReaderAuthenticationScope: Sendable, Equatable {
    /// Authentication covers one document request.
    case document
    /// Authentication covers the whole device request.
    case wholeRequest
}

/// Structural and cryptographic validity of reader authentication.
public enum ProximityReaderAuthenticationValidity: Sendable, Equatable {
    /// No reader authentication was supplied.
    case absent
    /// Reader authentication could not be decoded.
    case malformed
    /// Reader authentication was decoded but did not verify.
    case invalid
    /// Reader authentication verified cryptographically.
    case valid
}

/// Application-owned trust result for a cryptographically valid reader.
public enum ProximityReaderTrustState: Sendable, Equatable {
    /// No application trust evaluator ran.
    case notEvaluated
    /// The reader authentication is valid but the application does not trust it.
    case validButUntrusted
    /// The reader certificate was revoked.
    case revoked
    /// The application accepts the reader as trusted.
    case trusted
}

/// Certificate-path validation result kept separate from the trust decision.
public enum ProximityReaderCertificatePathState: Sendable, Equatable {
    /// Certificate-path validation was not performed.
    case notEvaluated
    /// The reader path is structurally valid, but no configured authority matches it.
    case unknownAuthority
    /// The certificate path is invalid.
    case invalid
    /// The certificate path is valid.
    case valid
}

/// Reader-certificate revocation result kept separate from the trust decision.
public enum ProximityReaderRevocationState: Sendable, Equatable {
    /// Revocation was not checked.
    case notChecked
    /// The certificate is known not to be revoked.
    case good
    /// The certificate is revoked.
    case revoked
    /// Revocation status could not be established.
    case indeterminate
}

/// Optional RICAL evidence. A match is evidence and never establishes product trust by itself.
public enum ProximityRICALState: Sendable, Equatable {
    /// RICAL evaluation was not performed.
    case notEvaluated
    /// No RICAL source was available.
    case unavailable
    /// The available RICAL data was invalid.
    case invalid
    /// No authority matched the reader certificate.
    case noMatchingAuthority
    /// A RICAL authority matched the reader certificate.
    case matched
}

/// Exact verified reader evidence passed to an application-owned trust policy.
public struct ProximityReaderEvidence: Sendable, Equatable {
    /// Portion of the request authenticated by this evidence.
    public let scope: ProximityReaderAuthenticationScope
    /// Zero-based document-request index when ``scope`` is ``ProximityReaderAuthenticationScope/document``.
    public let documentRequestIndex: Int?
    /// Zero-based statement index within the authentication scope.
    public let authenticationIndex: Int
    /// DER certificates in leaf-first order.
    public let certificateChainDER: [Data]

    /// Creates verified reader evidence for application trust evaluation.
    /// - Parameters:
    ///   - scope: Portion of the request covered by the authentication.
    ///   - documentRequestIndex: Document index for document-scoped authentication.
    ///   - authenticationIndex: Statement index within the authentication scope.
    ///   - certificateChainDER: Nonempty DER certificate chain in leaf-first order.
    public init(
        scope: ProximityReaderAuthenticationScope,
        documentRequestIndex: Int? = nil,
        authenticationIndex: Int = 0,
        certificateChainDER: [Data]
    ) {
        precondition(
            !certificateChainDER.isEmpty && certificateChainDER.allSatisfy { !$0.isEmpty },
            "Verified reader evidence requires nonempty certificates"
        )
        precondition(
            (scope == .document) == (documentRequestIndex != nil),
            "Only document-scoped reader evidence carries a document index"
        )
        precondition(documentRequestIndex == nil || documentRequestIndex! >= 0)
        precondition(authenticationIndex >= 0)
        self.scope = scope
        self.documentRequestIndex = documentRequestIndex
        self.authenticationIndex = authenticationIndex
        self.certificateChainDER = certificateChainDER
    }
}

/// Application trust decision with path, revocation, and RICAL facts kept separate.
public struct ProximityReaderTrustDecision: Sendable, Equatable {
    /// Final application trust state.
    public let state: ProximityReaderTrustState
    /// Independently reported certificate-path result.
    public let certificatePath: ProximityReaderCertificatePathState
    /// Independently reported revocation result.
    public let revocation: ProximityReaderRevocationState
    /// Independently reported RICAL evidence result.
    public let rical: ProximityRICALState
    /// Display-safe reader name supplied by the application.
    public let displayName: String?
    /// Display-safe explanation supplied by the application.
    public let reason: String?

    /// Creates a coherent application-owned reader-trust decision.
    /// - Parameters:
    ///   - state: Final trust state.
    ///   - certificatePath: Certificate-path result.
    ///   - revocation: Revocation result.
    ///   - rical: RICAL evidence result.
    ///   - displayName: Optional display-safe reader name.
    ///   - reason: Optional display-safe explanation.
    public init(
        state: ProximityReaderTrustState,
        certificatePath: ProximityReaderCertificatePathState = .notEvaluated,
        revocation: ProximityReaderRevocationState = .notChecked,
        rical: ProximityRICALState = .notEvaluated,
        displayName: String? = nil,
        reason: String? = nil
    ) {
        precondition(state != .notEvaluated, "A trust evaluator must return an evaluated state")
        precondition(state != .revoked || revocation == .revoked)
        precondition(revocation != .revoked || state == .revoked)
        precondition(state != .trusted || certificatePath == .valid)
        precondition(state != .revoked || certificatePath == .valid)
        precondition(certificatePath != .unknownAuthority || state == .validButUntrusted)
        precondition(certificatePath != .invalid || state == .validButUntrusted)
        precondition(certificatePath != .invalid || revocation == .notChecked)
        precondition(state != .trusted || revocation != .indeterminate)
        precondition(rical != .matched || certificatePath == .valid)
        precondition(displayName == nil || isProximityNonBlank(displayName!))
        precondition(reason == nil || isProximityNonBlank(reason!))
        self.state = state
        self.certificatePath = certificatePath
        self.revocation = revocation
        self.rical = rical
        self.displayName = displayName
        self.reason = reason
    }
}

/// Explicit Swift-owned trust boundary. The SDK performs no network lookup implicitly.
public protocol ProximityReaderTrustEvaluator: Sendable {
    /// Evaluates exact verified reader evidence without an implicit SDK lookup.
    /// - Parameter evidence: Verified reader evidence for one authentication scope.
    /// - Returns: The application's coherent trust decision.
    func evaluate(_ evidence: ProximityReaderEvidence) async throws -> ProximityReaderTrustDecision
}

/// Explicit application-provisioned Reader CA certificate and optional display label.
public struct ProximityReaderTrustAnchor: Sendable, Equatable {
    /// DER-encoded Reader CA certificate.
    public let certificateDER: Data
    /// Display-safe authority label that does not establish trust by itself.
    public let displayName: String?

    /// Creates an explicitly application-provisioned Reader CA trust anchor.
    /// - Parameters:
    ///   - certificateDER: DER-encoded Reader CA certificate.
    ///   - displayName: Optional display-safe authority label.
    public init(certificateDER: Data, displayName: String? = nil) {
        precondition(isProximityX509Certificate(certificateDER))
        precondition(displayName == nil || isProximityNonBlank(displayName!))
        self.certificateDER = certificateDER
        self.displayName = displayName
    }
}

/// Immutable result from an application-owned certificate-status source.
public struct ProximityCertificateRevocationResult: Sendable, Equatable {
    enum Storage: Sendable, Equatable {
        case good
        case revoked(reason: String?)
        case indeterminate(reason: String)
    }

    let storage: Storage

    /// The configured source established that the certificate is not revoked.
    public static let good = Self(storage: .good)

    /// The configured source established that the certificate is revoked.
    public static func revoked(reason: String? = nil) -> Self {
        precondition(reason == nil || isProximityNonBlank(reason!))
        return Self(storage: .revoked(reason: reason))
    }

    /// The configured source could not establish current revocation status.
    public static func indeterminate(reason: String) -> Self {
        precondition(isProximityNonBlank(reason))
        return Self(storage: .indeterminate(reason: reason))
    }
}

/// Application boundary for OCSP, CRL, or another reader-certificate status source.
public protocol ProximityReaderRevocationEvaluator: Sendable {
    /// Evaluates current revocation status for a verified reader chain.
    /// - Parameter evidence: Verified reader evidence for one authentication scope.
    /// - Returns: The configured source's immutable certificate-status result.
    func evaluate(_ evidence: ProximityReaderEvidence) async throws -> ProximityCertificateRevocationResult
}

/// Explicit revocation behavior selected for reader trust.
public enum ProximityReaderRevocationPolicy: Sendable {
    /// Do not perform revocation lookup; the resulting fact remains ``ProximityReaderRevocationState/notChecked``.
    case notChecked
    /// Require the supplied source to return a conclusive result before the reader can be trusted.
    case check(any ProximityReaderRevocationEvaluator)
}

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
    enum Storage: Sendable, Equatable {
        case available(signedRICAL: Data)
        case unavailable(reason: String)
        case conflict(reason: String)
    }

    let storage: Storage

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
    ///   - acceptedTypes: Nonempty set of accepted RICAL type identifiers.
    ///   - providerTrustAnchors: Nonempty set of explicit signer trust anchors.
    ///   - acceptedSignerCertificatePolicyOIDs: Nonempty set of accepted signer policy OIDs.
    ///   - signerRevocationPolicy: Revocation behavior for the verified signer certificate.
    ///   - establishReaderTrust: Whether an accepted authority may establish product reader trust.
    ///   - provider: Application-owned source of the current signed RICAL.
    ///   - constraintEvaluator: Optional evaluator for ecosystem-specific trust constraints.
    public init(
        providerID: String,
        acceptedTypes: Set<String>,
        providerTrustAnchors: [ProximityRICALProviderTrustAnchor],
        acceptedSignerCertificatePolicyOIDs: Set<String>,
        signerRevocationPolicy: ProximityRICALSignerRevocationPolicy = .notChecked,
        establishReaderTrust: Bool = false,
        provider: any ProximityRICALProvider,
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

/// Swift-native immutable configuration for the shared standards reader-trust evaluator.
public struct ProximityReaderTrustConfiguration: Sendable {
    /// Explicit application-provisioned Reader CA trust anchors.
    public let trustAnchors: [ProximityReaderTrustAnchor]
    /// Ordered application-configured RICAL provider policies.
    public let ricalProviders: [ProximityRICALConfiguration]
    /// Revocation behavior for reader chains trusted by direct Reader CA anchors.
    public let revocationPolicy: ProximityReaderRevocationPolicy

    /// Creates immutable application-owned reader-trust configuration.
    /// - Parameters:
    ///   - trustAnchors: Explicit Reader CA trust anchors.
    ///   - ricalProviders: Ordered RICAL provider policies.
    ///   - revocationPolicy: Revocation behavior for directly anchored reader chains.
    public init(
        trustAnchors: [ProximityReaderTrustAnchor] = [],
        ricalProviders: [ProximityRICALConfiguration] = [],
        revocationPolicy: ProximityReaderRevocationPolicy = .notChecked
    ) {
        precondition(!trustAnchors.isEmpty || !ricalProviders.isEmpty)
        precondition(Set(trustAnchors.map(\.certificateDER)).count == trustAnchors.count)
        precondition(Set(ricalProviders.map(\.providerID)).count == ricalProviders.count)
        self.trustAnchors = trustAnchors
        self.ricalProviders = ricalProviders
        self.revocationPolicy = revocationPolicy
    }
}

/// Application-owned status result for a candidate holder credential.
public enum ProximityCredentialStatus: Sendable {
    /// The credential is valid for disclosure.
    case valid
    /// The credential is revoked and must not be disclosed.
    case revoked
    /// The application could not establish credential status.
    case indeterminate
}

/// Credential facts supplied to an application-owned status evaluator.
public struct ProximityCredentialStatusInput: Sendable, Equatable {
    /// Stable wallet credential identifier.
    public let credentialID: String
    /// ISO mdoc document type.
    public let documentType: String
    /// Optional issuer identifier retained by the wallet.
    public let issuer: String?
    /// Credential validity start.
    public let validFrom: Date
    /// Credential validity end.
    public let validUntil: Date
}

/// Explicit status boundary. The SDK itself performs no hidden network lookup.
public protocol ProximityCredentialStatusEvaluator: Sendable {
    /// Evaluates credential status without an implicit SDK lookup.
    /// - Parameter credential: Candidate credential facts.
    /// - Returns: The application's status decision.
    func evaluate(_ credential: ProximityCredentialStatusInput) async throws -> ProximityCredentialStatus
}

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
    /// - Parameters:
    ///   - id: Stable profile-defined detail identifier.
    ///   - label: Display-safe detail label.
    ///   - value: Display-safe detail value.
    public init(id: String, label: String, value: String) {
        precondition(isProximityNonBlank(id) && isProximityNonBlank(label) && isProximityNonBlank(value))
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
    /// - Parameters:
    ///   - credentialID: Credential whose device key authenticates the value.
    ///   - namespace: Device namespace.
    ///   - elementIdentifier: Element identifier.
    ///   - valueCBOR: Exact encoded CBOR value.
    public init(credentialID: String, namespace: String, elementIdentifier: String, valueCBOR: Data) {
        precondition(isProximityNonBlank(credentialID))
        precondition(
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
    /// - Parameters:
    ///   - profileID: Stable profile identifier.
    ///   - displayTitle: Display-safe review title.
    ///   - details: Display-safe review details.
    ///   - compatibleCredentialIDs: Credentials permitted by the profile.
    ///   - deviceSignedElements: Profile-defined device-signed elements.
    ///   - resultBindingDigest: Profile-owned 32-byte SHA-256 binding contribution.
    public init(
        profileID: String,
        displayTitle: String,
        details: [ProximityApplicationAuthorizationDetail],
        compatibleCredentialIDs: Set<String>,
        deviceSignedElements: [ProximityDeviceSignedElement] = [],
        resultBindingDigest: Data
    ) {
        precondition(isProximityNonBlank(profileID) && isProximityNonBlank(displayTitle))
        precondition(!details.isEmpty && Set(details.map(\.id)).count == details.count)
        precondition(
            !compatibleCredentialIDs.isEmpty && compatibleCredentialIDs.allSatisfy(isProximityNonBlank)
        )
        precondition(deviceSignedElements.allSatisfy { compatibleCredentialIDs.contains($0.credentialID) })
        precondition(
            Set(deviceSignedElements.map {
                "\($0.credentialID)\u{0}\($0.namespace)\u{0}\($0.elementIdentifier)"
            }).count == deviceSignedElements.count
        )
        precondition(resultBindingDigest.count == 32, "Application-profile binding must be SHA-256")
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

/// Swift-native immutable configuration for one single-use session.
public struct ProximityPresentationConfiguration: Sendable {
    /// Versioned interoperability profile.
    public let profile: ProximityPresentationProfile
    /// Holder-to-reader engagement configuration selected for the session.
    public let engagement: ProximityPresentationEngagementConfiguration
    /// Nonempty typed device-retrieval configuration.
    public let retrieval: ProximityPresentationRetrievalConfiguration
    /// Reader-authentication policy.
    public let readerPolicy: ProximityPresentationReaderPolicy
    /// Holder-authentication policy frozen before review.
    public let deviceAuthenticationPolicy: ProximityDeviceAuthenticationPolicy
    /// Optional application-owned reader-trust evaluator.
    public let readerTrustEvaluator: (any ProximityReaderTrustEvaluator)?
    /// Optional application-owned credential-status evaluator.
    public let credentialStatusEvaluator: (any ProximityCredentialStatusEvaluator)?
    /// Ordered application-specific request interpreters.
    public let applicationProfiles: [any ProximityApplicationProfile]
    /// Maximum accepted protocol message size in bytes.
    public let maximumMessageBytes: Int

    /// Creates immutable configuration for one single-use session.
    /// - Parameters:
    ///   - profile: Versioned interoperability profile.
    ///   - engagement: Holder-to-reader engagement configuration.
    ///   - retrieval: Nonempty device-retrieval configuration.
    ///   - readerPolicy: Reader-authentication policy.
    ///   - deviceAuthenticationPolicy: Allowed holder-authentication methods and preference.
    ///   - readerTrustEvaluator: Optional explicit trust boundary.
    ///   - credentialStatusEvaluator: Optional explicit status boundary.
    ///   - applicationProfiles: Ordered application profiles.
    ///   - maximumMessageBytes: Positive limit of at most 16 MiB.
    public init(
        profile: ProximityPresentationProfile = .iso180135Edition2DIS2026,
        engagement: ProximityPresentationEngagementConfiguration = .qrOnly,
        retrieval: ProximityPresentationRetrievalConfiguration = .conventional(),
        readerPolicy: ProximityPresentationReaderPolicy = .allowAnonymousOrUntrusted,
        deviceAuthenticationPolicy: ProximityDeviceAuthenticationPolicy = .signatureOnly,
        readerTrustEvaluator: (any ProximityReaderTrustEvaluator)? = nil,
        credentialStatusEvaluator: (any ProximityCredentialStatusEvaluator)? = nil,
        applicationProfiles: [any ProximityApplicationProfile] = [],
        maximumMessageBytes: Int = 1_048_576
    ) {
        precondition(maximumMessageBytes > 0 && maximumMessageBytes <= 16_777_216)
        precondition(profile != .eudiARF3FCAF202608 || readerPolicy == .requireTrusted)
        precondition(profile != .eudiARF3FCAF202608 || deviceAuthenticationPolicy == .signatureOnly)
        precondition(
            profile != .iso1801352021 || !engagement.usesProvisionalNFCV2,
            "NFC Engagement v2 is not part of the ISO/IEC 18013-5:2021 compatibility profile"
        )
        let provisionalNFCV2Retrieval = retrieval.provisionalNFCV2
        precondition(
            engagement.usesProvisionalNFCV2 == (provisionalNFCV2Retrieval != nil),
            "NFCv2 engagement and its distinct retrieval configuration must be selected together"
        )
        if let provisionalNFCV2Retrieval {
            if engagement.includesQR {
                precondition(
                    provisionalNFCV2Retrieval.bluetoothLowEnergy != nil ||
                        provisionalNFCV2Retrieval.qrNFC != nil,
                    "A combined QR/NFCv2 session requires a QR-compatible retrieval method"
                )
            } else {
                precondition(
                    provisionalNFCV2Retrieval.qrNFC == nil,
                    "QR-only conventional NFC retrieval cannot be configured without a QR engagement path"
                )
            }
        }
        precondition(applicationProfiles.allSatisfy { isProximityNonBlank($0.id) })
        precondition(Set(applicationProfiles.map(\.id)).count == applicationProfiles.count)
        self.profile = profile
        self.engagement = engagement
        self.retrieval = retrieval
        self.readerPolicy = readerPolicy
        self.deviceAuthenticationPolicy = deviceAuthenticationPolicy
        self.readerTrustEvaluator = readerTrustEvaluator
        self.credentialStatusEvaluator = credentialStatusEvaluator
        self.applicationProfiles = applicationProfiles
        self.maximumMessageBytes = maximumMessageBytes
    }
}

/// Stable error category for host presentation and recovery policy.
public enum ProximityPresentationErrorCategory: Sendable, Equatable {
    /// Device or runtime capability is unavailable.
    case capability
    /// Device engagement failed.
    case engagement
    /// Retrieval transport failed.
    case transport
    /// ISO protocol processing failed.
    case protocolFailure
    /// Reader authentication was absent, malformed, or invalid.
    case readerAuthentication
    /// Application reader-trust policy rejected the request.
    case trust
    /// No acceptable credential can satisfy the request.
    case credential
    /// Holder-key resolution or use failed.
    case holderKey
    /// Application-profile processing rejected or failed.
    case applicationProfile
    /// The approved submission no longer matches current request state.
    case staleSubmission
    /// The requested action violates session policy.
    case policy
    /// An unexpected internal failure occurred.
    case internalFailure
}

/// Display-safe, typed proximity failure.
public struct ProximityPresentationError: Error, Sendable, Equatable {
    /// Stable failure category.
    public let category: ProximityPresentationErrorCategory
    /// Stable machine-readable error code.
    public let code: String
    /// Display-safe error message.
    public let message: String
    /// Whether the host may offer an in-session recovery action.
    public let recoverable: Bool
}

/// Host action that may restore a selected proximity capability.
public enum ProximityPresentationRemediationAction: Sendable, Hashable {
    /// Request Bluetooth permission using the platform system surface.
    case requestBluetoothPermission
    /// Open application settings using the platform system surface.
    case openApplicationSettings
    /// Ask the user to enable Bluetooth through the platform-owned surface.
    case enableBluetooth
    /// Ask the user to enable NFC through the platform-owned surface.
    case enableNFC
    /// Explain that the selected capability requires another device.
    case useSupportedDevice
    /// Re-run capability checks without another system surface.
    case retry
}

/// One engagement or retrieval dimension reported independently.
public struct ProximityPresentationTransportCapability: Sendable, Equatable {
    /// Whether this SDK build implements the dimension.
    public let implemented: Bool
    /// Whether the selected interoperability profile permits it.
    public let profilePermitted: Bool
    /// Whether the current device and runtime can use it now.
    public let runtimeAvailable: Bool
    /// Whether session configuration selected it.
    public let selected: Bool
    /// Display-safe reason the selected dimension cannot start.
    public let unavailable: ProximityPresentationError?
    /// Ordered host actions that may restore availability.
    public let remediationActions: [ProximityPresentationRemediationAction]
    /// Whether this selected dimension may start now.
    public var mayStart: Bool { implemented && profilePermitted && runtimeAvailable && selected }
}

/// Truthful capability report for every modeled engagement and retrieval dimension.
public struct ProximityPresentationCapabilities: Sendable, Equatable {
    /// Profile used to evaluate capability policy.
    public let profile: ProximityPresentationProfile
    /// QR device-engagement capability.
    public let qrEngagement: ProximityPresentationTransportCapability
    /// NFC device-engagement capability.
    public let nfcEngagement: ProximityPresentationTransportCapability
    /// Bluetooth Low Energy retrieval capability.
    public let bluetoothLowEnergy: ProximityPresentationTransportCapability
    /// Conventional NFC retrieval capability.
    public let nfcRetrieval: ProximityPresentationTransportCapability
    /// Provisional NFCv2 same-channel retrieval capability.
    public let nfcV2Retrieval: ProximityPresentationTransportCapability
    /// Wi-Fi Aware retrieval capability.
    public let wifiAwareRetrieval: ProximityPresentationTransportCapability
    /// Whether at least one selected engagement has a compatible retrieval path that may start now.
    public var mayStart: Bool {
        let qrPath = qrEngagement.mayStart
            && [bluetoothLowEnergy, nfcRetrieval, wifiAwareRetrieval].contains(where: \.mayStart)
        let nfcPath = nfcEngagement.mayStart
            && [bluetoothLowEnergy, nfcRetrieval, nfcV2Retrieval, wifiAwareRetrieval]
                .contains(where: \.mayStart)
        return qrPath || nfcPath
    }
    /// Stable, de-duplicated remediation actions for unavailable selected dimensions.
    public var remediationActions: [ProximityPresentationRemediationAction] {
        var seen = Set<ProximityPresentationRemediationAction>()
        return [
            qrEngagement,
            nfcEngagement,
            bluetoothLowEnergy,
            nfcRetrieval,
            nfcV2Retrieval,
            wifiAwareRetrieval,
        ]
            .filter(\.selected)
            .flatMap(\.remediationActions)
            .filter { seen.insert($0).inserted }
    }
}

/// Review-safe reader-authentication result for one request scope.
public struct ProximityReaderAuthentication: Sendable, Equatable {
    /// Portion of the request covered by the result.
    public let scope: ProximityReaderAuthenticationScope
    /// Document index for document-scoped authentication.
    public let documentRequestIndex: Int?
    /// Zero-based statement index within the authentication scope.
    public let authenticationIndex: Int
    /// Structural and cryptographic validity.
    public let validity: ProximityReaderAuthenticationValidity
    /// Application trust state.
    public let trust: ProximityReaderTrustState
    /// Certificate-path state.
    public let certificatePath: ProximityReaderCertificatePathState
    /// Revocation state.
    public let revocation: ProximityReaderRevocationState
    /// RICAL evidence state.
    public let rical: ProximityRICALState
    /// Display-safe reader name supplied by the application.
    public let displayName: String?
    /// Display-safe explanation supplied by the application.
    public let reason: String?
}

/// Names one issuer-signed element without exposing a credential model.
public struct ProximityElementReference: Sendable, Hashable {
    /// Issuer namespace.
    public let namespace: String
    /// Element identifier within ``namespace``.
    public let elementIdentifier: String

    /// Creates an issuer-signed element reference.
    /// - Parameters:
    ///   - namespace: Issuer namespace.
    ///   - elementIdentifier: Element identifier within the namespace.
    public init(namespace: String, elementIdentifier: String) {
        precondition(isProximityNonBlank(namespace) && isProximityNonBlank(elementIdentifier))
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

/// Frozen, display-safe review model for one exchange.
public struct ProximityPresentationReview: Sendable, Equatable {
    /// One-based exchange number.
    public let exchange: Int
    /// Requested documents and credential choices.
    public let documents: [ProximityDocumentReview]
    /// Reader-authentication results.
    public let readerAuthentication: [ProximityReaderAuthentication]
    /// Parsed ISO use-case metadata.
    public let useCases: [ProximityUseCase]
    /// Recognized application-profile contributions.
    public let applicationAuthorizations: [ProximityApplicationAuthorization]
}

/// Holder-approved credential and element selection for one document request.
public struct ProximityDocumentSubmission: Sendable, Equatable {
    /// Zero-based document-request index from the frozen review.
    public let requestIndex: Int
    /// Selected wallet credential identifier.
    public let credentialID: String
    /// Nonempty set of approved issuer-signed elements.
    public let disclosedElements: Set<ProximityElementReference>

    /// Creates a document submission tied to a frozen review.
    /// - Parameters:
    ///   - requestIndex: Document-request index.
    ///   - credentialID: Selected credential identifier.
    ///   - disclosedElements: Nonempty approved element set.
    public init(requestIndex: Int, credentialID: String, disclosedElements: Set<ProximityElementReference>) {
        precondition(requestIndex >= 0 && isProximityNonBlank(credentialID))
        precondition(!disclosedElements.isEmpty)
        self.requestIndex = requestIndex
        self.credentialID = credentialID
        self.disclosedElements = disclosedElements
    }
}

/// Complete holder-approved submission for the current exchange.
public struct ProximityPresentationSubmission: Sendable, Equatable {
    /// Nonempty document submissions.
    public let documents: [ProximityDocumentSubmission]
    /// Whether to keep the transport alive for another device request.
    public let continueAfterResponse: Bool

    /// Creates a complete exchange submission.
    /// - Parameters:
    ///   - documents: Nonempty approved document submissions.
    ///   - continueAfterResponse: Whether another exchange may follow.
    public init(documents: [ProximityDocumentSubmission], continueAfterResponse: Bool = false) {
        precondition(!documents.isEmpty)
        precondition(Set(documents.map(\.requestIndex)).count == documents.count)
        self.documents = documents
        self.continueAfterResponse = continueAfterResponse
    }
}

/// Host intent accepted by a session only when legal for its current state.
public enum ProximityPresentationAction: Sendable, Equatable {
    /// Approve a submission derived from the current frozen review.
    case approve(ProximityPresentationSubmission)
    /// Decline the current disclosure request without sharing documents.
    case decline
    /// Cancel the session and release its resources.
    case cancel
    /// Re-run prerequisite checks after external conditions may have changed.
    case retryPrerequisites
    /// Report the privacy-safe outcome of a host remediation surface.
    case reportRemediation(ProximityPresentationRemediationAction, ProximityPresentationHostActionResult)
}

/// Privacy-safe outcome of a system surface performed by the host application.
public enum ProximityPresentationHostActionResult: Sendable, Equatable {
    /// The host completed the requested platform action.
    case completed
    /// The user cancelled the platform action.
    case cancelled
    /// The platform action failed without exposing sensitive diagnostics.
    case failed
}

/// Prepared engagement presented by the host UI.
public enum ProximityPresentationEngagement: Sendable, Equatable {
    /// QR engagement payload to render locally without transformation.
    case qr(payload: String)
    /// NFC engagement is prepared and awaits a platform interaction.
    case nfc
}

/// One protected-key operation required by a frozen approved document response.
public struct ProximityHolderAuthorizationRequest: Sendable, Equatable, Identifiable {
    /// Stable identity equal to ``requestIndex``.
    public var id: Int { requestIndex }
    /// Zero-based document-request index.
    public let requestIndex: Int
    /// Credential whose protected key is required.
    public let credentialID: String
    /// Device-authentication method frozen during review.
    public let deviceAuthentication: ProximityDeviceAuthenticationMethod
}

/// Exact holder-key authorization context for a frozen approved submission.
public struct ProximityHolderAuthorization: Sendable, Equatable {
    /// One-based exchange number being authorized.
    public let exchange: Int
    /// Per-document protected-key operations required by the frozen response.
    public let requests: [ProximityHolderAuthorizationRequest]
}

/// Result of attempting a host action.
public enum ProximityPresentationActionResult: Sendable, Equatable {
    /// The session accepted the action.
    case accepted
    /// The session rejected the action without changing its approved state.
    case rejected(ProximityPresentationError)
}

/// Coarse action identity used to drive host controls from state.
public enum ProximityPresentationActionType: Sendable, Hashable {
    /// Approve the current review.
    case approve
    /// Decline the current review.
    case decline
    /// Cancel the active session.
    case cancel
    /// Re-run prerequisite checks.
    case retryPrerequisites
    /// Report a host remediation result.
    case reportRemediation
}

/// Display-safe session state projected exhaustively from the KMP source of truth.
public enum ProximityPresentationState: Sendable, Equatable {
    /// The session is waiting for selected capabilities to become available.
    case checkingPrerequisites(ProximityPresentationCapabilities)
    /// Session-owned cryptographic and transport resources are being prepared.
    case preparing(profile: ProximityPresentationProfile)
    /// At least one engagement is ready for the host to present.
    case engagementReady([ProximityPresentationEngagement])
    /// A reader is connecting through a prepared engagement.
    case connecting([ProximityPresentationEngagement])
    /// The holder is waiting for a device request.
    case awaitingRequest(exchange: Int)
    /// The host must present the frozen review and collect explicit holder intent.
    case reviewRequired(ProximityPresentationReview)
    /// A protected holder key is authorizing the frozen approved submission.
    case authorizingHolderKey(ProximityHolderAuthorization)
    /// The response for an exchange is being sent.
    case sendingResponse(exchange: Int)
    /// The response was sent and the session awaits another request.
    case awaitingNextRequest(completedExchanges: Int)
    /// The protocol is terminating the transport for an exchange.
    case terminating(exchange: Int)
    /// The session completed normally.
    case completed(exchanges: Int, declined: Bool)
    /// The host cancelled the session.
    case cancelled
    /// The session failed with a display-safe typed error.
    case failed(ProximityPresentationError)

    /// Actions legal in this exact state.
    public var legalActions: Set<ProximityPresentationActionType> {
        switch self {
        case .checkingPrerequisites: [.retryPrerequisites, .reportRemediation, .cancel]
        case .reviewRequired: [.approve, .decline, .cancel]
        case .preparing, .engagementReady, .connecting,
             .awaitingRequest, .authorizingHolderKey, .sendingResponse, .awaitingNextRequest:
            [.cancel]
        case .terminating, .completed, .cancelled, .failed:
            []
        }
    }
}

@available(macOS 10.15, *)
protocol ProximityPresentationSessionBridge: Sendable {
    var states: AsyncStream<ProximityPresentationState> { get }
    func dispatch(_ action: ProximityPresentationAction) async throws -> ProximityPresentationActionResult
    func close() async
}

/// Actor-safe, single-use native facade over one KMP proximity session.
@available(macOS 10.15, *)
public actor ProximityPresentationSession {
    /// Exhaustive state stream whose terminal state is emitted before completion.
    public nonisolated let states: AsyncStream<ProximityPresentationState>
    private let bridge: any ProximityPresentationSessionBridge
    private var closed = false

    init(bridge: any ProximityPresentationSessionBridge) {
        self.bridge = bridge
        self.states = bridge.states
    }

    /// Dispatches one host intent against the current session state.
    /// - Parameter action: Action derived from the current state's ``ProximityPresentationState/legalActions``.
    /// - Returns: Whether the session accepted the action.
    public func dispatch(_ action: ProximityPresentationAction) async throws -> ProximityPresentationActionResult {
        guard !closed else {
            return .rejected(
                ProximityPresentationError(
                    category: .policy,
                    code: "session_closed",
                    message: "The proximity presentation session is closed",
                    recoverable: false
                )
            )
        }
        return try await bridge.dispatch(action)
    }

    /// Idempotently cancels and releases session-owned resources.
    public func close() async {
        guard !closed else { return }
        closed = true
        await bridge.close()
    }
}

private func isProximityNonBlank(_ value: String) -> Bool {
    !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}

private func isProximityX509Certificate(_ data: Data) -> Bool {
    !data.isEmpty && SecCertificateCreateWithData(nil, data as CFData) != nil
}
