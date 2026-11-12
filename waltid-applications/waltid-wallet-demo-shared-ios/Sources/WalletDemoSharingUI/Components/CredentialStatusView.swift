import SwiftUI
import WalletSDK

/// Status is wallet chrome, independent of issuer artwork and shared claims.
public struct CredentialStatusUIContext {
    public var values: [String: CredentialStatus]
    public var refreshing: Set<String>
    public var check: ((String) -> Void)?
    public init(values: [String: CredentialStatus] = [:], refreshing: Set<String> = [], check: ((String) -> Void)? = nil) {
        self.values = values; self.refreshing = refreshing; self.check = check
    }
}
private struct CredentialStatusUIKey: EnvironmentKey { static let defaultValue = CredentialStatusUIContext() }
public extension EnvironmentValues {
    var credentialStatusUI: CredentialStatusUIContext {
        get { self[CredentialStatusUIKey.self] }
        set { self[CredentialStatusUIKey.self] = newValue }
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
        case .revoked: String(localized: "Revoked", bundle: .module)
        case .invalid: String(localized: "Invalid", bundle: .module)
        case .suspended: String(localized: "Suspended", bundle: .module)
        case .outOfDate: String(localized: "Status out of date", bundle: .module)
        case .unverified: String(localized: "Status could not be verified", bundle: .module)
        case .unsupported: String(localized: "Status method not supported", bundle: .module)
        case .partial: String(localized: "Some status checks are unavailable", bundle: .module)
        case .needsReview: String(localized: "Status needs review", bundle: .module)
        case .unavailable, .unknown: String(localized: "Status unavailable", bundle: .module)
        }
    }
    var hasStatusIssue: Bool { issue != .none }
    var hasAdverseStatus: Bool { [.revoked, .invalid, .suspended].contains(issue) }
}

public struct CredentialStatusIcon: View {
    let presentation: CredentialStatusPresentation
    @Environment(\.colorScheme) private var colorScheme
    public init(_ presentation: CredentialStatusPresentation) { self.presentation = presentation }
    public var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle([.revoked, .invalid].contains(presentation.issue) ? Color.red :
                (colorScheme == .dark ? Color(red: 1, green: 0.8, blue: 0.5) : Color(red: 0.58, green: 0.37, blue: 0)))
            .frame(width: 28, height: 28)
            .background(Color(uiColor: .systemBackground), in: Circle())
            .accessibilityHidden(true)
    }
    private var symbol: String {
        switch presentation.issue {
        case .revoked, .invalid: "xmark.circle.fill"
        case .suspended: "pause.circle.fill"
        case .outOfDate: "clock.fill"
        default: "exclamationmark.triangle.fill"
        }
    }
}

private struct CredentialStatusDetailsInitiallyExpandedKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    var credentialStatusDetailsInitiallyExpanded: Bool {
        get { self[CredentialStatusDetailsInitiallyExpandedKey.self] }
        set { self[CredentialStatusDetailsInitiallyExpandedKey.self] = newValue }
    }
}

