import Foundation
import WalletSDK
import XCTest
@testable import iosApp

@MainActor
final class WalletViewModelPinTests: XCTestCase {
    func testSetupRejectsPinsOutsideFourAsciiDigits() {
        for pin in ["123", "12345", "123456", "12a4", "١٢٣٤", "１２３４", "1234\n"] {
            let store = InMemoryDemoPinStore()
            let model = WalletViewModel(
                walletID: "pin-invalid-\(UUID().uuidString)",
                walletClient: MockWalletClient(),
                identityDocumentRegistrationUpdate: {},
                pinStore: store
            )
            model.pin = pin
            model.pinConfirmation = pin
            model.submitPin()

            XCTAssertEqual(model.auth, .setup)
            XCTAssertEqual(model.pinError, "PIN must contain four digits")
            XCTAssertFalse(store.hasPin)
            XCTAssertFalse(model.isReady)
        }
    }

    func testSetupPinUnlocksAndBootstrapsWallet() async throws {
        let pinStore = InMemoryDemoPinStore()
        let viewModel = WalletViewModel(
            walletID: "pin-setup-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore
        )

        XCTAssertEqual(viewModel.auth, .setup)
        XCTAssertFalse(viewModel.isReady)
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        XCTAssertEqual(viewModel.auth, .unlocked)
        XCTAssertTrue(pinStore.hasPin)
        XCTAssertFalse(pinStore.isBiometricUnlockEnabled)
    }

