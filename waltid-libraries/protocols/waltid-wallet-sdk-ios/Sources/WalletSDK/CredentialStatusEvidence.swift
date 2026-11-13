import Foundation

/// Successful reference discovery or a failure to enumerate the container safely.
public enum CredentialStatusMetadata: Decodable, Equatable, Sendable {
    /// An empty collection means no status reference, never clear status.
    case references([CredentialStatusEntry])
    /// The reference container is malformed or exceeds supported limits.
    case discoveryFailed(CredentialStatusDiscoveryProblem)

    /// Available reference records; inspect the metadata case to distinguish failed discovery.
    public var entries: [CredentialStatusEntry] {
        if case .references(let entries) = self { entries } else { [] }
    }
    /// Returned state contains a check, conflict or discovery error; adverse status alone is not an error.
    public var hasCheckError: Bool {
        if case .discoveryFailed = self { true } else { entries.contains { $0.hasCheckError } }
    }
    private enum CodingKeys: String, CodingKey { case type, entries, problem }
    /// Decodes the explicit discovery result without defaulting missing fields to absence.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "references":
            try validateStatusFields(decoder, allowed: ["type", "entries"])
            let values = try c.decode([CredentialStatusEntry].self, forKey: .entries)
            let ids = values.compactMap(\.referenceID)
            guard ids.count == values.count, Set(ids).count == ids.count else { throw invalidStatusData(decoder) }
            self = .references(values)
        case "discovery_failed":
            try validateStatusFields(decoder, allowed: ["type", "problem"])
            self = .discoveryFailed(try c.decode(CredentialStatusDiscoveryProblem.self, forKey: .problem))
        default: throw invalidStatusData(decoder)
        }
    }
}

/// Bounded discovery failure categories, without exception text or network locators.
public enum CredentialStatusDiscoveryProblem: String, Decodable, Sendable {
    /// The status container cannot be safely enumerated.
    case malformedContainer = "MALFORMED_CONTAINER"
    /// The collection exceeds supported resource limits.
    case resourceLimit = "RESOURCE_LIMIT"
}

/// A diagnostic for an identifiable reference; valid sibling references remain available.
public enum CredentialStatusReferenceProblem: String, Decodable, Sendable {
    /// A reference has invalid or missing required fields.
    case malformed = "MALFORMED"
    /// A reference exceeds the supported profile's bounds.
    case resourceLimit = "RESOURCE_LIMIT"
    /// Duplicate identity carries conflicting reference parameters.
    case conflictingReference = "CONFLICTING_REFERENCE"
}

/// A token status scalar. Classification is derived, so reserved/application categories cannot disagree with its code.
public struct CredentialTokenStatus: Decodable, Equatable, Sendable {
    /// The authenticated eight-bit status value.
    public let code: UInt8
    /// Classification under the supported Token Status List draft.
    public enum Kind: Equatable, Sendable {
        /// Standardized clear status.
        case clear
        /// Standardized invalid status.
        case invalid
        /// Standardized suspended status.
        case suspended
        /// One of 3 or 12 through 15.
        case applicationSpecific
        /// Reserved for future registration.
        case reserved
    }
    /// Classification for data inspection; display uses the shared presentation.
    public var kind: Kind {
        switch code {
        case 0: .clear
        case 1: .invalid
        case 2: .suspended
        case 3, 12...15: .applicationSpecific
        default: .reserved
        }
    }
    /// Decodes the scalar, rejecting negative, nonnumeric and out-of-range values.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws { code = try decoder.singleValueContainer().decode(UInt8.self) }
}

/// The supported one-bit ISO MSO status profile has no suspension value.
public enum CredentialMsoStatus: String, Decodable, Sendable {
    /// No revocation signal.
    case clear = "CLEAR"
    /// Issuer-reported revocation.
    case revoked = "REVOKED"
}

