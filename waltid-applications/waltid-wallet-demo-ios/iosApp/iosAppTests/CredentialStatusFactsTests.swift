import Foundation
import WalletSDK
import XCTest
@testable import WalletDemoSharingUI

final class CredentialStatusFactsTests: XCTestCase {
    private let checkedAt = "2026-11-13T09:00:00Z"
    private let later = "2026-11-13T10:00:00Z"

    func testSimpleResultsHaveNoRepeatedDisclosureAndClearRetainsCheckedScope() throws {
        let clear = try presentation(references: [reference("one")]).facts()
        XCTAssertTrue(clear.extra.isEmpty)
        XCTAssertEqual(clear.main, [.scope(["revocation"]), .time(.lastChecked, date(checkedAt))])
        let revoked = try presentation(references: [reference("one", result: "REVOKED")], overrides: ["kind": "REVOKED", "issue": "REVOKED"]).facts()
        XCTAssertTrue(revoked.extra.isEmpty)
        XCTAssertEqual(revoked.main, [.time(.lastChecked, date(checkedAt))])
        let absent = try presentation(references: [], overrides: ["kind": "NOT_PROVIDED", "freshness": "UNKNOWN", "coverage": "NONE", "action": "NONE",
            "lastCheckedAt": NSNull(), "lastAttemptAt": NSNull(), "purposes": []]).facts()
        XCTAssertTrue(absent.main.isEmpty)
        XCTAssertTrue(absent.extra.isEmpty)
    }

    func testSamePurposeReferencesKeepSeparateOriginalCheckTimes() throws {
        let facts = try presentation(references: [reference("one"), reference("two", checked: later)]).facts()
        XCTAssertEqual(facts.main, [.scope(["revocation"])], "The oldest aggregate time is not a common last-check time")
        XCTAssertEqual(facts.extra.map(\.id), ["one", "two"])
        XCTAssertEqual(facts.extra.map { $0.facts.last }, [
            .time(.lastChecked, date(checkedAt), referenceID: "one"), .time(.lastChecked, date(later), referenceID: "two"),
        ])
    }

    func testEqualTimeFailedRefreshKeepsSuccessAndAttemptDistinct() throws {
        var entry = reference("one")
        entry["failure"] = ["type": "reason", "reason": "TRANSPORT"]
        let facts = try presentation(references: [entry], overrides: ["action": "RETRY", "failure": entry["failure"]!]).facts()
        XCTAssertTrue(facts.main.contains(.note(.latestCheckFailed)))
        XCTAssertTrue(facts.main.contains(.time(.lastChecked, date(checkedAt))))
        XCTAssertEqual(facts.extra.first?.facts, [.note(.incomplete), .time(.latestAttempt, date(checkedAt))])
    }

    func testTemporalEligibilityHasRecoveryAndAttemptWithoutInventingSuccessfulEvidence() throws {
        for (failure, recovery) in [
            (["type": "not_yet_valid", "validFrom": later], CredentialStatusFact.TimeKind.validFrom),
            (["type": "rate_limited", "retryAt": later], CredentialStatusFact.TimeKind.retryFrom),
        ] {
            var entry = reference("one")
            entry["result"] = NSNull(); entry["checkedAt"] = NSNull(); entry["freshness"] = "UNKNOWN"; entry["failure"] = failure
            let facts = try presentation(references: [entry], overrides: ["kind": "UNAVAILABLE", "issue": "UNCERTAIN", "freshness": "UNKNOWN",
                "coverage": "NONE", "action": "NONE", "lastCheckedAt": NSNull(), "purposes": [], "failure": failure]).facts()
            XCTAssertTrue(facts.extra.isEmpty)
            XCTAssertTrue(facts.main.contains(.time(.latestAttempt, date(checkedAt))))
            XCTAssertTrue(facts.main.contains(.time(recovery, date(later))))
            XCTAssertFalse(facts.main.contains(.time(.lastChecked, date(checkedAt))))
        }
    }

    func testIrreversibleConflictIsVisibleWithOneExpandedExplanation() throws {
        var entry = reference("one", result: "REVOKED")
        entry["conflict"] = true
        let facts = try presentation(references: [entry], overrides: ["kind": "REVOKED", "issue": "REVOKED", "coverage": "PARTIAL", "action": "RETRY"]).facts()
        XCTAssertTrue(facts.main.contains(.note(.conflictingUpdate)))
        XCTAssertEqual(facts.extra.first?.facts, [.time(.latestAttempt, date(checkedAt)), .note(.conflict)])
    }

    private func reference(_ id: String, result: String = "CLEAR", checked: String? = nil) -> [String: Any] {
        ["referenceId": id, "protocol": "BITSTRING_STATUS_LIST", "purpose": "revocation", "result": result,
         "checkedAt": checked ?? checkedAt, "attemptedAt": checked ?? checkedAt, "freshness": "FRESH"]
    }

    private func presentation(references: [[String: Any]], overrides: [String: Any] = [:]) throws -> CredentialStatusPresentation {
        var json: [String: Any] = ["kind": "NOT_REVOKED", "issue": "NONE", "freshness": "FRESH", "action": "CHECK", "coverage": "COMPLETE",
            "advisoryRevision": "producer-revision", "lastKnown": false, "lastCheckedAt": checkedAt, "lastAttemptAt": checkedAt,
            "purposes": ["revocation"], "references": references]
        json.merge(overrides) { _, value in value }
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(CredentialStatusPresentation.self, from: JSONSerialization.data(withJSONObject: json))
    }

    private func date(_ value: String) -> Date { ISO8601DateFormatter().date(from: value)! }
}
