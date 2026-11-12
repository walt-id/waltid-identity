import Foundation
import XCTest
import WalletDemoSharingUI
@testable import WalletSDK
@testable import iosApp

@MainActor
final class WalletViewModelCredentialStatusTests: XCTestCase {
    func testDeferredNoticesAccumulateNamesAndOnlyAnnounceNewAdverseTransitions() async throws {
        let fixtures = try WalletVisualFixtures()
        let first = try fixtures.credential()
        let second = Credential(id: "address", format: "dc+sd-jwt", issuer: nil, subject: nil,
            label: "Address card", addedAt: nil, credentialDataJSON: "{}")
        let clear = try fixtures.credentialStatus("clear")
        let revoked = try fixtures.credentialStatus("revoked_partial")
        let suspended = try fixtures.credentialStatus("suspended")
        func snapshot(_ one: CredentialStatus, _ two: CredentialStatus) -> CredentialStatusSnapshot {
            CredentialStatusSnapshot(evaluatedAt: Date(), credentials: [
                .init(credentialID: first.id, storeIndex: 0, value: one),
                .init(credentialID: second.id, storeIndex: 0, value: two),
            ], refreshingCredentialIDs: [])
        }
        let client = MockWalletClient(credentialStatusSnapshot: snapshot(clear, clear))
        let model = WalletViewModel(walletID: "status-notice-test", walletClient: client,
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(),
            identityDocumentRegistrationUpdate: {}, pinStore: InMemoryDemoPinStore(),
            biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false))
        model.automaticCredentialStatusChecks = false
        defer {
            model.handleApplicationEnteredBackground()
            UserDefaults.standard.removeObject(forKey: "wallet.status-notice-test.status.automatic")
        }
        model.credentials = [first, second]; model.auth = .unlocked; model.isReady = true
        try await waitUntil { model.credentialStatuses.count == 2 }
        model.isLoading = true
        await client.setCredentialStatusSnapshot(snapshot(revoked, clear))
        try await waitUntil { model.credentialStatuses[first.id]?.presentation.issue == .revoked }
        await client.setCredentialStatusSnapshot(snapshot(revoked, suspended))
        try await waitUntil { model.credentialStatuses[second.id]?.presentation.issue == .suspended }
        XCTAssertNil(model.credentialStatusNotice)
        model.isLoading = false
        XCTAssertTrue(try XCTUnwrap(model.credentialStatusNotice).contains("Resident card"))
        XCTAssertTrue(try XCTUnwrap(model.credentialStatusNotice).contains("Address card"))
        model.credentialStatusNotice = nil

        let p = revoked.presentation
        let newer = CredentialStatus(status: revoked.status, presentation: .init(kind: p.kind, issue: p.issue,
            freshness: .fresh, action: p.action, lastCheckedAt: p.lastCheckedAt, lastAttemptAt: p.lastAttemptAt,
            failure: p.failure, purposes: p.purposes, coverage: p.coverage), evaluatedAt: Date())
        await client.setCredentialStatusSnapshot(snapshot(newer, suspended))
        try await waitUntil { model.credentialStatuses[first.id]?.presentation.freshness == .fresh }
        XCTAssertNil(model.credentialStatusNotice, "Freshness changes do not report a new issuer status")
        await client.setCredentialStatusSnapshot(snapshot(clear, suspended))
        try await waitUntil { model.credentialStatuses[first.id]?.presentation.issue == CredentialStatusIssue.none }
        await client.setCredentialStatusSnapshot(snapshot(revoked, suspended))
        try await waitUntil { model.credentialStatusNotice != nil }
    }

    func testDiscardingTheModelReleasesItsStatusCollector() async throws {
        let client = MockWalletClient()
        var model: WalletViewModel? = WalletViewModel(walletID: "status-release-test", walletClient: client,
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(),
            identityDocumentRegistrationUpdate: {}, pinStore: InMemoryDemoPinStore(),
            biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false))
        model?.automaticCredentialStatusChecks = false
        defer { UserDefaults.standard.removeObject(forKey: "wallet.status-release-test.status.automatic") }
        model?.auth = .unlocked; model?.isReady = true
        try await waitUntil { await client.statusCollectorCount == 1 }
        weak var released = model
        model = nil
        try await waitUntil {
            let count = await client.statusCollectorCount
            return released == nil && count == 0
        }
    }

    func testForegroundTimerUsesSharedDueTimeAndRejectsLateCallbacksAfterCancellation() async throws {
        let key = "wallet.status-timer-test.status.automatic"
        let saved = UserDefaults.standard.object(forKey: key)
        UserDefaults.standard.set(false, forKey: key)
        defer { UserDefaults.standard.set(saved, forKey: key) }
        let clock = StatusClock()
        // Supply only scheduling facts here; shared-core tests derive due times from signed evidence.
        let snapshot = CredentialStatusSnapshot(evaluatedAt: clock.now, credentials: [],
            refreshingCredentialIDs: [], nextRefreshAt: clock.now.addingTimeInterval(60))
        let client = MockWalletClient(credentialStatusSnapshot: snapshot)
        let model = WalletViewModel(walletID: "status-timer-test",
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(), walletClient: client,
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(),
            identityDocumentRegistrationUpdate: {}, pinStore: InMemoryDemoPinStore(),
            biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false),
            statusNow: { clock.now }, statusSleep: { await clock.sleep($0) })
        defer { model.handleApplicationEnteredBackground(); clock.resumeAll() }
        model.auth = .unlocked
        model.isReady = true
        model.automaticCredentialStatusChecks = true
        try await waitUntil { await client.statusRefreshCalls == 1 && !model.checkingCredentialStatuses && !clock.waits.isEmpty }
        XCTAssertEqual(clock.delays.last, 60)

        clock.now.addTimeInterval(60)
        clock.resumeAll()
        try await waitUntil { await client.statusRefreshCalls == 2 && !model.checkingCredentialStatuses && !clock.waits.isEmpty }
        XCTAssertEqual(clock.delays.last, 30, "A due time left in the past must use bounded retry spacing")

        model.handleApplicationEnteredBackground()
        clock.now.addTimeInterval(30)
        clock.resumeAll() // Deliberately ignores sleep cancellation to exercise the generation fence.
        for _ in 0..<10 { await Task.yield() }
        let hiddenCalls = await client.statusRefreshCalls
        XCTAssertEqual(hiddenCalls, 2)

        model.handleApplicationBecameActive()
        try await waitUntil { await client.statusRefreshCalls == 3 && !model.checkingCredentialStatuses && !clock.waits.isEmpty }
        model.automaticCredentialStatusChecks = false
        clock.resumeAll()
        for _ in 0..<10 { await Task.yield() }
        let disabledCalls = await client.statusRefreshCalls
        XCTAssertEqual(disabledCalls, 3)
        model.checkCredentialStatuses()
        try await waitUntil { await client.statusRefreshCalls == 4 && !model.checkingCredentialStatuses }
    }

    private func waitUntil(_ condition: @MainActor () async -> Bool) async throws {
        let deadline = Date().addingTimeInterval(5)
        while !(await condition()) {
            guard Date() < deadline else { XCTFail("Status work did not reach its expected state"); return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    private final class StatusClock {
        var now = Date(timeIntervalSince1970: 1_800_000_000)
        var delays: [TimeInterval] = []
        var waits: [CheckedContinuation<Void, Never>] = []
        func sleep(_ seconds: TimeInterval) async {
            delays.append(seconds)
            await withCheckedContinuation { waits.append($0) }
        }
        func resumeAll() {
            let pending = waits
            waits.removeAll()
            pending.forEach { $0.resume() }
        }
    }
}
