import SwiftUI
import WalletSDK

/// Stored details and expanded sharing/payment rows use the same facts body.
struct CredentialStatusFactsContent: View {
    let credentialID: String
    var main: [CredentialStatusFact] = []
    var extra: [CredentialStatusFactGroup] = []

    var body: some View {
        if !main.isEmpty { rows(main) }
        ForEach(extra) { group in
            Divider().padding(.horizontal, 16)
            rows(group.facts)
        }
    }

    private func rows(_ facts: [CredentialStatusFact]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(facts) { fact in
                switch fact {
                case .scope(let purposes):
                    Text("Checks: \(purposes.map(purposeTitle).joined(separator: ", "))", bundle: .module)
                        .font(.caption).foregroundStyle(.secondary)
                case .note(let note):
                    Text(LocalizedStringKey(note.rawValue), bundle: .module).font(.footnote).foregroundStyle(.secondary)
                case .time(let kind, let date, _):
                    CredentialDataRow(timeTitle(kind)) {
                        WalletInstantText(date, font: .caption).foregroundStyle(.secondary)
                            .accessibilityIdentifier("credential-status-\(fact.id)-\(credentialID)")
                    }
                case .outcome(let reference):
                    CredentialDataRow(reference.purpose.map(purposeTitle) ?? (reference.protocolName == "TOKEN_STATUS_LIST"
                        ? String(localized: "Token status", bundle: .module) : String(localized: "Status reference", bundle: .module))) {
                        let result = resultTitle(reference)
                        Text(reference.freshness == .stale ? "\(result) · \(String(localized: "Status outdated", bundle: .module))" : result)
                            .font(.subheadline)
                    }
                }
            }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func timeTitle(_ kind: CredentialStatusFact.TimeKind) -> String {
        switch kind {
        case .lastChecked: String(localized: "Last checked", bundle: .module)
        case .latestAttempt: String(localized: "Latest attempt", bundle: .module)
        case .validFrom: String(localized: "Check available from", bundle: .module)
        case .retryFrom: String(localized: "Retry available from", bundle: .module)
        }
    }

    private func purposeTitle(_ purpose: String) -> String {
        switch purpose {
        case "revocation": String(localized: "Revocation", bundle: .module)
        case "suspension": String(localized: "Suspension", bundle: .module)
        case "refresh": String(localized: "Credential update", bundle: .module)
        case "token": String(localized: "Token status", bundle: .module)
        case "message": String(localized: "Status message", bundle: .module)
        default: String(localized: "Other", bundle: .module)
        }
    }

    private func resultTitle(_ reference: CredentialStatusReferencePresentation) -> String {
        switch reference.result {
        case .clear: String(localized: "No issue reported", bundle: .module)
        case .revoked: String(localized: "Revoked", bundle: .module)
        case .invalid: String(localized: "Invalid", bundle: .module)
        case .suspended: String(localized: "Suspended", bundle: .module)
        case .refresh: String(localized: "Credential update signalled", bundle: .module)
        case .applicationSpecific: String(localized: "Custom status result", bundle: .module)
        case .reserved: String(localized: "Reserved status value", bundle: .module)
        case .unknown: String(localized: "Status unavailable", bundle: .module)
        case nil:
            if reference.problem == .malformed { String(localized: "Invalid reference", bundle: .module) }
            else if reference.problem != nil { String(localized: "Status unavailable", bundle: .module) }
            else if reference.method != nil { String(localized: "Check unsupported", bundle: .module) }
            else if reference.failure != nil { String(localized: "Status unavailable", bundle: .module) }
            else { String(localized: "Not checked", bundle: .module) }
        }
    }
}
