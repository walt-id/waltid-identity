import SwiftUI
import WalletDemoSharingUI

struct WalletSettingsView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var confirmsReset = false

    var body: some View {
        Form {
            if viewModel.statusShouldPersist(for: .credentials) {
                Section {
                    StatusBannerView(
                        message: viewModel.statusMessage(for: .credentials),
                        isLoading: viewModel.statusIsLoading(for: .credentials),
                        isError: viewModel.statusIsError(for: .credentials),
                        onDismiss: viewModel.dismissStatus
                    )
                }
            }

            Section("Wallet identity") {
                SettingsIdentityValue(
                    label: "Wallet DID",
                    value: viewModel.did,
                    accessibilityID: WalletAccessibilityID.walletDID
                )
                SettingsIdentityValue(
                    label: "Wallet key identifier",
                    value: viewModel.keyID,
                    accessibilityID: WalletAccessibilityID.walletKeyID
                )
            }

            Section("Wallet controls") {
                Button("Reset wallet", role: .destructive) {
                    confirmsReset = true
                }
                .disabled(!viewModel.isReady || viewModel.isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.resetWallet)

                Text("Reset removes credentials, the wallet identity, and key material stored by this app on this device.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityIdentifier(WalletAccessibilityID.settingsScreen)
        .alert("Reset wallet?", isPresented: $confirmsReset) {
            Button("Reset", role: .destructive) {
                Task { _ = await viewModel.resetWallet() }
            }
            .accessibilityIdentifier(WalletAccessibilityID.resetWalletConfirm)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently deletes all wallet data stored by this app on this device.")
        }
    }
}

private struct SettingsIdentityValue: View {
    let label: String
    let value: String
    let accessibilityID: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(accessibilityID)
    }
}
