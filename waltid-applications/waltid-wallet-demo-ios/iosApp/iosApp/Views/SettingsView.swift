import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct SettingsView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var confirmReset = false
    @State private var destination: Destination?
    private enum Destination { case walletAccess, signingKey, credentialStatus, technicalDetails, nearby, digitalCredentials }

    var body: some View {
        List {
            Section("Wallet") {
                SettingsDestinationRow("Wallet access", systemImage: "lock", summary: String(localized: "PIN and biometric unlock"),
                    identifier: "wallet.settingsWalletAccess") {
                    destination = .walletAccess
                }
                SettingsDestinationRow("Signing key", systemImage: "key", summary: String(localized: "Protection and key backup"),
                    identifier: "wallet.settingsSigningKey") {
                    destination = .signingKey
                }
                SettingsDestinationRow("Credential status", systemImage: "checkmark.circle", summary: String(localized: "Checks and last-known results"),
                    identifier: "settings-credential-status") {
                    destination = .credentialStatus
                }
                SettingsDestinationRow("Technical details", systemImage: "doc.text", summary: String(localized: "DID, key ID, and public key"),
                    identifier: "wallet.settingsTechnicalDetails") {
                    destination = .technicalDetails
                }
            }
            Section {
                SettingsDestinationRow("Nearby sharing", systemImage: "antenna.radiowaves.left.and.right", summary: viewModel.proximityTransportProfile.title,
                    identifier: WalletAccessibilityID.settingsProximityPresentation) {
                    destination = .nearby
                }
                SettingsDestinationRow("Digital Credentials API", systemImage: "person.text.rectangle",
                    summary: viewModel.showDcApiPresentationPreview ? String(localized: "Wallet review on") : String(localized: "Wallet review off"),
                    identifier: "wallet.settingsDigitalCredentialsApi") {
                    destination = .digitalCredentials
                }
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
        .walletDetailDestination(isPresented: Binding(get: { destination != nil }, set: { if !$0 { destination = nil } })) {
            switch destination {
            case .walletAccess: WalletAccessSettingsView(viewModel: viewModel)
            case .signingKey:
                if let model = viewModel.identityScreen {
                    WalletIdentityView(biometricAvailability: viewModel.biometricSigningRecoveryAvailability,
                        biometricKind: viewModel.access.biometricKind, model: model)
                } else {
                    List { signingProtectionSection }.navigationTitle("Signing key").navigationBarTitleDisplayMode(.inline)
                }
            case .credentialStatus: CredentialStatusSettingsView(viewModel: viewModel)
            case .technicalDetails: TechnicalDetailsView(viewModel: viewModel)
            case .nearby: NearbySettingsView(viewModel: viewModel)
            case .digitalCredentials: DigitalCredentialsSettingsView(showWalletReview: $viewModel.showDcApiPresentationPreview)
            case nil: EmptyView()
            }
        }
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
                if viewModel.biometricSigningAvailability == nil {
                    Text("Checking strong biometric availability...").font(.footnote).foregroundStyle(.secondary)
                        .accessibilityIdentifier(WalletAccessibilityID.signingProtectionAvailability)
                } else {
                    BiometricRecoverySection(availability: viewModel.biometricSigningRecoveryAvailability,
                        kind: viewModel.access.biometricKind)
                        .accessibilityIdentifier(WalletAccessibilityID.signingProtectionAvailability)
                }
            }

            if !viewModel.isReady {
                Button("Retry wallet setup") {
                    viewModel.requestSigningProtectionChange(viewModel.selectedSigningProtection)
                }
                .disabled(
                    viewModel.isChangingSigningProtection ||
                        viewModel.isLoading ||
                        (viewModel.selectedSigningProtection.requiresBiometrics &&
                            !viewModel.isBiometricSigningAvailable)
                )
                .accessibilityIdentifier(WalletAccessibilityID.signingProtectionRetry)
            }

            if viewModel.isChangingSigningProtection {
                WalletProgressRow("Changing signing protection...")
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
                (!protection.requiresBiometrics || viewModel.isBiometricSigningAvailable),
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

struct CredentialStatusSettingsView: View {
    @ObservedObject var viewModel: WalletViewModel
    var helpInitiallyExpanded = false
    var body: some View {
        Form {
            Section {
                Toggle("Automatic checks", isOn: $viewModel.automaticCredentialStatusChecks)
                    .accessibilityIdentifier("settings-credential-status-automatic")
            } footer: { Text("While this wallet is open and unlocked.\nChecks contact the issuer’s status service.") }
            Section {
                Button { viewModel.checkCredentialStatuses() } label: {
                    HStack {
                        Text("Check statuses")
                        Spacer()
                        if viewModel.checkingCredentialStatuses { ProgressView().accessibilityHidden(true) }
                        else { Image(systemName: "arrow.clockwise").accessibilityHidden(true) }
                    }
                }
                .disabled(viewModel.checkingCredentialStatuses || !viewModel.isReady || viewModel.auth != .unlocked)
                .accessibilityValue(viewModel.checkingCredentialStatuses ? String(localized: "Checking…") : "")
                .accessibilityIdentifier("settings-credential-status-check-all")
                if let summary = viewModel.credentialStatusSummary {
                    countRow(String(localized: "Processed"), summary.completed)
                    if summary.unchanged > 0 { countRow(String(localized: "Not refreshed"), summary.unchanged) }
                    if summary.deferred > 0 { countRow(String(localized: "Deferred"), summary.deferred) }
                    if summary.checkErrors > 0 {
                        countRow(String(localized: "With check errors"), summary.checkErrors)
                            .padding(.top, 8)
                    }
                }
                if let error = viewModel.credentialStatusBatchError { Text(error).font(.footnote).foregroundStyle(.red) }
            } footer: {
                if viewModel.credentialStatusSummary?.deferred ?? 0 > 0 { Text("Some checks were deferred. Try again later.") }
            }
            Section {
                MetadataDisclosure(title: String(localized: "How checks work"), initiallyExpanded: helpInitiallyExpanded,
                    accessibilityIdentifier: "settings-credential-status-how") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Checks download status lists. The credential itself is not sent.")
                        Text("List addresses and network metadata can correlate requests.")
                        Text("Recipients verify credential status independently.")
                        Text("“With check errors” counts credentials with failed checks, conflicting results or unreadable status information. These credentials are also included in the other counts.")
                    }.font(.footnote).foregroundStyle(.secondary)
                }
            }
        }.navigationTitle("Credential status").navigationBarTitleDisplayMode(.inline)
    }

    private func countRow(_ title: String, _ value: Int) -> some View {
        HStack { Text(title); Spacer(); Text(value.formatted()).monospacedDigit() }
            .foregroundStyle(Color.primary).accessibilityElement(children: .combine)
    }
}
