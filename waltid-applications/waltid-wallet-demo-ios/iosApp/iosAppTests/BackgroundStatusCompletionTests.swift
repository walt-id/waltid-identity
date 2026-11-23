import Foundation
import XCTest
@testable import WalletDemoBackgroundStatus

final class BackgroundStatusCompletionTests: XCTestCase {
    func testExpirationBeforeRegistrationStillCancelsThePublishedJobOnce() {
        let acknowledgement = expectation(description: "Expired task acknowledged")
        acknowledgement.assertForOverFulfill = true
        let cancelled = expectation(description: "Late job cancelled")
        cancelled.assertForOverFulfill = true
        let completion = BackgroundStatusCompletion {
            XCTAssertFalse($0)
            acknowledgement.fulfill()
        }

        completion.finish(success: false, cancel: true)
        completion.install { cancelled.fulfill() }
        completion.finish(success: true)

        wait(for: [acknowledgement, cancelled], timeout: 1)
        XCTAssertFalse(completion.isPending)
    }

    func testExpiryAfterSuccessfulCompletionCannotCancelOrAcknowledgeAgain() {
        let acknowledgement = expectation(description: "Success acknowledged")
        acknowledgement.assertForOverFulfill = true
        let completion = BackgroundStatusCompletion {
            XCTAssertTrue($0)
            acknowledgement.fulfill()
        }
        completion.install { XCTFail("A finished operation must not be cancelled") }

        completion.finish(success: true)
        completion.finish(success: false, cancel: true)

        wait(for: [acknowledgement], timeout: 1)
    }
}

@MainActor
final class BackgroundStatusSchedulerTests: XCTestCase {
    func testReconciliationDoesNotCancelWithoutAPreviousBinding() throws {
        var cancelledRequests = 0
        let scheduler = try makeScheduler { _ in cancelledRequests += 1 }

        scheduler.reconcile(binding: nil)
        XCTAssertEqual(cancelledRequests, 0, "A wallet that has never enrolled has no request to cancel")
        scheduler.reconcile(binding: "grant")
        XCTAssertEqual(cancelledRequests, 0, "The first enrollment has no previous request to cancel")

        scheduler.reconcile(binding: nil)
        XCTAssertEqual(cancelledRequests, 1, "Retiring the enrollment cancels its request")
        scheduler.reconcile(binding: nil)
        XCTAssertEqual(cancelledRequests, 1, "Reopening a wallet after retirement does not cancel again")
    }

    func testDeferralRetainsTheScheduleAndIneligibilityRetiresIt() throws {
        var cancelledRequests = 0
        let scheduler = try makeScheduler { _ in cancelledRequests += 1 }
        scheduler.reconcile(binding: "retired-grant")
        let originalCancellationCount = cancelledRequests

        var acknowledgements: [Bool] = []
        func deliver(_ outcome: WalletDemoBackgroundStatusScheduler.Outcome) {
            scheduler.deliver(BackgroundStatusCompletion { acknowledgements.append($0) }, start: { binding, finish in
                XCTAssertEqual(binding, "retired-grant")
                finish(outcome)
                return {}
            }, onResult: {})
        }

        deliver(.deferred)
        XCTAssertEqual(cancelledRequests, originalCancellationCount)

        deliver(.ineligible)
        XCTAssertEqual(cancelledRequests, originalCancellationCount + 1)
        XCTAssertEqual(acknowledgements, [false, true])

        scheduler.deliver(BackgroundStatusCompletion { XCTAssertTrue($0) }, start: { _, _ in
            XCTFail("A retired grant cannot start another delivery")
            return {}
        }, onResult: {})
    }

    func testALateIneligibleResultLeavesTheReplacementScheduled() throws {
        var cancelledRequests = 0
        let scheduler = try makeScheduler { _ in cancelledRequests += 1 }
        scheduler.reconcile(binding: "old-grant")
        var oldAcknowledgements: [Bool] = []
        var finishOld: WalletDemoBackgroundStatusScheduler.Finish?
        scheduler.deliver(BackgroundStatusCompletion { oldAcknowledgements.append($0) }, start: { _, finish in
            finishOld = finish
            return {}
        }, onResult: {})

        scheduler.reconcile(binding: "new-grant")
        let replacementCancellationCount = cancelledRequests
        let reportOld = try XCTUnwrap(finishOld)
        reportOld(.ineligible)

        XCTAssertEqual(cancelledRequests, replacementCancellationCount)
        XCTAssertEqual(oldAcknowledgements, [false])
        scheduler.deliver(BackgroundStatusCompletion { XCTAssertTrue($0) }, start: { binding, finish in
            XCTAssertEqual(binding, "new-grant")
            finish(.finished)
            return {}
        }, onResult: {})
    }

    private func makeScheduler(cancelRequest: @escaping (String) -> Void) throws -> WalletDemoBackgroundStatusScheduler {
        let suite = "BackgroundStatusSchedulerTests.\(UUID())"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        addTeardownBlock { UserDefaults(suiteName: suite)?.removePersistentDomain(forName: suite) }
        return WalletDemoBackgroundStatusScheduler(defaults: defaults, cancelRequest: cancelRequest)
    }
}
