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
