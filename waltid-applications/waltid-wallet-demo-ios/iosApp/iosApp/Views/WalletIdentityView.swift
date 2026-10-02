import SwiftUI
import WalletSDK
import WalletDemoSharingUI

struct WalletIdentityView: View {
    @ObservedObject var model: WalletIdentityScreenModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var deletion: WalletIdentityScreenModel.Choice?

    var body: some View {
        ScrollViewReader { proxy in
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
                    SigningKeySetupContent(options: model.setupOptions, selected: selected, step: model.step,
                        onSelect: model.select, onEdit: model.edit)
                }
                if !model.recoveryUnavailableReasons.isEmpty && (model.identity != nil || model.step == .recovery) {
                    Section("Backup availability") {
                        ForEach(Array(Set(model.recoveryUnavailableReasons)).sorted(), id: \.self) { Text($0).font(.callout).foregroundStyle(.secondary) }
                        Button("Check again") { Task { await model.refresh() } }
                    }
                }
                if model.identity == nil, let message = model.message { Section { Text(message).foregroundStyle(.red) } }
                if model.identity == nil && !model.choices.isEmpty {
                    Section("Pending setup") { identityActions }
                }
                Section {
                    if model.busy && model.identity == nil { ProgressView(model.progress) }
                    else if model.refreshing || !model.loaded {
                        ProgressView(model.identity == nil ? "Loading signing key options…" : "Loading signing key details…")
                    }
                    if model.loaded && !model.refreshing && model.identity == nil && model.setupOptions.isEmpty && model.choices.isEmpty && model.message == nil {
                        Text("No signing key options are available for this device and app configuration.")
                    }
                    if model.loadFailed || (model.loaded && !model.refreshing && model.identity == nil && model.setupOptions.isEmpty && model.choices.isEmpty) {
                        Button("Try again") { Task { await model.refresh() } }
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
            .onChange(of: model.step) { _ in proxy.scrollTo("setup-top", anchor: .top) }
            .safeAreaInset(edge: .bottom) {
                if let selected = model.selected {
                    WalletActionBar(
                        primary: WalletAction(
                            model.step != .summary ? String(localized: "Done")
                                : selected.restoring ? String(localized: "Restore signing key") : String(localized: "Create signing key"),
                            enabled: !model.busy && !model.refreshing, identifier: "wallet.keySetupContinue",
                            perform: model.continueSetup
                        ),
                        secondary: model.step == .summary ? nil : WalletAction(String(localized: "Back"),
                            enabled: !model.busy && !model.refreshing) {
                                model.step = .summary
                            }
                    )
                }
            }
        }
        .navigationTitle(model.identity == nil ? "Set up your wallet" : "Signing key")
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
                    Label(choice.title, systemImage: choice.destructive ? "trash" : "square.and.arrow.down")
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
