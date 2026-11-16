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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    @ScaledMetric(relativeTo: .subheadline) private var compactGlyphSize = 22.0
    @ScaledMetric(relativeTo: .subheadline) private var detailGlyphSize = 26.0
    @State private var expanded: Bool
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
        .onChange(of: statusUI.contextKey) { _ in expanded = false }
    }

    private func content(_ display: CredentialStatusDisplay) -> some View {
        let value = display.presentation
        let facts = value?.facts(readUnavailable: display.isUnavailable)
            ?? CredentialStatusFacts(main: display.isUnavailable ? [.note(.readUnavailable)] : [], extra: [])
        let refreshing = statusUI.refreshing.contains(credentialID)
        let refreshVisible = statusUI.check != nil && (value?.action == .check || value?.action == .retry || refreshing || value?.references.contains {
            switch $0.failure { case .notYetValid: true; case .rateLimited(let date): date != nil; default: false }
        } == true)
        let disclosure = !facts.extra.isEmpty || compact && !facts.main.isEmpty
        let disclosureEnabled = !display.isLoading || value != nil
        let expandedVisible = expanded && disclosure
        let toggleDisclosure = { expanded.toggle() }
        return VStack(alignment: .leading, spacing: 0) {
            if compact { divider }
            HStack(spacing: 0) {
                if disclosure {
                    Button(action: toggleDisclosure) { header(display, trailingInset: 4) }
                        .buttonStyle(.plain).disabled(!disclosureEnabled)
                        .accessibilityHint(String(localized: "Credential status details", bundle: .module))
                        .accessibilityValue(expandedVisible ? String(localized: "Expanded", bundle: .module) : String(localized: "Collapsed", bundle: .module))
                        .accessibilityIdentifier("credential-status-details-\(credentialID)")
                } else {
                    header(display, trailingInset: refreshVisible ? 8 : 16)
                }
                if refreshVisible { refreshButton(display) }
                if disclosure {
                    Button(action: toggleDisclosure) { chevron(expanded: expandedVisible) }
                        .buttonStyle(.plain).disabled(!disclosureEnabled)
                        // The title exposes this same disclosure action to accessibility services.
                        .accessibilityHidden(true)
                }
            }.padding(.trailing, disclosure || refreshVisible ? 4 : 0)
            if !compact { CredentialStatusFactsContent(credentialID: credentialID, main: facts.main) }
            if expandedVisible {
                if compact && !facts.main.isEmpty { divider }
                CredentialStatusFactsContent(credentialID: credentialID, main: compact ? facts.main : [], extra: facts.extra)
            }
        }
    }

    private func header(_ display: CredentialStatusDisplay, trailingInset: CGFloat = 16) -> some View {
        HStack(alignment: .center, spacing: 8) {
            CredentialStatusIcon(display, size: compact ? compactGlyphSize : detailGlyphSize)
            Text(display.statusTitle).font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier(compact ? "credential-status-\(credentialID)" : "")
        }.padding(.leading, 16).padding(.trailing, trailingInset).frame(minHeight: 44)
    }

    private func chevron(expanded: Bool) -> some View {
        Image(systemName: "chevron.forward").font(.body.weight(.semibold)).foregroundStyle(.tint)
            .rotationEffect(.degrees(expanded ? (layoutDirection == .rightToLeft ? -90 : 90) : 0))
            .frame(width: 44, height: 44).contentShape(Rectangle())
    }

    private func refreshButton(_ display: CredentialStatusDisplay) -> some View {
        let refreshing = statusUI.refreshing.contains(credentialID)
        return Button { statusUI.check?(credentialID) } label: {
            Group {
                if refreshing { ProgressView() }
                else { Image(systemName: "arrow.clockwise").foregroundStyle(.tint) }
            }.frame(width: 44, height: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(refreshing || statusUI.checking || display.isLoading || !(display.presentation?.action == .check || display.presentation?.action == .retry))
        .accessibilityLabel(refreshing ? String(localized: "Checking…", bundle: .module) : String(localized: "Refresh status", bundle: .module))
        .accessibilityIdentifier("credential-status-check-\(credentialID)")
    }

    private var divider: some View { Divider().padding(.horizontal, 16) }
}
