import XCTest
import TestHelpers

/// Runs against the coordinated Enterprise mobile fixture, using the real wallet and issuer.
@MainActor
final class BatchIssuanceE2ETests: XCTestCase {
    func testExplicitCopiesSurviveRelaunch() async throws {
        continueAfterFailure = false
        guard let fixtureURL = ProcessInfo.processInfo.environment["ENTERPRISE_MOBILE_FIXTURE_BASE_URL"] else {
            throw XCTSkip("Start enterpriseMobileFixtureServer and supply ENTERPRISE_MOBILE_FIXTURE_BASE_URL")
        }
        let fixture = EnterpriseMobileFixture(baseURL: try XCTUnwrap(URL(string: fixtureURL)))
        let scenarios = try await fixture.scenarios()
        let scenario = try XCTUnwrap(scenarios.first { $0.id == "enterprise-mdl" })
        let offer = try await fixture.createOffer(scenario: scenario, platform: .ios)
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["ATTESTATION_BASE_URL": "", "TRANSACTION_DATA_PROFILES_URL": ""])
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 60), "Wallet ready")

        ui.openWalletLink(offer.offerUrl)
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 60), "Review credential offer")
        let increment = app.buttons["issuance-copies-org.iso.18013.5.1.mDL-Increment"]
        XCTAssertTrue(increment.waitForExistence(timeout: 10))
        XCTAssertEqual(increment.value as? String, "1", "Advertised batch support must not request extra copies")
        increment.tap()
        XCTAssertEqual(increment.value as? String, "2")
        attachScreenshot(app, name: "Two copies selected before acceptance")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        // Apple's first mdoc registration waits for this system consent, outside the app hierarchy.
        let registrationAlert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts
            .containing(NSPredicate(format: "label CONTAINS %@", "Identity Verification")).firstMatch
        if registrationAlert.waitForExistence(timeout: 15) {
            registrationAlert.buttons["Allow"].tap()
        }

        let cards = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
        let twoCards = NSPredicate { _, _ in Set(cards.allElementsBoundByIndex.map(\.identifier)).count == 2 }
        let stored = XCTNSPredicateExpectation(predicate: twoCards, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [stored], timeout: 90), .completed, app.debugDescription)
        let storedIDs = Set(cards.allElementsBoundByIndex.map(\.identifier))
        attachScreenshot(app, name: "Two credentials stored")

        app.terminate()
        ui.launch(environment: ["ATTESTATION_BASE_URL": "", "TRANSACTION_DATA_PROFILES_URL": ""])
        ui.returnToWallet()
        ui.assertExists(identifierPrefix: "wallet.credentialCard.", timeout: 60)
        XCTAssertEqual(Set(cards.allElementsBoundByIndex.map(\.identifier)), storedIDs)
        attachScreenshot(app, name: "Same credentials after relaunch")
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
