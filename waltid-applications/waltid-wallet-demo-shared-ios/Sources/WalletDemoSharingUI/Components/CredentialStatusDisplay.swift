import Foundation
import WalletSDK

/// Read availability belongs to the UI and never changes the issuer's retained evidence.
public enum CredentialStatusDisplay {
    case loading(retained: CredentialStatusPresentation?)
    case current(CredentialStatusPresentation)
    case unavailable(retained: CredentialStatusPresentation?)

    var presentation: CredentialStatusPresentation? {
        switch self { case .current(let value): value; case .loading(let value), .unavailable(let value): value }
    }
    var hasStatusIssue: Bool {
        switch self { case .current(let value): value.hasStatusIssue; case .loading(let value): value?.hasStatusIssue == true; case .unavailable: true }
    }
    var hasPositiveStatus: Bool {
        switch self { case .current(let value): value.hasPositiveStatus; case .loading, .unavailable: false }
    }
    var statusTitle: String {
        switch self {
        case .current(let value): return value.statusTitle
        case .loading(let value):
            guard let value, value.hasStatusIssue else { return String(localized: "Loading status…", bundle: .module) }
            return value.hasAdverseStatus ? CredentialStatusDisplay.unavailable(retained: value).statusTitle : value.statusTitle
        case .unavailable(let value):
            switch value?.issue {
            case .revoked: return String(localized: "Last known: revoked", bundle: .module)
            case .invalid: return String(localized: "Last known: invalid", bundle: .module)
            case .suspended: return String(localized: "Last known: suspended", bundle: .module)
            default: return String(localized: "Status unavailable", bundle: .module)
            }
        }
    }
    var isUnavailable: Bool { if case .unavailable = self { true } else { false } }
    var isLoading: Bool { if case .loading = self { true } else { false } }
}

public extension CredentialStatusUIContext {
    func displayStatus(for credentialID: String) -> CredentialStatusDisplay {
        if readState == .loading { .loading(retained: values[credentialID]?.presentation) }
        else if available, let value = values[credentialID] { .current(value.presentation) }
        else { .unavailable(retained: values[credentialID]?.presentation) }
    }
}

public extension CredentialStatusPresentation {
    var statusTitle: String {
        switch kind {
        case .notProvided: String(localized: "Status not provided", bundle: .module)
        case .notChecked: String(localized: "Not checked", bundle: .module)
        case .notRevoked: String(localized: "Not revoked", bundle: .module)
        case .notSuspended: String(localized: "Not suspended", bundle: .module)
        case .checked: String(localized: "Status checked", bundle: .module)
        case .revoked: lastKnown ? String(localized: "Last known: revoked", bundle: .module) : String(localized: "Revoked", bundle: .module)
        case .invalid: lastKnown ? String(localized: "Last known: invalid", bundle: .module) : String(localized: "Invalid", bundle: .module)
        case .suspended: lastKnown ? String(localized: "Last known: suspended", bundle: .module) : String(localized: "Suspended", bundle: .module)
        case .outOfDate: String(localized: "Status outdated", bundle: .module)
        case .unverified: String(localized: "Could not verify", bundle: .module)
        case .unsupported: String(localized: "Check unsupported", bundle: .module)
        case .partial: String(localized: "Partly checked", bundle: .module)
        case .needsReview: String(localized: "Status needs review", bundle: .module)
        case .unavailable, .unknown: String(localized: "Status unavailable", bundle: .module)
        }
    }
    var hasStatusIssue: Bool { issue != .none || kind == .unknown || coverage == .unknown || action == .unknown }
    var hasAdverseStatus: Bool { [.revoked, .invalid, .suspended].contains(issue) }
    var hasPositiveStatus: Bool {
        !hasStatusIssue && freshness == .fresh && coverage == .complete && [.notRevoked, .notSuspended, .checked].contains(kind)
    }
}
