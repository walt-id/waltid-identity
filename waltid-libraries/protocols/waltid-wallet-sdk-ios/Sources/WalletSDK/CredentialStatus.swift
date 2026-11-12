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

/// Local metadata. An empty collection means that no status reference was supplied.
public struct CredentialStatusMetadata: Decodable, Equatable, Sendable {
    /// Per-reference results; an empty collection means no status was provided.
    public let entries: [CredentialStatusEntry]
    private enum CodingKeys: String, CodingKey { case entries }
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws {
        entries = try decoder.container(keyedBy: CodingKeys.self).decodeIfPresent([CredentialStatusEntry].self, forKey: .entries) ?? []
    }
}

/// Mutually exclusive results for one credential-status reference.
public enum CredentialStatusEntry: Decodable, Equatable, Sendable {
    /// A supported reference that has not yet been checked.
    case notChecked(referenceID: String, method: String, purpose: String)
    /// A reference whose method or proof this wallet cannot evaluate.
    case unsupported(referenceID: String, method: String)
    /// An authenticated, applicable issuer observation.
    case observed(CredentialStatusObservation)
    /// A failed attempt, optionally retaining the dated previous authenticated observation.
    case unavailable(referenceID: String, reason: String, attemptedAt: Date, previous: CredentialStatusObservation?)
    /// A future entry type that this SDK cannot interpret.
    case unknown

    private enum CodingKeys: String, CodingKey { case type, referenceId, `protocol`, purpose, method, observation, reason, attemptedAt, previous }
    /// Decodes shared status data, retaining unknown semantic codes conservatively.
    /// - Parameter decoder: Decoder positioned at the shared status value.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "not_checked": self = .notChecked(referenceID: try c.decode(String.self, forKey: .referenceId),
            method: try c.decode(String.self, forKey: .protocol), purpose: try c.decode(String.self, forKey: .purpose))
        case "unsupported": self = .unsupported(referenceID: try c.decode(String.self, forKey: .referenceId),
            method: try c.decode(String.self, forKey: .method))
        case "observed": self = .observed(try c.decode(CredentialStatusObservation.self, forKey: .observation))
        case "unavailable": self = .unavailable(referenceID: try c.decode(String.self, forKey: .referenceId),
            reason: try c.decode(String.self, forKey: .reason), attemptedAt: try c.decode(Date.self, forKey: .attemptedAt),
            previous: try c.decodeIfPresent(CredentialStatusObservation.self, forKey: .previous))
        default: self = .unknown
        }
    }

    /// The previous successful result remains available after a failed update.
    public var lastObservation: CredentialStatusObservation? {
        switch self {
        case .observed(let value): value
        case .unavailable(_, _, _, let previous): previous
        default: nil
        }
    }
}

/// Authenticated evidence without a status-list URL, credential index or signed token.
public struct CredentialStatusObservation: Decodable, Equatable, Sendable {
    /// Opaque reference identity, excluding the list URI and credential index.
    public let referenceID: String
    /// Shared protocol code, preserved as a string for future protocols.
    public let method: String
    /// The applicable status purpose, such as revocation, suspension or token status.
    public let purpose: String
    /// Authenticated status value; use meaning for interpretation.
    public let value: UInt8
    /// Purpose-aware meaning computed by the shared evaluator.
    public let meaning: CredentialStatusMeaning
    /// Time of the successful authenticated check.
    public let checkedAt: Date
    /// Exclusive deadline for treating this observation as current.
    public let freshUntil: Date
    /// Optional signed or certificate validity bound, independent of HTTP revalidation.
    public let validUntil: Date?
    private enum CodingKeys: String, CodingKey {
        case referenceID = "referenceId", method = "protocol", purpose, value, meaning, checkedAt, freshUntil, validUntil
    }
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
    /// The applicable revocation or token-status observations are clear.
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
    public let failure: String?
    /// Distinct sorted purposes covered by retained observations.
    public let purposes: [String]
    /// Whether all supplied references have usable observations.
    public let coverage: CredentialStatusCoverage

    /// Compare what was actually rendered before confirmation; check-time-only updates are excluded.
    public var advisoryRevision: String {
        "\(kind.rawValue)|\(issue.rawValue)|\(freshness.rawValue)|\(coverage.rawValue)|\(purposes.joined(separator: ","))"
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
    return CredentialStatusSnapshot(evaluatedAt: wire.evaluatedAt, credentials: wire.credentials.map {
        CredentialStatusRecord(credentialID: $0.reference.credentialID, storeIndex: $0.reference.storeIndex,
            value: CredentialStatus(status: $0.status, presentation: $0.presentation, evaluatedAt: wire.evaluatedAt))
    }, refreshingCredentialIDs: Set(wire.refreshing.map(\.credentialID)), nextRefreshAt: wire.nextRefreshAt)
}
