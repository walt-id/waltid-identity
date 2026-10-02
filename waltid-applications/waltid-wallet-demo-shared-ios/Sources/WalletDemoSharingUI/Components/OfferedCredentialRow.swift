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
                    Stepper(String(format: String(localized: "Copies: %d", bundle: .module), copies.wrappedValue),
                            value: copies, in: 1...limit)
                        .font(.subheadline)
                        .disabled(!enabled)
                        .accessibilityIdentifier("issuance-copies-\(credential.configurationID)")
                        .padding(.horizontal, 16)
                        .padding(.bottom, 4)
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
