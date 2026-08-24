import Foundation
import WalletSDK
import XCTest
@testable import iosApp

@MainActor
final class WalletViewModelPinTests: XCTestCase {
    func testSetupPinUnlocksAndBootstrapsWallet() async throws {
        let pinStore = InMemoryDemoPinStore()
        let viewModel = WalletViewModel(
            walletID: "pin-setup-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
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
            pinStore: pinStore
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.lock()
        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertTrue(viewModel.isReady)

        viewModel.pin = "1234"
        viewModel.submitPin()
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertTrue(viewModel.isReady)
    }

    func testResetWalletClearsPinAndReturnsToSetup() async throws {
        let pinStore = InMemoryDemoPinStore()
        let viewModel = WalletViewModel(
            walletID: "pin-reset-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            pinStore: pinStore
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }

        XCTAssertFalse(pinStore.hasPin)
        XCTAssertFalse(pinStore.isBiometricUnlockEnabled)
    }

    func testEnablingBiometricsRequestsPermissionBeforePersisting() async throws {
        let pinStore = InMemoryDemoPinStore()
        let biometrics = FakeDemoBiometricAuthenticator()
        let viewModel = WalletViewModel(
            walletID: "pin-bio-enable-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            pinStore: pinStore,
            biometricAuthenticator: biometrics
        )

        XCTAssertEqual(viewModel.auth, .setup)
        viewModel.refreshBiometricAvailability()
        viewModel.updateUseBiometrics(true)
        try await waitUntil { !viewModel.isAuthenticating }

        XCTAssertTrue(viewModel.useBiometrics)
        XCTAssertEqual(biometrics.authenticateCalls, 1)

        viewModel.unlockForTests()
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertTrue(pinStore.isBiometricUnlockEnabled)
    }

    func testCancelledBiometricEnableLeavesPinOnly() async throws {
        let pinStore = InMemoryDemoPinStore()
        let biometrics = FakeDemoBiometricAuthenticator(result: .failed)
        let viewModel = WalletViewModel(
            walletID: "pin-bio-enable-cancel-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            pinStore: pinStore,
            biometricAuthenticator: biometrics
        )

        viewModel.refreshBiometricAvailability()
        viewModel.updateUseBiometrics(true)
        try await waitUntil { !viewModel.isAuthenticating }

        XCTAssertFalse(viewModel.useBiometrics)
        XCTAssertEqual(biometrics.authenticateCalls, 1)

        viewModel.unlockForTests()
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertFalse(pinStore.isBiometricUnlockEnabled)
    }

    func testBiometricUnlockSkipsPinWhenEnabled() async throws {
        let pinStore = InMemoryDemoPinStore()
        try await pinStore.setPin("1234")
        pinStore.isBiometricUnlockEnabled = true
        let biometrics = FakeDemoBiometricAuthenticator()
        let viewModel = WalletViewModel(
            walletID: "pin-bio-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
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

    func testFreshSignupReportsBiometricAvailabilityWithoutSceneActivation() {
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: true)
        let viewModel = WalletViewModel(
            walletID: "pin-signup-available-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
            biometricAuthenticator: biometrics
        )

        XCTAssertEqual(viewModel.auth, .setup)
        XCTAssertTrue(viewModel.isBiometricUnlockAvailable)
        XCTAssertFalse(viewModel.useBiometrics)
    }

    func testFreshSignupKeepsBiometricToggleDisabledWhenUnavailable() {
        let biometrics = FakeDemoBiometricAuthenticator(isAvailable: false)
        let viewModel = WalletViewModel(
            walletID: "pin-signup-unavailable-\(UUID().uuidString)",
            walletClient: MockWalletClient(),
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

    init(isAvailable: Bool = true, result: DemoBiometricResult = .succeeded) {
        self.isAvailable = isAvailable
        self.result = result
    }

    func authenticate(reason: String) async -> DemoBiometricResult {
        authenticateCalls += 1
        return result
    }
}
