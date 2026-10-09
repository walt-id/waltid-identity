import XCTest

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
        let environment = ["WALLET_ID": "batch-\(UUID().uuidString)", "ATTESTATION_BASE_URL": "", "TRANSACTION_DATA_PROFILES_URL": ""]
        ui.launch(environment: environment)
        XCTAssertEqual(ui.waitUntilWalletReady(timeout: 60), "Wallet ready")

        ui.openDeepLink(offer.offerUrl)
        XCTAssertTrue(app.buttons["wallet.offerAcceptButton"].waitForExistence(timeout: 60))
        let copies = app.staticTexts["issuance-copies-org.iso.18013.5.1.mDL"]
        XCTAssertTrue(copies.waitForExistence(timeout: 10))
        XCTAssertEqual(copies.label, "Copies: 1", "Advertised batch support must not request extra copies")
        app.swipeUp()
        ui.tapButton(identifier: "issuance-more-org.iso.18013.5.1.mDL", fallbackLabel: "More", useCoordinateTap: true)
        let updatedCopies = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "Copies: 2"), object: copies)
        XCTAssertEqual(XCTWaiter.wait(for: [updatedCopies], timeout: 10), .completed, app.debugDescription)
        attachScreenshot(app, name: "Two copies selected before acceptance")
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")

        // Apple's first mdoc registration waits for this system consent, outside the app hierarchy.
        let registrationAlert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts
            .containing(NSPredicate(format: "label CONTAINS %@", "Identity Verification")).firstMatch
        if registrationAlert.waitForExistence(timeout: 15) {
            registrationAlert.buttons["Allow"].tap()
        }

        XCTAssertTrue(app.buttons["issuance-done"].waitForExistence(timeout: 90))
        ui.tapButton(identifier: "issuance-done", fallbackLabel: "Done")
        let cards = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
        let twoCards = NSPredicate { _, _ in Set(cards.allElementsBoundByIndex.map(\.identifier)).count == 2 }
        let stored = XCTNSPredicateExpectation(predicate: twoCards, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [stored], timeout: 90), .completed, app.debugDescription)
        let storedIDs = Set(cards.allElementsBoundByIndex.map(\.identifier))
        attachScreenshot(app, name: "Two credentials stored")

        app.terminate()
        ui.launchExpectingLoginAndUnlock(environment: environment)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: twoCards, object: nil)], timeout: 60), .completed)
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
