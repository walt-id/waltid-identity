import SwiftUI
import UIKit
import UniformTypeIdentifiers
import WalletDemoIdentityDocumentSupport
import WalletDemoSharingUI
import WalletSDK

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
                NavigationLink("Reader Authentication") {
                    ReaderTrustSettingsView(controller: viewModel.readerTrustSettings)
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsReaderAuthentication)
            }
            .accessibilityIdentifier(WalletAccessibilityID.settingsCredentialSharing)
            proximityPresentationSection
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
    private var proximityPresentationSection: some View {
        Section {
            Text("Choose the transport profile for the next in-person presentation session.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            proximityTransportProfileChoice(
                .defaultProfile,
                title: "Default (QR + conventional NFC)",
                description: "Production default with negotiated QR/NFC engagement and conventional retrieval.",
                accessibilityIdentifier: WalletAccessibilityID.settingsProximityDefault
            )
            proximityTransportProfileChoice(
                .provisionalNfcV2Hybrid,
                title: "NFCv2 hybrid (provisional)",
                description: "Starts over NFCv2 and transfers the session over Bluetooth LE.",
                accessibilityIdentifier: WalletAccessibilityID.settingsProximityNfcV2Hybrid
            )
            proximityTransportProfileChoice(
                .provisionalNfcV2Direct,
                title: "NFCv2 direct (provisional)",
                description: "Keeps engagement and encrypted session messages on NFCv2.",
                accessibilityIdentifier: WalletAccessibilityID.settingsProximityNfcV2Direct
            )
            Text("Availability is checked before engagement. The active session keeps its starting profile.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } header: {
            Text("Proximity Presentation")
                .accessibilityIdentifier(WalletAccessibilityID.settingsProximityPresentation)
        }
    }

    private func proximityTransportProfileChoice(
        _ profile: WalletDemoProximityTransportProfile,
        title: String,
        description: String,
        accessibilityIdentifier: String
    ) -> some View {
        Button {
            viewModel.proximityTransportProfile = profile
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(
                    systemName: viewModel.proximityTransportProfile == profile
                        ? "largecircle.fill.circle"
                        : "circle"
                )
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .foregroundStyle(.primary)
                    Text(description)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
        .accessibilityValue(viewModel.proximityTransportProfile == profile ? "Selected" : "Not selected")
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

private struct ReaderTrustSettingsView: View {
    @ObservedObject var controller: DemoReaderTrustSettingsController
    @State private var importing = false
    @State private var confirmReset = false

    var body: some View {
        List {
            Section("Reader policy") {
                policyChoice(
                    .allowAnonymousOrUntrusted,
                    title: "Allow anonymous or untrusted readers",
                    detail: "Reader Authentication remains visible during holder review."
                )
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustAllowUntrusted)
                policyChoice(
                    .requireTrusted,
                    title: "Require a trusted reader",
                    detail: "Only readers accepted by configured Reader CAs or RICALs may reach review."
                )
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustRequireTrusted)
                if controller.settings.readerPolicy == .requireTrusted,
                   controller.settings.trustAnchors.isEmpty,
                   controller.settings.ricalProviders.isEmpty {
                    Text("No trust material is configured, so all readers will be rejected.")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.readerTrustPolicy)

            Section("Configured trust material") {
                if controller.settings.trustAnchors.isEmpty,
                   controller.settings.ricalProviders.isEmpty {
                    Text("No Reader CAs or RICAL providers configured")
                        .foregroundStyle(.secondary)
                }
                ForEach(controller.settings.trustAnchors) { anchor in
                    configuredMaterialRow(
                        title: anchor.displayName,
                        detail: "Reader CA",
                        remove: { controller.removeReaderAuthority(id: anchor.id) }
                    )
                }
                ForEach(controller.settings.ricalProviders) { provider in
                    configuredMaterialRow(
                        title: provider.providerID,
                        detail: provider.establishesReaderTrust
                            ? "RICAL provider · establishes reader trust"
                            : "RICAL provider · evidence only",
                        remove: { controller.removeRICALProvider(id: provider.id) }
                    )
                }
            }

            Section("Import") {
                Button("Import Reader CA or trust bundle") {
                    importing = true
                }
                .disabled(controller.importInProgress)
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustImport)
                Text("Accepted: DER or certificate-only PEM Reader CAs, and versioned walt.id JSON trust bundles. Private keys and PKCS#12 files are rejected.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if controller.importInProgress {
                    ProgressView("Validating trust material...")
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportProgress)
                }
            }

            if !controller.settings.trustAnchors.isEmpty ||
                !controller.settings.ricalProviders.isEmpty ||
                controller.settings.readerPolicy != .allowAnonymousOrUntrusted {
                Section {
                    Button("Reset Reader Authentication settings", role: .destructive) {
                        confirmReset = true
                    }
                    .accessibilityIdentifier(WalletAccessibilityID.readerTrustReset)
                }
            }
        }
        .navigationTitle("Reader Authentication")
        .fileImporter(
            isPresented: $importing,
            allowedContentTypes: [.data],
            allowsMultipleSelection: false,
            onCompletion: handleImportResult
        )
        .sheet(isPresented: importReviewPresented) {
            if let preview = controller.pendingImport {
                ReaderTrustImportReviewView(
                    preview: preview,
                    confirm: controller.confirmImport,
                    cancel: controller.cancelImport
                )
            }
        }
        .confirmationDialog(
            "Reset Reader Authentication settings?",
            isPresented: $confirmReset,
            titleVisibility: .visible
        ) {
            Button("Reset", role: .destructive, action: controller.reset)
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustResetConfirm)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes all imported Reader CAs and RICAL providers and restores the permissive reader policy.")
        }
        .alert("Reader Authentication import failed", isPresented: errorPresented) {
            Button("OK", action: controller.dismissError)
        } message: {
            Text(controller.errorMessage ?? "Unknown error")
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustError)
        }
    }

    private func policyChoice(
        _ policy: ProximityStoredReaderPolicy,
        title: String,
        detail: String
    ) -> some View {
        Button {
            controller.setReaderPolicy(policy)
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: controller.settings.readerPolicy == policy
                    ? "largecircle.fill.circle"
                    : "circle")
                    .foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).foregroundStyle(.primary)
                    Text(detail).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityValue(
            controller.settings.readerPolicy == policy ? "Selected" : "Not selected"
        )
        .accessibilityAddTraits(
            controller.settings.readerPolicy == policy ? .isSelected : []
        )
    }

    private func configuredMaterialRow(
        title: String,
        detail: String,
        remove: @escaping () -> Void
    ) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive, action: remove) {
                Image(systemName: "trash")
            }
            .accessibilityLabel("Remove \(title)")
        }
    }

    private func handleImportResult(_ result: Result<[URL], Error>) {
        do {
            switch try ReaderTrustImportFileLoader.load(result) {
            case .cancelled:
                return
            case let .selected(sourceName, data):
                Task {
                    await controller.prepareImport(sourceName: sourceName, data: data)
                }
            }
        } catch {
            controller.reportImportError(error.localizedDescription)
        }
    }

    private var importReviewPresented: Binding<Bool> {
        Binding(
            get: { controller.pendingImport != nil },
            set: { if !$0 { controller.cancelImport() } }
        )
    }

    private var errorPresented: Binding<Bool> {
        Binding(
            get: { controller.errorMessage != nil },
            set: { if !$0 { controller.dismissError() } }
        )
    }
}

