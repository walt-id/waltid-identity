import Combine
import Foundation
import XCTest
import WalletDemoSharingUI
@testable import WalletSDK
@testable import iosApp

@MainActor
final class WalletViewModelCredentialStatusTests: XCTestCase {
    func testExplicitReviewRefreshPreservesSelectionAndWaitsForItsRenderedAssessment() async throws {
        let client = MockWalletClient(paymentTransaction: false)
        let model = try await reviewModel(client, walletID: "status-review-completion-test")
        let barrier = StatusRefreshBarrier()
        defer {
            model.handleApplicationEnteredBackground()
            UserDefaults.standard.removeObject(forKey: "wallet.status-review-completion-test.status.automatic")
            Task { await barrier.resume() }
        }
        await client.setStatusRefreshResults([], waitFor: { await barrier.wait() })
        let preview = try XCTUnwrap(model.presentationPreview)
        let credentialID = try XCTUnwrap(preview.credentialOptions.first?.credentialID)
        let credentials = model.selectedPresentationCredentialOptions
        let disclosures = model.selectedPresentationDisclosureOptions
        let initialCalls = await client.statusRefreshCalls
        XCTAssertEqual(initialCalls, 0, "Review entry performs no status refresh with automatic checks off")
        model.checkPresentationCredentialStatus(credentialID, handle: preview.previewHandle, navigationKey: model.presentationNavigationResetKey)
        XCTAssertEqual(model.presentationStatusRefresh?.phase, .checking)
        try await waitUntil { await barrier.started }
        model.submitPresentation(reviewedStatus: model.credentialStatuses.mapValues { $0.presentation.advisoryRevision })
        var submitted = await client.submittedPaymentConsentRevisions
        XCTAssertTrue(submitted.isEmpty)
        await barrier.resume()
        try await waitUntil { model.presentationStatusRefresh?.phase == .awaitingRender }
        XCTAssertEqual(model.selectedPresentationCredentialOptions, credentials)
        XCTAssertEqual(model.selectedPresentationDisclosureOptions, disclosures)
        model.submitPresentation(reviewedStatus: model.credentialStatuses.mapValues { $0.presentation.advisoryRevision })
        submitted = await client.submittedPaymentConsentRevisions
        XCTAssertTrue(submitted.isEmpty, "Refresh completion cannot bypass the rendered-result acknowledgement")
        model.presentationStatusRefreshRendered(UUID())
        XCTAssertNotNil(model.presentationStatusRefresh)
        model.presentationStatusRefreshRendered(try XCTUnwrap(model.presentationStatusRefresh).id)
        XCTAssertNil(model.presentationStatusRefresh)
        submitted = await client.submittedPaymentConsentRevisions
        XCTAssertTrue(submitted.isEmpty, "Refresh does not automatically submit")
        model.submitPresentation(reviewedStatus: model.credentialStatuses.mapValues { $0.presentation.advisoryRevision })
        try await waitUntil { await client.submittedPaymentConsentRevisions.count == 1 }
    }

    func testUnownedStatusWorkDoesNotBecomeANewSubmissionPrecondition() async throws {
        let client = MockWalletClient(paymentTransaction: false)
        let model = try await reviewModel(client, walletID: "status-unowned-refresh-test")
        let barrier = StatusRefreshBarrier()
        defer {
            model.handleApplicationEnteredBackground()
            UserDefaults.standard.removeObject(forKey: "wallet.status-unowned-refresh-test.status.automatic")
            Task { await barrier.resume() }
        }
        await client.setStatusRefreshResults([], waitFor: { await barrier.wait() })
        model.checkCredentialStatuses(ids: [try XCTUnwrap(model.presentationPreview?.credentialOptions.first?.credentialID)])
        try await waitUntil { await barrier.started }
        XCTAssertTrue(model.checkingCredentialStatuses)
        XCTAssertNil(model.presentationStatusRefresh)
        model.submitPresentation(reviewedStatus: model.credentialStatuses.mapValues { $0.presentation.advisoryRevision })
        try await waitUntil { await client.submittedPaymentConsentRevisions.count == 1 }
        await barrier.resume()
    }

