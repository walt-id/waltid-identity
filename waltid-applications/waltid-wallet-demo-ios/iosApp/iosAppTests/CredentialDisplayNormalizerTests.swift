import Foundation
import XCTest
@testable import iosApp

final class CredentialDisplayNormalizerTests: XCTestCase {

    func testFlattensNamespacedMdocObjectClaimsIntoDisplayRows() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "mso_mdoc",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: """
            {
              "docType": "eu.europa.ec.eudi.pid.1",
              "eu.europa.ec.eudi.pid.1": {
                "resident_state": "Vienna",
                "birth_place": {
                  "locality": "Vienna",
                  "country": "Austria"
                }
              }
            }
            """
        )

        let credentialData = details.groups.first { $0.title == "Credential data" }
        XCTAssertEqual(credentialData?.items.first?.path.id, "docType")
        XCTAssertEqual(
            Set(credentialData?.items.map(\.path.id) ?? []),
            Set([
                "docType",
                "eu.europa.ec.eudi.pid.1.resident_state",
                "eu.europa.ec.eudi.pid.1.birth_place.locality",
                "eu.europa.ec.eudi.pid.1.birth_place.country"
            ])
        )
        XCTAssertEqual(credentialData?.items.contains { item in
            if case .object = item.value { return true }
            return false
        }, false)
    }

    func testKeepsNestedMdocPortraitLabelWhenFlatteningNamespaceObject() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "mso_mdoc",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: """
            {
              "eu.europa.ec.eudi.pid.1": {
                "portrait": {
                  "elementValue": \(onePixelPNGByteArrayJSON())
                }
              }
            }
            """
        )

        let portrait = try XCTUnwrap(
            details.groups.flatMap(\.items).first { $0.path.id == "eu.europa.ec.eudi.pid.1.portrait.elementValue" }
        )
        XCTAssertEqual(portrait.label, "Portrait")
        guard case .image(_, let data, let mimeType, let byteCount) = portrait.value else {
            return XCTFail("Expected portrait to decode as image")
        }
        XCTAssertEqual(mimeType, "image/png")
        XCTAssertEqual(byteCount, onePixelPNGData.count)
        XCTAssertEqual(data, onePixelPNGData)
    }

    func testUsesPortraitImageForCardSummary() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "mso_mdoc",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: #"{"portrait":{"elementValue":\#(onePixelPNGByteArrayJSON())}}"#
        )

        XCTAssertEqual(details.cardSummary.portraitData, onePixelPNGData)
        XCTAssertEqual(details.cardSummary.portraitMimeType, "image/png")
    }

    func testBuildsSystemInfoGroupFromCredentialMetadata() throws {
        let addedAt = try XCTUnwrap(Self.isoDateFormatter.date(from: "2026-07-09T12:00:00Z"))
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "PID",
            issuer: "Example Issuer",
            subject: "did:key:holder",
            format: "vc+sd-jwt",
            addedAt: addedAt,
            credentialDataJSON: "{}"
        )

        let systemInfo = try XCTUnwrap(details.systemInfoGroup)
        XCTAssertEqual(systemInfo.title, "System info")
        XCTAssertEqual(systemInfo.items.map(\.path.id), [
            "system.added",
            "system.id",
            "system.format",
            "system.issuer",
            "system.subject"
        ])
        XCTAssertEqual(systemInfo.items.map(\.label), [
            "Added",
            "Credential ID",
            "Format",
            "Issuer",
            "Subject"
        ])
        XCTAssertEqual(systemInfo.items.map(\.value), [
            .text("2026-07-09"),
            .text("cred-1"),
            .text("vc+sd-jwt"),
            .text("Example Issuer"),
            .text("did:key:holder")
        ])
    }

    func testLeavesMalformedCredentialJSONWithoutDisplayGroups() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Broken credential",
            issuer: nil,
            subject: nil,
            format: "jwt_vc_json",
            addedAt: nil,
            credentialDataJSON: "{not-json"
        )

        XCTAssertTrue(details.groups.isEmpty)
    }

    private func onePixelPNGByteArrayJSON() -> String {
        "[" + onePixelPNGData.map { String($0) }.joined(separator: ",") + "]"
    }

    private var onePixelPNGData: Data {
        Data(base64Encoded: Self.onePixelPNGBase64)!
    }

    private static let onePixelPNGBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="

    private static let isoDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        return formatter
    }()
}
