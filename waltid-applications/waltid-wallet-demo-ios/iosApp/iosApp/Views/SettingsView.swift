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
            signingProtectionSection
            Section("Credential Sharing") {
                Toggle(
                    "Show Walt Wallet preview for DC API Presentation",
                    isOn: $viewModel.showDcApiPresentationPreview
                )
                .accessibilityIdentifier(WalletAccessibilityID.settingsShowDcApiPreview)
                Text("When off, Digital Credentials presentations skip the wallet review and continue from the system picker to biometrics.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .accessibilityIdentifier(WalletAccessibilityID.settingsCredentialSharing)
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
        .confirmationDialog(
            "Change signing protection?",
            isPresented: signingProtectionConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button("Create new wallet", role: .destructive) {
                viewModel.confirmSigningProtectionChange()
            }
            .accessibilityIdentifier(WalletAccessibilityID.signingProtectionConfirm)
            Button("Cancel", role: .cancel) {
                viewModel.cancelSigningProtectionChange()
            }
        } message: {
            Text("This creates a new key and DID, and removes all credentials. Credentials must be issued again.")
        }
    }

    @ViewBuilder
    private var signingProtectionSection: some View {
        Section("Signing protection") {
            HStack {
                Text("Current")
                Spacer()
                Text(viewModel.appliedSigningProtection?.title ?? "Not available")
            }
            Text("Changing signing protection creates a new wallet key and DID.")
                .font(.footnote)
                .foregroundStyle(.secondary)

            switch viewModel.signingProtectionMode {
            case .optional:
                signingProtectionChoice(.biometric)
                signingProtectionChoice(.none)
            case .required, .disabled:
                let managedProtection = viewModel.signingProtectionMode.defaultSelection
                signingProtectionChoice(
                    managedProtection,
                    managed: viewModel.appliedSigningProtection == managedProtection
                )
                Text("Managed by app configuration.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            if !viewModel.isBiometricSigningAvailable,
               viewModel.signingProtectionMode != .disabled {
                Text(
                    viewModel.biometricSigningAvailability?.message
                        ?? "Checking strong biometric availability..."
                )
                .font(.footnote)
                .foregroundStyle(
                    viewModel.biometricSigningAvailability == nil ? Color.secondary : Color.red
                )
                .accessibilityIdentifier(WalletAccessibilityID.signingProtectionAvailability)
            }

            if !viewModel.isReady {
                Button("Retry wallet setup") {
                    viewModel.requestSigningProtectionChange(viewModel.selectedSigningProtection)
                }
                .disabled(
                    viewModel.isChangingSigningProtection ||
                        viewModel.isLoading ||
                        (viewModel.selectedSigningProtection == .biometric &&
                            !viewModel.isBiometricSigningAvailable)
                )
                .accessibilityIdentifier(WalletAccessibilityID.signingProtectionRetry)
            }

            if viewModel.isChangingSigningProtection {
                ProgressView("Changing signing protection...")
                    .accessibilityIdentifier(WalletAccessibilityID.signingProtectionProgress)
            }
            if let error = viewModel.signingProtectionError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .accessibilityIdentifier(WalletAccessibilityID.signingProtectionError)
            }
        }
    }

    private func signingProtectionChoice(
        _ protection: WalletDemoSigningProtection,
        managed: Bool = false
    ) -> some View {
        SigningProtectionChoiceView(
            protection: protection,
            selected: viewModel.selectedSigningProtection == protection,
            enabled: !managed &&
                !viewModel.isChangingSigningProtection &&
                !viewModel.isLoading &&
                (protection != .biometric || viewModel.isBiometricSigningAvailable),
            action: { viewModel.requestSigningProtectionChange(protection) }
        )
        .accessibilityIdentifier(
            protection == .biometric
                ? WalletAccessibilityID.signingProtectionBiometric
                : WalletAccessibilityID.signingProtectionNone
        )
    }

    private var signingProtectionConfirmationPresented: Binding<Bool> {
        Binding(
            get: { viewModel.pendingSigningProtectionChange != nil },
            set: { isPresented in
                if !isPresented, viewModel.pendingSigningProtectionChange != nil {
                    viewModel.cancelSigningProtectionChange()
                }
            }
        )
    }
}