private struct ReaderTrustImportReviewView: View {
    let preview: ProximityReaderTrustImportPreview
    let confirm: () -> Void
    let cancel: () -> Void

    var body: some View {
        NavigationView {
            List {
                Section("Import") {
                    reviewDetail("File", preview.sourceName)
                    reviewDetail(
                        "Kind",
                        preview.kind == .readerCA ? "Reader CA" : "Trust bundle"
                    )
                    Text(preview.policyEffect)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if !preview.readerAuthorities.isEmpty {
                    Section("Reader authorities") {
                        ForEach(preview.readerAuthorities) { authority in
                            VStack(alignment: .leading, spacing: 5) {
                                Text(authority.displayName).font(.headline)
                                reviewDetail("Profile", authority.profile)
                                reviewDetail("Subject", authority.subject)
                                reviewDetail("Issuer", authority.issuer)
                                reviewDate("Valid from", authority.validFrom)
                                reviewDate("Valid until", authority.validUntil)
                                Text(authority.sha256Fingerprint)
                                    .font(.caption.monospaced())
                                    .textSelection(.enabled)
                            }
                        }
                    }
                }
                if !preview.ricalProviders.isEmpty {
                    Section("RICAL providers") {
                        ForEach(preview.ricalProviders) { provider in
                            VStack(alignment: .leading, spacing: 5) {
                                Text(provider.providerID).font(.headline)
                                reviewDetail("Type", provider.type)
                                reviewDetail(
                                    "Trust effect",
                                    provider.establishesReaderTrust
                                        ? "May establish reader trust"
                                        : "Evidence only"
                                )
                            }
                        }
                    }
                }
            }
            .navigationTitle("Review import")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: cancel)
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import", action: confirm)
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportConfirm)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportReview)
        }
    }

    private func reviewDetail(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.footnote)
        }
    }

    private func reviewDate(_ label: String, _ value: Date) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value, style: .date).font(.footnote)
        }
    }
}
