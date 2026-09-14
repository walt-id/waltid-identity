import SwiftUI
import WalletSDK

@MainActor
final class WalletIdentityScreenModel: ObservableObject {
    struct Choice: Identifiable {
        let id = UUID()
        let title: String
        let detail: String
        let destructive: Bool
        let perform: @MainActor () async throws -> Void
    }

    @Published private(set) var identity: WalletIdentity?
    @Published private(set) var choices: [Choice] = []
    @Published private(set) var message: String?
    @Published private(set) var busy = false
    private var refreshing = false
    private let service: WalletIdentityService
    private let onActivated: @MainActor () -> Void

    init(service: WalletIdentityService, onActivated: @escaping @MainActor () -> Void) {
        self.service = service
        self.onActivated = onActivated
    }

    func refresh() async {
        guard !refreshing else { return }
        refreshing = true
        defer { refreshing = false }
        do {
            choices = []
            message = nil
            switch try await service.state() {
            case .active(let identity):
                self.identity = identity
                for option in try await service.backupOptions(identityID: identity.id) {
                    choices.append(Choice(title: "Back up with \(option.providerName)",
                        detail: "Retains a secret capable of recreating this signing identity.", destructive: false) { [service] in
                        try Self.check(await service.backup(option))
                    })
                }
                for candidate in try await service.recoveryCandidates() where candidate.reference.recordID == identity.id {
                    choices.append(Choice(title: "Delete recovery record", detail: candidate.providerName, destructive: true) { [service] in
                        _ = try await service.deleteRecovery(candidate)
                    })
                }
            case .pending(let id, let reason):
                identity = nil
                switch reason {
                case .providerInteractionRequired: message = "Unlock or sign in to your recovery provider, then retry setup."
                case .providerConflict: message = "The backup destination contains a different record. Resolve the conflict before retrying."
                case .providerRejected: message = "The recovery provider rejected this backup. Check its access and storage settings."
                case .providerConfirmationPending: message = "The provider has not confirmed backup delivery yet. Retry after delivery completes."
                default: message = "Identity setup is pending. Retry to resume the recorded operation."
                }
                choices.append(Choice(title: "Retry setup", detail: "Keeps the original identity.", destructive: false) { [service] in
                    try Self.check(await service.resumePending(identityID: id))
                })
                choices.append(Choice(title: "Cancel pending setup", detail: "Removes pending local setup; submitted recovery records remain.", destructive: false) { [service] in
                    try await service.cancelPending(identityID: id)
                })
            case .absent:
                identity = nil
                for intent in [WalletIdentityIntent.withoutRecovery, .recoverable] {
                    if case .available(let recommended, let alternatives) = try await service.creationOptions(intent: intent) {
                        for option in [recommended] + alternatives {
                            choices.append(Choice(title: "Create with \(Self.storage(option.storage))",
                                detail: "\(Self.authorization(option.authorization)). " + (option.recoveryProviderName.map {
                                    "Recovery through \($0); the recovery secret exists outside signing hardware."
                                } ?? "No recovery backup; device loss may require credential reissuance."), destructive: false) { [service] in
                                    try Self.check(await service.create(option))
                                })
                        }
                    }
                }
                try await addRecoveryChoices()
            case .unavailable(_, let reason):
                identity = nil
                message = "Existing identity needs attention: \(reason)."
                try await addRecoveryChoices()
            }
        } catch is CancellationError { return }
        catch { message = error.localizedDescription }
    }

    func perform(_ choice: Choice) {
        guard !busy else { return }
        busy = true
        Task {
            defer { busy = false }
            do {
                try await choice.perform()
                await refresh()
                if identity != nil { onActivated() }
            } catch is CancellationError { return }
            catch { message = error.localizedDescription }
        }
    }

