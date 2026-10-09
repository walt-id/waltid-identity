import SwiftUI
import WalletSDK
import WalletDemoSharingUI

struct WalletIdentityView: View {
    var biometricAvailability: DemoBiometricAvailability = .available
    var biometricKind: DemoBiometricKind = .generic
    @ObservedObject var model: WalletIdentityScreenModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var deletion: WalletIdentityScreenModel.Choice?

    var body: some View {
        ZStack {
            identityList
                .id(model.identity == nil ? model.step : .summary)
                .transition(WalletMotion.page(forward: model.step != .summary, reduceMotion: reduceMotion))
        }
        .animation(WalletMotion.navigation(reduceMotion: reduceMotion), value: model.step)
        .clipped()
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if model.identity == nil {
                WalletFooter {
                    if model.busy { ProgressView(model.progress).frame(maxWidth: .infinity, alignment: .leading) }
                    else if model.refreshing || !model.loaded { ProgressView("Loading signing key options…").frame(maxWidth: .infinity, alignment: .leading) }
                    else if let message = model.setupMessage { Text(message).font(.callout).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading) }
                    if let selected = model.selected {
                        WalletActions(
                            primary: WalletAction(
                                model.step != .summary ? String(localized: "Done")
                                    : model.loadFailed || model.canRetrySetupOperation ? String(localized: "Try again")
                                    : selected.restoring ? String(localized: "Restore signing key") : String(localized: "Create signing key"),
                                enabled: !model.busy && !model.refreshing && (model.step != .summary || model.canCreate || model.loadFailed), identifier: "wallet.keySetupContinue") {
                                    if model.loadFailed && model.step == .summary { Task { await model.refresh() } }
                                    else { model.continueSetup() }
                                },
                            secondary: model.step == .summary
                                ? (!model.canCreate && biometricAvailability.offersSettings ? WalletAction("Open Settings", identifier: "wallet.biometricOpenSettings", perform: BiometricSettings.open) : nil)
                                : WalletAction(String(localized: "Back"),
                                enabled: !model.busy && !model.refreshing) { model.step = .summary }
                        )
                    } else if model.loaded && !model.refreshing && model.choices.isEmpty {
                        WalletActions(primary: WalletAction("Try again", enabled: !model.busy) { Task { await model.refresh() } },
                            secondary: biometricAvailability.offersSettings ? WalletAction("Open Settings", identifier: "wallet.biometricOpenSettings", perform: BiometricSettings.open) : nil)
                    }
                }
            }
        }
        .navigationTitle(model.identity != nil ? "Signing key" : model.step == .summary ? "Set up your wallet" : model.step.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh() }
        .onChange(of: scenePhase) { phase in
            if phase == .active && !model.busy { Task { await model.refresh() } }
        }
        .alert("Delete key backup?", isPresented: Binding(get: { deletion != nil }, set: { if !$0 { deletion = nil } })) {
            if let choice = deletion { Button("Delete key backup", role: .destructive) { model.perform(choice); deletion = nil } }
            Button("Cancel", role: .cancel) { deletion = nil }
        } message: {
            Text("This asks \(deletion?.detail ?? String(localized: "the backup provider")) to delete the key backup. You may lose the ability to recover the key. Keys already restored on other devices are not deleted.")
        }
    }

    private var identityList: some View {
        List {
            if let identity = model.identity {
                Section {
                    SigningKeySummary(recovery: recoveryDescription(identity.recovery),
                        storage: WalletIdentityScreenModel.storage(identity.storage),
                        approval: WalletIdentityScreenModel.authorization(identity.authorization))
                    detailRow("Key protection", identity.securityLevel.displayName)
                    detailRow("Key origin", identity.origin.displayName)
                } header: { Text("Signing key") } footer: {
                    Text("Reset the wallet to change key storage or signing approval. This removes local credentials. Restoring a signing key does not restore credentials.")
                }
                if !model.choices.isEmpty || model.message != nil || model.busy {
                    Section("Key backup") {
                        identityActions
                        if let message = model.message { Text(message).foregroundStyle(.red) }
                        if model.busy { ProgressView(model.progress) }
                    }
                }
            } else if let selected = model.selected {
                SigningKeySetupContent(options: model.setupOptions, selected: selected, step: model.step, requestedApproval: model.requestedApproval,
                    onSelect: model.select, onEdit: model.edit)
            }
            if model.identity == nil && model.step == .summary && model.loaded && !model.refreshing {
                if !model.canCreate && biometricAvailability != .available {
                    Section { BiometricRecoverySection(availability: biometricAvailability, kind: biometricKind, showSettingsAction: false) }
                } else if model.selected != nil && !model.canCreate {
                    Section { Text("The selected signing approval is unavailable for this configuration. Choose compatible storage or explicitly choose another signing approval.").font(.callout).foregroundStyle(.secondary) }
                }
                if model.existingKeyUnavailable {
                    Section { Text("Your existing signing key has not been changed. Restore its availability and try again.").font(.callout).foregroundStyle(.secondary) }
                }
            }
            if !model.recoveryUnavailableReasons.isEmpty && (model.identity != nil || model.step == .recovery || model.selected == nil) {
                Section("Backup availability") {
                    ForEach(Array(Set(model.recoveryUnavailableReasons)).sorted(), id: \.self) { Text($0).font(.callout).foregroundStyle(.secondary) }
                    if model.identity != nil || model.step == .recovery {
                        Button("Check again") { Task { await model.refresh() } }
                    }
                }
            }
            if model.identity == nil && !model.choices.isEmpty {
                Section("Pending setup") { identityActions }
            }
            Section {
                if model.identity != nil && (model.refreshing || !model.loaded) {
                    ProgressView("Loading signing key details…")
                }
                if model.loaded && !model.refreshing && model.identity == nil && model.setupOptions.isEmpty && model.choices.isEmpty && model.message == nil {
                    Text("No signing key options are available for this device and app configuration.")
                }
            }
            if model.identity != nil {
                Section {
                    Text("A local save does not confirm cloud delivery. Deleting a key backup does not erase keys already restored elsewhere.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .disabled(model.busy || model.refreshing)
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.callout)
        }
    }

    private var identityActions: some View {
        ForEach(model.choices) { choice in
            VStack(alignment: .leading, spacing: 8) {
                Button(role: choice.destructive ? .destructive : nil) {
                    if choice.destructive { deletion = choice } else { model.perform(choice) }
                } label: {
                    Text(choice.title)
                }
                Text(choice.detail).font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func recoveryDescription(_ state: SigningIdentityRecoveryState) -> String {
        switch state {
        case .disabled: "No key backup submitted."
        case .submitted(_, let receipt): receipt == .acceptedLocally ? "Saved on this device. Delivery to another device is not confirmed." : "Backup confirmed by the provider."
        case .recovered: "The original signing key was restored in this wallet."
        case .removalRequested: "Backup deletion requested. Keys already restored on other devices are not deleted."
        }
    }
}

private extension KeySecurityLevel {
    var displayName: String {
        switch self {
        case .software: "Software"
        case .trustedEnvironment: "Trusted execution environment (TEE)"
        case .strongBox: "StrongBox"
        case .secureEnclave: "Secure Enclave"
        case .unknown: "Unknown"
        }
    }
}

private extension KeyOrigin {
    var displayName: String {
        switch self {
        case .generated: "Generated on this device"
        case .imported: "Imported"
        case .unknown: "Unknown"
        }
    }
}