    func testLockReturnsToLoginAndPinUnlocksAgain() async throws {
        let pinStore = InMemoryDemoPinStore()
        let viewModel = WalletViewModel(
            walletID: "pin-lock-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.lock()
        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertTrue(viewModel.isReady)

        viewModel.pin = "123456"
        viewModel.submitPin()
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertTrue(viewModel.isReady)
    }

    func testResetWalletClearsPinAndReturnsToSetup() async throws {
        let pinStore = InMemoryDemoPinStore()
        let viewModel = WalletViewModel(
            walletID: "pin-reset-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }

        XCTAssertFalse(pinStore.hasPin)
        XCTAssertFalse(pinStore.isBiometricUnlockEnabled)
    }

    func testChooseAndConfirmRequireSixDigitsBeforePersisting() async throws {
        let store = InMemoryDemoPinStore()
        let biometrics = FakeDemoBiometricAuthenticator()
        let model = makeModel(store, biometrics)
        for invalid in ["1234", "12345", "1234567", "12a456", "１２３４５６", "1️⃣2️⃣3️⃣4️⃣5️⃣6️⃣", "12345\n"] {
            model.updatePin(invalid)
            model.submitPin()
            XCTAssertEqual(model.pinSetupStep, .choose)
            XCTAssertEqual(model.pinError, "Choose a six-digit PIN")
            XCTAssertFalse(store.hasPin)
        }
        model.updatePin("123456")
        model.updatePinConfirmation("123456")
        model.submitPin()
        XCTAssertEqual(model.pinSetupStep, .confirm)
        XCTAssertFalse(store.hasPin)
        XCTAssertEqual(biometrics.authenticateCalls, 0)
        model.updatePinConfirmation("654321")
        XCTAssertEqual(model.pinError, "PIN confirmation does not match")
        XCTAssertEqual(model.pinConfirmation, "")
        XCTAssertFalse(store.hasPin)
        model.updatePinConfirmation("123456")
        try await waitUntil { model.isReady }
        XCTAssertEqual(model.auth, .unlocked)
        XCTAssertEqual(model.pin, "")
        XCTAssertEqual(model.pinConfirmation, "")
        XCTAssertEqual(biometrics.authenticateCalls, 1)
        XCTAssertTrue(store.isBiometricUnlockEnabled)
    }

    func testExistingUnicodeDigitPinRetainsItsOriginalVerification() async throws {
        let store = InMemoryDemoPinStore()
        let legacyPin = "１２３４５６"
        try await store.setPin(legacyPin)
        let model = makeModel(store, FakeDemoBiometricAuthenticator())
        XCTAssertEqual(model.auth, .login)
        model.updatePin(legacyPin)
        model.submitPin()
        try await waitUntil { model.isReady }
        XCTAssertEqual(model.auth, .unlocked)
    }

    func testBackAllowsEditingAndDiscardsTheOldConfirmation() {
        let model = makeModel(InMemoryDemoPinStore(), FakeDemoBiometricAuthenticator())
        model.updatePin("123456")
        model.submitPin()
        model.updatePinConfirmation("123")
        model.editSetupPin()
        XCTAssertEqual(model.pinSetupStep, .choose)
        XCTAssertEqual(model.pinConfirmation, "")
        model.updatePin("654321")
        model.submitPin()
        model.updatePinConfirmation("123456")
        XCTAssertEqual(model.pinError, "PIN confirmation does not match")
        XCTAssertFalse(model.isAuthenticating)
    }

    func testMatchingConfirmationPromptsOnceAndOsResultControlsOptIn() async throws {
        for result in [DemoBiometricResult.succeeded, .failed] {
            let store = InMemoryDemoPinStore()
            let gate = DemoBiometricTestGate()
            let biometrics = FakeDemoBiometricAuthenticator(gate: gate)
            let model = makeModel(store, biometrics)
            model.updatePin("123456")
            model.submitPin()
            XCTAssertEqual(biometrics.authenticateCalls, 0)
            model.updatePinConfirmation("123456")
            model.submitPin()
            try await waitUntil { biometrics.authenticateCalls == 1 }
            XCTAssertTrue(model.isAuthenticating)
            XCTAssertTrue(store.hasPin)
            XCTAssertFalse(store.isBiometricUnlockEnabled)
            XCTAssertFalse(model.isReady)
            model.editSetupPin()
            model.updatePin("999999")
            XCTAssertEqual(model.pin, "123456")
            XCTAssertEqual(model.pinSetupStep, .confirm)
            await gate.complete(result)
            try await waitUntil { model.isReady }
            XCTAssertEqual(model.auth, .unlocked)
            XCTAssertEqual(store.isBiometricUnlockEnabled, result == .succeeded)
            XCTAssertEqual(biometrics.authenticateCalls, 1)
        }
    }

    func testUnavailableBiometricsFinishesPinOnlyWithoutPrompt() async throws {
        let store = InMemoryDemoPinStore()
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: false)
        let model = makeModel(store, biometrics)
        model.unlockForTests()
        try await waitUntil { model.isReady }
        XCTAssertFalse(store.isBiometricUnlockEnabled)
        XCTAssertEqual(biometrics.authenticateCalls, 0)
    }

    func testPersistenceFailureDoesNotPromptAndRemainsRetryable() async throws {
        let store = FailingDemoPinStore()
        let biometrics = FakeDemoBiometricAuthenticator()
        let model = makeModel(store, biometrics)
        model.unlockForTests()
        try await waitUntil { !model.isAuthenticating }
        XCTAssertEqual(model.auth, .setup)
        XCTAssertEqual(model.pinSetupStep, .confirm)
        XCTAssertEqual(model.pinError, "PIN could not be saved. Try again.")
        XCTAssertEqual(biometrics.authenticateCalls, 0)
        store.fail = false
        model.submitPin()
        try await waitUntil { model.isReady }
        XCTAssertEqual(biometrics.authenticateCalls, 1)
    }

    func testResetIgnoresLateBiometricSuccess() async throws {
        let store = InMemoryDemoPinStore()
        let gate = DemoBiometricTestGate()
        let biometrics = FakeDemoBiometricAuthenticator(gate: gate)
        let model = makeModel(store, biometrics)
        model.unlockForTests()
        try await waitUntil { biometrics.authenticateCalls == 1 }
        model.resetWallet()
        try await waitUntil { model.auth == .setup && !model.isAuthenticating }
        await gate.complete(.succeeded)
        await Task.yield()
        XCTAssertEqual(model.auth, .setup)
        XCTAssertEqual(model.pinSetupStep, .choose)
        XCTAssertFalse(store.hasPin)
        XCTAssertFalse(store.isBiometricUnlockEnabled)
        XCTAssertFalse(model.isReady)
    }

    private func makeModel(_ store: DemoPinStore, _ biometrics: any DemoBiometricAuthenticator) -> WalletViewModel {
        WalletViewModel(walletID: "pin-\(UUID().uuidString)", walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {}, pinStore: store, biometricAuthenticator: biometrics)
    }

    func testBiometricUnlockSkipsPinWhenEnabled() async throws {
        let pinStore = InMemoryDemoPinStore()
        try await pinStore.setPin("1234")
        pinStore.isBiometricUnlockEnabled = true
        let biometrics = FakeDemoBiometricAuthenticator()
        let viewModel = WalletViewModel(
            walletID: "pin-bio-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore,
            biometricAuthenticator: biometrics
        )

        XCTAssertEqual(viewModel.auth, .login)
        viewModel.unlockWithBiometrics()
        try await waitUntil { viewModel.isReady }

        XCTAssertEqual(viewModel.auth, .unlocked)
        XCTAssertEqual(biometrics.authenticateCalls, 1)
    }

    func testCancelledBiometricsLeavesPinFallback() async throws {
        let pinStore = InMemoryDemoPinStore()
        try await pinStore.setPin("1234")
        pinStore.isBiometricUnlockEnabled = true
        let biometrics = FakeDemoBiometricAuthenticator(result: .failed)
        let viewModel = WalletViewModel(
            walletID: "pin-bio-cancel-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore,
            biometricAuthenticator: biometrics
        )

        viewModel.unlockWithBiometrics()
        try await waitUntil { !viewModel.isAuthenticating }
        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertFalse(viewModel.isReady)

        viewModel.pin = "1234"
        viewModel.submitPin()
        try await waitUntil { viewModel.isReady }
        XCTAssertEqual(viewModel.auth, .unlocked)
    }

    func testLockDoesNotAutoPromptBiometrics() async throws {
        let pinStore = InMemoryDemoPinStore()
        try await pinStore.setPin("1234")
        pinStore.isBiometricUnlockEnabled = true
        let biometrics = FakeDemoBiometricAuthenticator()
        let walletClient = MockWalletClient()
        let viewModel = WalletViewModel(
            walletID: "pin-lock-no-auto-\(UUID().uuidString)",
            walletClient: walletClient,
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore,
            biometricAuthenticator: biometrics
        )

        viewModel.unlockWithBiometrics()
        try await waitUntil { viewModel.auth == .unlocked && viewModel.isReady }
        XCTAssertEqual(biometrics.authenticateCalls, 1)
        let bootstrapCallsAfterUnlock = await walletClient.bootstrapCalls
        XCTAssertEqual(bootstrapCallsAfterUnlock, 1)

        viewModel.lock()
        XCTAssertEqual(viewModel.auth, .login)
        viewModel.promptBiometricUnlockIfNeeded()
        viewModel.handleApplicationBecameActive()
        await Task.yield()
        XCTAssertEqual(biometrics.authenticateCalls, 1)
        XCTAssertEqual(viewModel.auth, .login)

        viewModel.unlockWithBiometrics(force: true)
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertEqual(biometrics.authenticateCalls, 2)
        XCTAssertTrue(viewModel.isReady)
        let bootstrapCallsAfterForcedUnlock = await walletClient.bootstrapCalls
        XCTAssertEqual(bootstrapCallsAfterForcedUnlock, bootstrapCallsAfterUnlock)
    }

    func testColdLaunchAutoPromptsBiometricsWithoutActiveScenePhase() async throws {
        let pinStore = InMemoryDemoPinStore()
        try await pinStore.setPin("1234")
        pinStore.isBiometricUnlockEnabled = true
        let biometrics = FakeDemoBiometricAuthenticator()
        let walletClient = MockWalletClient()
        let viewModel = WalletViewModel(
            walletID: "pin-cold-launch-\(UUID().uuidString)",
            walletClient: walletClient,
            identityDocumentRegistrationUpdate: {},
            pinStore: pinStore,
            biometricAuthenticator: biometrics
        )

        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertEqual(biometrics.authenticateCalls, 0)

        viewModel.handleApplicationBecameActive()
        try await waitUntil { viewModel.auth == .unlocked && viewModel.isReady }

        XCTAssertEqual(biometrics.authenticateCalls, 1)
        let bootstrapCalls = await walletClient.bootstrapCalls
        XCTAssertEqual(bootstrapCalls, 1)
    }

    func testFreshSignupReportsBiometricAvailabilityWithoutSceneActivation() {
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: true)
        let viewModel = WalletViewModel(
            walletID: "pin-signup-available-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            biometricAuthenticator: biometrics
        )

        XCTAssertEqual(viewModel.auth, .setup)
        XCTAssertTrue(viewModel.isBiometricUnlockAvailable)
    }

