import CryptoKit
import SwiftUI
import WalletSDK

@MainActor
final class WalletIdentityScreenModel: ObservableObject {
    struct Choice: Identifiable {
        let id = UUID()
        let title: String
        let detail: String
        let destructive: Bool
        let progress: String
        let perform: @MainActor () async throws -> Void
    }

    struct Selection: Identifiable, Equatable {
        let id: String
        let title: String
        let detail: String
        var identifier: String? = nil
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
        if step == .approval { perform(selected.restoring ? "Restoring key…" : "Creating key…", selected.perform) }
        else if let next = Step(rawValue: step.rawValue + 1) { step = next }
    }

    @Published private(set) var identity: SigningIdentity?
    @Published private(set) var choices: [Choice] = []
    @Published private(set) var message: String?
    @Published private(set) var busy = false
    @Published private(set) var refreshing = false
    @Published private(set) var loaded = false
    @Published private(set) var progress = ""
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
            loaded = true
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
                    choices.append(Choice(title: "Back up with \(Self.providerTitle(option.providerName))",
                        detail: "Saves a backup of your signing key. Credentials are not included.", destructive: false, progress: "Saving backup…") { [service] in
                        try Self.check(await service.backup(option))
                    })
                }
                let discovery = try await service.discoverRecovery()
                recoveryUnavailableReasons = discovery.failures.map { "\(Self.providerTitle($0.providerName)): \($0.message)" }
                for candidate in discovery.candidates where candidate.reference.recordID == identity.id {
                    choices.append(Choice(title: "Delete key backup", detail: Self.providerTitle(candidate.providerName), destructive: true, progress: "Deleting backup…") { [service] in
                        _ = try await service.deleteRecovery(candidate)
                    })
                }
            case .pending(let id, let reason):
                identity = nil
                message = reason.explanation
                if reason.canRetry {
                    choices.append(Choice(title: "Retry setup", detail: "Continues setup with the same signing key.", destructive: false, progress: "Resuming setup…") { [service] in
                        try Self.check(await service.resumePending(identityID: id))
                    })
                }
                choices.append(Choice(title: "Cancel pending setup", detail: "Removes pending local setup; submitted key backups remain.", destructive: false, progress: "Cancelling setup…") { [service] in
                    try await service.cancelPending(identityID: id)
                })
            case .absent:
                identity = nil
                var unavailableReasons: [String] = []
                for intent in [SigningIdentityIntent.withoutRecovery, .recoverable] {
                    switch try await service.creationOptions(intent: intent) {
                    case .unavailable(let reasons): unavailableReasons += reasons
                    case .available(let recommended, let alternatives):
                        for option in [recommended] + alternatives {
                            let recovery = option.recoveryProviderName.map { provider in
                                Selection(id: "backup:\(provider)", title: "Back up with \(Self.providerTitle(provider))",
                                    detail: "Save a backup of your signing key. Credentials are not included. Saving on this device does not confirm cloud delivery.")
                            } ?? Selection(id: "new", title: "Create without a key backup",
                                detail: "No recovery backup is created. If the key is lost, credentials using it may need to be issued again.")
                            setupOptions.append(SetupOption(recovery: recovery,
                                storage: Self.storageChoice(option.storage), approval: Self.approvalChoice(option.authorization), restoring: false) { [service] in
                                    try Self.check(await service.create(option))
                                })
                        }
                    }
                }
                try await addRecoveryChoices()
                if setupOptions.isEmpty && !unavailableReasons.isEmpty {
                    message = Array(Set(unavailableReasons)).sorted().joined(separator: "\n")
                }
            case .unavailable(_, let reason):
                identity = nil
                message = reason.explanation
                try await addRecoveryChoices()
            }
        } catch {
            identity = previousIdentity
            choices = previousChoices
            setupOptions = previousOptions
            recoveryUnavailableReasons = previousUnavailableReasons
            if error is CancellationError { return }
            loadFailed = true
            message = "Signing key options could not be loaded. Try again."
        }
    }

    func perform(_ choice: Choice) {
        perform(choice.progress, choice.perform)
    }

    private func perform(_ label: String, _ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true
        progress = label
        Task {
            defer { busy = false }
            do {
                try await action()
                await refresh()
                if identity != nil { onActivated() }
            } catch is CancellationError { return }
            catch { message = (error as? KeyOperationError)?.errorDescription ?? "Could not complete the signing-key operation. Check device and backup availability, then try again." }
        }
    }

    private func addRecoveryChoices() async throws {
        let discovery = try await service.discoverRecovery()
        recoveryUnavailableReasons += discovery.failures.map { "\(Self.providerTitle($0.providerName)): \($0.message)" }
        for candidate in discovery.candidates {
            let options: [SigningIdentityRestorationOption]
            do { options = try await service.restorationOptions(candidate) }
            catch is CancellationError { throw CancellationError() }
            catch {
                recoveryUnavailableReasons.append("\(candidate.providerName): Could not read this key backup. Try again.")
                continue
            }
            for option in options {
                setupOptions.append(SetupOption(
                    recovery: Selection(id: "restore:\(candidate.reference)", title: "Restore from \(Self.providerTitle(candidate.providerName))",
                        detail: "Restore the original signing key. Credentials are not included.\nKey \(SHA256.hash(data: Data(option.did.utf8)).prefix(6).map { String(format: "%02x", $0) }.joined())", identifier: option.did),
                    storage: Self.storageChoice(option.storage), approval: Self.approvalChoice(option.authorization), restoring: true) { [service] in
                        try Self.check(await service.restore(option))
                    })
            }
        }
    }

    private static func check(_ result: SigningIdentityOperationResult) throws {
        if case .failed(let reason) = result { throw KeyOperationError(errorDescription: reason.explanation) }
    }
    static func providerTitle(_ name: String) -> String { name == "iCloud Keychain recovery" ? "iCloud Keychain" : name }

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
        case .biometricCurrentSet: title = "Biometrics · current enrollment"
        case .biometricAny: title = "Biometrics · allow new enrollment"
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
        case .biometricAny: "Approve each use with biometrics, including newly enrolled biometrics. Security changes can still make the key unavailable."
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
                        detailRow("Storage requirement", WalletIdentityScreenModel.storage(identity.storage))
                        detailRow("Signing protection", identity.securityLevel.displayName)
                        detailRow("Key origin", identity.origin.displayName)
                        detailRow("Signing approval", WalletIdentityScreenModel.authorization(identity.authorization))
                        detailRow("Recovery", recoveryDescription(identity.recovery))
                    }
                    Section {
                        Text("Reset this wallet to choose different key storage or signing approval. This removes local credentials; key recovery does not restore them.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                } else if let selected = model.selected {
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            if model.step == .recovery { Text("Your wallet uses a signing key to prove that you hold your credentials.") }
                            Text("\(model.step.rawValue + 1) of 3 · \(model.step.title)").font(.title3.weight(.semibold))
                            Text(stepDescription).font(.callout).foregroundStyle(.secondary)
                            if model.step == .storage && selected.recovery.id != "new" {
                                Text("The Secure Enclave cannot restore a key. Recoverable keys use Keychain or the encrypted wallet database.").font(.callout)
                            }
                        }.listRowBackground(Color.clear)
                    }.id("setup-top")
                    if model.step == .recovery {
                        if model.selections.contains(where: { !$0.id.hasPrefix("restore:") }) {
                            Section("Create a new key") { selectionRows(restoring: false) }
                        }
                        if model.selections.contains(where: { $0.id.hasPrefix("restore:") }) {
                            Section("Restore an existing key") { selectionRows(restoring: true) }
                        }
                    } else {
                        Section { selectionRows() }
                    }
                    if model.step == .approval {
                        Section("Your selection") {
                            detailRow("Recovery", selected.recovery.title)
                            detailRow("Key storage", selected.storage.title)
                            detailRow("Signing approval", selected.approval.title)
                            Text("\(selected.restoring ? "Restores" : "Creates") your signing key. Key recovery does not restore credentials.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                if !model.recoveryUnavailableReasons.isEmpty && (model.identity != nil || model.step == .recovery) {
                    Section("Backup availability") {
                        ForEach(Array(Set(model.recoveryUnavailableReasons)).sorted(), id: \.self) { Text($0).font(.callout).foregroundStyle(.secondary) }
                        Button("Check again") { Task { await model.refresh() } }
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
                    if model.busy { ProgressView(model.progress) }
                    else if model.refreshing || !model.loaded { ProgressView("Checking signing key options…") }
                    if model.loaded && !model.refreshing && model.identity == nil && model.setupOptions.isEmpty && model.choices.isEmpty && model.message == nil {
                        Text("No signing-key option is currently available for this device and app configuration.")
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
        .navigationTitle(model.identity == nil ? "Set up your wallet" : "Protection and recovery")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh() }
        .onChange(of: scenePhase) { phase in
            if phase == .active && !model.busy { Task { await model.refresh() } }
        }
        .alert("Delete key backup?", isPresented: Binding(get: { deletion != nil }, set: { if !$0 { deletion = nil } })) {
            if let choice = deletion { Button("Delete key backup", role: .destructive) { model.perform(choice); deletion = nil } }
            Button("Cancel", role: .cancel) { deletion = nil }
        } message: {
            Text("This requests deletion of the key backup from the provider. You may lose the ability to recover this key. Keys already restored on other devices are not erased.")
        }
    }

    private var stepDescription: String {
        switch model.step {
        case .recovery: "Choose whether to back up a new signing key or restore an existing one."
        case .storage: "Choose where signing happens. Only storage compatible with your recovery choice is shown."
        case .approval: "Choose when the system asks you to approve signing. This is separate from unlocking the app."
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.callout)
        }
    }

    private func selectionRows(restoring: Bool? = nil) -> some View {
        ForEach(Array(model.selections.enumerated()).filter { _, choice in
            restoring == nil || choice.id.hasPrefix("restore:") == restoring
        }, id: \.element.id) { index, choice in
            VStack(alignment: .leading, spacing: 8) {
                selectionCard(choice, selected: model.selected.map { model.step.choice($0).id == choice.id } ?? false)
                    .accessibilityIdentifier("wallet.keySetupChoice.\(model.step).\(index)")
                if let identifier = choice.identifier {
                    DisclosureGroup("Show full identifier") {
                        Text(identifier).font(.caption).textSelection(.enabled)
                    }.font(.caption).padding(.horizontal, 16)
                }
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
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
        case .disabled: "No key backup submitted."
        case .submitted(_, let receipt): receipt == .acceptedLocally ? "Saved on this device. Delivery to another device is not confirmed." : "Backup confirmed by the provider."
        case .recovered: "The original signing key was restored on this installation."
        case .removalRequested: "Backup deletion requested. Removal from other devices is not confirmed."
        }
    }
}

private struct KeyOperationError: LocalizedError {
    let errorDescription: String?
}

private extension SigningIdentityFailure {
    var canRetry: Bool {
        switch self {
        case .providerConflict, .providerRejected, .invalidRecoveryRecord, .unsupportedPolicy, .existingIdentity, .keyUnavailable: false
        default: true
        }
    }

    var explanation: String {
        switch self {
        case .unsupportedPolicy: "This device cannot use the selected protection. Choose another supported option."
        case .staleOption: "The available options have changed. Check the options again before continuing."
        case .keyUnavailable: "The signing key is unavailable. Restore its backup to use this key again."
        case .invalidRecoveryRecord: "This backup could not be validated. Choose another backup; no replacement key was created."
        case .authorizationNotCompleted: "Signing approval was not completed. Try again and approve the system prompt."
        case .nativeOperationFailed: "The device could not complete the key operation. Try again; protection has not been reduced."
        case .providerUnavailable: "The backup provider is unavailable. Check its availability, then try again."
        case .existingIdentity: "A signing key is already active or being set up. Complete or cancel that setup first."
        case .providerInteractionRequired: "Unlock or sign in to your backup provider, then retry setup."
        case .providerRejected: "The backup provider rejected this request. Check its access and storage settings, or choose another backup option."
        case .providerConflict: "A different backup already uses this identifier. Choose another backup destination. The existing backup has not been overwritten."
        case .providerConfirmationPending: "The provider has not met the required backup confirmation. Check again later to continue setup with the same key."
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
