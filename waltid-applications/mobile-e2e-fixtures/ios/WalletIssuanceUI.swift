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
    // SpringBoard can ignore the first tap even after reporting a hittable button. Retry only this
    // consent's Allow action once, and require dismissal within the same ten-second wait budget.
    for _ in 0..<2 {
        guard prompt.exists else { return }
        if allow.exists && allow.isEnabled && allow.isHittable {
            allow.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        if prompt.waitForNonExistence(timeout: 5) { return }
    }
    XCTFail("Identity verification consent did not close: \(springboard.debugDescription)",
            file: file, line: line)
}

/// Wait for usable Home after receiving or sharing, without racing the five-second result sheet.
@MainActor
func waitForIdleResultDismissal(_ app: XCUIApplication, timeout: TimeInterval = 20,
                                file: StaticString = #filePath, line: UInt = #line) {
    // Poll one accessibility property; assert receipt dismissal once Home is usable.
    XCTAssertTrue(app.buttons["wallet.scanButton"].wait(for: \.isHittable, toEqual: true, timeout: timeout),
                  app.debugDescription, file: file, line: line)
    XCTAssertFalse(app.buttons["issuance-done"].exists, file: file, line: line)
    XCTAssertFalse(app.buttons["wallet.presentationDone"].exists, file: file, line: line)
}
