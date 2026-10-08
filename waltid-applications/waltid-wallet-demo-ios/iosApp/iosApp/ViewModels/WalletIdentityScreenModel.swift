import CryptoKit
import SwiftUI
import WalletSDK
import WalletDemoSharingUI

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
        case recovery, storage, approval, summary
        var title: String {
            switch self { case .recovery: "Recovery"; case .storage: "Key storage"; case .approval: "Signing approval"; case .summary: "Signing key" }
        }
        func choice(_ option: SetupOption) -> Selection? {
            switch self { case .recovery: option.recovery; case .storage: option.storage; case .approval: option.approval; case .summary: nil }
        }
        func options(_ all: [SetupOption], selected: SetupOption) -> [SetupOption] {
            all.filter { option in
                switch self {
                case .summary: false
                case .recovery: true
                case .storage: option.recovery.id == selected.recovery.id
                case .approval: option.recovery.id == selected.recovery.id && option.storage.id == selected.storage.id
                }
            }
        }
        func select(_ all: [SetupOption], selected: SetupOption, choiceID: String) -> SetupOption {
            let candidates = options(all, selected: selected).filter { choice($0)?.id == choiceID }
            return candidates.first { $0.storage.id == selected.storage.id && $0.approval.id == selected.approval.id }
                ?? candidates.first { $0.approval.id == selected.approval.id } ?? candidates.first ?? selected
        }

    }

    @Published private(set) var setupOptions: [SetupOption] = []
    @Published var step: Step = .summary
    @Published private(set) var selectedID: UUID?
    @Published private(set) var requestedApproval: Selection
    @Published private(set) var existingKeyUnavailable = false
    var selected: SetupOption? {
        setupOptions.first { $0.id == selectedID }
            ?? Self.resolveSetupOption(setupOptions, approvalID: requestedApproval.id)
    }
    var canCreate: Bool { selected?.approval.id == requestedApproval.id && !loadFailed }

    /// The fallback is display-only; continueSetup also checks the requested approval.
    static func resolveSetupOption(_ options: [SetupOption], recoveryID: String? = nil,
        storageID: String? = nil, approvalID: String) -> SetupOption? {
        let matchingRecovery = options.filter { $0.recovery.id == recoveryID }
        let recovery = matchingRecovery.isEmpty ? options : matchingRecovery
        let matchingStorage = recovery.filter { $0.storage.id == storageID }
        let storage = matchingStorage.isEmpty ? recovery : matchingStorage
        return storage.first { $0.approval.id == approvalID } ?? storage.first
    }
    func select(_ id: String) {
        guard let selected, !busy && !refreshing else { return }
        let choice = step.select(setupOptions, selected: selected, choiceID: id)
        if step == .approval { requestedApproval = choice.approval }
        selectedID = Self.resolveSetupOption(setupOptions, recoveryID: choice.recovery.id,
            storageID: choice.storage.id, approvalID: requestedApproval.id)?.id
    }

    func edit(_ setting: SigningKeySetting) {
        guard !busy && !refreshing else { return }
        switch setting {
        case .recovery: step = .recovery
        case .storage: step = .storage
        case .approval: step = .approval
        }
    }

    func continueSetup() {
        guard let selected, !busy && !refreshing else { return }
        if step == .summary {
            guard canCreate else { return }
            perform(selected.restoring ? "Restoring key…" : "Creating key…", selected.perform)
        } else { step = .summary }
    }

    @Published private(set) var identity: SigningIdentity?
    @Published private(set) var choices: [Choice] = []
    @Published private(set) var message: String?
    private var failedSetupSelection: [String]?
    private func configuration(_ option: SetupOption) -> [String] {
        [option.recovery.id, option.storage.id, option.approval.id]
    }
    var setupMessage: String? {
        guard step == .summary else { return nil }
        if let failedSetupSelection, selected.map(configuration) != failedSetupSelection { return nil }
        return message
    }
    var canRetrySetupOperation: Bool { failedSetupSelection != nil && setupMessage != nil && !loadFailed }
    @Published private(set) var busy = false
    @Published private(set) var refreshing = false
    @Published private(set) var loaded = false
    @Published private(set) var progress = ""
    @Published private(set) var loadFailed = false
    @Published private(set) var recoveryUnavailableReasons: [String] = []
    private let service: SigningIdentityManager
    private let onActivated: @MainActor () -> Void

    init(service: SigningIdentityManager, preferredAuthorization: WalletKeyUseAuthorizationPolicy, onActivated: @escaping @MainActor () -> Void) {
        self.service = service
        self.requestedApproval = Self.approvalChoice(preferredAuthorization)
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
            let retained = Self.resolveSetupOption(setupOptions, recoveryID: previous?.recovery.id,
                storageID: previous?.storage.id, approvalID: requestedApproval.id)
            if previous?.recovery.id != retained?.recovery.id || previous?.storage.id != retained?.storage.id { step = .summary }
            selectedID = retained?.id
            refreshing = false
            loaded = true
        }
        do {
            choices = []
            setupOptions = []
            selectedID = nil
            message = nil
            failedSetupSelection = nil
            existingKeyUnavailable = false
            recoveryUnavailableReasons = []
            switch try await service.state() {
            case .active(let identity):
                self.identity = identity
                for option in try await service.backupOptions(identityID: identity.id) {
                    choices.append(Choice(title: "Back up with \(Self.providerTitle(option.providerName))",
                        detail: "Saves a backup of your signing key. Credentials are not included.", destructive: false, progress: "Backing up signing key…") { [service] in
                        try Self.check(await service.backup(option))
                    })
                }
                let discovery = try await service.discoverRecovery()
                recoveryUnavailableReasons = discovery.failures.map { "\(Self.providerTitle($0.providerName)): \($0.message)" }
                for candidate in discovery.candidates where candidate.reference.recordID == identity.id {
                    choices.append(Choice(title: "Delete key backup", detail: Self.providerTitle(candidate.providerName), destructive: true, progress: "Deleting key backup…") { [service] in
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
                            } ?? Selection(id: "new", title: "Without a backup",
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
                existingKeyUnavailable = true
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
            message = previousIdentity == nil ? "Could not load the signing key options. Try again." : "Could not load the signing key details. Try again."
        }
    }

    func perform(_ choice: Choice) {
        perform(choice.progress, choice.perform)
    }

    private func perform(_ label: String, _ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true
        message = nil
        failedSetupSelection = nil
        progress = label
        Task {
            defer { busy = false }
            do {
                try await action()
                await refresh()
                if identity != nil { onActivated() }
            } catch is CancellationError { return }
            catch {
                failedSetupSelection = identity == nil ? selected.map(configuration) : nil
                message = (error as? KeyOperationError)?.errorDescription ?? "Could not complete the signing-key operation. Check device and backup availability, then try again."
            }
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
            detail: "The key is stored in the iOS Keychain. Signing takes place outside the Secure Enclave.")
        case .encryptedDatabase: Selection(id: "database", title: "Encrypted wallet database",
            detail: "The key is stored in the encrypted wallet database and used for software signing. Hardware protection and system signing prompts are not available.")
        }
    }

    static func approvalChoice(_ policy: WalletKeyUseAuthorizationPolicy) -> Selection {
        let title: String
        switch policy {
        case .none: title = "No signing prompt"
        case .biometricCurrentSet: title = "Current biometrics only"
        case .biometricAny: title = "Current and future biometrics"
        case .biometricTimedReuse: title = "Biometrics with timed approval"
        case .deviceCredential: title = "Device passcode"
        case .biometricOrDeviceCredential: title = "Biometrics or device passcode"
        }
        let id: String
        switch policy {
        case .none: id = WalletDemoSigningProtection.none.rawValue
        case .biometricTimedReuse(let seconds) where seconds == 10: id = WalletDemoSigningProtection.biometric.rawValue
        case .biometricCurrentSet: id = WalletDemoSigningProtection.biometricPerUse.rawValue
        default: id = String(describing: policy)
        }
        return Selection(id: id, title: title, detail: authorization(policy))
    }

    static func authorization(_ policy: WalletKeyUseAuthorizationPolicy) -> String {
        switch policy {
        case .none: "Signing does not require system approval."
        case .biometricCurrentSet: "Approve each signature with biometrics. Changing enrolled biometrics makes this key unusable."
        case .biometricAny: "Approve each use with biometrics, including newly enrolled biometrics. Security changes can still make the key unavailable."
        case .biometricTimedReuse(let seconds): "Approve signing with biometrics. The system may reuse that approval for \(duration(seconds))."
        case .deviceCredential(let seconds): "Approve signing with the device passcode. " + reuse(seconds)
        case .biometricOrDeviceCredential(let seconds): "Approve signing with biometrics or the device passcode. " + reuse(seconds)
        }
    }

    private static func duration(_ seconds: Int) -> String { seconds == 1 ? "1 second" : "\(seconds) seconds" }

    private static func reuse(_ seconds: Int) -> String {
        seconds == 0 ? "Approval is required for each use." : "The system may reuse that approval for \(duration(seconds))."
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
        case .keyUnavailable: "The signing key is unavailable."
        case .invalidRecoveryRecord: "This backup could not be validated. Choose another backup; no replacement key was created."
        case .authorizationNotCompleted: "Signing approval was not completed. Try again and approve the system prompt."
        case .nativeOperationFailed: "The device could not complete the key operation. Try again; protection has not been reduced."
        case .providerUnavailable: "The backup provider is unavailable. Check its availability, then try again."
        case .existingIdentity: "A signing key is already active or being set up. Complete or cancel that setup first."
        case .providerInteractionRequired: "Unlock or sign in to your backup provider, then retry setup."
        case .providerRejected: "The backup provider rejected this request. Check its access and storage settings, or choose another backup option."
        case .providerConflict: "A different backup already uses this identifier. Choose another backup destination. The existing backup has not been overwritten."
        case .providerConfirmationPending: "The backup provider has not confirmed the backup yet. Check again later to continue setup with the same key."
        }
    }
}
