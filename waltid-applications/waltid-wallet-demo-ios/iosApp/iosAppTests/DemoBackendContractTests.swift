import TestHelpers
import XCTest

final class DemoBackendContractTests: XCTestCase {
    func testPaymentRequestsTheCardClaimsFromItsCredentialQuery() throws {
        let scenario = DemoBackend.scaPaymentScenario
        XCTAssertEqual(scenario.profileId, "scaPaymentCardSdJwt")
        XCTAssertEqual(scenario.credentialConfigurationId, "sca_payment_card_sd_jwt")
        let query = scenario.verifierCredentialQuery
        XCTAssertEqual(query["id"] as? String, "sca_payment")
        XCTAssertEqual(query["format"] as? String, "dc+sd-jwt")
        XCTAssertEqual((query["meta"] as? [String: [String]])?["vct_values"],
                       ["https://issuer2.demo.walt.id/openid4vci/sca_payment_card_sd_jwt"])
        let claims = try XCTUnwrap(query["claims"] as? [[String: [String]]])
        XCTAssertEqual(claims.compactMap { $0["path"] },
                       [["card_scheme"], ["card_last4"], ["card_holder_name"]])
    }
}
