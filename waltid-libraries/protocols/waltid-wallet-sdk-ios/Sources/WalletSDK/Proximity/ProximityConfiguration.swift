import Foundation

/// Reader-authentication policy applied before disclosure review.
public enum ProximityReaderPolicy: Sendable, Equatable {
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

/// Swift-native immutable configuration for one single-use session.
public struct ProximityConfiguration: Sendable {
    /// Versioned interoperability profile.
    public let profile: ProximityProfile
    /// Engagement and compatible retrieval plans owned by this session.
    public let session: ProximitySessionConfiguration
    /// Reader-authentication policy.
    public let readerPolicy: ProximityReaderPolicy
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
    /// Explicit holder-approval behavior; a stored preference never creates an approval.
    public private(set) var approval: ProximityApproval

    /// Creates the built-in valid configuration without application-supplied values.
    public init() {
        // Only fixed SDK defaults enter this construction path.
        try! self.init(profile: .iso180135Edition2DIS2026)
    }

    /// Creates immutable configuration for one single-use session.
    /// - Throws: `WalletError.invalidInput` if the values violate the model contract.
    /// - Parameters:
    ///   - profile: Versioned interoperability profile.
    ///   - session: Engagement and compatible retrieval plans.
    ///   - readerPolicy: Reader-authentication policy.
    ///   - deviceAuthenticationPolicy: Allowed holder-authentication methods and preference.
    ///   - readerTrustEvaluator: Optional explicit trust boundary.
    ///   - credentialStatusEvaluator: Optional explicit status boundary.
    ///   - applicationProfiles: Ordered application profiles.
    ///   - maximumMessageBytes: Positive limit of at most 16 MiB.
    ///   - approval: Explicit holder-approval behavior for this session; defaults to reviewing each connected request.
    public init(
        profile: ProximityProfile = .iso180135Edition2DIS2026,
        session: ProximitySessionConfiguration = .qr(),
        readerPolicy: ProximityReaderPolicy = .allowAnonymousOrUntrusted,
        deviceAuthenticationPolicy: ProximityDeviceAuthenticationPolicy = .signatureOnly,
        readerTrustEvaluator: (any ProximityReaderTrustEvaluator)? = nil,
        credentialStatusEvaluator: (any ProximityCredentialStatusEvaluator)? = nil,
        applicationProfiles: [any ProximityApplicationProfile] = [],
        maximumMessageBytes: Int = 1_048_576,
        approval: ProximityApproval = .askEachTime
    ) throws {
        try requireProximityInput(maximumMessageBytes > 0 && maximumMessageBytes <= 16_777_216)
        try requireProximityInput(profile != .eudiARF3FCAF202608 || readerPolicy == .requireTrusted)
        try requireProximityInput(profile != .eudiARF3FCAF202608 || deviceAuthenticationPolicy == .signatureOnly)
        try requireProximityInput(
            profile != .iso1801352021 || !session.usesProvisionalNFCV2,
            "NFC Engagement v2 is not part of the ISO/IEC 18013-5:2021 compatibility profile"
        )
        try requireProximityInput(applicationProfiles.allSatisfy { isProximityNonBlank($0.id) })
        try requireProximityInput(Set(applicationProfiles.map(\.id)).count == applicationProfiles.count)
        self.profile = profile
        self.session = session
        self.readerPolicy = readerPolicy
        self.deviceAuthenticationPolicy = deviceAuthenticationPolicy
        self.readerTrustEvaluator = readerTrustEvaluator
        self.credentialStatusEvaluator = credentialStatusEvaluator
        self.applicationProfiles = applicationProfiles
        self.maximumMessageBytes = maximumMessageBytes
        self.approval = approval
    }

    /// Replaces only approval behavior, preserving the profile, trust and transport policies.
    /// - Parameter approval: Holder-approval behavior to use in the returned configuration.
    public func withApproval(_ approval: ProximityApproval) -> ProximityConfiguration {
        var updated = self
        updated.approval = approval
        return updated
    }
}