    func testFailedReviewRefreshRetainsEvidenceAndSettingsReportUntilErrorIsDisplayed() async throws {
        let client = MockWalletClient(paymentTransaction: false)
        let model = try await reviewModel(client, walletID: "status-review-failure-test")
        defer {
            model.handleApplicationEnteredBackground()
            UserDefaults.standard.removeObject(forKey: "wallet.status-review-failure-test.status.automatic")
        }
        let previous = model.credentialStatuses
        let report = CredentialStatusRefreshSummary(completed: 2, unchanged: 1, deferred: 0, checkErrors: 0)
        model.credentialStatusSummary = report
        await client.setStatusRefreshResults([], fails: true)
        let preview = try XCTUnwrap(model.presentationPreview)
        model.checkPresentationCredentialStatus(try XCTUnwrap(preview.credentialOptions.first?.credentialID),
            handle: preview.previewHandle, navigationKey: model.presentationNavigationResetKey)
        try await waitUntil { model.presentationStatusRefresh?.phase == .awaitingRender }
        XCTAssertEqual(model.credentialStatuses, previous)
        XCTAssertNotNil(model.credentialStatusError)
        XCTAssertNil(model.credentialStatusBatchError)
        XCTAssertEqual(model.credentialStatusSummary, report)
        model.presentationStatusRefreshRendered(try XCTUnwrap(model.presentationStatusRefresh).id)
        XCTAssertNil(model.presentationStatusRefresh)
    }

    func testReviewDisposalReplacementBackgroundAndLockIgnoreLateRefreshCompletion() async throws {
        for exit in ["dispose", "replace", "background", "lock"] {
            let client = MockWalletClient(paymentTransaction: false)
            let walletID = "status-review-close-\(exit)-test"
            let model = try await reviewModel(client, walletID: walletID)
            let barrier = StatusRefreshBarrier()
            defer {
                model.handleApplicationEnteredBackground()
                UserDefaults.standard.removeObject(forKey: "wallet.\(walletID).status.automatic")
                Task { await barrier.resume() }
            }
            await client.setStatusRefreshResults([], waitFor: { await barrier.wait() })
            let preview = try XCTUnwrap(model.presentationPreview)
            let key = model.presentationNavigationResetKey
            model.checkPresentationCredentialStatus(try XCTUnwrap(preview.credentialOptions.first?.credentialID), handle: preview.previewHandle, navigationKey: key)
            try await waitUntil { await barrier.started }
            model.releasePresentationStatusRefresh(handle: PresentationPreviewHandle(value: "foreign"), navigationKey: key)
            XCTAssertNotNil(model.presentationStatusRefresh)
            switch exit {
            case "dispose": model.releasePresentationStatusRefresh(handle: preview.previewHandle, navigationKey: key)
            case "replace": model.cancelPresentationReview()
            case "background": model.handleApplicationEnteredBackground()
            default: model.lock()
            }
            XCTAssertNil(model.presentationStatusRefresh, exit)
            XCTAssertFalse(model.checkingCredentialStatuses, exit)
            let snapshotReads = await client.statusSnapshotReads
            let lateResult = expectation(description: "A closed \(exit) review must not install its late result")
            lateResult.isInverted = true
            let observation = model.$credentialStatuses.dropFirst().sink { _ in lateResult.fulfill() }
            await barrier.resume()
            try await waitUntil { await barrier.finished }
            await fulfillment(of: [lateResult], timeout: 0.2)
            withExtendedLifetime(observation) {}
            let after = await client.statusSnapshotReads
            XCTAssertEqual(after, snapshotReads, "A late \(exit) result must not fetch/install an assessment")
            XCTAssertNil(model.presentationStatusRefresh)
        }
    }