    private func addRecoveryChoices() async throws {
        for candidate in try await service.recoveryCandidates() {
            for option in try await service.restorationOptions(candidate) {
                choices.append(Choice(title: "Restore with \(Self.storage(option.storage))",
                    detail: "\(candidate.providerName). \(Self.authorization(option.authorization)). \(option.did)", destructive: false) { [service] in
                        try Self.check(await service.restore(option))
                    })
            }
        }
    }

    private static func check(_ result: WalletIdentityOperationResult) throws {
        if case .failed(let reason) = result { throw WalletError.invalidInput("Identity operation failed: \(reason)") }
    }
    static func storage(_ storage: WalletIdentityStorage) -> String {
        switch storage {
        case .hardware: "Secure Enclave"
        case .nativeStorage: "ordinary Keychain"
        case .encryptedDatabase: "encrypted database"
        }
    }
    static func authorization(_ policy: WalletKeyUseAuthorizationPolicy) -> String {
        switch policy {
        case .none: "No native signing authorization; app authentication is separate"
        case .biometricCurrentSet: "Current biometric enrollment"
        case .biometricAny: "Any enrolled biometric"
        case .biometricTimedReuse(let seconds): "Biometric authorization reusable for \(seconds) seconds"
        case .deviceCredential(let seconds): "Device passcode, \(seconds == 0 ? "each use" : "\(seconds)-second reuse")"
        case .biometricOrDeviceCredential(let seconds): "Biometric or device passcode, \(seconds == 0 ? "each use" : "\(seconds)-second reuse")"
        }
    }
}

struct WalletIdentityView: View {
    @ObservedObject var model: WalletIdentityScreenModel
    @State private var deletion: WalletIdentityScreenModel.Choice?

    var body: some View {
        List {
            Section("Signing identity") {
                Text("Secure Enclave keys cannot be restored on another device. Recoverable iOS identities use ordinary Keychain or software signing.")
                    .font(.callout)
                if let identity = model.identity {
                    HStack { Text("Storage"); Spacer(); Text(WalletIdentityScreenModel.storage(identity.storage)).foregroundStyle(.secondary) }
                    HStack { Text("Key origin"); Spacer(); Text(String(describing: identity.origin)).foregroundStyle(.secondary) }
                    Text(WalletIdentityScreenModel.authorization(identity.authorization))
                    Text(identity.did).font(.caption).textSelection(.enabled)
                    Text(recoveryDescription(identity.recovery)).font(.callout)
                }
            }
            if let message = model.message { Section { Text(message).foregroundStyle(.secondary) } }
            Section(model.identity == nil ? "Choose an identity" : "Recovery") {
                ForEach(model.choices) { choice in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(choice.detail).font(.caption).foregroundStyle(.secondary)
                        Button(choice.title, role: choice.destructive ? .destructive : nil) {
                            if choice.destructive { deletion = choice } else { model.perform(choice) }
                        }
                    }.disabled(model.busy)
                }
                if model.busy { ProgressView() }
                Button("Refresh options") { Task { await model.refresh() } }.disabled(model.busy)
            }
            Section {
                Text("A successful OS write confirms local acceptance. It does not confirm cloud delivery or availability on another device. Deleting a recovery record does not erase copies already restored elsewhere.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(model.identity == nil ? "Set up identity" : "Signing identity")
        .task { await model.refresh() }
        .confirmationDialog("Delete this recovery record?", isPresented: Binding(get: { deletion != nil }, set: { if !$0 { deletion = nil } })) {
            if let choice = deletion { Button("Delete recovery record", role: .destructive) { model.perform(choice); deletion = nil } }
            Button("Cancel", role: .cancel) { deletion = nil }
        }
    }

    private func recoveryDescription(_ state: WalletIdentityRecoveryState) -> String {
        switch state {
        case .disabled: "No recovery backup submitted."
        case .submitted(_, let receipt): receipt == .acceptedLocally ? "Recovery record accepted locally; cloud delivery unknown." : "Recovery submission confirmed by provider."
        case .recovered: "The original signing identity was recovered on this installation."
        case .removalRequested: "Recovery record deletion requested; removal from other devices is not verified."
        }
    }
}
