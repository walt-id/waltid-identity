import Foundation
import WalletSDK

/// Layout facts only. The shared producer owns status, coverage, freshness and eligibility.
enum CredentialStatusFact: Equatable, Identifiable {
    case scope([String])
    case time(TimeKind, Date, referenceID: String? = nil)
    case note(Note)
    case outcome(CredentialStatusReferencePresentation)

    enum TimeKind: String {
        case lastChecked = "last-checked", latestAttempt = "latest-attempt"
        case validFrom = "valid-from", retryFrom = "retry-from"
    }

    // These bounded keys live in the package's existing en/de .strings resources.
    enum Note: String {
        case readUnavailable = "Status information is unavailable. Previous results are retained."
        case stale = "Status outdated"
        case partial = "Partly checked"
        case latestCheckFailed = "Latest check failed"
        case conflictingUpdate = "Conflicting update"
        case expired = "The credential has expired. Online status checks were skipped."
        case notYetValid = "The credential is not yet valid. Its status can be checked from its valid-from time."
        case referenceAuthentication = "The credential’s status reference could not be authenticated. No status list was requested."
        case invalidReference = "The credential contains an invalid status reference."
        case rateLimited = "The status service asked to wait. Try again later."
        case resourceLimit = "This status check exceeds the wallet’s processing limits."
        case unverified = "The status information could not be authenticated. Any previous result is retained."
        case incomplete = "The latest check could not complete. Previous results keep their original check time."
        case unsupported = "This wallet cannot check this status method. The recipient may support it."
        case discoveryFailed = "The credential’s status references could not be read."
        case conflictingReference = "The credential has conflicting status reference parameters."
        case conflict = "The issuer returned a status inconsistent with an earlier irreversible result. The earlier result is retained."
        case customValue = "Custom status result"
        case reservedValue = "Reserved status value"
        case update = "Credential update signalled"
    }

    var id: String {
        switch self {
        case .scope: "scope"
        case .time(let kind, _, let referenceID): referenceID.map { "\($0)-\(kind.rawValue)" } ?? kind.rawValue
        case .note(let note): "note-\(note.rawValue)"
        case .outcome(let reference): "outcome-\(reference.referenceID)"
        }
    }
}

struct CredentialStatusFactGroup: Identifiable {
    let id: String
    let facts: [CredentialStatusFact]
}

struct CredentialStatusFacts {
    let main: [CredentialStatusFact]
    let extra: [CredentialStatusFactGroup]
}

