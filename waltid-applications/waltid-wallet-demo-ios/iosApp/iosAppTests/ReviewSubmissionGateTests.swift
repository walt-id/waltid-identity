import XCTest
import WalletSDK
@testable import WalletDemoSharingUI

@MainActor
final class ReviewSubmissionGateTests: XCTestCase {
    func testChangedClaimReviewInvalidatesStatusConfirmationWithTheSameSelection() throws {
        let option = try PresentationCredentialOption(
            queryID: "identity", credential: .init(id: "card", format: "dc+sd-jwt", credentialDataJSON: #"{"name":"Ada"}"#, issuer: nil, subject: nil, label: "Identity"),
            disclosures: []
        )
        let review = SharingReviewModel(request: .init(requester: nil), credentialOptions: [option])
        let selection = SharingSelection(credentials: [option.selection], disclosures: [])
        func key(_ claimsJSON: String) throws -> SharingSubmissionKey {
            let prepared = ReviewedPresentationClaims(credentials: [.init(queryID: option.queryID,
                credentialID: option.credentialID, format: option.format, claimsJSON: claimsJSON, wholeCredential: false,
                locations: ["[]", #"["name"]"#].map {
                    .init(sourcePathJSON: $0, receivedPathJSON: $0, sharedBy: [], alwaysCarried: true)
                })])
            let claims = try EffectivePresentationReview(prepared: prepared, model: review, selection: selection)
            return SharingSubmissionKey(requestIdentity: "request", review: review, selection: selection,
                claimReview: .ready(claims))
        }
        let old = try key(#"{"name":"Ada"}"#), updated = try key(#"{"name":"Grace"}"#)
        let gate = ReviewSubmissionGate<SharingSubmissionKey>()
        var delivered = 0
        let attempt = ReviewSubmissionGate<SharingSubmissionKey>.Attempt(key: old, revisions: ["card": "revoked"],
            unsigned: false, message: "Revoked", affirmative: "Share anyway", submit: { _ in delivered += 1 })
        gate.update(old, enabled: true)
        gate.begin(attempt, needsConfirmation: true)
        gate.update(updated, enabled: true)
        XCTAssertNil(gate.pending)
        XCTAssertTrue(gate.changed)
        gate.confirm(attempt)
        XCTAssertEqual(delivered, 0)
    }

    func testOrdinaryAttemptDeliversCapturedRevisionsOnce() {
        let gate = ReviewSubmissionGate<String>()
        var delivered: [[String: String]] = []
        XCTAssertFalse(gate.canRefresh)
        gate.update("request-and-selection", enabled: true)
        XCTAssertTrue(gate.canRefresh)
        let attempt = ReviewSubmissionGate<String>.Attempt(key: "request-and-selection", revisions: ["selected": "rendered"],
            unsigned: false, message: "", affirmative: "Share", submit: { delivered.append($0) })
        gate.begin(attempt, needsConfirmation: false)
        gate.begin(attempt, needsConfirmation: false)
        XCTAssertEqual(delivered, [["selected": "rendered"]])
        XCTAssertNil(gate.pending)
        XCTAssertFalse(gate.canRefresh, "A consumed submission cannot start new refresh work")
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
        XCTAssertFalse(gate.canRefresh)
        gate.dismiss()
        XCTAssertTrue(gate.canRefresh)
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
