import Foundation

/// Persisted Reader Authentication policy selected by the holder.
public enum ProximityStoredReaderPolicy: String, Sendable, CaseIterable, Equatable {
    /// Permit absent or untrusted reader authentication to reach holder review.
    case allowAnonymousOrUntrusted
    /// Permit only readers accepted by the configured trust material to reach holder review.
    case requireTrusted
}

/// One validated Reader CA retained in holder-owned settings.
public struct ProximityStoredReaderTrustAnchor: Sendable, Equatable, Identifiable {
    /// Stable identity derived from the canonical certificate bytes.
    public var id: String { certificateDERBase64URL }
    /// DER certificate encoded as unpadded Base64URL.
    public let certificateDERBase64URL: String
    /// Holder-visible authority label.
    public let displayName: String

    fileprivate init(certificateDERBase64URL: String, displayName: String) {
        self.certificateDERBase64URL = certificateDERBase64URL
        self.displayName = displayName
    }
}

/// One validated static RICAL provider retained in holder-owned settings.
public struct ProximityStoredRICALProvider: Sendable, Equatable, Identifiable {
    /// Stable provider identifier.
    public var id: String { providerID }
    public let providerID: String
    public let acceptedTypes: Set<String>
    public let providerTrustAnchorsDERBase64URL: [String]
    public let acceptedSignerCertificatePolicyOIDs: Set<String>
    public let establishesReaderTrust: Bool
    /// Exact untagged COSE_Sign1 bytes encoded as unpadded Base64URL.
    public let signedRICALBase64URL: String

    fileprivate init(
        providerID: String,
        acceptedTypes: Set<String>,
        providerTrustAnchorsDERBase64URL: [String],
        acceptedSignerCertificatePolicyOIDs: Set<String>,
        establishesReaderTrust: Bool,
        signedRICALBase64URL: String
    ) {
        self.providerID = providerID
        self.acceptedTypes = acceptedTypes
        self.providerTrustAnchorsDERBase64URL = providerTrustAnchorsDERBase64URL
        self.acceptedSignerCertificatePolicyOIDs = acceptedSignerCertificatePolicyOIDs
        self.establishesReaderTrust = establishesReaderTrust
        self.signedRICALBase64URL = signedRICALBase64URL
    }
}

/// Complete immutable Reader Authentication settings snapshot.
public struct ProximityReaderTrustSettings: Sendable, Equatable {
    public let readerPolicy: ProximityStoredReaderPolicy
    public let trustAnchors: [ProximityStoredReaderTrustAnchor]
    public let ricalProviders: [ProximityStoredRICALProvider]

    /// Creates empty settings with the selected reader policy.
    public init(readerPolicy: ProximityStoredReaderPolicy = .allowAnonymousOrUntrusted) {
        self.readerPolicy = readerPolicy
        trustAnchors = []
        ricalProviders = []
    }

    fileprivate init(
        readerPolicy: ProximityStoredReaderPolicy,
        trustAnchors: [ProximityStoredReaderTrustAnchor],
        ricalProviders: [ProximityStoredRICALProvider]
    ) {
        self.readerPolicy = readerPolicy
        self.trustAnchors = trustAnchors
        self.ricalProviders = ricalProviders
    }

    /// Returns a new snapshot with the selected reader policy.
    public func updatingReaderPolicy(_ policy: ProximityStoredReaderPolicy) -> Self {
        .init(readerPolicy: policy, trustAnchors: trustAnchors, ricalProviders: ricalProviders)
    }

    /// Returns a new snapshot without the selected Reader CA.
    public func removingReaderTrustAnchor(id: String) -> Self {
        .init(
            readerPolicy: readerPolicy,
            trustAnchors: trustAnchors.filter { $0.id != id },
            ricalProviders: ricalProviders
        )
    }

