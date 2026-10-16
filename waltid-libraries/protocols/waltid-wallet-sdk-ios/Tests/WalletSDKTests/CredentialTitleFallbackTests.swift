import XCTest
@testable import WalletSDK

final class CredentialTitleFallbackTests: XCTestCase {
    func testBlankDisplayNameAndMalformedPayloadUseTrimmedFallback() {
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "vc+sd-jwt",
                credentialDataJSON: "{invalid",
                displayName: " \n ",
                fallback: " Photo ID \n"
            ),
            "Photo ID"
        )
    }

    func testMissingPayloadAndBlankFallbackPreserveFormat() {
        XCTAssertEqual(
            CredentialTitles.displayName(
                format: "custom_credential",
                credentialDataJSON: nil,
                fallback: " \n "
            ),
            "custom_credential"
        )
    }
}