/// Bitstring purposes preserve extension strings and derive their known classification.
public struct CredentialBitstringPurpose: Decodable, Equatable, Sendable {
    /// The signed purpose name.
    public let value: String
    /// Standardized Bitstring purposes.
    public enum Known: String, Sendable {
        /// Irreversible revocation.
        case revocation
        /// Reversible suspension.
        case suspension
        /// Credential refresh signal, independent of invalidation.
        case refresh
        /// Application-defined messages.
        case message
    }
    /// A known purpose, or nil for an extension.
    public var known: Known? { Known(rawValue: value) }
    /// Decodes a nonblank purpose, preserving its exact protocol-specific string.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws { value = try nonblankStatusString(decoder) }
}

/// Legacy purposes use a separate type; refresh remains an extension in this protocol.
public struct CredentialLegacyPurpose: Decodable, Equatable, Sendable {
    /// The signed legacy purpose name.
    public let value: String
    /// Standardized legacy purposes.
    public enum Known: String, Sendable {
        /// Irreversible revocation.
        case revocation
        /// Reversible suspension.
        case suspension
    }
    /// A known legacy purpose, or nil for an extension.
    public var known: Known? { Known(rawValue: value) }
    /// Decodes a nonblank purpose without assigning Bitstring semantics.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws { value = try nonblankStatusString(decoder) }
}

/// A dated value within its typed owner, without duplicated identity, protocol or purpose.
public struct CredentialStatusObservation<Value: Decodable & Equatable & Sendable>: Decodable, Equatable, Sendable {
    /// Protocol-specific observed value.
    public let value: Value
    /// Original successful check time.
    public let checkedAt: Date
    /// Exclusive evidence freshness deadline.
    public let freshUntil: Date
    /// Optional signed or authorization validity bound.
    public let validUntil: Date?
    private enum CodingKeys: String, CodingKey { case value, checkedAt, freshUntil, validUntil }
    /// Decodes typed evidence and validates its lifetime bound, including immediate-stale history.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        try validateStatusFields(decoder, allowed: ["value", "checkedAt", "freshUntil", "validUntil"])
        let c = try decoder.container(keyedBy: CodingKeys.self)
        value = try c.decode(Value.self, forKey: .value)
        checkedAt = try c.decode(Date.self, forKey: .checkedAt)
        freshUntil = try c.decode(Date.self, forKey: .freshUntil)
        validUntil = try c.decodeIfPresent(Date.self, forKey: .validUntil)
        guard validUntil.map({ freshUntil <= $0 }) ?? true else { throw invalidStatusData(decoder) }
    }
}

/// Check outcome; failed attempts can retain an observation of the same protocol value type.
public enum CredentialStatusCheck<Value: Decodable & Equatable & Sendable>: Decodable, Equatable, Sendable {
    /// No attempt yet.
    case notChecked
    /// Successful applicable observation.
    case observed(CredentialStatusObservation<Value>)
    /// Failure before or after a successful observation.
    case failed(attemptedAt: Date, cause: CredentialStatusCheckFailure, previous: CredentialStatusObservation<Value>?)
    /// A contradictory irreversible update retained the original assertion.
    case conflict(attemptedAt: Date, retained: CredentialStatusObservation<Value>)

    /// The latest retained successful observation, with its original time.
    public var lastObservation: CredentialStatusObservation<Value>? {
        switch self {
        case .notChecked: nil
        case .observed(let value): value
        case .failed(_, _, let previous): previous
        case .conflict(_, let retained): retained
        }
    }
    /// Whether this check represents a failure/conflict rather than an issuer adverse result.
    public var hasError: Bool {
        switch self { case .failed, .conflict: true; default: false }
    }
    private enum CodingKeys: String, CodingKey { case type, observation, attemptedAt, cause, previous, retained }
    /// Decodes the outcome with its required typed payload.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "not_checked":
            try validateStatusFields(decoder, allowed: ["type"]); self = .notChecked
        case "observed":
            try validateStatusFields(decoder, allowed: ["type", "observation"])
            self = .observed(try c.decode(CredentialStatusObservation<Value>.self, forKey: .observation))
        case "failed":
            try validateStatusFields(decoder, allowed: ["type", "attemptedAt", "cause", "previous"])
            self = .failed(attemptedAt: try c.decode(Date.self, forKey: .attemptedAt),
                cause: try c.decode(CredentialStatusCheckFailure.self, forKey: .cause),
                previous: try c.decodeIfPresent(CredentialStatusObservation<Value>.self, forKey: .previous))
        case "conflict":
            try validateStatusFields(decoder, allowed: ["type", "attemptedAt", "retained"])
            self = .conflict(attemptedAt: try c.decode(Date.self, forKey: .attemptedAt),
                retained: try c.decode(CredentialStatusObservation<Value>.self, forKey: .retained))
        default: throw invalidStatusData(decoder)
        }
    }
}