    /// Returns a new snapshot without the selected RICAL provider.
    public func removingRICALProvider(id: String) -> Self {
        .init(
            readerPolicy: readerPolicy,
            trustAnchors: trustAnchors,
            ricalProviders: ricalProviders.filter { $0.id != id }
        )
    }

    /// Applies this immutable settings snapshot to one new proximity session.
    public func applying(
        to configuration: ProximityPresentationConfiguration = .init()
    ) -> ProximityPresentationConfiguration {
        let evaluator = configuredReaderTrustEvaluator(for: self)

        return ProximityPresentationConfiguration(
            profile: configuration.profile,
            bleRoles: configuration.bleRoles,
            bearerPolicy: configuration.bearerPolicy,
            engagementMethods: configuration.engagementMethods,
            retrievalMethods: configuration.retrievalMethods,
            readerPolicy: readerPolicy.presentationPolicy,
            deviceAuthenticationPolicy: configuration.deviceAuthenticationPolicy,
            readerTrustEvaluator: evaluator,
            credentialStatusEvaluator: configuration.credentialStatusEvaluator,
            applicationProfiles: configuration.applicationProfiles,
            maximumMessageBytes: configuration.maximumMessageBytes
        )
    }
}

/// Kind of validated reader-trust material awaiting confirmation.
public enum ProximityReaderTrustImportKind: Sendable, Equatable {
    case readerCA
    case trustBundle
}

/// Display-safe preview of one imported Reader CA.
public struct ProximityReaderTrustAnchorImportPreview: Sendable, Equatable, Identifiable {
    public var id: String { sha256Fingerprint }
    public let displayName: String
    public let subject: String
    public let issuer: String
    public let sha256Fingerprint: String
    public let validFrom: Date
    public let validUntil: Date
    public let profile: String
}

/// Display-safe preview of one validated static RICAL provider.
public struct ProximityRICALImportPreview: Sendable, Equatable, Identifiable {
    public var id: String { providerID }
    public let providerID: String
    public let providerName: String
    public let type: String
    public let issuedAt: Date
    public let nextUpdate: Date?
    public let validUntil: Date?
    public let establishesReaderTrust: Bool
}

/// Immutable import review which must be explicitly confirmed before persistence.
public struct ProximityReaderTrustImportPreview: Sendable, Equatable {
    public let kind: ProximityReaderTrustImportKind
    public let sourceName: String
    public let readerAuthorities: [ProximityReaderTrustAnchorImportPreview]
    public let ricalProviders: [ProximityRICALImportPreview]
    public let policyEffect: String
    public let resultingSettings: ProximityReaderTrustSettings
}

/// Shared canonical settings codec and strict Reader CA / RICAL importer.
public enum ProximityReaderTrustSettingsCodec {
    public static let maximumImportBytes = 1_048_576

    /// Encodes settings using the shared versioned persistence schema.
    public static func encode(_ settings: ProximityReaderTrustSettings) throws -> String {
        #if canImport(WalletCore) && os(iOS)
        return MobileWalletProximityReaderTrustSettingsCodec.shared.encode(
            settings: settings.toKMPSettings()
        )
        #else
        throw ProximityReaderTrustSettingsCodecError.walletCoreUnavailable
        #endif
    }

    /// Decodes and validates the shared versioned persistence schema.
    public static func decode(_ encoded: String) throws -> ProximityReaderTrustSettings {
        #if canImport(WalletCore) && os(iOS)
        return try MobileWalletProximityReaderTrustSettingsCodec.shared
            .decode(encoded: encoded)
            .toSwiftSettings()
        #else
        throw ProximityReaderTrustSettingsCodecError.walletCoreUnavailable
        #endif
    }