    private func reviewModel(_ client: MockWalletClient, walletID: String) async throws -> WalletViewModel {
        let model = WalletViewModel(walletID: walletID, walletClient: client,
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(), identityDocumentRegistrationUpdate: {},
            pinStore: InMemoryDemoPinStore(), biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false))
        model.automaticCredentialStatusChecks = false
        model.unlockForTests()
        try await waitUntil { model.isReady }
        model.presentationRequestUrl = "openid4vp://mock"
        model.previewPresentation()
        try await waitUntil { model.presentationReviewEnabled && model.paymentReview.canConfirm }
        let credentialID = try XCTUnwrap(model.presentationPreview?.credentialOptions.first?.credentialID)
        let clear = try WalletVisualFixtures().credentialStatus("clear")
        await client.setCredentialStatusSnapshot(CredentialStatusSnapshot(evaluatedAt: Date(), credentials: [
            .init(credentialID: credentialID, storeIndex: 0, value: clear),
        ], refreshingCredentialIDs: []))
        try await waitUntil { model.credentialStatuses[credentialID] != nil }
        return model
    }

    func testPendingInitialLocalStatusReadIsNeutralAndFailureIsUnavailable() async throws {
        let client = MockWalletClient()
        let model = WalletViewModel(walletID: "status-initial-read-test", walletClient: client,
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(), identityDocumentRegistrationUpdate: {},
            pinStore: InMemoryDemoPinStore(), biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false))
        model.automaticCredentialStatusChecks = false
        defer {
            model.handleApplicationEnteredBackground()
            UserDefaults.standard.removeObject(forKey: "wallet.status-initial-read-test.status.automatic")
        }
        model.auth = .unlocked; model.isReady = true
        try await waitUntil { await client.statusCollectorCount == 1 }
        XCTAssertEqual(model.credentialStatusReadState, .loading)
        XCTAssertFalse(model.credentialStatusAvailable)
        if case .loading(nil) = model.credentialStatusUIContext.displayStatus(for: "pending") {} else {
            XCTFail("A pending initial local read must remain neutral")
        }
        await client.failCredentialStatusObservation()
        try await waitUntil { model.credentialStatusReadState == .unavailable }
        if case .unavailable(nil) = model.credentialStatusUIContext.displayStatus(for: "pending") {} else {
            XCTFail("A completed failed read must show unavailable feedback")
        }
        let snapshot = CredentialStatusSnapshot(evaluatedAt: Date(), credentials: [], refreshingCredentialIDs: [])
        await client.setCredentialStatusSnapshot(snapshot)
        model.handleApplicationEnteredBackground()
        model.handleApplicationBecameActive()
        try await waitUntil { model.credentialStatusAvailable }
        XCTAssertEqual(model.credentialStatusReadState, .available)
    }

    func testOnlySettingsBatchesReplaceTheirStructuredReportAndErrors() async throws {
        let fixtures = try WalletVisualFixtures()
        let failed = try fixtures.credentialStatus("unverified").status
        let clear = try fixtures.credentialStatus("clear").status
        func result(_ id: String, _ outcome: CredentialStatusRefreshOutcome, _ status: CredentialStatusMetadata) throws -> CredentialStatusRefreshResult {
            let data = try JSONSerialization.data(withJSONObject: ["storeIndex": 0, "credentialId": id])
            return CredentialStatusRefreshResult(reference: try JSONDecoder().decode(CredentialStatusReference.self, from: data),
                outcome: outcome, status: status)
        }
        let results = try [result("processed", .completed, failed), result("cached", .unchanged, clear),
                           result("later", .deferred, clear), result("removed", .removed, failed)]
        let snapshot = CredentialStatusSnapshot(evaluatedAt: Date(), credentials: [], refreshingCredentialIDs: [])
        let client = MockWalletClient(credentialStatusSnapshot: snapshot)
        let model = WalletViewModel(walletID: "status-batch-test", walletClient: client,
            readerTrustSettingsPersistence: InMemoryDemoReaderTrustSettingsPersistence(), identityDocumentRegistrationUpdate: {},
            pinStore: InMemoryDemoPinStore(), biometricAuthenticator: FakeDemoBiometricAuthenticator(isAvailable: false))
        model.automaticCredentialStatusChecks = false
        defer {
            model.handleApplicationEnteredBackground()
            UserDefaults.standard.removeObject(forKey: "wallet.status-batch-test.status.automatic")
        }
        model.auth = .unlocked; model.isReady = true
        await client.setStatusRefreshResults(results)
        model.checkCredentialStatuses()
        try await waitUntil { model.credentialStatusSummary != nil && !model.checkingCredentialStatuses }
        let report = CredentialStatusRefreshSummary(completed: 1, unchanged: 1, deferred: 1, checkErrors: 1)
        XCTAssertEqual(model.credentialStatusSummary, report)
        await client.setStatusRefreshResults([])
        model.checkCredentialStatuses(ids: ["processed"])
        try await waitUntil { !model.checkingCredentialStatuses }
        XCTAssertEqual(model.credentialStatusSummary, report)
        model.automaticCredentialStatusChecks = true
        try await waitUntil { !model.checkingCredentialStatuses }
        XCTAssertEqual(model.credentialStatusSummary, report)
        await client.setStatusRefreshResults([], fails: true)
        model.checkCredentialStatuses()
        try await waitUntil { model.credentialStatusBatchError != nil && !model.checkingCredentialStatuses }
        XCTAssertNil(model.credentialStatusSummary)
        let error = model.credentialStatusBatchError
        await client.setStatusRefreshResults([])
        model.checkCredentialStatuses(ids: ["processed"])
        try await waitUntil { !model.checkingCredentialStatuses }
        XCTAssertEqual(model.credentialStatusBatchError, error)
        model.checkCredentialStatuses()
        try await waitUntil { model.credentialStatusSummary != nil && !model.checkingCredentialStatuses }
        XCTAssertNil(model.credentialStatusBatchError)
    }

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

        let newer = try fixtures.credentialStatus("revoked_current")
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

private actor StatusRefreshBarrier {
    private var continuation: CheckedContinuation<Void, Never>?
    private(set) var started = false
    private(set) var finished = false
    func wait() async {
        started = true
        await withCheckedContinuation { continuation = $0 }
        finished = true
    }
    func resume() { continuation?.resume(); continuation = nil }
}