    func testFreshSignupReportsBiometricsUnavailable() {
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: false)
        let viewModel = WalletViewModel(
            walletID: "pin-signup-unavailable-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            identityDocumentRegistrationUpdate: {},
            biometricAuthenticator: biometrics
        )

        XCTAssertEqual(viewModel.auth, .setup)
        XCTAssertFalse(viewModel.isBiometricUnlockAvailable)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 20_000_000_000,
        _ predicate: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !predicate() {
            guard DispatchTime.now().uptimeNanoseconds < deadline else {
                XCTFail("Timed out waiting for wallet state")
                return
            }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
    }
}

final class FakeDemoBiometricAuthenticator: DemoBiometricAuthenticator {
    var isAvailable: Bool
    var result: DemoBiometricResult
    private(set) var authenticateCalls = 0
    let gate: DemoBiometricTestGate?

    init(isAvailable: Bool = true, result: DemoBiometricResult = .succeeded, gate: DemoBiometricTestGate? = nil) {
        self.isAvailable = isAvailable
        self.result = result
        self.gate = gate
    }

    func authenticate(reason: String) async -> DemoBiometricResult {
        authenticateCalls += 1
        if let gate { return await gate.wait() }
        return result
    }
}

actor DemoBiometricTestGate {
    private var result: DemoBiometricResult?
    private var continuation: CheckedContinuation<DemoBiometricResult, Never>?

    func wait() async -> DemoBiometricResult {
        if let result { return result }
        return await withCheckedContinuation { continuation = $0 }
    }

    func complete(_ result: DemoBiometricResult) {
        self.result = result
        continuation?.resume(returning: result)
        continuation = nil
    }
}

private final class FailingDemoPinStore: DemoPinStore {
    let store = InMemoryDemoPinStore()
    var fail = true
    var hasPin: Bool { store.hasPin }
    var isBiometricUnlockEnabled: Bool {
        get { store.isBiometricUnlockEnabled }
        set { store.isBiometricUnlockEnabled = newValue }
    }
    func setPin(_ pin: String) async throws {
        if fail { throw DemoPinRecordError.derivationFailed }
        try await store.setPin(pin)
    }
    func verifyPin(_ pin: String) async -> Bool { await store.verifyPin(pin) }
    func clear() { store.clear() }
}
