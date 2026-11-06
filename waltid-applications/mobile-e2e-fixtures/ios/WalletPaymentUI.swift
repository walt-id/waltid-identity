import XCTest
#if canImport(TestHelpers)
import TestHelpers
#endif

/// Shared assertions over the real payment review in both iOS demos.
@MainActor
extension XCTestCase {
    func reviewPublicPayment(_ app: XCUIApplication, payment: DemoPayment, capturePrefix: String) {
        let consent = app.descendants(matching: .any).matching(identifier: "payment-consent").firstMatch
        XCTAssertTrue(consent.waitForExistence(timeout: 60), "Payment consent missing; check deployed issuer metadata")
        func text(_ value: String) -> XCUIElement {
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", value)).firstMatch
        }
        for value in ["Confirm this payment", "Super Store", "merchant-001", "Payee ID", "Amount", "11.56", "EUR",
                      "Check the payee, amounts, timing and recurring terms before confirming."] {
            revealPaymentElement(text(value), in: app)
        }
        XCTAssertFalse(app.images["payment-payee-logo"].exists, "A failed logo must leave no placeholder")
        XCTAssertFalse(app.staticTexts["Payee logo"].exists)
        XCTAssertFalse(app.staticTexts["Currency"].exists)
        revealPaymentElement(text("Super Store"), in: app)
        captureGallery(app, name: "\(capturePrefix)-main")
        if payment != .minimal {
            let payee = app.descendants(matching: .any).matching(identifier: "payment-payee-details-toggle").firstMatch
            revealPaymentElement(payee, in: app)
            payee.tap()
            let website = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "https://example.com ↗")).firstMatch
            revealPaymentElement(website, in: app)
            website.tap()
            XCTAssertTrue(XCUIApplication(bundleIdentifier: "com.apple.mobilesafari").wait(for: .runningForeground, timeout: 15))
            app.activate()
            revealPaymentElement(website, in: app)
            revealPaymentElement(payee, in: app)
            payee.tap()
            XCTAssertFalse(website.exists, "Collapsing payee details must hide the website")
            for value in payment == .scheduled ? ["Payment timing", "Execution date and time"] :
                ["Payment timing", "First payment", "Last payment", "Monthly", "Payment terms",
                 "9007199254740993.01", "-1.23", "Annual percentage rate", "0.1234567890123456789"] {
                revealPaymentElement(text(value), in: app)
            }
            let details = app.descendants(matching: .any).matching(identifier: "payment-details-toggle").firstMatch
            revealPaymentElement(details, in: app)
            details.tap()
            for value in ["Example Payments Ltd", "Estimated amount", "wal-1665-\(payment.rawValue)"] {
                revealPaymentElement(text(value), in: app)
            }
            for value in ["No", "Yes"] {
                revealPaymentElement(app.staticTexts.matching(NSPredicate(format: "label == %@", value)).firstMatch, in: app)
            }
            captureGallery(app, name: "\(capturePrefix)-details")
            revealPaymentElement(details, in: app)
            details.tap()
            XCTAssertFalse(text("Example Payments Ltd").exists, "Details must collapse without submitting")
        }
        XCTAssertTrue(app.buttons["wallet.presentationRejectButton"].isEnabled)
        XCTAssertTrue(app.buttons["wallet.presentationSubmitButton"].isEnabled)
    }

    private func revealPaymentElement(_ element: XCUIElement, in app: XCUIApplication) {
        // Compose static text has no activation point; geometry works for both renderers.
        func visible() -> Bool {
            guard element.exists else { return false }
            let frame = element.frame
            return !frame.isEmpty && frame.minY >= app.frame.minY + 120 && frame.maxY <= app.frame.maxY - 120
        }
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
        for _ in 0..<12 { if visible() { break }; start.press(forDuration: 0.05, thenDragTo: end) }
        for _ in 0..<24 { if visible() { break }; end.press(forDuration: 0.05, thenDragTo: start) }
        XCTAssertTrue(visible(), "Payment field/control is not visible: \(element.identifier) \(element.label)")
    }
}
