import XCTest

final class WaltidWalletSDKSampleUITests: XCTestCase {
    private let credentialOperationTimeout: TimeInterval = 90
    private let verifierPollingTimeout: TimeInterval = 30

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    @MainActor
    func testBootstrapListsCredentialsThroughSDKFacade() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["sdk-title"].waitForExistence(timeout: 10))

        app.buttons["sdk-bootstrap-button"].tap()

        XCTAssertTrue(app.staticTexts["sdk-bootstrap-success"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["sdk-credentials-empty"].exists)
    }

    @MainActor
    func testReceiveAndPresentAgainstEudiThroughSDKFacade() async throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["sdk-title"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.textViews["sdk-offer-input"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textViews["sdk-presentation-input"].waitForExistence(timeout: 5))

        app.buttons["sdk-bootstrap-button"].tap()
        XCTAssertTrue(app.staticTexts["sdk-bootstrap-success"].waitForExistence(timeout: 30))

        let backend = SampleEudiPublicBackend()
        let offerURL = try await backend.generatePreAuthorizedOffer()

        replaceText(in: app.textViews["sdk-offer-input"], value: offerURL, app: app)
        app.buttons["sdk-receive-button"].tap()

        let received = app.staticTexts.matching(
            NSPredicate(format: "identifier == %@ AND label BEGINSWITH %@", "sdk-receive-success", "Received")
        ).firstMatch
        XCTAssertTrue(received.waitForExistence(timeout: credentialOperationTimeout))
        XCTAssertTrue(app.staticTexts["sdk-last-event"].waitForExistence(timeout: 5))

        let verifier = try await backend.createVerifierTransaction()
        replaceText(in: app.textViews["sdk-presentation-input"], value: verifier.authorizationRequestURI, app: app)
        app.buttons["sdk-present-button"].tap()

        let presented = app.staticTexts.matching(
            NSPredicate(format: "identifier == %@ AND label BEGINSWITH %@", "sdk-present-success", "Presentation sent")
        ).firstMatch
        XCTAssertTrue(presented.waitForExistence(timeout: credentialOperationTimeout))
        let presentationEvent = app.staticTexts.matching(
            NSPredicate(format: "identifier == %@ AND label CONTAINS %@", "sdk-last-event", "presentation")
        ).firstMatch
        XCTAssertTrue(presentationEvent.waitForExistence(timeout: 10))

        try await backend.waitForVerifierSuccess(
            transactionID: verifier.transactionID,
            timeoutSeconds: verifierPollingTimeout
        )
    }

    private func replaceText(in element: XCUIElement, value: String, app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 20), "Input element not found")
        makeHittable(element, app: app)
        XCTAssertTrue(element.isHittable, "Input element is not hittable")
        element.tap()

        if let currentValue = element.value as? String, !currentValue.isEmpty {
            element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count))
        }

        element.typeText(value)
    }

    private func makeHittable(_ element: XCUIElement, app: XCUIApplication) {
        guard element.exists, !element.isHittable else {
            return
        }

        for _ in 0..<8 where !element.isHittable {
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
    }
}
