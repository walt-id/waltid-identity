import SwiftUI
import UIKit
import WalletDemoSharingUI

struct SettingsView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.walletDemoBranding) private var branding
    @State private var confirmReset = false

    var body: some View {
        List {
            Section("Wallet") {
                Text(branding.appTitle)
                    .font(.headline)
                    .accessibilityIdentifier(WalletAccessibilityID.settingsAppTitle)
            }
            Section("Wallet DID") {
                Text(viewModel.did.isEmpty ? "Not available" : viewModel.did)
                    .font(.footnote)
                    .textSelection(.enabled)
                    .accessibilityIdentifier(WalletAccessibilityID.settingsDid)
                Button("Copy DID") {
                    UIPasteboard.general.string = viewModel.did
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsDidCopy)
            }
            Section("Wallet key") {
                Text(viewModel.keyID.isEmpty ? "Not available" : viewModel.keyID)
                    .font(.footnote)
                    .textSelection(.enabled)
                    .accessibilityIdentifier(WalletAccessibilityID.settingsKeyId)
                Button("Copy key ID") {
                    UIPasteboard.general.string = viewModel.keyID
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsKeyIdCopy)
            }
            Section("Public JWK") {
                Text(viewModel.publicJWK.isEmpty ? "Not available" : viewModel.publicJWK)
                    .font(.footnote)
                    .textSelection(.enabled)
                    .accessibilityIdentifier(WalletAccessibilityID.settingsPublicJwk)
                Button("Copy public JWK") {
                    UIPasteboard.general.string = viewModel.publicJWK
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsPublicJwkCopy)
            }
            Section {
                Button("Lock") {
                    viewModel.lock()
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsLock)
                Button("Reset wallet", role: .destructive) {
                    confirmReset = true
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsReset)
            }
        }
        .navigationTitle("Settings")
        .confirmationDialog(
            "Reset wallet?",
            isPresented: $confirmReset,
            titleVisibility: .visible
        ) {
            Button("Reset", role: .destructive) {
                viewModel.resetWallet()
            }
            .accessibilityIdentifier(WalletAccessibilityID.settingsResetConfirm)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes the wallet DID, keys, credentials, and PIN. This cannot be undone.")
        }
    }
}