extension CredentialStatusPresentation {
    /// Simple results stay flat. Disclose only facts not already visible in the main section.
    func facts(readUnavailable: Bool = false) -> CredentialStatusFacts {
        let observedTimes = Set(references.compactMap(\.checkedAt))
        let checked = observedTimes.count == 1 ? observedTimes.first
            : (observedTimes.isEmpty && kind != .notProvided ? lastCheckedAt : nil)
        let observed = checked != nil || !observedTimes.isEmpty
        let attemptTimes = Set(references.compactMap(\.attemptedAt))
        let attempted = observed ? nil : (attemptTimes.count == 1 ? attemptTimes.first : (attemptTimes.isEmpty ? lastAttemptAt : nil))
        let recoveries = distinctFacts(references.compactMap { $0.recoveryFact() })
        let recovery = recoveries.count == 1 && action == .none ? recoveries.first : nil
        let single = references.count == 1 ? references.first : nil
        let cause = single?.failure ?? failure
        let mainCause = cause.flatMap { failure in
            (references.count > 1 ? action == .none : !observed || ["CREDENTIAL_EXPIRED", "NOT_YET_VALID"].contains(failure.code))
                ? failure : nil
        }
        var main: [CredentialStatusFact] = []
        if hasPositiveStatus && !purposes.isEmpty { main.append(.scope(purposes)) }
        if let checked { main.append(.time(.lastChecked, checked)) }
        if let attempted { main.append(.time(.latestAttempt, attempted)) }
        if readUnavailable { main.append(.note(.readUnavailable)) }
        if freshness == .stale && kind != .outOfDate { main.append(.note(.stale)) }
        if coverage == .partial && kind != .partial { main.append(.note(.partial)) }
        if references.contains(where: \.conflict) { main.append(.note(.conflictingUpdate)) }
        else if observed && mainCause == nil && (failure != nil || references.contains { $0.failure != nil }) {
            main.append(.note(.latestCheckFailed))
        }
        if let mainCause { main.append(.note(failureNote(mainCause))) }
        if let problem = discoveryProblem { main.append(.note(problem == .resourceLimit ? .resourceLimit : .discoveryFailed)) }
        if let problem = single?.problem { main.append(.note(referenceNote(problem))) }
        if single?.method != nil && mainCause == nil { main.append(.note(.unsupported)) }
        switch single?.result {
        case .applicationSpecific: main.append(.note(.customValue))
        case .reserved: main.append(.note(.reservedValue))
        case .refresh: main.append(.note(.update))
        default: break
        }
        if let recovery { main.append(recovery) }

        var extra: [CredentialStatusFactGroup] = references.compactMap { reference in
            var facts: [CredentialStatusFact] = []
            if references.count > 1 {
                facts.append(.outcome(reference))
                if let date = reference.checkedAt, date != checked { facts.append(.time(.lastChecked, date, referenceID: reference.referenceID)) }
                if let problem = reference.problem { facts.append(.note(referenceNote(problem))) }
            }
            if let failure = reference.failure, references.count > 1 || failure.code != mainCause?.code {
                facts.append(.note(failureNote(failure)))
            }
            let distinctAttempt = reference.failure != nil || reference.conflict || reference.attemptedAt != reference.checkedAt
            if distinctAttempt, let date = reference.attemptedAt, date != attempted {
                facts.append(.time(.latestAttempt, date, referenceID: references.count == 1 ? nil : reference.referenceID))
            }
            if reference.conflict { facts.append(.note(.conflict)) }
            if let next = reference.recoveryFact(), next != recovery,
               case .time(let kind, let date, _) = next {
                facts.append(.time(kind, date, referenceID: reference.referenceID))
            }
            return facts.isEmpty ? nil : CredentialStatusFactGroup(id: reference.referenceID, facts: distinctFacts(facts))
        }
        if references.isEmpty && observed, let cause {
            var facts: [CredentialStatusFact] = []
            if cause.code != mainCause?.code { facts.append(.note(failureNote(cause))) }
            if let lastAttemptAt { facts.append(.time(.latestAttempt, lastAttemptAt)) }
            if !facts.isEmpty { extra.append(CredentialStatusFactGroup(id: "latest-attempt", facts: facts)) }
        }
        return CredentialStatusFacts(main: distinctFacts(main), extra: extra)
    }
}

private extension CredentialStatusReferencePresentation {
    func recoveryFact() -> CredentialStatusFact? {
        switch failure {
        case .notYetValid(let date): .time(.validFrom, date)
        case .rateLimited(let date): date.map { .time(.retryFrom, $0) }
        default: nil
        }
    }
}

private func distinctFacts(_ facts: [CredentialStatusFact]) -> [CredentialStatusFact] {
    facts.reduce(into: []) { if !$0.contains($1) { $0.append($1) } }
}

private func referenceNote(_ problem: CredentialStatusReferenceProblem) -> CredentialStatusFact.Note {
    switch problem {
    case .resourceLimit: .resourceLimit
    case .conflictingReference: .conflictingReference
    default: .invalidReference
    }
}

private func failureNote(_ failure: CredentialStatusCheckFailure) -> CredentialStatusFact.Note {
    switch failure {
    case .notYetValid: .notYetValid
    case .rateLimited: .rateLimited
    case .reason(let reason):
        switch reason {
        case .credentialExpired: .expired
        case .referenceUnauthenticated: .referenceAuthentication
        case .resourceLimit: .resourceLimit
        case .unsupportedProof: .unsupported
        case .invalidSignature, .unauthorizedSigner, .invalidList, .invalidValidity: .unverified
        case .transport, .timeout: .incomplete
        }
    }
}
