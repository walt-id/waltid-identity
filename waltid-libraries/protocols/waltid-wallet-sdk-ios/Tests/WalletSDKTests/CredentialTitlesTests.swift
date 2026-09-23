import XCTest
@testable import WalletSDK

final class CredentialTitlesTests: XCTestCase {
    func testPrefersDisplayName() {
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "mso_mdoc",
                credentialDataJSON: #"{"docType":"org.iso.18013.5.1.mDL"}"#,
                displayName: "Photo ID",
                fallback: "fallback"
            ),
            "Photo ID"
        )
    }

    func testW3CUsesFirstNonGenericType() {
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "jwt_vc_json",
                credentialDataJSON: #"{"type":["VerifiableCredential","UniversityDegreeCredential"]}"#
            ),
            "University Degree Credential"
        )
    }

    func testSdJwtHumanizesVct() {
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "vc+sd-jwt",
                credentialDataJSON: #"{"vct":"this_case"}"#
            ),
            "This Case"
        )
    }

    func testMdocUsesFriendlyNames() {
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "mso_mdoc",
                credentialDataJSON: #"{"docType":"org.iso.18013.5.1.mDL"}"#
            ),
            "Mobile Driving Licence"
        )
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "mso_mdoc",
                credentialDataJSON: #"{"doctype":"eu.europa.ec.eudi.pid.1"}"#
            ),
            "PID"
        )
    }
}
