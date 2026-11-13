import SwiftUI
import WalletSDK

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
    @Environment(\.credentialStatusDetailsInitiallyExpanded) private var detailsInitiallyExpanded
    public init(credentialID: String, compact: Bool = false) { self.credentialID = credentialID; self.compact = compact }

    public var body: some View {
        CredentialStatusContent(credentialID: credentialID, compact: compact, initiallyExpanded: detailsInitiallyExpanded)
            .id(credentialID)
    }
}

private struct CredentialStatusContent: View {
    let credentialID: String
    let compact: Bool
    @Environment(\.credentialStatusUI) private var statusUI
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    @State private var expanded: Bool
    @ScaledMetric(relativeTo: .subheadline) private var baselineOffset: CGFloat = 5
    init(credentialID: String, compact: Bool, initiallyExpanded: Bool) {
        self.credentialID = credentialID; self.compact = compact
        _expanded = State(initialValue: initiallyExpanded)
    }

    var body: some View {
        let display = statusUI.displayStatus(for: credentialID)
            Group {
                if compact { content(display) }
                else { WalletSection(String(localized: "Credential status", bundle: .module),
                    titleIdentifier: "credential-status-\(credentialID)") { content(display) } }
            }
            .animation(WalletMotion.disclosure(reduceMotion: reduceMotion), value: expanded)
    }