public struct CredentialStatusSection: View {
    let credentialID: String
    let compact: Bool
    @Environment(\.credentialStatusUI) private var statusUI
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone
    @Environment(\.credentialStatusDetailsInitiallyExpanded) private var detailsInitiallyExpanded
    @State private var expanded = false
    public init(credentialID: String, compact: Bool = false) { self.credentialID = credentialID; self.compact = compact }
    public var body: some View {
        if let status = statusUI.values[credentialID] {
            if compact { content(status).padding(12).onAppear { expanded = detailsInitiallyExpanded } }
            else { WalletSection(String(localized: "Credential status", bundle: .module)) { content(status).padding(16) }
                .accessibilityIdentifier("credential-status-\(credentialID)")
                .onAppear { expanded = detailsInitiallyExpanded } }
        }
    }
    @ViewBuilder private func content(_ status: CredentialStatus) -> some View {
        let value = status.presentation
        if compact {
            DisclosureGroup(isExpanded: $expanded) { detailContents(status).padding(.top, 8) } label: {
                HStack(spacing: 8) {
                    if value.hasStatusIssue { CredentialStatusIcon(value) }
                    Text(compactTitle(value)).font(.subheadline).foregroundStyle(Color.primary)
                    Spacer(minLength: 0)
                    Text("Details", bundle: .module).font(.caption)
                }.frame(minHeight: 44)
            }.accessibilityIdentifier("credential-status-details-\(credentialID)")
        } else {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 8) {
                    if value.hasStatusIssue { CredentialStatusIcon(value) }
                    Text(value.statusTitle).font(.body).frame(maxWidth: .infinity, alignment: .leading)
                    if value.action != .none, let check = statusUI.check {
                        Button(statusUI.refreshing.contains(credentialID) ? String(localized: "Checking…", bundle: .module) :
                            (value.action == .retry ? String(localized: "Retry", bundle: .module) : String(localized: "Check now", bundle: .module))) { check(credentialID) }
                            .disabled(statusUI.refreshing.contains(credentialID)).frame(minHeight: 44)
                            .accessibilityIdentifier("credential-status-check-\(credentialID)")
                    }
                }
                checkTime(value)
                if value.freshness == .stale { Text("The previous result is out of date.", bundle: .module).font(.footnote) }
                if value.coverage == .partial { Text("Some status references could not be checked.", bundle: .module).font(.footnote) }
                if value.hasAdverseStatus { Text("The recipient may reject this credential.", bundle: .module).font(.footnote) }
                DisclosureGroup(isExpanded: $expanded) { detailContents(status).padding(.top, 8) }
                    label: { Text("Details", bundle: .module).font(.subheadline) }
                    .accessibilityIdentifier("credential-status-details-\(credentialID)")
            }
        }
    }

    private func compactTitle(_ value: CredentialStatusPresentation) -> String {
        value.statusTitle + (value.freshness == .stale && value.kind != .outOfDate ? " · " + String(localized: "out of date", bundle: .module) : "") +
            (value.coverage == .partial && value.kind != .partial ? " · " + String(localized: "partial check", bundle: .module) : "")
    }

    private func detailContents(_ status: CredentialStatus) -> some View {
        let value = status.presentation
        let purposes = Set(value.purposes.map(purposeTitle)).sorted().joined(separator: ", ")
        return VStack(alignment: .leading, spacing: 8) {
            Text(explanation(value)).font(.footnote)
            if !purposes.isEmpty {
                Text("Checked purposes: \(purposes)", bundle: .module).font(.footnote)
            }
            if compact, value.coverage == .partial { Text("Some status references could not be checked.", bundle: .module).font(.footnote) }
            if compact { checkTime(value) }
            if let attempt = value.lastAttemptAt {
                Text("Last attempt: \(WalletInstant(attempt).formatted(timeZone: timeZone, locale: locale).fullText)", bundle: .module).font(.footnote)
            }
            if let failure = value.failure { Text(failureExplanation(failure)).font(.footnote) }
            Text("Checks contact the issuer’s status service. Credential contents and status indexes are not sent in the request.", bundle: .module).font(.footnote)
            if compact { Text("Status information is shown for your review and is not an additional claim to share.", bundle: .module).font(.footnote) }
        }
    }

    private func purposeTitle(_ purpose: String) -> String {
        switch purpose {
        case "revocation": String(localized: "Revocation", bundle: .module)
        case "suspension": String(localized: "Suspension", bundle: .module)
        case "refresh": String(localized: "Credential update", bundle: .module)
        case "token": String(localized: "Token status", bundle: .module)
        default: String(localized: "Other", bundle: .module)
        }
    }

    private func failureExplanation(_ reason: String) -> String {
        switch reason {
        case "CREDENTIAL_EXPIRED": String(localized: "The credential is expired or not yet valid. Online status checks were skipped.", bundle: .module)
        case "REFERENCE_UNAUTHENTICATED": String(localized: "The credential’s status reference could not be authenticated. No status list was requested.", bundle: .module)
        case "REFERENCE_MALFORMED": String(localized: "The credential contains an invalid status reference.", bundle: .module)
        case "RATE_LIMITED": String(localized: "The status service asked to wait. Try again later.", bundle: .module)
        case "RESOURCE_LIMIT": String(localized: "This status check exceeds the wallet’s processing limits.", bundle: .module)
        case "CONFLICTING_IRREVERSIBLE_STATUS": String(localized: "The issuer returned a status inconsistent with an earlier irreversible result. The earlier result is retained.", bundle: .module)
        case "UNSUPPORTED_METHOD", "UNSUPPORTED_PROOF": String(localized: "This wallet cannot check this status method. The recipient may support it.", bundle: .module)
        case "INVALID_SIGNATURE", "UNAUTHORIZED_SIGNER", "INVALID_LIST", "INVALID_VALIDITY": String(localized: "The status information could not be authenticated. Any previous result is retained.", bundle: .module)
        case "DISABLED": String(localized: "Status checking is disabled by the wallet host.", bundle: .module)
        default: String(localized: "The latest check could not complete. Previous results keep their original check time.", bundle: .module)
        }
    }
    @ViewBuilder private func checkTime(_ value: CredentialStatusPresentation) -> some View {
        if let date = value.lastCheckedAt { Text("Last checked: \(WalletInstant(date).formatted(timeZone: timeZone, locale: locale).fullText)", bundle: .module).font(.footnote).foregroundStyle(.secondary) }
        else { Text("No successful check yet", bundle: .module).font(.footnote).foregroundStyle(.secondary) }
    }
    private func explanation(_ value: CredentialStatusPresentation) -> String {
        switch value.kind {
        case .notProvided: String(localized: "The credential contains no status reference. This does not establish that it is valid.", bundle: .module)
        case .notChecked: String(localized: "The credential has a status reference that has not been checked yet.", bundle: .module)
        case .unsupported: String(localized: "This wallet cannot check this status method. The recipient may support it.", bundle: .module)
        case .unverified: String(localized: "The status information could not be authenticated. Any previous result is retained.", bundle: .module)
        default: String(localized: "This check only reports the issuer’s status information. It does not confirm every aspect of the credential’s validity. The recipient performs its own checks.", bundle: .module)
        }
    }
}

public struct CredentialStatusAdvisory: View {
    public let statuses: [(String, CredentialStatusPresentation)]
    public init(_ statuses: [(String, CredentialStatusPresentation)]) { self.statuses = statuses }
    public var body: some View {
        let issues = statuses.filter { $0.1.hasStatusIssue }
        if !issues.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("Status issues affect the selected credentials below. The recipient may reject them.", bundle: .module)
                ForEach(Array(issues.enumerated()), id: \.offset) { _, item in Text("\(item.0): \(item.1.statusTitle)") }
            }.font(.footnote).frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("sharing-credential-status-advisory")
        }
    }
}
