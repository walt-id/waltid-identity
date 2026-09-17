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

    struct Selection: Identifiable, Equatable {
        let id: String
        let title: String
        let detail: String
    }

    struct SetupOption: Identifiable {
        let id = UUID()
        let recovery: Selection
        let storage: Selection
        let approval: Selection
        let restoring: Bool
        let perform: @MainActor () async throws -> Void
    }

    enum Step: Int, CaseIterable {
        case recovery, storage, approval
        var title: String {
            switch self { case .recovery: "Recovery"; case .storage: "Key storage"; case .approval: "Signing approval" }
        }
        func choice(_ option: SetupOption) -> Selection {
            switch self { case .recovery: option.recovery; case .storage: option.storage; case .approval: option.approval }
        }
        func options(_ all: [SetupOption], selected: SetupOption) -> [SetupOption] {
            all.filter { option in
                switch self {
                case .recovery: true
                case .storage: option.recovery.id == selected.recovery.id
                case .approval: option.recovery.id == selected.recovery.id && option.storage.id == selected.storage.id
                }
            }
        }
        func select(_ all: [SetupOption], selected: SetupOption, choiceID: String) -> SetupOption {
            let candidates = options(all, selected: selected).filter { choice($0).id == choiceID }
            return candidates.first { $0.storage.id == selected.storage.id && $0.approval.id == selected.approval.id }
                ?? candidates.first { $0.approval.id == selected.approval.id } ?? candidates.first ?? selected
        }

    }

    @Published private(set) var setupOptions: [SetupOption] = []
    @Published var step: Step = .recovery
    @Published private(set) var selectedID: UUID?
    var selected: SetupOption? { setupOptions.first { $0.id == selectedID } ?? setupOptions.first }
    var selections: [Selection] {
        guard let selected else { return [] }
        return step.options(setupOptions, selected: selected).map(step.choice).reduce(into: []) { values, next in
            if !values.contains(where: { $0.id == next.id }) { values.append(next) }
        }
    }

    func select(_ id: String) {
        guard let selected else { return }
        selectedID = step.select(setupOptions, selected: selected, choiceID: id).id
    }

    func continueSetup() {
        guard let selected, !busy else { return }
        if step == .approval { perform(selected.perform) }
        else if let next = Step(rawValue: step.rawValue + 1) { step = next }
    }

    @Published private(set) var identity: SigningIdentity?
    @Published private(set) var choices: [Choice] = []
    @Published private(set) var message: String?
    @Published private(set) var busy = false
    @Published private(set) var refreshing = false
    @Published private(set) var loadFailed = false
    @Published private(set) var recoveryUnavailableReasons: [String] = []
    private let service: SigningIdentityManager
    private let onActivated: @MainActor () -> Void

    init(service: SigningIdentityManager, onActivated: @escaping @MainActor () -> Void) {
        self.service = service
        self.onActivated = onActivated
    }

    func refresh() async {
        guard !refreshing else { return }
        let previous = selected
        let previousIdentity = identity
        let previousChoices = choices
        let previousOptions = setupOptions
        let previousUnavailableReasons = recoveryUnavailableReasons
        refreshing = true
        loadFailed = false
        defer {
            if let previous, let retained = setupOptions.first(where: {
                $0.recovery.id == previous.recovery.id && $0.storage.id == previous.storage.id &&
                    $0.approval.id == previous.approval.id
            }) {
                selectedID = retained.id
            } else {
                step = .recovery
                selectedID = setupOptions.first?.id
            }
            refreshing = false
        }
        do {
            choices = []
            setupOptions = []
            selectedID = nil
            message = nil
            recoveryUnavailableReasons = []
            switch try await service.state() {
            case .active(let identity):
                self.identity = identity
                for option in try await service.backupOptions(identityID: identity.id) {
                    choices.append(Choice(title: "Back up with \(option.providerName)",
                        detail: "Saves a recovery secret for this signing key and DID. Credentials are not included.", destructive: false) { [service] in
                        try Self.check(await service.backup(option))
                    })
                }
                let discovery = try await service.discoverRecovery()
                recoveryUnavailableReasons = discovery.failures.map { "\($0.providerName): \($0.message)" }
                for candidate in discovery.candidates where candidate.reference.recordID == identity.id {
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
                default: message = "Key setup is pending. Retry to continue with the same key and DID."
                }
                choices.append(Choice(title: "Retry setup", detail: "Keeps the original signing key and DID.", destructive: false) { [service] in
                    try Self.check(await service.resumePending(identityID: id))
                })
                choices.append(Choice(title: "Cancel pending setup", detail: "Removes pending local setup; submitted recovery records remain.", destructive: false) { [service] in
                    try await service.cancelPending(identityID: id)
                })
            case .absent:
                identity = nil
                for intent in [SigningIdentityIntent.withoutRecovery, .recoverable] {
                    if case .available(let recommended, let alternatives) = try await service.creationOptions(intent: intent) {
                        for option in [recommended] + alternatives {
                            let recovery = option.recoveryProviderName.map { provider in
                                Selection(id: "backup:\(provider)", title: "Back up with \(provider)",
                                    detail: "Save a recovery secret for the same signing key and DID. Credentials are not included. Cloud delivery depends on the provider and is not confirmed by a local save.")
                            } ?? Selection(id: "new", title: "No recovery backup",
                                detail: "Keep this key on this device. If it is lost, you may need to have your credentials issued again.")
                            setupOptions.append(SetupOption(recovery: recovery,
                                storage: Self.storageChoice(option.storage), approval: Self.approvalChoice(option.authorization), restoring: false) { [service] in
                                    try Self.check(await service.create(option))
                                })
                        }
                    }
                }
                try await addRecoveryChoices()
            case .unavailable(_, let reason):
                identity = nil
                message = "The existing signing key needs attention: \(reason)."
                try await addRecoveryChoices()
            }
        } catch {
            identity = previousIdentity
            choices = previousChoices
            setupOptions = previousOptions
            recoveryUnavailableReasons = previousUnavailableReasons
            if error is CancellationError { return }
            loadFailed = true
            message = error.localizedDescription
        }
    }

    func perform(_ choice: Choice) {
        perform(choice.perform)
    }

    private func perform(_ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true
        Task {
            defer { busy = false }
            do {
                try await action()
                await refresh()
                if identity != nil { onActivated() }
            } catch is CancellationError { return }
            catch { message = error.localizedDescription }
        }
    }

    private func addRecoveryChoices() async throws {
        let discovery = try await service.discoverRecovery()
        recoveryUnavailableReasons += discovery.failures.map { "\($0.providerName): \($0.message)" }
        for candidate in discovery.candidates {
            let options: [SigningIdentityRestorationOption]
            do { options = try await service.restorationOptions(candidate) }
            catch is CancellationError { throw CancellationError() }
            catch {
                recoveryUnavailableReasons.append("\(candidate.providerName): Could not read this recovery record. Try again.")
                continue
            }
            for option in options {
                setupOptions.append(SetupOption(
                    recovery: Selection(id: "restore:\(candidate.reference)", title: "Restore from \(candidate.providerName)",
                        detail: "Restore the original signing key and DID. Credentials are not included.\n\(option.did)"),
                    storage: Self.storageChoice(option.storage), approval: Self.approvalChoice(option.authorization), restoring: true) { [service] in
                        try Self.check(await service.restore(option))
                    })
            }
        }
    }

    private static func check(_ result: SigningIdentityOperationResult) throws {
        if case .failed(let reason) = result { throw WalletError.invalidInput("Identity operation failed: \(reason)") }
    }
    static func storage(_ storage: SigningIdentityKeyStorage) -> String { storageChoice(storage).title }

    static func storageChoice(_ storage: SigningIdentityKeyStorage) -> Selection {
        switch storage {
        case .hardwareBacked: Selection(id: "hardware", title: "Secure Enclave",
            detail: "Generates and uses the key inside the Secure Enclave. This key cannot be restored on another device.")
        case .nativeStorage: Selection(id: "native", title: "Keychain",
            detail: "Stores the key in the iOS Keychain. Signing runs outside the Secure Enclave.")
        case .encryptedDatabase: Selection(id: "database", title: "Encrypted wallet database",
            detail: "Stores the key in the encrypted wallet database and signs in software. No hardware protection or system signing prompt.")
        }
    }

    static func approvalChoice(_ policy: WalletKeyUseAuthorizationPolicy) -> Selection {
        let title: String
        switch policy {
        case .none: title = "No signing prompt"
        case .biometricCurrentSet: title = "Current biometrics"
        case .biometricAny: title = "Biometrics"
        case .biometricTimedReuse(let seconds): title = "Biometrics · \(seconds)s reuse"
        case .deviceCredential: title = "Device passcode"
        case .biometricOrDeviceCredential: title = "Biometrics or device passcode"
        }
        return Selection(id: String(describing: policy), title: title, detail: authorization(policy))
    }

    static func authorization(_ policy: WalletKeyUseAuthorizationPolicy) -> String {
        switch policy {
        case .none: "Signing does not ask for system approval. The wallet PIN and app-unlock biometrics are separate."
        case .biometricCurrentSet: "Approve each signature with biometrics. Changing enrolled biometrics makes this key unusable."
        case .biometricAny: "Approve each signature with biometrics. Changing enrolled biometrics keeps this key usable."
        case .biometricTimedReuse(let seconds): "Approve signing with biometrics. The system can reuse approval for \(seconds) seconds."
        case .deviceCredential(let seconds): "Approve signing with the device passcode. " + reuse(seconds)
        case .biometricOrDeviceCredential(let seconds): "Approve signing with biometrics or the device passcode. " + reuse(seconds)
        }
    }

    private static func reuse(_ seconds: Int) -> String {
        seconds == 0 ? "Approval is required for each use." : "Approval can be reused for \(seconds) seconds."
    }
}

