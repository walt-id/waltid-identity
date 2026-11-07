import TestHelpers
import XCTest

final class DemoBackendContractTests: XCTestCase {
    func testPaymentVectorsKeepWireTypesExactNumbersAndFutureDates() throws {
        let now = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-11-06T12:00:00Z"))
        let scheduled = DemoPayment.scheduled.payload(now: now)
        XCTAssertEqual(scheduled["execution_date"] as? String, "2026-11-07T12:00:00Z")
        XCTAssertNil(scheduled["recurrence"])
        let recurring = DemoPayment.recurring.payload(now: now)
        XCTAssertNil(recurring["execution_date"])
        let json = try jsonString(recurring)
        XCTAssertTrue(json.contains("9007199254740993.01"))
        XCTAssertTrue(json.contains("0.1234567890123456789"))
        XCTAssertFalse(json.contains("\"9007199254740993.01\""))
        XCTAssertTrue(json.contains("-1.23"))
        for token in ["\"amount_estimated\":false", "\"amount_earmarked\":false", "\"sct_inst\":true",
                      "\"amount_variable\":false", "\"min_amount\":0", "\"initial_amount_number\":2"] {
            XCTAssertTrue(json.contains(token), "Missing JSON token: \(token)")
        }
        let recurrence = try XCTUnwrap(recurring["recurrence"] as? [String: Any])
        XCTAssertEqual(recurrence["end_date"] as? String, "2027-11-06T12:00:00Z")
    }

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
