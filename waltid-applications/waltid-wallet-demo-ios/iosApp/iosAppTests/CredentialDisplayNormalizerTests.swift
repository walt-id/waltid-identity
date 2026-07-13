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

    func testKeepsMalformedCredentialJSONAsRawTechnicalDetails() throws {
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
        let raw = try XCTUnwrap(details.technicalGroups.single?.items.single)
        XCTAssertEqual(raw.label, "Raw credential data")
        XCTAssertEqual(raw.value, .raw("{not-json"))
    }

    private func onePixelPNGByteArrayJSON() -> String {
        "[" + onePixelPNGData.map { String($0) }.joined(separator: ",") + "]"
    }

    private var onePixelPNGData: Data {
        Data(base64Encoded: Self.onePixelPNGBase64)!
    }

    private static let onePixelPNGBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
}

private extension Array {
    var single: Element? {
        count == 1 ? first : nil
    }
}