struct WalletIdentityView: View {
    @ObservedObject var model: WalletIdentityScreenModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var deletion: WalletIdentityScreenModel.Choice?

    var body: some View {
        ScrollViewReader { proxy in
            List {
                if let identity = model.identity {
                    Section("Wallet signing key") {
                        HStack { Text("Storage"); Spacer(); Text(WalletIdentityScreenModel.storage(identity.storage)).foregroundStyle(.secondary) }
                        HStack { Text("Key origin"); Spacer(); Text(String(describing: identity.origin)).foregroundStyle(.secondary) }
                        Text(WalletIdentityScreenModel.authorization(identity.authorization))
                        Text(identity.did).font(.caption).textSelection(.enabled)
                        Text(recoveryDescription(identity.recovery)).font(.callout)
                    }
                } else if let selected = model.selected {
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Your wallet uses a signing key to prove that you hold your credentials.")
                            Text("\(model.step.rawValue + 1) of 3 · \(model.step.title)").font(.title3.weight(.semibold))
                            Text(stepDescription).font(.callout).foregroundStyle(.secondary)
                            if model.step == .recovery {
                                ForEach(model.recoveryUnavailableReasons, id: \.self) { Text($0).font(.callout) }
                            }
                            if model.step == .storage && selected.recovery.id != "new" {
                                Text("The Secure Enclave cannot restore a key. Recoverable keys use Keychain or the encrypted wallet database.").font(.callout)
                            }
                        }.listRowBackground(Color.clear)
                    }.id("setup-top")
                    Section {
                        ForEach(Array(model.selections.enumerated()), id: \.element.id) { index, choice in
                            selectionCard(choice, selected: model.step.choice(selected).id == choice.id)
                                .accessibilityIdentifier("wallet.keySetupChoice.\(model.step).\(index)")
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                        }
                    }
                    if model.step == .approval {
                        Section("Your selection") {
                            Text(selected.recovery.title)
                            Text(selected.storage.title)
                            Text("\(selected.restoring ? "Restores" : "Creates") a signing key and its wallet identifier (DID). Credentials are not restored by key recovery.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                if let message = model.message { Section { Text(message).foregroundStyle(.secondary) } }
                if !model.choices.isEmpty {
                    Section(model.identity == nil ? "Pending setup" : "Recovery") {
                        ForEach(model.choices) { choice in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(choice.detail).font(.caption).foregroundStyle(.secondary)
                                Button(choice.title, role: choice.destructive ? .destructive : nil) {
                                    if choice.destructive { deletion = choice } else { model.perform(choice) }
                                }
                            }
                        }
                    }
                }
                Section {
                    if model.busy { ProgressView("Saving signing key…") }
                    if model.identity == nil && model.setupOptions.isEmpty && model.choices.isEmpty && model.message == nil {
                        Text("No configured option is available. Check device authorization and backup settings, then return to the app.")
                    }
                    if model.loadFailed || (model.identity == nil && model.setupOptions.isEmpty && model.choices.isEmpty) ||
                        (model.selected != nil && model.step == .recovery && !model.recoveryUnavailableReasons.isEmpty) {
                        Button("Try again") { Task { await model.refresh() } }
                    }
                }
                if model.identity != nil {
                    Section {
                        Text("A local save does not confirm cloud delivery. Deleting a recovery record does not erase copies already restored elsewhere.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }
            .disabled(model.busy || model.refreshing)
            .onChange(of: model.step) { _ in proxy.scrollTo("setup-top", anchor: .top) }
            .safeAreaInset(edge: .bottom) {
                if let selected = model.selected {
                    HStack(spacing: 12) {
                        if model.step != .recovery {
                            Button("Back") { model.step = WalletIdentityScreenModel.Step(rawValue: model.step.rawValue - 1) ?? .recovery }
                        }
                        Button {
                            model.continueSetup()
                        } label: {
                            Text(model.step != .approval ? "Continue" : selected.restoring ? "Restore signing key" : "Create signing key")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("wallet.keySetupContinue")
                    }
                    .disabled(model.busy || model.refreshing)
                    .padding().background(.bar)
                }
            }
        }
        .navigationTitle(model.identity == nil ? "Set up your wallet" : "Wallet signing key")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh() }
        .onChange(of: scenePhase) { phase in
            if phase == .active && !model.busy { Task { await model.refresh() } }
        }
        .alert("Delete recovery record?", isPresented: Binding(get: { deletion != nil }, set: { if !$0 { deletion = nil } })) {
            if let choice = deletion { Button("Delete recovery record", role: .destructive) { model.perform(choice); deletion = nil } }
            Button("Cancel", role: .cancel) { deletion = nil }
        } message: {
            Text("This requests deletion from the provider. It does not erase signing keys already restored on other devices.")
        }
    }

    private var stepDescription: String {
        switch model.step {
        case .recovery: "Choose whether to back up a new signing key or restore an existing one."
        case .storage: "Choose where signing happens. Only storage compatible with your recovery choice is shown."
        case .approval: "Choose when the system asks you to approve signing. This is separate from unlocking the app."
        }
    }

    private func selectionCard(_ choice: WalletIdentityScreenModel.Selection, selected: Bool) -> some View {
        Button { model.select(choice.id) } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? Color.accentColor : .secondary)
                VStack(alignment: .leading, spacing: 6) {
                    Text(choice.title).font(.headline).foregroundStyle(.primary)
                    Text(choice.detail).font(.callout).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(selected ? Color.accentColor.opacity(0.1) : Color(uiColor: .secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: selected ? 2 : 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private func recoveryDescription(_ state: SigningIdentityRecoveryState) -> String {
        switch state {
        case .disabled: "No recovery backup submitted."
        case .submitted(_, let receipt): receipt == .acceptedLocally ? "Recovery record accepted locally; delivery to another device is not confirmed." : "Recovery submission confirmed by provider."
        case .recovered: "The original signing key and DID were restored on this installation."
        case .removalRequested: "Recovery record deletion requested; removal from other devices is not verified."
        }
    }
}