    private func content(_ display: CredentialStatusDisplay) -> some View {
        let value = display.presentation
        let refreshing = statusUI.refreshing.contains(credentialID)
        return VStack(alignment: .leading, spacing: 0) {
            if compact { divider }
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                CredentialStatusIcon(display).alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + baselineOffset }
                Text(display.statusTitle).font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier(compact ? "credential-status-\(credentialID)" : "")
                if !compact, (value?.action == .check || value?.action == .retry || refreshing), let check = statusUI.check {
                    Button { check(credentialID) } label: {
                        Group {
                            if refreshing { ProgressView() }
                            else { Image(systemName: "arrow.clockwise") }
                        }.frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain).disabled(refreshing || statusUI.checking || display.isLoading)
                    .accessibilityLabel(refreshing ? String(localized: "Checking…", bundle: .module) : String(localized: "Refresh status", bundle: .module))
                    .accessibilityIdentifier("credential-status-check-\(credentialID)")
                    .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + baselineOffset }
                }
                Button { expanded.toggle() } label: {
                    Image(systemName: "chevron.forward")
                        .font(.body.weight(.semibold)).foregroundStyle(.tint)
                        .rotationEffect(.degrees(expanded ? (layoutDirection == .rightToLeft ? -90 : 90) : 0))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(display.isLoading && value == nil)
                .accessibilityLabel(String(localized: "Credential status details", bundle: .module))
                .accessibilityValue(expanded ? String(localized: "Expanded", bundle: .module) : String(localized: "Collapsed", bundle: .module))
                .accessibilityIdentifier("credential-status-details-\(credentialID)")
                .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + baselineOffset }
            }.padding(.leading, 16).padding(.trailing, 4).frame(minHeight: 44)
            if !compact, let checked = value?.lastCheckedAt {
                Text("Last checked: \(WalletInstant(checked).formatted(timeZone: timeZone, locale: locale).date)", bundle: .module)
                    .font(.footnote).foregroundStyle(.secondary)
                    .padding(.leading, 52).padding(.trailing, 16).padding(.bottom, 12)
            }
            if expanded {
                divider
                if display.isUnavailable { notice(String(localized: "Credential status is unavailable. Previous results are retained.", bundle: .module)) }
                if let value { detailContents(value) }
            }
        }
    }

    @ViewBuilder private func detailContents(_ value: CredentialStatusPresentation) -> some View {
        let showReferences = value.references.count > 1 || value.references.contains {
            $0.failure != nil || $0.conflict || $0.problem != nil || $0.result != .clear
        }
        if let problem = value.discoveryProblem {
            notice(problem == .resourceLimit ? String(localized: "This check exceeds the wallet’s processing limits.", bundle: .module)
                : String(localized: "The credential’s status references could not be read.", bundle: .module))
        }
        if (!showReferences || value.references.count == 1), let date = value.lastCheckedAt {
            fact(String(localized: "Last checked", bundle: .module), dateText(date), identifier: "credential-status-last-checked-\(credentialID)")
        }
        if !showReferences && value.freshness == .stale { notice(String(localized: "Some status information is out of date.", bundle: .module)) }
        if !showReferences && !value.purposes.isEmpty {
            fact(String(localized: "Checks", bundle: .module), value.purposes.map(purposeTitle).joined(separator: ", "))
        }
        if value.coverage == .partial { notice(String(localized: "Some status references could not be checked.", bundle: .module)) }
        if showReferences {
            ForEach(value.references, id: \.referenceID) { reference in
                divider
                fact(reference.purpose.map(purposeTitle) ?? (reference.protocolName == "TOKEN_STATUS_LIST"
                    ? String(localized: "Token status", bundle: .module) : String(localized: "Status reference", bundle: .module)), resultTitle(reference))
                if value.references.count > 1, let date = reference.checkedAt { fact(String(localized: "Last checked", bundle: .module), dateText(date)) }
                if let failure = reference.failure {
                    notice(failureExplanation(failure.code))
                    switch failure {
                    case .notYetValid(let date): fact(String(localized: "Check available from", bundle: .module), dateText(date))
                    case .rateLimited(let date): if let date { fact(String(localized: "Retry available from", bundle: .module), dateText(date)) }
                    case .reason: EmptyView()
                    }
                }
                if value.references.count > 1, let date = reference.attemptedAt, date != reference.checkedAt {
                    fact(String(localized: "Latest attempt", bundle: .module), dateText(date))
                }
                if reference.freshness == .stale { notice(String(localized: "This result is out of date.", bundle: .module)) }
                if reference.problem == .resourceLimit { notice(String(localized: "This check exceeds the wallet’s processing limits.", bundle: .module)) }
                if reference.problem == .conflictingReference { notice(String(localized: "The credential has conflicting status reference parameters.", bundle: .module)) }
                if reference.conflict {
                    notice(String(localized: "The issuer contradicted an earlier irreversible result. The earlier result is retained.", bundle: .module))
                }
            }
        }
        if (!showReferences || value.references.count == 1), let date = value.lastAttemptAt, date != value.lastCheckedAt {
            divider
            fact(String(localized: "Latest attempt", bundle: .module), dateText(date), identifier: "credential-status-latest-attempt-\(credentialID)")
        }
        if !showReferences, let failure = value.failure { notice(failureExplanation(failure.code)) }
        if value.hasAdverseStatus { notice(String(localized: "The recipient may reject this credential.", bundle: .module)) }
        else if value.kind == .notProvided { notice(String(localized: "The credential contains no status reference. This does not establish that it is valid.", bundle: .module)) }
        else if value.kind == .notChecked { notice(String(localized: "The credential has a status reference that has not been checked yet.", bundle: .module)) }
    }

    private var divider: some View { Divider().padding(.horizontal, 16) }
    private func fact(_ label: String, _ value: String, identifier: String = "") -> some View {
        CredentialDataRow(label) { Text(value).font(.body).accessibilityIdentifier(identifier) }.padding(16)
    }
    private func notice(_ text: String) -> some View {
        Text(text).font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).padding(16)
    }
    private func dateText(_ date: Date) -> String { WalletInstant(date).formatted(timeZone: timeZone, locale: locale).fullText }

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
    private func failureExplanation(_ reason: String) -> String {
        switch reason {
        case "CREDENTIAL_EXPIRED": String(localized: "The credential has expired. Online status checks were skipped.", bundle: .module)
        case "NOT_YET_VALID": String(localized: "The credential is not yet valid. Its status can be checked from its valid-from time.", bundle: .module)
        case "REFERENCE_UNAUTHENTICATED": String(localized: "The credential’s status reference could not be authenticated.", bundle: .module)
        case "RATE_LIMITED": String(localized: "The status service asked to wait before retrying.", bundle: .module)
        case "RESOURCE_LIMIT": String(localized: "This check exceeds the wallet’s processing limits.", bundle: .module)
        case "UNSUPPORTED_PROOF": String(localized: "This wallet cannot check this status proof.", bundle: .module)
        case "INVALID_SIGNATURE", "UNAUTHORIZED_SIGNER", "INVALID_LIST", "INVALID_VALIDITY": String(localized: "Could not verify the latest status information. Any previous result is retained.", bundle: .module)
        default: String(localized: "Could not update. Any previous result keeps its original check time.", bundle: .module)
        }
    }
}
