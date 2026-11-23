import Foundation

/// The wallet's dated issuer observation, independent of expiry, trust and presentation acceptance.
public struct CredentialStatus: Decodable, Equatable, Sendable {
    /// Per-reference results, including last-known observations after unsuccessful checks.
    public let status: CredentialStatusMetadata
    /// Shared Kotlin interpretation for display; native apps localize these semantic codes.
    public let presentation: CredentialStatusPresentation
    /// Time at which the shared implementation evaluated freshness.
    public let evaluatedAt: Date
}

/// Protocol meaning computed by the shared evaluator.
public enum CredentialStatusMeaning: String, Decodable, Sendable {
    /// The applicable standardized status value is clear.
    case clear = "CLEAR"
    /// The issuer reported revocation for a revocation purpose.
    case revoked = "REVOKED"
    /// The issuer reported Token Status List invalid status.
    case invalid = "INVALID"
    /// The issuer reported suspension.
    case suspended = "SUSPENDED"
    /// The issuer indicated that the credential should be refreshed.
    case refresh = "REFRESH"
    /// The purpose or value requires application-specific interpretation.
    case applicationSpecific = "APPLICATION_SPECIFIC"
    /// Reserved for future registration in the supported Token Status List draft.
    case reserved = "RESERVED"
    /// A meaning added by a newer shared evaluator.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// A compact state suitable for a standalone status section or a credential row.
public enum CredentialStatusKind: String, Decodable, Sendable {
    /// The credential supplied no status reference.
    case notProvided = "NOT_PROVIDED"
    /// A provided reference has not yet been checked.
    case notChecked = "NOT_CHECKED"
    /// The applicable revocation-purpose observations are clear.
    case notRevoked = "NOT_REVOKED"
    /// The applicable suspension observations are clear.
    case notSuspended = "NOT_SUSPENDED"
    /// The provided status references have authenticated observations.
    case checked = "CHECKED"
    /// An observation reports revocation, even if that evidence is now stale.
    case revoked = "REVOKED"
    /// An observation reports invalid status.
    case invalid = "INVALID"
    /// An observation reports suspension.
    case suspended = "SUSPENDED"
    /// Retained evidence has exceeded its freshness bounds.
    case outOfDate = "OUT_OF_DATE"
    /// No usable observation is available after a failed check.
    case unavailable = "UNAVAILABLE"
    /// Reference or list authentication could not be established.
    case unverified = "UNVERIFIED"
    /// A provided status method cannot be evaluated.
    case unsupported = "UNSUPPORTED"
    /// Only part of the supplied status references has usable evidence.
    case partial = "PARTIAL"
    /// An application-specific result needs review.
    case needsReview = "NEEDS_REVIEW"
    /// A presentation kind added by a newer evaluator.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// Home uses an icon only for an issue. Absence of an icon does not assert credential validity.
public enum CredentialStatusIssue: String, Decodable, Sendable {
    /// No issue glyph is needed; this does not assert overall validity.
    case none = "NONE"
    /// Show a revocation issue.
    case revoked = "REVOKED"
    /// Show an invalid-status issue.
    case invalid = "INVALID"
    /// Show a suspension issue.
    case suspended = "SUSPENDED"
    /// Show that retained evidence is out of date.
    case outOfDate = "OUT_OF_DATE"
    /// Show uncertainty or incomplete coverage.
    case uncertain = "UNCERTAIN"
    /// Show that a status method cannot be checked.
    case unsupported = "UNSUPPORTED"
    /// Show an application-specific result needing review.
    case needsReview = "NEEDS_REVIEW"
    /// Show a conservative issue for a future code.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// Freshness computed by Kotlin from authenticated bounds and the shared clock.
public enum CredentialStatusFreshness: String, Decodable, Sendable {
    /// Evidence is within the shared evaluator's authenticated freshness bounds.
    case fresh = "FRESH"
    /// Evidence is dated and no longer a current confirmation.
    case stale = "STALE"
    /// There is no established current freshness.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// Available manual action, subject to the embedding application's networking policy.
public enum CredentialStatusRefreshAction: String, Decodable, Sendable {
    /// No supported manual check is available for this result.
    case none = "NONE"
    /// A manual revalidation action is available.
    case check = "CHECK"
    /// A failed attempt can be retried, subject to host admission limits.
    case retry = "RETRY"
    /// The evaluator supplied an action this SDK does not recognize.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// Whether every supplied relevant reference has a usable observation.
public enum CredentialStatusCoverage: String, Decodable, Sendable {
    /// No supplied reference has a usable observation.
    case none = "NONE"
    /// Every supplied reference has an applicable observation; freshness is separate.
    case complete = "COMPLETE"
    /// Some references have observations and others remain unresolved or unsupported.
    case partial = "PARTIAL"
    /// Coverage cannot be interpreted by this SDK version.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// Shared presentation semantics; timestamps remain facts rather than a status-history stream.
public struct CredentialStatusPresentation: Decodable, Equatable, Sendable {
    /// Shared result wording category.
    public let kind: CredentialStatusKind
    /// Issue-only home-card decoration category.
    public let issue: CredentialStatusIssue
    /// Current evidence freshness at evaluatedAt.
    public let freshness: CredentialStatusFreshness
    /// Available manual action, subject to host policy.
    public let action: CredentialStatusRefreshAction
    /// Oldest successful check time among the retained observations.
    public let lastCheckedAt: Date?
    /// Latest failed attempt time, when a check has failed.
    public let lastAttemptAt: Date?
    /// Sanitized shared failure code, without exception text or list contents.
    public let failure: CredentialStatusCheckFailure?
    /// Distinct sorted purpose localization keys, derived with each protocol’s vocabulary.
    public let purposes: [String]
    /// Whether all supplied references have usable observations.
    public let coverage: CredentialStatusCoverage

