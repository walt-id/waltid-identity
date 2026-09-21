import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct SettingsView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var confirmReset = false

    var body: some View {
        List {
            Section("Wallet") {
                NavigationLink {
                    if let model = viewModel.identityScreen { WalletIdentityView(model: model) }
                    else { List { signingProtectionSection }.navigationTitle("Signing key").navigationBarTitleDisplayMode(.inline) }
                } label: {
                    SettingsDestinationLabel("Signing key", systemImage: "key", summary: String(localized: "Protection and key backup"))
                }
                .accessibilityIdentifier("wallet.settingsSigningKey")
                NavigationLink { TechnicalDetailsView(viewModel: viewModel) } label: {
                    SettingsDestinationLabel("Technical details", systemImage: "doc.text", summary: String(localized: "DID, key ID, and public key"))
                }
                .accessibilityIdentifier("wallet.settingsTechnicalDetails")
            }
            Section {
                NavigationLink { NearbySettingsView(viewModel: viewModel) } label: {
                    SettingsDestinationLabel("Nearby sharing", systemImage: "antenna.radiowaves.left.and.right", summary: viewModel.proximityTransportProfile.title)
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsProximityPresentation)
                NavigationLink {
                    List {
                        Section {
                            Toggle("Show wallet review", isOn: $viewModel.showDcApiPresentationPreview)
                                .accessibilityIdentifier(WalletAccessibilityID.settingsShowDcApiPreview)
                        } footer: {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Show the wallet review after you select a credential in the system picker. Turning this off skips only the wallet review. System consent and any required signing approval still apply.")
                                Text("Requires iOS 26 or later and a compatible app or browser. This setting controls wallet review only.")
                            }
                        }
                    }
                    .frame(maxWidth: 640)
                    .frame(maxWidth: .infinity)
                    .background(Color(uiColor: .systemGroupedBackground))
                    .navigationTitle("Digital Credentials API")
                    .navigationBarTitleDisplayMode(.inline)
                } label: {
                    SettingsDestinationLabel("Digital Credentials API", systemImage: "person.text.rectangle",
                                             summary: viewModel.showDcApiPresentationPreview ? String(localized: "Wallet review on") : String(localized: "Wallet review off"))
                }
                .accessibilityIdentifier("wallet.settingsDigitalCredentialsApi")
            } header: {
                Text("Sharing").accessibilityIdentifier(WalletAccessibilityID.settingsCredentialSharing)
            }
            Section {
                Button { dismiss(); viewModel.lock() } label: { Label("Lock wallet", systemImage: "lock") }
                    .accessibilityIdentifier(WalletAccessibilityID.settingsLock)
                Button(role: .destructive) { confirmReset = true } label: {
                    Label("Reset wallet", systemImage: "arrow.counterclockwise").foregroundStyle(.red)
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsReset)
            }
        }
        .listStyle(.insetGrouped)
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Settings")
        .alert("Reset wallet?", isPresented: $confirmReset) {
            Button("Reset wallet", role: .destructive, action: viewModel.resetWallet)
                .accessibilityIdentifier(WalletAccessibilityID.settingsResetConfirm)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the wallet’s local keys, credentials, and wallet PIN. Saved key backups remain. Restoring a key does not restore credentials.")
        }
        .confirmationDialog("Change signing protection?", isPresented: signingProtectionConfirmationPresented, titleVisibility: .visible) {
            Button("Create new wallet", role: .destructive, action: viewModel.confirmSigningProtectionChange)
                .accessibilityIdentifier(WalletAccessibilityID.signingProtectionConfirm)
            Button("Cancel", role: .cancel, action: viewModel.cancelSigningProtectionChange)
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
                Text(viewModel.appliedSigningProtection?.title ?? "Unavailable")
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