    /// Validates imported bytes and returns a review without persisting any material.
    public static func prepareImport(
        sourceName: String,
        data: Data,
        existing: ProximityReaderTrustSettings,
        now: Date = Date()
    ) async throws -> ProximityReaderTrustImportPreview {
        #if canImport(WalletCore) && os(iOS)
        let seconds = now.timeIntervalSince1970
        let wholeSeconds = Int64(seconds.rounded(.down))
        let nanoseconds = Int32(((seconds - Double(wholeSeconds)) * 1_000_000_000).rounded())
        let preview = try await MobileWalletProximityReaderTrustSettingsCodec.shared.prepareImport(
            sourceName: sourceName,
            bytes: data.toKotlinByteArray(),
            existing: existing.toKMPSettings(),
            now: KotlinInstant.companion.fromEpochSeconds(
                epochSeconds: wholeSeconds,
                nanosecondAdjustment: nanoseconds
            )
        )
        return try preview.toSwiftPreview()
        #else
        throw ProximityReaderTrustSettingsCodecError.walletCoreUnavailable
        #endif
    }
}

public enum ProximityReaderTrustSettingsCodecError: LocalizedError, Sendable {
    case walletCoreUnavailable
    case invalidCoreData

    public var errorDescription: String? {
        switch self {
        case .walletCoreUnavailable:
            return "WalletCore is unavailable"
        case .invalidCoreData:
            return "WalletCore returned invalid reader trust data"
        }
    }
}

private struct StaticProximityRICALProvider: ProximityRICALProvider {
    let signedRICAL: Data

    func current() async throws -> ProximityRICALProviderResult {
        .available(signedRICAL: signedRICAL)
    }
}

private extension ProximityStoredReaderPolicy {
    var presentationPolicy: ProximityPresentationReaderPolicy {
        switch self {
        case .allowAnonymousOrUntrusted: return .allowAnonymousOrUntrusted
        case .requireTrusted: return .requireTrusted
        }
    }
}

private func decodedReaderTrustBase64URL(_ encoded: String) -> Data? {
    var base64 = encoded.replacingOccurrences(of: "-", with: "+")
        .replacingOccurrences(of: "_", with: "/")
    base64.append(String(repeating: "=", count: (4 - base64.count % 4) % 4))
    return Data(base64Encoded: base64)
}

private func validatedBase64URL(_ encoded: String) -> Data {
    guard let data = decodedReaderTrustBase64URL(encoded), !data.isEmpty else {
        preconditionFailure("Validated reader trust settings contained invalid Base64URL data")
    }
    return data
}

private func configuredReaderTrustEvaluator(
    for settings: ProximityReaderTrustSettings
) -> (any ProximityReaderTrustEvaluator)? {
    guard !settings.trustAnchors.isEmpty || !settings.ricalProviders.isEmpty else { return nil }
    #if canImport(WalletCore) && os(iOS)
    return ProximityConfiguredReaderTrustEvaluator(
        configuration: ProximityReaderTrustConfiguration(
            trustAnchors: settings.trustAnchors.map {
                ProximityReaderTrustAnchor(
                    certificateDER: validatedBase64URL($0.certificateDERBase64URL),
                    displayName: $0.displayName
                )
            },
            ricalProviders: settings.ricalProviders.map { provider in
                ProximityRICALConfiguration(
                    providerID: provider.providerID,
                    acceptedTypes: provider.acceptedTypes,
                    providerTrustAnchors: provider.providerTrustAnchorsDERBase64URL.map {
                        ProximityRICALProviderTrustAnchor(certificateDER: validatedBase64URL($0))
                    },
                    acceptedSignerCertificatePolicyOIDs:
                        provider.acceptedSignerCertificatePolicyOIDs,
                    establishReaderTrust: provider.establishesReaderTrust,
                    provider: StaticProximityRICALProvider(
                        signedRICAL: validatedBase64URL(provider.signedRICALBase64URL)
                    )
                )
            }
        )
    )
    #else
    preconditionFailure("Configured reader trust requires WalletCore")
    #endif
}

#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

private extension ProximityStoredReaderPolicy {
    var kmpPolicy: MobileWalletProximityReaderPolicy {
        switch self {
        case .allowAnonymousOrUntrusted: return .allowAnonymousOrUntrusted
        case .requireTrusted: return .requireTrusted
        }
    }
}