    /// Opaque revision from the shared producer. Native clients carry it unchanged.
    public let advisoryRevision: String
    /// The primary adverse observation is historical rather than a current confirmation.
    public let lastKnown: Bool
    /// Producer-interpreted per-reference detail facts; views do not parse protocol values.
    public let references: [CredentialStatusReferencePresentation]
    /// A bounded cause when the reference container could not be enumerated.
    public let discoveryProblem: CredentialStatusDiscoveryProblem?

    private enum CodingKeys: String, CodingKey {
        case kind, issue, freshness, action, lastCheckedAt, lastAttemptAt, failure, purposes, coverage, advisoryRevision, lastKnown, references, discoveryProblem
    }
    /// Decodes the shared revision instead of synthesizing a native approximation.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = try c.decode(CredentialStatusKind.self, forKey: .kind)
        issue = try c.decode(CredentialStatusIssue.self, forKey: .issue)
        freshness = try c.decode(CredentialStatusFreshness.self, forKey: .freshness)
        action = try c.decode(CredentialStatusRefreshAction.self, forKey: .action)
        lastCheckedAt = try c.decodeIfPresent(Date.self, forKey: .lastCheckedAt)
        lastAttemptAt = try c.decodeIfPresent(Date.self, forKey: .lastAttemptAt)
        failure = try c.decodeIfPresent(CredentialStatusCheckFailure.self, forKey: .failure)
        purposes = try c.decode([String].self, forKey: .purposes)
        coverage = try c.decode(CredentialStatusCoverage.self, forKey: .coverage)
        advisoryRevision = try c.decode(String.self, forKey: .advisoryRevision)
        lastKnown = try c.decode(Bool.self, forKey: .lastKnown)
        references = try c.decodeIfPresent([CredentialStatusReferencePresentation].self, forKey: .references) ?? []
        discoveryProblem = try c.decodeIfPresent(CredentialStatusDiscoveryProblem.self, forKey: .discoveryProblem)
        guard !advisoryRevision.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              Set(references.map(\.referenceID)).count == references.count else { throw invalidStatusData(decoder) }
    }
}

/// Shared detail facts for one reference. These are rendering data, not independent evidence.
public struct CredentialStatusReferencePresentation: Decodable, Equatable, Sendable {
    /// Opaque reference identity.
    public let referenceID: String
    /// Shared protocol code for a supported reference.
    public let protocolName: String?
    /// Method name for an unsupported reference.
    public let method: String?
    /// Protocol-aware purpose localization key; extension names remain in typed metadata.
    public let purpose: String?
    /// Protocol-aware observed meaning.
    public let result: CredentialStatusMeaning?
    /// Original successful check time.
    public let checkedAt: Date?
    /// Exclusive freshness deadline.
    public let freshUntil: Date?
    /// Signed or authorization validity bound.
    public let validUntil: Date?
    /// Latest failed/conflicting attempt time.
    public let attemptedAt: Date?
    /// Typed latest check failure.
    public let failure: CredentialStatusCheckFailure?
    /// Shared irreversible-conflict fact.
    public let conflict: Bool
    /// Diagnostic for an identifiable malformed reference.
    public let problem: CredentialStatusReferenceProblem?
    /// Per-reference freshness computed by the shared evaluator, never recomputed by native views.
    public let freshness: CredentialStatusFreshness
    private enum CodingKeys: String, CodingKey {
        case referenceID = "referenceId", protocolName = "protocol", method, purpose, result,
            checkedAt, freshUntil, validUntil, attemptedAt, failure, conflict, problem, freshness
    }
    /// Decodes shared facts while retaining the distinction between missing evidence and clear.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        referenceID = try c.decode(String.self, forKey: .referenceID)
        protocolName = try c.decodeIfPresent(String.self, forKey: .protocolName)
        method = try c.decodeIfPresent(String.self, forKey: .method)
        purpose = try c.decodeIfPresent(String.self, forKey: .purpose)
        result = try c.decodeIfPresent(CredentialStatusMeaning.self, forKey: .result)
        checkedAt = try c.decodeIfPresent(Date.self, forKey: .checkedAt)
        freshUntil = try c.decodeIfPresent(Date.self, forKey: .freshUntil)
        validUntil = try c.decodeIfPresent(Date.self, forKey: .validUntil)
        attemptedAt = try c.decodeIfPresent(Date.self, forKey: .attemptedAt)
        failure = try c.decodeIfPresent(CredentialStatusCheckFailure.self, forKey: .failure)
        conflict = try c.decodeIfPresent(Bool.self, forKey: .conflict) ?? false
        problem = try c.decodeIfPresent(CredentialStatusReferenceProblem.self, forKey: .problem)
        freshness = try c.decodeIfPresent(CredentialStatusFreshness.self, forKey: .freshness) ?? .unknown
        guard !referenceID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw invalidStatusData(decoder) }
    }
}

/// Current local status for the wallet, delivered initially and when results/freshness change.
public struct CredentialStatusSnapshot: Equatable, Sendable {
    /// Time at which the shared evaluator assessed this snapshot.
    public let evaluatedAt: Date
    /// Store-scoped local status records, without credential artwork.
    public let credentials: [CredentialStatusRecord]
    /// Credential IDs with an active refresh owned by this wallet instance.
    public let refreshingCredentialIDs: Set<String>
    /// Earliest useful IfDue check; the app decides whether its foreground policy permits scheduling.
    public let nextRefreshAt: Date?

