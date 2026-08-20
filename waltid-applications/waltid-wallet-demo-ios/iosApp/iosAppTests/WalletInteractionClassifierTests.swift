import XCTest
@testable import iosApp

final class WalletInteractionClassifierTests: XCTestCase {
    func testClassifiesCredentialOffersWithoutKeepingWhitespace() {
        XCTAssertEqual(
            classifyWalletInteraction("  openid-credential-offer://issuer.example?offer=1  "),
            .supported(
                kind: .credentialOffer,
                normalizedInput: "openid-credential-offer://issuer.example?offer=1"
            )
        )
    }

    func testClassifiesPresentationRequestsCaseInsensitively() {
        XCTAssertEqual(
            classifyWalletInteraction("OPENID4VP://verifier.example?request=1"),
            .supported(
                kind: .presentationRequest,
                normalizedInput: "OPENID4VP://verifier.example?request=1"
            )
        )
    }

    func testDistinguishesMalformedAndUnsupportedInput() {
        if case .invalid = classifyWalletInteraction("not a wallet link") {} else {
            XCTFail("Expected malformed input to be invalid")
        }
        if case .unsupported = classifyWalletInteraction("https://example.com") {} else {
            XCTFail("Expected web URL to be unsupported")
        }
        if case .unsupported = classifyWalletInteraction("openid://callback") {} else {
            XCTFail("Expected callback URL to be unavailable from Scan")
        }
    }
}
