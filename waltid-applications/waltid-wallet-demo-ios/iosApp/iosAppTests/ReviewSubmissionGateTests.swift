import XCTest
@testable import WalletDemoSharingUI

@MainActor
final class ReviewSubmissionGateTests: XCTestCase {
    func testOrdinaryAttemptDeliversCapturedRevisionsOnce() {
        let gate = ReviewSubmissionGate<String>()
        var delivered: [[String: String]] = []
        gate.update("request-and-selection", enabled: true)
        let attempt = ReviewSubmissionGate<String>.Attempt(key: "request-and-selection", revisions: ["selected": "rendered"],
            unsigned: false, message: "", affirmative: "Share", submit: { delivered.append($0) })
        gate.begin(attempt, needsConfirmation: false)
        gate.begin(attempt, needsConfirmation: false)
        XCTAssertEqual(delivered, [["selected": "rendered"]])
        XCTAssertNil(gate.pending)
    }

    func testWarningDismissalAndChangedReviewNeverDeliverOldAttempt() {
        let gate = ReviewSubmissionGate<String>()
        var delivered = 0
        func attempt(_ key: String) -> ReviewSubmissionGate<String>.Attempt {
            .init(key: key, revisions: ["selected": key], unsigned: true, message: "Unsigned and revoked",
                  affirmative: "Pay", submit: { _ in delivered += 1 })
        }
        gate.update("old", enabled: true)
        let old = attempt("old")
        gate.begin(old, needsConfirmation: true)
        gate.dismiss()
        gate.confirm(old)
        XCTAssertEqual(delivered, 0)
        gate.begin(old, needsConfirmation: true)
        gate.update("new", enabled: true)
        XCTAssertNil(gate.pending)
        XCTAssertTrue(gate.changed)
        gate.confirm(old)
        XCTAssertEqual(delivered, 0)
        let fresh = attempt("new")
        gate.begin(fresh, needsConfirmation: true)
        gate.confirm(fresh)
        gate.confirm(fresh)
        XCTAssertEqual(delivered, 1)
    }

    func testDisabledOrDisposedReviewCannotReuseConfirmationAfterReopening() {
        let gate = ReviewSubmissionGate<String>()
        var delivered = 0
        let attempt = ReviewSubmissionGate<String>.Attempt(key: "same", revisions: [:], unsigned: false,
            message: "Revoked", affirmative: "Approve", submit: { _ in delivered += 1 })
        gate.update("same", enabled: true)
        gate.begin(attempt, needsConfirmation: true)
        gate.update("same", enabled: false)
        gate.update("same", enabled: true)
        gate.confirm(attempt)
        gate.begin(attempt, needsConfirmation: true)
        gate.close()
        gate.update("same", enabled: true)
        gate.confirm(attempt)
        XCTAssertEqual(delivered, 0)
    }
}
