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
    @State private var showDetails = false

    public init(credential: IssuanceCredentialPreview, issuerName: String, issuerIdentifier: String,
                copies: Binding<Int>, limit: Int, enabled: Bool) {
        self.credential = credential
        self.issuerName = issuerName
        self.issuerIdentifier = issuerIdentifier
        self.copies = copies
        self.limit = limit
        self.enabled = enabled
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                CredentialSummaryRow(summary: .offered(from: credential))
                    .accessibilityIdentifier("issuance-identity-\(credential.configurationID)")
                Toggle(isOn: Binding(get: { copies.wrappedValue > 0 }, set: { copies.wrappedValue = $0 ? 1 : 0 })) {
                    Text(String(format: String(localized: "Receive %@", bundle: .module), CredentialCardSummary.offered(from: credential).title))
                }
                .labelsHidden()
                .fixedSize()
                .disabled(!enabled)
                .accessibilityIdentifier("issuance-select-\(credential.configurationID)")
            }.padding(16)
            if copies.wrappedValue > 0 {
                if limit > 1 {
                    HStack {
                        Text(String(format: String(localized: "Copies: %d", bundle: .module), copies.wrappedValue))
                            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                        WalletCountControl(value: copies, range: 1...limit, enabled: enabled,
                            decreaseLabel: String(format: String(localized: "Fewer copies of %@", bundle: .module), CredentialCardSummary.offered(from: credential).title),
                            increaseLabel: String(format: String(localized: "More copies of %@", bundle: .module), CredentialCardSummary.offered(from: credential).title),
                            identifier: "issuance-copies-\(credential.configurationID)")
                    }
                    .accessibilityIdentifier("issuance-copies-\(credential.configurationID)")
                    .padding(.horizontal, 16)
                } else {
                    Text(String(localized: "1 copy", bundle: .module))
                        .font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 16)
                }
            }
            WalletNavigationRow(String(localized: "Credential information", bundle: .module)) { showDetails = true }
                .accessibilityIdentifier("issuance-details-\(credential.configurationID)")
        }
        .sheet(isPresented: $showDetails) {
            WalletDetailSheet(CredentialCardSummary.offered(from: credential).title, onDismiss: { showDetails = false }) {
                OfferedCredentialDetails(credential: credential, issuerName: issuerName, issuerIdentifier: issuerIdentifier)
            }.accessibilityIdentifier("issuance-credential-details")
        }
    }
}
