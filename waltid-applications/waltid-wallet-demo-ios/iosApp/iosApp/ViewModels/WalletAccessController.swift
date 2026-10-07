import Foundation
import Combine

enum WalletAuthState: Equatable {
    case setup
    case login
    case biometricSetup(DemoBiometricResult?)
    case storageUnavailable(String)
    case unlocked
}

enum PinSetupStep { case choose, confirm }

enum WalletAccessText {
    static let pinMustContain4Digits = "PIN must contain four digits"
    static let pinConfirmationDoesNotMatch = "PIN confirmation does not match"
    static let wrongPin = "Wrong PIN"
    static let enableBiometricUnlock = "Enable biometric unlock"
}

enum WalletAccessOperation: Equatable {
    case idle, checkingPin, savingPin, biometrics
    case retryPin(String)
    var isBusy: Bool { self == .checkingPin || self == .savingPin || self == .biometrics }
}

enum WalletPinChange: Equatable {
    case current, newPin
}

struct WalletAccessNotice: Equatable {
    let message: String
    let isError: Bool
}

/// App unlock and access settings only. Signing keys and wallet bootstrap remain with the host.
@MainActor
final class WalletAccessController: ObservableObject {
    @Published var auth: WalletAuthState
    @Published var pin = ""
    @Published var confirmation = ""
    @Published private(set) var step: PinSetupStep = .choose
    @Published private(set) var error: String?
    @Published private(set) var operation: WalletAccessOperation = .idle
    @Published private(set) var biometricAvailable: Bool
    @Published private(set) var biometricEnabled: Bool
    @Published private(set) var biometricOutcome: DemoBiometricResult?
    @Published private(set) var pinChange: WalletPinChange?
    @Published private(set) var settingsNotice: WalletAccessNotice?
    var beforeSetupSave: () -> Void = {}
    var onUnlocked: () -> Void = {}
    private let store: any DemoPinStore
    private let biometrics: any DemoBiometricAuthenticator
    private var job: Task<Void, Never>?
    private var generation = 0
    private var automaticPromptConsumed = false

    init(store: any DemoPinStore, biometrics: any DemoBiometricAuthenticator) {
        self.store = store
        self.biometrics = biometrics
        auth = store.hasPin ? .login : .setup
        biometricAvailable = biometrics.isAvailable
        biometricEnabled = !store.isBiometricSetupPending && store.isBiometricUnlockEnabled
    }

    var biometricKind: DemoBiometricKind { biometrics.kind }
    var isBusy: Bool { operation.isBusy }
    var isCreatingPin: Bool { auth == .setup || pinChange == .newPin }
    var shouldPromptBiometrics: Bool {
        auth == .login && biometricEnabled && biometricAvailable && !automaticPromptConsumed
    }
    var pinError: String? {
        if case .retryPin(let message) = operation { return message }
        return error
    }

    func updatePin(_ value: String, confirming: Bool = false) {
        guard !isBusy, auth == .login || isCreatingPin || pinChange == .current else { return }
        if confirming {
            guard isCreatingPin, step == .confirm, value != confirmation else { return }
            confirmation = value
        } else {
            guard !isCreatingPin || step == .choose, value != pin else { return }
            pin = value
            confirmation = ""
        }
        error = nil
        biometricOutcome = nil
        operation = .idle
        if Self.validPin(value) { submitPin() }
    }

    func clearPin() {
        guard !isBusy else { return }
        if isCreatingPin && step == .confirm { confirmation = "" }
        else { pin = ""; confirmation = "" }
        error = nil
        biometricOutcome = nil
        operation = .idle
    }

    func back() {
        guard isCreatingPin, !isBusy else { return }
        if step == .confirm { step = .choose }
        else if pinChange != nil { pinChange = .current }
        clearDraft()
    }

    func submitPin() {
        guard !isBusy, auth == .login || isCreatingPin || pinChange == .current else { return }
        guard Self.validPin(pin) else { error = WalletAccessText.pinMustContain4Digits; return }
        if isCreatingPin {
            if step == .choose { step = .confirm; confirmation = ""; error = nil }
            else if !Self.validPin(confirmation) { return }
            else if pin != confirmation { confirmation = ""; error = WalletAccessText.pinConfirmationDoesNotMatch }
            else { savePin() }
        } else { checkPin() }
    }

    func unlockWithBiometrics(force: Bool = false) {
        guard auth == .login, !isBusy, force || !automaticPromptConsumed,
              biometricEnabled, biometrics.isAvailable else { return }
        automaticPromptConsumed = true
        clearDraft()
        biometricOutcome = nil
        operation = .biometrics
        let attempt = generation
        job = Task {
            let result = await biometrics.authenticate(reason: "Unlock the wallet")
            guard isCurrent(attempt) else { return }
            if result == .succeeded { unlock() }
            else { biometricOutcome = result; operation = .idle; refreshBiometrics() }
        }
    }

    func refreshBiometrics() {
        biometricAvailable = biometrics.isAvailable
        biometricEnabled = !store.isBiometricSetupPending && store.isBiometricUnlockEnabled
    }