/// Check failures preserve temporal eligibility and bounded reason codes.
public enum CredentialStatusCheckFailure: Decodable, Equatable, Sendable {
    /// The credential can be checked at its valid-from time.
    case notYetValid(validFrom: Date)
    /// A service cooldown, optionally with a known retry deadline.
    case rateLimited(retryAt: Date?)
    /// Other supported failure categories.
    case reason(Reason)
    /// Failure reasons independent of issuer status and host preferences.
    public enum Reason: String, Decodable, Sendable {
        /// Credential validity ended.
        case credentialExpired = "CREDENTIAL_EXPIRED"
        /// Status transport failed.
        case transport = "TRANSPORT"
        /// Work exceeded its time budget.
        case timeout = "TIMEOUT"
        /// The reference's signed credential could not be authenticated.
        case referenceUnauthenticated = "REFERENCE_UNAUTHENTICATED"
        /// The proof suite is outside the supported profile.
        case unsupportedProof = "UNSUPPORTED_PROOF"
        /// Signature verification failed.
        case invalidSignature = "INVALID_SIGNATURE"
        /// The signer is not authorized.
        case unauthorizedSigner = "UNAUTHORIZED_SIGNER"
        /// A signed time bound is invalid.
        case invalidValidity = "INVALID_VALIDITY"
        /// A status list is malformed or inapplicable.
        case invalidList = "INVALID_LIST"
        /// Work exceeded resource limits.
        case resourceLimit = "RESOURCE_LIMIT"
    }
    /// Stable localization code; presentation semantics remain shared-owned.
    public var code: String {
        switch self { case .notYetValid: "NOT_YET_VALID"; case .rateLimited: "RATE_LIMITED"; case .reason(let reason): reason.rawValue }
    }
    private enum CodingKeys: String, CodingKey { case type, validFrom, retryAt, reason }
    /// Decodes required temporal payloads and reasons, rejecting incomplete known cases.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "not_yet_valid":
            try validateStatusFields(decoder, allowed: ["type", "validFrom"])
            self = .notYetValid(validFrom: try c.decode(Date.self, forKey: .validFrom))
        case "rate_limited":
            try validateStatusFields(decoder, allowed: ["type", "retryAt"])
            self = .rateLimited(retryAt: try c.decodeIfPresent(Date.self, forKey: .retryAt))
        case "reason":
            try validateStatusFields(decoder, allowed: ["type", "reason"])
            self = .reason(try c.decode(Reason.self, forKey: .reason))
        default: throw invalidStatusData(decoder)
        }
    }
}

/// A protocol owns its identity, sole purpose where applicable, and typed check.
public enum CredentialStatusEntry: Decodable, Equatable, Sendable {
    /// Token Status List with its own scalar classification.
    case token(referenceID: String, check: CredentialStatusCheck<CredentialTokenStatus>)
    /// One-bit MSO revocation, with no arbitrary purpose.
    case mso(referenceID: String, check: CredentialStatusCheck<CredentialMsoStatus>)
    /// W3C Bitstring purpose and up to eight-bit wallet-profile value.
    case bitstring(referenceID: String, purpose: CredentialBitstringPurpose, check: CredentialStatusCheck<UInt8>)
    /// Legacy purpose and single-bit value.
    case legacy(referenceID: String, purpose: CredentialLegacyPurpose, check: CredentialStatusCheck<Bool>)
    /// An unimplemented method has no observation/check payload.
    case unsupported(referenceID: String, method: String)
    /// An identifiable malformed reference, alongside usable siblings.
    case malformed(referenceID: String, problem: CredentialStatusReferenceProblem)
    /// A future wire variant; enclosing metadata rejects it conservatively.
    case unknown

