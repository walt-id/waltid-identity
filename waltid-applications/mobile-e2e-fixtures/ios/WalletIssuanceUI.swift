import XCTest

/// First-use mdoc registration is an expected system step, separate from wallet receipt rendering.
@MainActor
func allowIdentityDocumentRegistrationIfRequested(file: StaticString = #filePath, line: UInt = #line) {
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    // iOS 27 reports this container as both Alert and Sheet. Match its text without binding a type.
    let prompt = springboard.descendants(matching: .any)
        .matching(NSPredicate(format: "label CONTAINS %@", "Identity Verification")).firstMatch
    guard prompt.waitForExistence(timeout: 15) else { return }
    let allow = springboard.buttons["Allow"]
    let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
        prompt.exists && allow.exists && allow.isEnabled && allow.isHittable
    }, object: nil)
    guard XCTWaiter.wait(for: [ready], timeout: 5) == .completed else {
        XCTFail("Identity verification consent has no hittable Allow button: \(springboard.debugDescription)",
                file: file, line: line)
        return
    }
    // A frame can exist before the system alert accepts input. Resolve the button's current center
    // after it is hittable, without binding the inconsistently typed Alert/Sheet container.
    allow.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    XCTAssertTrue(prompt.waitForNonExistence(timeout: 10),
                  "Identity verification consent did not close: \(springboard.debugDescription)",
                  file: file, line: line)
}

/// Wait for usable Home after receiving or sharing, without racing the five-second result sheet.
@MainActor
func waitForIdleResultDismissal(_ app: XCUIApplication, timeout: TimeInterval = 20,
                                file: StaticString = #filePath, line: UInt = #line) {
    let home = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
        !app.buttons["issuance-done"].exists && !app.buttons["wallet.presentationDone"].exists
            && app.buttons["wallet.scanButton"].exists && app.buttons["wallet.scanButton"].isHittable
    }, object: nil)
    XCTAssertEqual(XCTWaiter.wait(for: [home], timeout: timeout), .completed, app.debugDescription,
                   file: file, line: line)
    XCTAssertFalse(app.buttons["issuance-done"].exists, file: file, line: line)
    XCTAssertFalse(app.buttons["wallet.presentationDone"].exists, file: file, line: line)
}
