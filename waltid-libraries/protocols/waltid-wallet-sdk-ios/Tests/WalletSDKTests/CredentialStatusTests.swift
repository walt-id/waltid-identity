import Foundation
import XCTest
@testable import WalletSDK
#if canImport(WalletCore) && os(iOS)
import WalletCore
#endif

final class CredentialStatusTests: XCTestCase {
#if canImport(WalletCore) && os(iOS)
    func testUnreadableAdvisoryCannotHideAReadableCredentialAtTheActualBridgeBoundary() throws {
        let stored = MobileWalletCredential(id: "identity", format: "dc+sd-jwt", issuer: "https://issuer.example",
            subject: nil, label: "Resident card", addedAt: nil, credentialDataJson: #"{"name":"Example"}"#, metadataJson: nil)
        let value = WalletBridgeCredentialRead(credential: stored, statusJson: #"{"status":"future-wire-shape"}"#).toSwiftCredential()
        XCTAssertEqual(value.id, "identity")
        XCTAssertEqual(value.label, "Resident card")
        XCTAssertEqual(value.credentialDataJSON, #"{"name":"Example"}"#)
        XCTAssertNil(value.status, "Unreadable data must not invent no-reference or clear evidence")
        XCTAssertThrowsError(try decodeCredentialStatusSnapshot(#"{"status":"future-wire-shape"}"#))
    }
#endif
    func testSnapshotPreservesStoreCoverageTypedPreviousEvidenceAndNanosecondDates() throws {
        let snapshot = try decodeCredentialStatusSnapshot(Self.snapshot)
        let record = try XCTUnwrap(snapshot.credentials.first)
        XCTAssertEqual(record.storeIndex, 2)
        XCTAssertEqual(record.credentialID, "identity")
        XCTAssertEqual(snapshot.refreshingCredentialIDs, ["identity"])
        XCTAssertEqual(record.value.presentation.issue, .revoked)
        XCTAssertEqual(record.value.presentation.freshness, .stale)
        XCTAssertEqual(record.value.presentation.coverage, .partial)
        XCTAssertTrue(record.value.presentation.lastKnown)
        XCTAssertEqual(record.value.presentation.references.first?.result, .revoked)
        XCTAssertEqual(record.value.presentation.references.first?.freshness, .stale)
        guard case let .bitstring(referenceID, purpose, .failed(_, cause, previous)) =
            try XCTUnwrap(record.value.status.entries.first) else {
            return XCTFail("The protocol owner and typed previous observation must survive the bridge")
        }
        XCTAssertEqual(referenceID, "reference-1")
        XCTAssertEqual(purpose.known, .revocation)
        XCTAssertEqual(cause, .reason(.timeout))
        XCTAssertEqual(previous?.value, 1)
        XCTAssertLessThan(try XCTUnwrap(previous?.checkedAt), snapshot.evaluatedAt)
        XCTAssertTrue(record.value.status.hasCheckError)
    }

    func testFuturePresentationCodesRemainConservativeWithoutChangingTheProducerRevision() throws {
        let future = Self.snapshot.replacingOccurrences(of: #""kind":"REVOKED""#, with: #""kind":"FUTURE_STATUS""#)
            .replacingOccurrences(of: #""issue":"REVOKED""#, with: #""issue":"FUTURE_ISSUE""#)
        let value = try XCTUnwrap(decodeCredentialStatusSnapshot(future).credentials.first?.value)
        XCTAssertEqual(value.presentation.kind, .unknown)
        XCTAssertEqual(value.presentation.issue, .unknown)
        XCTAssertNotEqual(value.presentation.issue, .none)
        XCTAssertEqual(value.presentation.advisoryRevision, "opaque-shared-revision-1")
    }

    func testConsentRevisionIsOpaqueAndRequiredRatherThanRecomputedBySwift() throws {
        let original = try XCTUnwrap(decodeCredentialStatusSnapshot(Self.snapshot).credentials.first?.value.presentation)
        XCTAssertEqual(original.advisoryRevision, "opaque-shared-revision-1")
        let later = Self.snapshot.replacingOccurrences(of: "2026-11-12T09:00:00Z", with: "2026-11-12T09:05:00Z")
        let updated = try XCTUnwrap(decodeCredentialStatusSnapshot(later).credentials.first?.value.presentation)
        XCTAssertEqual(original.advisoryRevision, updated.advisoryRevision)
        let revised = Self.snapshot.replacingOccurrences(of: "opaque-shared-revision-1", with: "opaque-shared-revision-2")
        XCTAssertEqual(try decodeCredentialStatusSnapshot(revised).credentials.first?.value.presentation.advisoryRevision,
            "opaque-shared-revision-2")
        for invalid in [Self.snapshot.replacingOccurrences(of: #""advisoryRevision":"opaque-shared-revision-1","#, with: ""),
                        Self.snapshot.replacingOccurrences(of: "opaque-shared-revision-1", with: "")] {
            XCTAssertThrowsError(try decodeCredentialStatusSnapshot(invalid))
        }
    }

    func testSnapshotRejectsDuplicateScopedRecordsWhileKeepingDifferentStoresDistinct() throws {
        var root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(Self.snapshot.utf8)) as? [String: Any])
        let record = try XCTUnwrap((root["credentials"] as? [[String: Any]])?.first)
        root["credentials"] = [record, record]
        let duplicate = try XCTUnwrap(String(data: JSONSerialization.data(withJSONObject: root), encoding: .utf8))
        XCTAssertThrowsError(try decodeCredentialStatusSnapshot(duplicate))

        var otherStore = record
        otherStore["reference"] = ["storeIndex": 3, "credentialId": "identity"]
        root["credentials"] = [record, otherStore]
        let distinct = try XCTUnwrap(String(data: JSONSerialization.data(withJSONObject: root), encoding: .utf8))
        let snapshot = try decodeCredentialStatusSnapshot(distinct)
        XCTAssertEqual(snapshot.credentials.map(\.storeIndex), [2, 3])
        XCTAssertEqual(snapshot.credentials.map(\.credentialID), ["identity", "identity"])
    }

    func testProtocolCasesKeepTheirScalarTypesAndPurposeVocabularies() throws {
        let json = #"""
        {"type":"references","entries":[
          {"type":"token","referenceId":"token","check":{"type":"observed","observation":{"value":4,
            "checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z"}}},
          {"type":"mso","referenceId":"mso","check":{"type":"observed","observation":{"value":"REVOKED",
            "checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z"}}},
          {"type":"legacy","referenceId":"legacy","purpose":"refresh","check":{"type":"observed","observation":{"value":true,
            "checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z"}}},
          {"type":"bitstring","referenceId":"bitstring","purpose":"refresh","check":{"type":"observed","observation":{"value":255,
            "checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z"}}}
        ]}
        """#
        let metadata = try decodeCredentialStatus(CredentialStatusMetadata.self, json: json)
        guard case .token(_, .observed(let token)) = metadata.entries[0],
              case .mso(_, .observed(let mso)) = metadata.entries[1],
              case .legacy(_, let legacyPurpose, .observed(let legacy)) = metadata.entries[2],
              case .bitstring(_, let bitstringPurpose, .observed(let bitstring)) = metadata.entries[3] else {
            return XCTFail("Every protocol must decode with its own value type")
        }
        XCTAssertEqual(token.value.code, 4)
        XCTAssertEqual(token.value.kind, .reserved)
        XCTAssertEqual(mso.value, .revoked)
        XCTAssertNil(legacyPurpose.known)
        XCTAssertEqual(legacyPurpose.value, "refresh")
        XCTAssertTrue(legacy.value)
        XCTAssertEqual(bitstringPurpose.known, .refresh)
        XCTAssertEqual(bitstring.value, 255)
        XCTAssertFalse(metadata.hasCheckError, "An adverse issuer result is not a check error")
        XCTAssertEqual(try decodeCredentialStatus(CredentialStatusMetadata.self,
            json: #"{"type":"discovery_failed","problem":"RESOURCE_LIMIT"}"#), .discoveryFailed(.resourceLimit))
    }

    func testInvalidEvidenceCannotDecodeAsClearAndErrorsDoNotExposePayloads() throws {
        let invalid = [
            Self.snapshot.replacingOccurrences(of: #""value":1"#, with: #""value":256"#),
            Self.snapshot.replacingOccurrences(of: #""value":1"#, with: #""value":true"#),
            Self.snapshot.replacingOccurrences(of: #""value":1"#, with: #""value":null"#),
            Self.snapshot.replacingOccurrences(of: #""value":1"#, with: #""value":1,"protocol":"MSO_STATUS_LIST""#),
            Self.snapshot.replacingOccurrences(of: #""type":"bitstring""#, with: #""type":"mso""#),
            Self.snapshot.replacingOccurrences(of: #""type":"bitstring""#, with: #""type":"future""#),
            Self.snapshot.replacingOccurrences(of: "2026-11-12T10:00:00.123456789Z", with: "secret-invalid-date"),
            Self.snapshot.replacingOccurrences(of: #""freshUntil":"2026-11-12T09:15:00Z""#,
                with: #""freshUntil":"2026-11-12T09:15:00Z","validUntil":"2026-11-12T09:10:00Z""#)
        ]
        for json in invalid {
            XCTAssertThrowsError(try decodeCredentialStatusSnapshot(json)) { error in
                XCTAssertEqual(error as? WalletError, .internalFailure("Invalid credential status data"))
                XCTAssertFalse(error.localizedDescription.contains("secret-invalid-date"))
            }
        }
    }

    private static let snapshot = #"""
    {
      "evaluatedAt":"2026-11-12T10:00:00.123456789Z",
      "refreshing":[{"storeIndex":2,"credentialId":"identity"}],
      "credentials":[{
        "reference":{"storeIndex":2,"credentialId":"identity"},
        "status":{"type":"references","entries":[{
          "type":"bitstring","referenceId":"reference-1","purpose":"revocation",
          "check":{"type":"failed","cause":{"type":"reason","reason":"TIMEOUT"},
            "attemptedAt":"2026-11-12T10:00:00Z",
            "previous":{"value":1,"checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z"}}
        },{"type":"unsupported","referenceId":"reference-2","method":"OtherStatus"}]},
        "presentation":{"kind":"REVOKED","issue":"REVOKED","freshness":"STALE","action":"RETRY",
          "advisoryRevision":"opaque-shared-revision-1","lastKnown":true,
          "lastCheckedAt":"2026-11-12T09:00:00Z","lastAttemptAt":"2026-11-12T10:00:00Z",
          "failure":{"type":"reason","reason":"TIMEOUT"},"purposes":["revocation"],"coverage":"PARTIAL",
          "references":[{"referenceId":"reference-1","protocol":"BITSTRING_STATUS_LIST","purpose":"revocation",
            "result":"REVOKED","freshness":"STALE","checkedAt":"2026-11-12T09:00:00Z","freshUntil":"2026-11-12T09:15:00Z",
            "attemptedAt":"2026-11-12T10:00:00Z","failure":{"type":"reason","reason":"TIMEOUT"}},
            {"referenceId":"reference-2","method":"OtherStatus"}]}
      }]
    }
    """#
}
