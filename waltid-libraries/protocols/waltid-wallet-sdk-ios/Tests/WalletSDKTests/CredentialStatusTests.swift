import XCTest
@testable import WalletSDK

final class CredentialStatusTests: XCTestCase {
    func testSnapshotPreservesStoreCoveragePreviousEvidenceAndNanosecondDates() throws {
        let snapshot = try decodeCredentialStatusSnapshot(Self.snapshot)
        let record = try XCTUnwrap(snapshot.credentials.first)
        XCTAssertEqual(record.storeIndex, 2)
        XCTAssertEqual(record.credentialID, "identity")
        XCTAssertEqual(snapshot.refreshingCredentialIDs, ["identity"])
        XCTAssertEqual(record.value.presentation.issue, .revoked)
        XCTAssertEqual(record.value.presentation.freshness, .stale)
        XCTAssertEqual(record.value.presentation.coverage, .partial)
        guard case let .unavailable(referenceID, reason, _, previous) = try XCTUnwrap(record.value.status.entries.first) else {
            return XCTFail("Failure and previous observation must survive the bridge")
        }
        XCTAssertEqual(referenceID, "reference-1")
        XCTAssertEqual(reason, "TIMEOUT")
        XCTAssertEqual(previous?.meaning, .revoked)
        XCTAssertEqual(previous?.value, 1)
        XCTAssertLessThan(try XCTUnwrap(previous?.checkedAt), snapshot.evaluatedAt)
    }

    func testFutureCodesRemainUnknownAndMalformedEvidenceFailsWithoutExposingPayload() throws {
        let future = Self.snapshot.replacingOccurrences(of: "\"kind\":\"REVOKED\"", with: "\"kind\":\"FUTURE_STATUS\"")
            .replacingOccurrences(of: "\"issue\":\"REVOKED\"", with: "\"issue\":\"FUTURE_ISSUE\"")
        let value = try XCTUnwrap(decodeCredentialStatusSnapshot(future).credentials.first?.value)
        XCTAssertEqual(value.presentation.kind, .unknown)
        XCTAssertEqual(value.presentation.issue, .unknown)
        XCTAssertNotEqual(value.presentation.issue, .none)

        for invalid in [Self.snapshot.replacingOccurrences(of: "\"value\":1", with: "\"value\":256"),
                        Self.snapshot.replacingOccurrences(of: "2026-11-12T10:00:00.123456789Z", with: "secret-invalid-date")] {
            XCTAssertThrowsError(try decodeCredentialStatusSnapshot(invalid)) { error in
                XCTAssertEqual(error as? WalletError, .internalFailure("Invalid credential status data"))
                XCTAssertFalse(error.localizedDescription.contains("secret-invalid-date"))
            }
        }
    }

    func testConsentRevisionExcludesCheckTimeButIncludesFreshnessAndCoverage() throws {
        let original = try XCTUnwrap(decodeCredentialStatusSnapshot(Self.snapshot).credentials.first?.value.presentation)
        let later = Self.snapshot.replacingOccurrences(of: "2026-11-12T09:00:00Z", with: "2026-11-12T09:30:00Z")
        let updated = try XCTUnwrap(decodeCredentialStatusSnapshot(later).credentials.first?.value.presentation)
        XCTAssertEqual(original.advisoryRevision, updated.advisoryRevision)
        for pair in [("\"freshness\":\"STALE\"", "\"freshness\":\"FRESH\""),
                     ("\"coverage\":\"PARTIAL\"", "\"coverage\":\"COMPLETE\"")] {
            let changed = try XCTUnwrap(decodeCredentialStatusSnapshot(Self.snapshot.replacingOccurrences(of: pair.0, with: pair.1))
                .credentials.first?.value.presentation)
            XCTAssertNotEqual(original.advisoryRevision, changed.advisoryRevision)
        }
    }

    private static let snapshot = #"""
    {
      "evaluatedAt":"2026-11-12T10:00:00.123456789Z",
      "refreshing":[{"storeIndex":2,"credentialId":"identity"}],
      "credentials":[{
        "reference":{"storeIndex":2,"credentialId":"identity"},
        "status":{"entries":[{
          "type":"unavailable","referenceId":"reference-1","reason":"TIMEOUT",
          "attemptedAt":"2026-11-12T10:00:00Z",
          "previous":{"referenceId":"reference-1","protocol":"BITSTRING_STATUS_LIST",
            "purpose":"revocation","value":1,"meaning":"REVOKED",
            "checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z"}
        }]},
        "presentation":{"kind":"REVOKED","issue":"REVOKED","freshness":"STALE","action":"RETRY",
          "lastCheckedAt":"2026-11-12T09:00:00Z","lastAttemptAt":"2026-11-12T10:00:00Z",
          "failure":"TIMEOUT","purposes":["revocation"],"coverage":"PARTIAL"}
      }]
    }
    """#
}
