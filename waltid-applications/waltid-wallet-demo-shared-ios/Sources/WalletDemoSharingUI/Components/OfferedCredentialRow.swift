import SwiftUI
import WalletSDK

/// A review row keeps inspecting the offer independent from selecting it or requesting copies.
public struct OfferedCredentialRow: View {
    private let credential: IssuanceCredentialPreview
    private let issuerName: String
    private let issuerIdentifier: String
    private let copies: Binding<Int>
    private let limit: Int
    private let enabled: Bool
    private let allowsSelection: Bool
    private let largeArt: Bool
    @State private var showDetails = false
    @Environment(\.walletReviewNavigationAvailable) private var hasNavigation
    @Environment(\.sizeCategory) private var sizeCategory

    public init(credential: IssuanceCredentialPreview, issuerName: String, issuerIdentifier: String,
                copies: Binding<Int>, limit: Int, enabled: Bool, allowsSelection: Bool, largeArt: Bool = false) {
        self.credential = credential
        self.issuerName = issuerName
        self.issuerIdentifier = issuerIdentifier
        self.copies = copies
        self.limit = limit
        self.enabled = enabled
        self.allowsSelection = allowsSelection
        self.largeArt = largeArt && !allowsSelection
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if largeArt || allowsSelection { identity.padding(16) }
            else {
                Button { showDetails = true } label: { identity.padding(16).contentShape(Rectangle()) }
                    .buttonStyle(.plain)
                    .accessibilityHint(String(localized: "View credential information", bundle: .module))
                    .accessibilityIdentifier("issuance-details-\(credential.configurationID)")
            }
            if copies.wrappedValue > 0 {
                if limit > 1 {
                    HStack {
                        Text(String(format: String(localized: "Copies: %d", bundle: .module), copies.wrappedValue))
                            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("issuance-copies-\(credential.configurationID)")
                        WalletCountControl(value: copies, range: 1...limit, enabled: enabled,
                            decreaseLabel: String(format: String(localized: "Fewer copies of %@", bundle: .module), CredentialCardSummary.offered(from: credential).title),
                            increaseLabel: String(format: String(localized: "More copies of %@", bundle: .module), CredentialCardSummary.offered(from: credential).title),
                            identifier: "issuance-copies-\(credential.configurationID)")
                    }
                    .padding(.horizontal, 16)
                }
            }
            if allowsSelection {
                WalletNavigationRow(String(localized: "Credential information", bundle: .module)) { showDetails = true }
                    .accessibilityIdentifier("issuance-details-\(credential.configurationID)")
            }
        }
        .walletReviewDestination(isPresented: $showDetails) {
            Group {
                if hasNavigation {
                    WalletDetailPage(String(localized: "Credential information", bundle: .module)) { information }
                } else {
                    WalletDetailSheet(String(localized: "Credential information", bundle: .module), onDismiss: { showDetails = false }) { information }
                }
            }.accessibilityIdentifier("issuance-credential-details")
        }
    }

    private var information: some View {
        OfferedCredentialDetails(credential: credential, issuerName: issuerName, issuerIdentifier: issuerIdentifier)
    }

    @ViewBuilder private var identity: some View {
        if largeArt {
            ZStack(alignment: .bottomLeading) {
                CredentialCardArtView(summary: .offered(from: credential))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(CredentialCardSummary.offered(from: credential).title)
                    .accessibilityIdentifier("issuance-large-art-\(credential.configurationID)")
                Button { showDetails = true } label: {
                    Image(systemName: "info.circle")
                        .resizable().frame(width: 20, height: 20)
                        .foregroundStyle(Color(css: CredentialCardSummary.offered(from: credential).textColor) ?? .white)
                        .frame(width: 48, height: 48).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Credential information", bundle: .module))
                .accessibilityIdentifier("issuance-details-\(credential.configurationID)")
                // Align the 20pt icon with the card's 16pt inset: 2 + (48 - 20) / 2.
                .padding(2)
            }
        } else if !allowsSelection {
            HStack(spacing: 12) { summary; selection(showsLabel: false) }
        } else if #available(iOS 16, *) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) { summary; selection(showsLabel: false) }
                    .fixedSize(horizontal: true, vertical: false)
                stackedIdentity
            }
        } else if sizeCategory.isAccessibilityCategory {
            stackedIdentity
        } else {
            HStack(spacing: 12) { summary; selection(showsLabel: false) }
        }
    }

    private var summary: some View {
        CredentialSummaryRow(summary: .offered(from: credential))
            .accessibilityIdentifier("issuance-identity-\(credential.configurationID)")
    }

    private var stackedIdentity: some View {
        VStack(alignment: .leading, spacing: 8) { summary; selection(showsLabel: true) }
    }

    @ViewBuilder private func selection(showsLabel: Bool) -> some View {
        if allowsSelection {
            if showsLabel { toggle }
            else { toggle.labelsHidden().fixedSize() }
        } else {
            Image(systemName: "chevron.forward").font(.footnote).foregroundStyle(.secondary).accessibilityHidden(true)
        }
    }

    private var toggle: some View {
        Toggle(String(localized: "Receive", bundle: .module), isOn: Binding(
            get: { copies.wrappedValue > 0 }, set: { copies.wrappedValue = $0 ? 1 : 0 }))
            .accessibilityLabel(String(format: String(localized: "Receive %@", bundle: .module), CredentialCardSummary.offered(from: credential).title))
            .disabled(!enabled)
            .accessibilityIdentifier("issuance-select-\(credential.configurationID)")
    }
}
