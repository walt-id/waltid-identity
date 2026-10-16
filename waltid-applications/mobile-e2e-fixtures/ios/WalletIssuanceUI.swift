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
    guard allow.waitForExistence(timeout: 5) else {
        XCTFail("Identity verification consent has no Allow button", file: file, line: line)
        return
    }
    // Use the observed button frame to avoid XCTest's conflicting alert identity during hit testing.
    let frame = allow.frame
    XCTAssertFalse(frame.isEmpty, "Identity verification button has no frame", file: file, line: line)
    guard !frame.isEmpty else { return }
    springboard.coordinate(withNormalizedOffset: .zero)
        .withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
    XCTAssertTrue(prompt.waitForNonExistence(timeout: 10), "Identity verification consent did not close",
                  file: file, line: line)
}

/// Wait for usable Home after receiving or sharing, without racing the five-second result sheet.
@MainActor
func waitForIdleResultDismissal(_ app: XCUIApplication, timeout: TimeInterval = 20,
                                file: StaticString = #filePath, line: UInt = #line) {
    let home = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"),
                                        object: app.buttons["wallet.scanButton"])
    XCTAssertEqual(XCTWaiter.wait(for: [home], timeout: timeout), .completed, app.debugDescription,
                   file: file, line: line)
    XCTAssertFalse(app.buttons["issuance-done"].exists, file: file, line: line)
    XCTAssertFalse(app.buttons["wallet.presentationDone"].exists, file: file, line: line)
}