    func retryBiometricSetup() {
        guard case .biometricSetup = auth, !isBusy else { return }
        operation = .biometrics
        error = nil
        let attempt = generation
        job = Task { await offerBiometrics(attempt) }
    }

    func continueWithoutBiometrics() {
        guard case .biometricSetup = auth, !isBusy else { return }
        finishBiometricSetup(false)
    }

    func startPinChange() {
        guard auth == .unlocked, !isBusy else { return }
        cancelAttempt()
        clearDraft()
        step = .choose
        pinChange = .current
        settingsNotice = nil
    }

    func cancelPinChange() {
        // Disappearing settings must not cancel a prompt owned by the new unlock session.
        guard auth == .unlocked, operation != .savingPin else { return }
        cancelAttempt()
        pinChange = nil
        clearDraft()
    }

    func setBiometricUnlock(_ enabled: Bool) {
        guard auth == .unlocked, pinChange == nil, !isBusy else { return }
        settingsNotice = nil
        if !enabled { saveBiometricPreference(false); return }
        guard biometrics.isAvailable else {
            settingsNotice = WalletAccessNotice(message: "Biometric unlock is unavailable on this device.", isError: true)
            refreshBiometrics()
            return
        }
        operation = .biometrics
        let attempt = generation
        job = Task {
            let result = await biometrics.authenticate(reason: WalletAccessText.enableBiometricUnlock)
            guard isCurrent(attempt) else { return }
            if result == .succeeded { saveBiometricPreference(true) }
            else {
                operation = .idle
                refreshBiometrics()
                settingsNotice = WalletAccessNotice(message: result.fallbackMessage ?? "Biometric unlock was not enabled. You can try again.", isError: true)
            }
        }
    }

    func lock() {
        cancelAttempt()
        clearDraft()
        pinChange = nil
        settingsNotice = nil
        automaticPromptConsumed = false
        biometricOutcome = nil
        auth = .login
    }

    func reset() {
        cancelAttempt()
        store.clear()
        clearDraft()
        step = .choose
        pinChange = nil
        settingsNotice = nil
        automaticPromptConsumed = false
        biometricOutcome = nil
        auth = .setup
        refreshBiometrics()
    }

    func cancelAttempt() { generation += 1; job?.cancel(); job = nil; operation = .idle }

    private func checkPin() {
        operation = .checkingPin
        error = nil
        let entered = pin
        let attempt = generation
        job = Task {
            do {
                let matches = try await store.verifyPin(entered)
                guard isCurrent(attempt) else { return }
                operation = .idle
                if !matches { pin = ""; error = WalletAccessText.wrongPin }
                else if pinChange == .current { clearDraft(); pinChange = .newPin; step = .choose }
                else if store.isBiometricSetupPending { clearDraft(); auth = .biometricSetup(nil) }
                else { unlock() }
            } catch {
                guard isCurrent(attempt) else { return }
                operation = .retryPin("PIN could not be verified. Try again.")
            }
        }
    }

    private func savePin() {
        operation = .savingPin
        error = nil
        let confirmed = pin
        let changing = pinChange != nil
        let attempt = generation
        job = Task {
            do {
                let offer = !changing && biometrics.isAvailable
                if !changing { beforeSetupSave(); store.isBiometricSetupPending = offer }
                try Task.checkCancellation()
                try await store.setPin(confirmed)
                guard isCurrent(attempt) else { return }
                clearDraft()
                if changing {
                    operation = .idle
                    pinChange = nil
                    settingsNotice = WalletAccessNotice(message: "PIN changed", isError: false)
                } else {
                    auth = .biometricSetup(nil)
                    store.isBiometricUnlockEnabled = false
                    if offer { operation = .biometrics; await offerBiometrics(attempt) }
                    else { finishBiometricSetup(false) }
                }
            } catch {
                guard isCurrent(attempt) else { return }
                operation = .retryPin("PIN could not be saved. Try again.")
            }
        }
    }

    private func offerBiometrics(_ attempt: Int) async {
        let result = await biometrics.authenticate(reason: WalletAccessText.enableBiometricUnlock)
        guard isCurrent(attempt) else { return }
        if result == .succeeded { finishBiometricSetup(true) }
        else { auth = .biometricSetup(result); operation = .idle; refreshBiometrics() }
    }

    private func finishBiometricSetup(_ enabled: Bool) {
        store.isBiometricUnlockEnabled = enabled
        store.isBiometricSetupPending = false
        unlock()
    }

    private func saveBiometricPreference(_ enabled: Bool) {
        store.isBiometricUnlockEnabled = enabled
        biometricEnabled = enabled
        operation = .idle
        settingsNotice = WalletAccessNotice(message: enabled ? "Biometric unlock enabled" : "Biometric unlock turned off", isError: false)
    }

    private func unlock() {
        clearDraft()
        auth = .unlocked
        operation = .idle
        refreshBiometrics()
        onUnlocked()
    }

    private func clearDraft() { pin = ""; confirmation = ""; error = nil }
    private func isCurrent(_ attempt: Int) -> Bool { !Task.isCancelled && attempt == generation }
    static let pinLength = 4
    static func validPin(_ value: String) -> Bool { value.utf8.count == pinLength && value.utf8.allSatisfy { (48...57).contains($0) } }
}