private extension MobileWalletProximityReaderPolicy {
    var storedPolicy: ProximityStoredReaderPolicy {
        switch self {
        case .allowAnonymousOrUntrusted: return .allowAnonymousOrUntrusted
        case .requireTrusted: return .requireTrusted
        }
    }
}

private extension ProximityReaderTrustSettings {
    func toKMPSettings() -> MobileWalletProximityReaderTrustSettings {
        MobileWalletProximityReaderTrustSettings(
            readerPolicy: readerPolicy.kmpPolicy,
            trustAnchors: trustAnchors.map {
                MobileWalletProximityStoredReaderTrustAnchor(
                    certificateDerBase64Url: $0.certificateDERBase64URL,
                    displayName: $0.displayName
                )
            },
            ricalProviders: ricalProviders.map {
                MobileWalletProximityStoredRicalProvider(
                    providerId: $0.providerID,
                    acceptedTypes: $0.acceptedTypes,
                    providerTrustAnchorsDerBase64Url: $0.providerTrustAnchorsDERBase64URL,
                    acceptedSignerCertificatePolicyOids: $0.acceptedSignerCertificatePolicyOIDs,
                    establishReaderTrust: $0.establishesReaderTrust,
                    signedRicalBase64Url: $0.signedRICALBase64URL
                )
            }
        )
    }
}

private extension MobileWalletProximityReaderTrustSettings {
    func toSwiftSettings() throws -> ProximityReaderTrustSettings {
        ProximityReaderTrustSettings(
            readerPolicy: readerPolicy.storedPolicy,
            trustAnchors: trustAnchors.map {
                ProximityStoredReaderTrustAnchor(
                    certificateDERBase64URL: $0.certificateDerBase64Url,
                    displayName: $0.displayName
                )
            },
            ricalProviders: ricalProviders.map {
                ProximityStoredRICALProvider(
                    providerID: $0.providerId,
                    acceptedTypes: stringSet($0.acceptedTypes),
                    providerTrustAnchorsDERBase64URL: $0.providerTrustAnchorsDerBase64Url,
                    acceptedSignerCertificatePolicyOIDs:
                        stringSet($0.acceptedSignerCertificatePolicyOids),
                    establishesReaderTrust: $0.establishReaderTrust,
                    signedRICALBase64URL: $0.signedRicalBase64Url
                )
            }
        )
    }
}

private extension MobileWalletProximityReaderTrustImportPreview {
    func toSwiftPreview() throws -> ProximityReaderTrustImportPreview {
        ProximityReaderTrustImportPreview(
            kind: kind == .readerCa ? .readerCA : .trustBundle,
            sourceName: sourceName,
            readerAuthorities: readerAuthorities.map {
                ProximityReaderTrustAnchorImportPreview(
                    displayName: $0.displayName,
                    subject: $0.subject,
                    issuer: $0.issuer,
                    sha256Fingerprint: $0.sha256Fingerprint,
                    validFrom: $0.validFrom.toDate(),
                    validUntil: $0.validUntil.toDate(),
                    profile: $0.profile
                )
            },
            ricalProviders: ricalProviders.map {
                ProximityRICALImportPreview(
                    providerID: $0.providerId,
                    providerName: $0.providerName,
                    type: $0.type,
                    issuedAt: $0.issuedAt.toDate(),
                    nextUpdate: $0.nextUpdate?.toDate(),
                    validUntil: $0.validUntil?.toDate(),
                    establishesReaderTrust: $0.establishesReaderTrust
                )
            },
            policyEffect: policyEffect,
            resultingSettings: try resultingSettings.toSwiftSettings()
        )
    }
}

private func stringSet(_ value: Any) -> Set<String> {
    if let values = value as? Set<String> { return values }
    if let values = value as? NSSet { return Set(values.compactMap { $0 as? String }) }
    return []
}
#endif