    /// The opaque reference identity; future variants have no usable identity.
    public var referenceID: String? {
        switch self {
        case .token(let id, _), .mso(let id, _), .bitstring(let id, _, _), .legacy(let id, _, _), .unsupported(let id, _), .malformed(let id, _): id
        case .unknown: nil
        }
    }
    /// A check/discovery error, distinct from a known adverse issuer result.
    public var hasCheckError: Bool {
        switch self {
        case .token(_, let check): check.hasError
        case .mso(_, let check): check.hasError
        case .bitstring(_, _, let check): check.hasError
        case .legacy(_, _, let check): check.hasError
        case .malformed, .unknown: true
        case .unsupported: false
        }
    }
    private enum CodingKeys: String, CodingKey { case type, referenceId, purpose, check, method, problem }
    /// Decodes the concrete protocol and validates conflict applicability without reinterpreting UI semantics.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)
        if !["token", "mso", "bitstring", "legacy", "unsupported", "malformed"].contains(type) { self = .unknown; return }
        let id = try c.decode(String.self, forKey: .referenceId)
        guard !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw invalidStatusData(decoder) }
        switch type {
        case "token":
            try validateStatusFields(decoder, allowed: ["type", "referenceId", "check"])
            let check = try c.decode(CredentialStatusCheck<CredentialTokenStatus>.self, forKey: .check)
            if case .conflict = check { throw invalidStatusData(decoder) }
            self = .token(referenceID: id, check: check)
        case "mso":
            try validateStatusFields(decoder, allowed: ["type", "referenceId", "check"])
            let check = try c.decode(CredentialStatusCheck<CredentialMsoStatus>.self, forKey: .check)
            if case .conflict(_, let retained) = check, retained.value != .revoked { throw invalidStatusData(decoder) }
            self = .mso(referenceID: id, check: check)
        case "bitstring":
            try validateStatusFields(decoder, allowed: ["type", "referenceId", "purpose", "check"])
            let purpose = try c.decode(CredentialBitstringPurpose.self, forKey: .purpose)
            let check = try c.decode(CredentialStatusCheck<UInt8>.self, forKey: .check)
            if case .conflict(_, let retained) = check, (purpose.known != .revocation && purpose.known != .refresh || retained.value == 0) { throw invalidStatusData(decoder) }
            self = .bitstring(referenceID: id, purpose: purpose, check: check)
        case "legacy":
            try validateStatusFields(decoder, allowed: ["type", "referenceId", "purpose", "check"])
            let purpose = try c.decode(CredentialLegacyPurpose.self, forKey: .purpose)
            let check = try c.decode(CredentialStatusCheck<Bool>.self, forKey: .check)
            if case .conflict(_, let retained) = check, purpose.known != .revocation || !retained.value { throw invalidStatusData(decoder) }
            self = .legacy(referenceID: id, purpose: purpose, check: check)
        case "unsupported":
            try validateStatusFields(decoder, allowed: ["type", "referenceId", "method"])
            let method = try c.decode(String.self, forKey: .method)
            guard !method.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw invalidStatusData(decoder) }
            self = .unsupported(referenceID: id, method: method)
        case "malformed":
            try validateStatusFields(decoder, allowed: ["type", "referenceId", "problem"])
            self = .malformed(referenceID: id, problem: try c.decode(CredentialStatusReferenceProblem.self, forKey: .problem))
        default: self = .unknown
        }
    }
}

private struct StatusFieldKey: CodingKey {
    let stringValue: String
    let intValue: Int? = nil
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

func validateStatusFields(_ decoder: Decoder, allowed: Set<String>) throws {
    let keys = try decoder.container(keyedBy: StatusFieldKey.self).allKeys.map(\.stringValue)
    guard Set(keys).isSubset(of: allowed) else { throw invalidStatusData(decoder) }
}
func invalidStatusData(_ decoder: Decoder) -> DecodingError {
    .dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Invalid credential status data"))
}
private func nonblankStatusString(_ decoder: Decoder) throws -> String {
    let value = try decoder.singleValueContainer().decode(String.self)
    guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw invalidStatusData(decoder) }
    return value
}