    init(evaluatedAt: Date, credentials: [CredentialStatusRecord], refreshingCredentialIDs: Set<String>, nextRefreshAt: Date? = nil) {
        self.evaluatedAt = evaluatedAt; self.credentials = credentials
        self.refreshingCredentialIDs = refreshingCredentialIDs; self.nextRefreshAt = nextRefreshAt
    }
}

/// A store-scoped record; mobile wallets currently use store index zero.
public struct CredentialStatusRecord: Equatable, Sendable {
    /// Stored credential identifier within the indicated store.
    public let credentialID: String
    /// Index of the owning configured credential store.
    public let storeIndex: Int
    /// The local typed status and its evaluated presentation.
    public let value: CredentialStatus
}

/// Outcome of an explicit check for one wallet-owned credential.
public enum CredentialStatusRefreshOutcome: String, Decodable, Sendable {
    /// A check completed; inspect metadata for per-reference failures.
    case completed = "COMPLETED"
    /// No check was needed or supported; this is not proof of fresh clear status.
    case unchanged = "UNCHANGED"
    /// The batch or host admission limits deferred this record.
    case deferred = "DEFERRED"
    /// The selected record was removed or replaced before a result could commit.
    case removed = "REMOVED"
    /// An outcome added by a newer evaluator.
    case unknown = "UNKNOWN"
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws { self = try decodeStatusCode(Self.self, from: decoder, fallback: .unknown) }
}

/// Bounded refresh results include deferred work and preserve metadata for per-credential failures.
public struct CredentialStatusRefreshResult: Decodable, Equatable, Sendable {
    /// Store-scoped credential selected for this check.
    public let reference: CredentialStatusReference
    /// Per-credential admission or completion outcome.
    public let outcome: CredentialStatusRefreshOutcome
    /// Metadata after the attempt, including unsupported or failed references.
    public let status: CredentialStatusMetadata
}

/// A credential identifier within its wallet's configured store namespace.
public struct CredentialStatusReference: Decodable, Hashable, Sendable {
    /// Index of the owning configured credential store.
    public let storeIndex: Int
    /// Stored credential ID within that store.
    public let credentialID: String
    private enum CodingKeys: String, CodingKey { case storeIndex, credentialID = "credentialId" }
    /// Validates the store-scoped identity received from the shared producer.
    /// - Parameter decoder: Decoder positioned at the shared credential-status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        storeIndex = try c.decode(Int.self, forKey: .storeIndex)
        credentialID = try c.decode(String.self, forKey: .credentialID)
        guard storeIndex >= 0, !credentialID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw invalidStatusData(decoder) }
    }
}

private func decodeStatusCode<T: RawRepresentable>(_ type: T.Type, from decoder: Decoder, fallback: T) throws -> T where T.RawValue == String {
    T(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? fallback
}

func decodeCredentialStatus<T: Decodable>(_ type: T.Type, json: String) throws -> T {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .custom { decoder in
        let value = try decoder.singleValueContainer().decode(String.self)
        guard let date = parseWalletISO8601Date(value) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Invalid status timestamp"))
        }
        return date
    }
    do { return try decoder.decode(type, from: Data(json.utf8)) }
    catch { throw WalletError.internalFailure("Invalid credential status data") }
}

private struct StatusSnapshotWire: Decodable {
    let evaluatedAt: Date
    let credentials: [Record]
    let refreshing: Set<CredentialStatusReference>
    let nextRefreshAt: Date?
    struct Record: Decodable {
        let reference: CredentialStatusReference
        let status: CredentialStatusMetadata
        let presentation: CredentialStatusPresentation
    }
}

func decodeCredentialStatusSnapshot(_ json: String) throws -> CredentialStatusSnapshot {
    let wire = try decodeCredentialStatus(StatusSnapshotWire.self, json: json)
    guard Set(wire.credentials.map(\.reference)).count == wire.credentials.count else {
        throw WalletError.internalFailure("Invalid credential status data")
    }
    return CredentialStatusSnapshot(evaluatedAt: wire.evaluatedAt, credentials: wire.credentials.map {
        CredentialStatusRecord(credentialID: $0.reference.credentialID, storeIndex: $0.reference.storeIndex,
            value: CredentialStatus(status: $0.status, presentation: $0.presentation, evaluatedAt: wire.evaluatedAt))
    }, refreshingCredentialIDs: Set(wire.refreshing.map(\.credentialID)), nextRefreshAt: wire.nextRefreshAt)
}
