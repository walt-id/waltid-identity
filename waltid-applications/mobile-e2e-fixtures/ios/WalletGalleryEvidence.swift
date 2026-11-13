import CryptoKit
#if canImport(TestHelpers)
import TestHelpers
#endif
import XCTest

/// Opt-in wallet-gallery/v1 attachments shared by the two public-demo UI test targets.
@MainActor
extension XCTestCase {
    /// The two iOS hosts share the production accessibility contract for this real issuer journey.
    func runControlledStatusIssuanceAndRevocation(_ app: XCUIApplication, capturePrefix: String,
                                                 openWalletLink: @escaping @MainActor (String) -> Void) async throws {
        guard let raw = ProcessInfo.processInfo.environment["WALLET_STATUS_CONTROL_URL"], let control = URL(string: raw) else {
            throw XCTSkip("Run explicitly with the isolated controlled status issuer")
        }
        let fixture = try ControlledStatusBackend(controlURL: control)
        func shareWithPolicy(clear: Bool) async throws {
            let session = try await DemoBackend.shared.createStatusEnforcementVerifierSession()
            recordGalleryRequest("presentation", value: session.authorizationRequestUri)
            openWalletLink(session.authorizationRequestUri)
            let submit = app.buttons["wallet.presentationSubmitButton"].firstMatch
            XCTAssertTrue(submit.waitForExistence(timeout: 90), app.debugDescription)
            XCTAssertTrue(submit.wait(for: \.isEnabled, toEqual: true, timeout: 20))
            submit.tap()
            let warning = app.staticTexts["Share with status issues?"].firstMatch
            if clear {
                XCTAssertFalse(warning.waitForExistence(timeout: 2))
            } else {
                XCTAssertTrue(warning.waitForExistence(timeout: 10), app.debugDescription)
                app.buttons["credential-status-confirm"].firstMatch.tap()
            }
            let terminal = app.staticTexts.matching(NSPredicate(format:
                "label BEGINSWITH %@ OR label BEGINSWITH %@ OR label BEGINSWITH %@",
                "Presentation sent", "Presentation finished", "Present failed")).firstMatch
            XCTAssertTrue(terminal.waitForExistence(timeout: 90), app.debugDescription)
            try await DemoBackend.shared.verifyCredentialStatusPolicy(sessionID: session.sessionID, clear: clear, timeoutSeconds: 30)
            if !clear {
                let done = app.buttons["wallet.presentationDone"].firstMatch
                XCTAssertTrue(done.wait(for: \.isHittable, toEqual: true, timeout: 10))
                done.tap()
            }
            waitForIdleResultDismissal(app)
        }
        let offer = try await fixture.createOffer(configuration: "staff_access")
        recordGalleryRequest("issuance", value: offer.url)
        openWalletLink(offer.url)
        let accept = app.buttons["wallet.offerAcceptButton"].firstMatch
        let failed = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Receive failed")).firstMatch
        let prepared = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in accept.exists || failed.exists }, object: nil)
        let readiness = await XCTWaiter.fulfillment(of: [prepared], timeout: 90)
        XCTAssertEqual(readiness, .completed)
        XCTAssertTrue(accept.exists, app.debugDescription)
        accept.tap()
        let card = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard.")).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 90), app.debugDescription)
        waitForIdleResultDismissal(app)
        let reference = try await fixture.issuedReference(for: offer)
        XCTAssertTrue(card.isHittable)
        card.tap()
        XCTAssertTrue(app.staticTexts["Status clear"].waitForExistence(timeout: 30), app.debugDescription)
        captureGallery(app, name: capturePrefix + "-status-authenticated-clear")
        assertCredentialStatusControls(app)
        try await shareWithPolicy(clear: true)
        XCTAssertTrue(card.wait(for: \.isHittable, toEqual: true, timeout: 10))
        card.tap()
        try await fixture.setStatus(reference, value: 1)
        let refresh = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "credential-status-check-")).firstMatch
        XCTAssertTrue(refresh.waitForExistence(timeout: 10), app.debugDescription)
        assertCredentialStatusControls(app)
        if !refresh.isHittable { app.swipeUp() }
        refresh.tap()
        XCTAssertTrue(app.staticTexts["Revoked"].waitForExistence(timeout: 30), app.debugDescription)
        captureGallery(app, name: capturePrefix + "-status-authenticated-revoked")
        try await shareWithPolicy(clear: false)
    }

    /// Real metadata, persisted cards, reversible suspension and retained irreversible evidence.
    func runCredentialStatusLifecycle(_ app: XCUIApplication, capturePrefix: String,
                                      openWalletLink: @escaping @MainActor (String) -> Void,
                                      returnToWallet: @escaping @MainActor () -> Void,
                                      relaunch: @escaping @MainActor () -> Void) async throws {
        guard let raw = ProcessInfo.processInfo.environment["WALLET_STATUS_CONTROL_URL"], let control = URL(string: raw) else {
            throw XCTSkip("Run explicitly with the isolated controlled status issuer")
        }
        let fixture = try ControlledStatusBackend(controlURL: control)
        try await fixture.setOutage(false)
        addTeardownBlock { try await fixture.setOutage(false) }

        func cardIDs() -> Set<String> {
            Set(app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
                .allElementsBoundByIndex.map(\.identifier))
        }
        func issue(_ url: String) async throws -> String {
            let before = cardIDs()
            recordGalleryRequest("issuance", value: url)
            openWalletLink(url)
            let accept = app.buttons["wallet.offerAcceptButton"].firstMatch
            XCTAssertTrue(accept.waitForExistence(timeout: 90), app.debugDescription)
            accept.tap()
            waitForIdleResultDismissal(app, timeout: 90)
            let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in cardIDs().count == before.count + 1 }, object: nil)
            let result = await XCTWaiter.fulfillment(of: [ready], timeout: 30)
            XCTAssertEqual(result, .completed)
            return try XCTUnwrap(cardIDs().subtracting(before).onlyElement)
        }
        func openCard(_ identifier: String, title: String) {
            returnToWallet()
            XCTAssertTrue(app.buttons["wallet.scanButton"].firstMatch.wait(for: \.isHittable, toEqual: true, timeout: 20))
            let card = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
            XCTAssertTrue(card.waitForExistence(timeout: 20), app.debugDescription)
            // XCTest's center tap can land on a different card covering this one.
            // Derive the exposed band from the actual sibling frames, without changing stack geometry.
            let frame = card.frame
            let fronts = app.descendants(matching: .any)
                .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
                .allElementsBoundByIndex.filter { $0.identifier != identifier }.map(\.frame)
            let lowerEdge = fronts.filter { $0.minY > frame.minY && $0.intersects(frame) }.map(\.minY).min() ?? frame.maxY
            XCTAssertGreaterThan(lowerEdge, frame.minY)
            card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: (lowerEdge - frame.minY) / (2 * frame.height))).tap()
            XCTAssertTrue(app.staticTexts[title].firstMatch.waitForExistence(timeout: 30), app.debugDescription)
        }
        func button(_ prefix: String) -> XCUIElement {
            app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", prefix)).firstMatch
        }
        func reveal(_ element: XCUIElement) {
            for _ in 0..<8 where !element.isHittable {
                if element.exists && !element.frame.isEmpty && element.frame.minY < app.frame.midY { app.swipeDown() }
                else { app.swipeUp() }
            }
            XCTAssertTrue(element.exists && element.isHittable, app.debugDescription)
        }
        func refresh(expect title: String) {
            let refresh = button("credential-status-check-")
            XCTAssertTrue(refresh.waitForExistence(timeout: 10), app.debugDescription)
            reveal(refresh)
            XCTAssertTrue(refresh.wait(for: \.isEnabled, toEqual: true, timeout: 20))
            refresh.tap()
            XCTAssertTrue(app.staticTexts[title].firstMatch.waitForExistence(timeout: 30), app.debugDescription)
            XCTAssertTrue(refresh.wait(for: \.isEnabled, toEqual: true, timeout: 30))
        }
        func refreshReview(_ card: String, action: String) {
            let id = String(card.dropFirst("wallet.credentialCard.".count))
            let details = app.buttons["credential-status-details-" + id].firstMatch
            XCTAssertTrue(details.waitForExistence(timeout: 10), app.debugDescription)
            reveal(details); details.tap()
            let checked = app.staticTexts["credential-status-last-checked-" + id].firstMatch
            XCTAssertTrue(checked.waitForExistence(timeout: 10), app.debugDescription)
            let before = checked.label
            refresh(expect: "Revoked")
            XCTAssertNotEqual(checked.label, before, "Explicit refresh must display its new authenticated result")
            let submit = app.buttons["wallet.presentationSubmitButton"].firstMatch
            XCTAssertTrue(submit.wait(for: \.isEnabled, toEqual: true, timeout: 15))
            XCTAssertEqual(submit.label, action)
            XCTAssertFalse(app.buttons["wallet.presentationDone"].exists)
            XCTAssertFalse(app.staticTexts["Credential status warning"].exists)
        }
        func captureStatus(_ suffix: String) async {
            let notice = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Status changed:")).firstMatch
            if notice.exists {
                let dismiss = app.buttons["Dismiss"].firstMatch
                if dismiss.exists && dismiss.isHittable { dismiss.tap() }
                let hidden = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !notice.exists }, object: nil)
                let result = await XCTWaiter.fulfillment(of: [hidden], timeout: 10)
                XCTAssertEqual(result, .completed, "Transient feedback must not cover the evidence")
            }
            captureGallery(app, name: capturePrefix + suffix)
        }

        // Wallet IDs isolate credentials; the user's automatic-check preference is app-wide.
        // Establish this journey's precondition through the real control, including after a
        // previous manual-mode run, rather than replacing persisted preferences behind the UI.
        let initialSettings = app.buttons["wallet.settingsButton"].firstMatch
        XCTAssertTrue(initialSettings.waitForExistence(timeout: 15)); initialSettings.tap()
        let statusSettings = app.buttons["settings-credential-status"].firstMatch
        XCTAssertTrue(statusSettings.waitForExistence(timeout: 15)); statusSettings.tap()
        await setAutomaticStatusChecks(app.switches["settings-credential-status-automatic"].firstMatch, enabled: true)
        returnToWallet()

        let pidScenario = try XCTUnwrap(DemoBackend.scenarios.first { $0.id == "eudi-pid-sdjwt" })
        let pidOffer = try await DemoBackend.shared.createOffer(scenario: pidScenario)
        let pidCard = try await issue(pidOffer.offerUrl)
        let eventOffer = try await fixture.createOffer(configuration: "event_pass")
        let eventCard = try await issue(eventOffer.url)
        let event = try await fixture.issuedReference(for: eventOffer)
        let accessOffer = try await fixture.createOffer(configuration: "staff_access")
        let accessCard = try await issue(accessOffer.url)
        let access = try await fixture.issuedReference(for: accessOffer)
        XCTAssertEqual(cardIDs().count, 3)
        captureGallery(app, name: capturePrefix + "-status-multiple-real-credentials")
        let savedCards = cardIDs()
        let restartStarted = ProcessInfo.processInfo.systemUptime
        app.terminate(); relaunch()
        XCTAssertEqual(cardIDs(), savedCards, "Restart must retain every independently issued card")
        if ProcessInfo.processInfo.environment["WALLET_GALLERY_CAPTURE"] == "1" {
            let data = try JSONSerialization.data(withJSONObject: [
                "elapsedSeconds": ProcessInfo.processInfo.systemUptime - restartStarted,
                "storedImageBearingCredentials": savedCards.count,
                "measurement": "XCTest termination, launch, PIN and local-card readiness",
            ], options: [.sortedKeys])
            let receipt = XCTAttachment(string: String(decoding: data, as: UTF8.self))
            receipt.name = "credential-status-startup-readiness"
            receipt.lifetime = .keepAlways
            add(receipt)
        }

        openCard(pidCard, title: "Status not provided")
        captureGallery(app, name: capturePrefix + "-status-public-demo-no-reference")
        openCard(eventCard, title: "Status clear")
        try await fixture.setStatus(event, value: 1)
        refresh(expect: "Suspended")
        await captureStatus("-status-authenticated-suspended")
        try await fixture.setStatus(event, value: 0)
        refresh(expect: "Status clear")
        await captureStatus("-status-suspension-cleared")

        openCard(accessCard, title: "Status clear")
        assertCredentialStatusControls(app)
        captureGallery(app, name: capturePrefix + "-status-authenticated-clear")
        try await fixture.setStatus(access, value: 1)
        refresh(expect: "Revoked")
        let disclosure = button("credential-status-details-")
        XCTAssertFalse(disclosure.exists, "Simple issuer results have no redundant disclosure")
        let checked = app.staticTexts.matching(NSPredicate(format: "identifier BEGINSWITH %@", "credential-status-last-checked-")).firstMatch
        XCTAssertTrue(checked.waitForExistence(timeout: 10), app.debugDescription)
        let originalCheckTime = checked.label
        XCTAssertFalse(originalCheckTime.isEmpty)
        reveal(checked)
        await captureStatus("-status-authenticated-revoked")
        try await fixture.setOutage(true)
        refresh(expect: "Last known: revoked")
        XCTAssertEqual(checked.label, originalCheckTime, "A failed check must preserve the original evidence timestamp")
        XCTAssertTrue(disclosure.waitForExistence(timeout: 10), app.debugDescription)
        assertCredentialStatusControls(app, expectsDisclosure: true)
        reveal(disclosure); disclosure.tap()
        let latest = app.staticTexts.matching(NSPredicate(format: "identifier BEGINSWITH %@", "credential-status-latest-attempt-")).firstMatch
        XCTAssertTrue(latest.waitForExistence(timeout: 10), app.debugDescription)
        reveal(latest)
        // Compose exposes SettingsNotice as a labelled accessibility container, SwiftUI as text.
        let stale = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Status outdated")).firstMatch
        XCTAssertTrue(stale.waitForExistence(timeout: 45), "The real signed freshness deadline must expire: \(app.debugDescription)")
        reveal(stale)
        XCTAssertEqual(checked.label, originalCheckTime)
        captureGallery(app, name: capturePrefix + "-status-outage-retained-evidence")
        try await fixture.setOutage(false)
        refresh(expect: "Revoked")
        returnToWallet()
        captureGallery(app, name: capturePrefix + "-status-home-adverse")

        let session = try await DemoBackend.shared.createCredentialStatusVerifierSession()
        recordGalleryRequest("presentation", value: session.authorizationRequestUri)
        openWalletLink(session.authorizationRequestUri)
        let share = app.buttons["wallet.presentationSubmitButton"].firstMatch
        XCTAssertTrue(share.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertTrue(share.wait(for: \.isEnabled, toEqual: true, timeout: 30))
        XCTAssertEqual(share.label, "Share")
        let selectedAdverse = app.staticTexts.matching(NSPredicate(format: "label IN %@", ["Revoked", "Last known: revoked"])).firstMatch
        XCTAssertTrue(selectedAdverse.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.staticTexts["Status not provided"].firstMatch.exists)
        captureGallery(app, name: capturePrefix + "-status-mixed-sharing")
        refreshReview(accessCard, action: "Share")
        captureGallery(app, name: capturePrefix + "-status-sharing-refreshed")
        share.tap()
        // Native alerts and Compose dialogs expose the same heading and action IDs,
        // but only the native renderer exposes an XCUIElementTypeAlert container.
        let warning = app.staticTexts["Share with status issues?"].firstMatch
        XCTAssertTrue(warning.waitForExistence(timeout: 10), app.debugDescription)
        let affected = app.staticTexts.matching(NSPredicate(format:
            "label CONTAINS %@ AND label CONTAINS %@", "Workplace access", "The recipient may reject it.")).firstMatch
        XCTAssertTrue(affected.waitForExistence(timeout: 5))
        captureGallery(app, name: capturePrefix + "-status-sharing-confirmation")
        app.buttons["credential-status-back"].firstMatch.tap()
        XCTAssertTrue(share.waitForExistence(timeout: 10)); XCTAssertTrue(share.isEnabled)
        share.tap()
        XCTAssertTrue(warning.waitForExistence(timeout: 10))
        app.buttons["credential-status-confirm"].firstMatch.tap()
        let terminal = app.staticTexts.matching(NSPredicate(format:
            "label BEGINSWITH %@ OR label BEGINSWITH %@ OR label BEGINSWITH %@",
            "Presentation sent", "Presentation finished", "Present failed")).firstMatch
        XCTAssertTrue(terminal.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertFalse(terminal.label.hasPrefix("Present failed"), terminal.label)
        _ = try await DemoBackend.shared.waitForVerifierSuccess(sessionID: session.sessionID, timeoutSeconds: 30)
        // This ordinary request uses the public verifier's default policy; it does not prove status enforcement.
        captureGallery(app, name: capturePrefix + "-status-sharing-result")
        waitForIdleResultDismissal(app)
        returnToWallet()

        let alternativeOffer = try await fixture.createOffer(configuration: "staff_access_alternative")
        let alternativeCard = try await issue(alternativeOffer.url)
        _ = try await fixture.issuedReference(for: alternativeOffer)
        func toggleCredential(_ card: String) {
            let id = String(card.dropFirst("wallet.credentialCard.".count))
            let choice = app.descendants(matching: .any).matching(NSPredicate(
                format: "identifier BEGINSWITH %@ AND identifier CONTAINS %@", "wallet.presentationCredentialToggle.", id)).firstMatch
            XCTAssertTrue(choice.waitForExistence(timeout: 15), app.debugDescription)
            reveal(choice)
            choice.tap()
        }
        // The same explicit verifier policy distinguishes a clear selected alternative from an unselected revoked card.
        let clearSession = try await DemoBackend.shared.createStatusEnforcementVerifierSession()
        recordGalleryRequest("presentation", value: clearSession.authorizationRequestUri)
        openWalletLink(clearSession.authorizationRequestUri)
        XCTAssertTrue(share.waitForExistence(timeout: 90), app.debugDescription)
        // A new review initially selects the first stored match. Deliberately deselect it,
        // then select the clear alternative; backend acceptance below proves that only it was sent.
        toggleCredential(accessCard)
        toggleCredential(alternativeCard)
        XCTAssertTrue(share.wait(for: \.isEnabled, toEqual: true, timeout: 15))
        captureGallery(app, name: capturePrefix + "-status-unselected-adverse-alternative")
        share.tap()
        XCTAssertFalse(warning.waitForExistence(timeout: 2), "An unselected adverse alternative must not trigger confirmation")
        XCTAssertTrue(terminal.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertFalse(terminal.label.hasPrefix("Present failed"), terminal.label)
        try await DemoBackend.shared.verifyCredentialStatusPolicy(sessionID: clearSession.sessionID, clear: true, timeoutSeconds: 30)
        captureGallery(app, name: capturePrefix + "-status-clear-verifier-policy-result")
        waitForIdleResultDismissal(app)

        let revokedSession = try await DemoBackend.shared.createStatusEnforcementVerifierSession()
        recordGalleryRequest("presentation", value: revokedSession.authorizationRequestUri)
        openWalletLink(revokedSession.authorizationRequestUri)
        XCTAssertTrue(share.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertTrue(share.wait(for: \.isEnabled, toEqual: true, timeout: 15))
        share.tap()
        XCTAssertTrue(warning.waitForExistence(timeout: 10), app.debugDescription)
        captureGallery(app, name: capturePrefix + "-status-revoked-verifier-policy-confirmation")
        app.buttons["credential-status-confirm"].firstMatch.tap()
        XCTAssertTrue(terminal.waitForExistence(timeout: 90), app.debugDescription)
        try await DemoBackend.shared.verifyCredentialStatusPolicy(sessionID: revokedSession.sessionID, clear: false, timeoutSeconds: 30)
        captureGallery(app, name: capturePrefix + "-status-revoked-verifier-policy-result")
        let rejectedDone = app.buttons["wallet.presentationDone"].firstMatch
        XCTAssertTrue(rejectedDone.wait(for: \.isHittable, toEqual: true, timeout: 10))
        rejectedDone.tap()
        waitForIdleResultDismissal(app)
        returnToWallet()

        // Exercise the real preference before payment. Manual mode lets us deliberately
        // review expired evidence without an automatic refresh invalidating its warning
        // while XCTest captures it. The earlier outage assertions still prove real expiry.
        await runCredentialStatusSettingsReview(app, capturePrefix: capturePrefix, finishInManualMode: true)
        returnToWallet()

        let paymentOffer = try await DemoBackend.shared.createOffer(scenario: DemoBackend.credentialStatusPaymentScenario)
        _ = try await issue(paymentOffer.offerUrl)
        let payment = try await DemoBackend.shared.createCredentialStatusPaymentVerifierSession()
        recordGalleryRequest("presentation", value: payment.authorizationRequestUri)
        openWalletLink(payment.authorizationRequestUri)
        XCTAssertTrue(share.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertTrue(app.staticTexts["Review payment"].firstMatch.waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["42.00"].firstMatch.exists)
        XCTAssertTrue(app.staticTexts["EUR"].firstMatch.exists)
        XCTAssertTrue(app.staticTexts["ACME Corp"].firstMatch.exists)
        XCTAssertEqual(share.label, "Confirm payment")
        captureGallery(app, name: capturePrefix + "-status-payment-main-terms")
        XCTAssertTrue(share.wait(for: \.isEnabled, toEqual: true, timeout: 15))
        reveal(selectedAdverse)
        XCTAssertTrue(app.staticTexts["Last known: revoked"].firstMatch.waitForExistence(timeout: 45),
                      "Payment evidence must deliberately review the expired result in manual mode")
        captureGallery(app, name: capturePrefix + "-status-payment-selected-credentials")
        refreshReview(accessCard, action: "Confirm payment")
        captureGallery(app, name: capturePrefix + "-status-payment-refreshed")
        let paymentWarning = app.staticTexts["Credential status warning"].firstMatch
        func showPaymentWarning() async {
            share.tap()
            let changed = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@",
                "The review changed. Check the updated information before continuing.")).firstMatch
            let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                paymentWarning.exists || changed.exists
            }, object: nil)
            let result = await XCTWaiter.fulfillment(of: [ready], timeout: 10)
            XCTAssertEqual(result, .completed, app.debugDescription)
            if !paymentWarning.exists {
                // The deliberately short signed lifetime can expire during review. The gate must
                // return to review without submitting; deliberately approve the updated evidence.
                XCTAssertTrue(changed.exists, app.debugDescription)
                XCTAssertTrue(app.staticTexts["Last known: revoked"].firstMatch.exists)
                XCTAssertFalse(terminal.exists)
                XCTAssertTrue(share.wait(for: \.isEnabled, toEqual: true, timeout: 10))
                share.tap()
            }
            XCTAssertTrue(paymentWarning.waitForExistence(timeout: 10), app.debugDescription)
        }
        await showPaymentWarning()
        XCTAssertEqual(app.buttons["credential-status-confirm"].firstMatch.label, "Confirm payment")
        XCTAssertFalse(app.buttons["Share anyway"].exists)
        captureGallery(app, name: capturePrefix + "-status-payment-confirmation")
        app.buttons["credential-status-back"].firstMatch.tap()
        XCTAssertTrue(share.wait(for: \.isEnabled, toEqual: true, timeout: 15))
        XCTAssertEqual(share.label, "Confirm payment")
        await showPaymentWarning()
        app.buttons["credential-status-confirm"].firstMatch.tap()
        XCTAssertTrue(terminal.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertFalse(terminal.label.hasPrefix("Present failed"), terminal.label)
        let paymentResult = try await DemoBackend.shared.waitForVerifierSuccess(sessionID: payment.sessionID, timeoutSeconds: 30)
        let paymentPolicies = (paymentResult["policy_results"] as? [String: Any])?["vp_policies"] as? [String: Any] ?? [:]
        XCTAssertEqual(Set(paymentPolicies.keys), Set(["pid", "payment_card", "workplace"]))
        captureGallery(app, name: capturePrefix + "-status-payment-result")
        waitForIdleResultDismissal(app)
        returnToWallet()
    }

    func runCredentialStatusSettingsReview(_ app: XCUIApplication, capturePrefix: String,
                                          finishInManualMode: Bool = false) async {
        let settings = app.buttons["wallet.settingsButton"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 10)); settings.tap()
        captureGallery(app, name: capturePrefix + "-status-root-settings")
        let destination = app.buttons["settings-credential-status"].firstMatch
        XCTAssertTrue(destination.waitForExistence(timeout: 10)); destination.tap()
        let automatic = app.switches["settings-credential-status-automatic"].firstMatch
        XCTAssertTrue(automatic.waitForExistence(timeout: 10))
        XCTAssertEqual(automaticStatusChecksState(automatic), true)
        await setAutomaticStatusChecks(automatic, enabled: false)
        captureGallery(app, name: capturePrefix + "-status-settings-manual")
        await setAutomaticStatusChecks(automatic, enabled: true)
        let batch = app.buttons["settings-credential-status-check-all"].firstMatch
        XCTAssertTrue(batch.wait(for: \.isEnabled, toEqual: true, timeout: 30)); batch.tap()
        XCTAssertTrue(app.staticTexts["Processed"].firstMatch.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(batch.wait(for: \.isEnabled, toEqual: true, timeout: 30))
        captureGallery(app, name: capturePrefix + "-status-settings-completed")
        let help = app.buttons["settings-credential-status-how"].firstMatch
        XCTAssertTrue(help.waitForExistence(timeout: 10)); help.tap()
        let explanation = app.staticTexts["Recipients verify credential status independently."].firstMatch
        for _ in 0..<4 where !explanation.isHittable { app.swipeUp() }
        XCTAssertTrue(explanation.isHittable, app.debugDescription)
        captureGallery(app, name: capturePrefix + "-status-settings-help")
        if !help.isHittable { app.swipeDown() }
        help.tap()
        let collapsed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !explanation.exists }, object: nil)
        let collapseResult = await XCTWaiter.fulfillment(of: [collapsed], timeout: 5)
        XCTAssertEqual(collapseResult, .completed,
                       "Help must disappear after the disclosure animation")
        XCTAssertTrue(app.staticTexts["Processed"].firstMatch.exists)
        XCTAssertEqual(automaticStatusChecksState(automatic), true)
        if finishInManualMode { await setAutomaticStatusChecks(automatic, enabled: false) }
    }

    private func automaticStatusChecksState(_ automatic: XCUIElement) -> Bool? {
        guard automatic.exists else { return nil }
        switch automatic.value as? String {
        case "1": return true
        case "0": return false
        // Compose exposes its toggleable state through the selected trait;
        // SwiftUI exposes the native switch's numeric accessibility value.
        case "": return automatic.isSelected
        default: return nil
        }
    }

    private func setAutomaticStatusChecks(_ automatic: XCUIElement, enabled: Bool) async {
        XCTAssertTrue(automatic.waitForExistence(timeout: 10))
        let initial = automaticStatusChecksState(automatic)
        XCTAssertNotNil(initial, automatic.debugDescription)
        if initial == enabled { return }
        // SwiftUI may expose an outer labelled switch and a nested native thumb.
        var target = automatic
        for _ in 0..<3 {
            let child = target.switches.firstMatch
            if !child.exists { break }
            target = child
        }
        target.tap()
        let changed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.automaticStatusChecksState(automatic) == enabled
        }, object: nil)
        let result = await XCTWaiter.fulfillment(of: [changed], timeout: 5)
        XCTAssertEqual(result, .completed, automatic.debugDescription)
    }

    private func assertCredentialStatusControls(_ app: XCUIApplication, expectsDisclosure: Bool = false) {
        let refresh = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "credential-status-check-")).firstMatch
        let details = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "credential-status-details-")).firstMatch
        XCTAssertTrue(refresh.waitForExistence(timeout: 10))
        XCTAssertEqual(details.exists, expectsDisclosure)
        for control in expectsDisclosure ? [refresh, details] : [refresh] {
            XCTAssertGreaterThanOrEqual(control.frame.width, 44)
            XCTAssertGreaterThanOrEqual(control.frame.height, 44)
        }
        if expectsDisclosure { XCTAssertFalse(refresh.frame.intersects(details.frame)) }
    }

    func captureGallery(_ app: XCUIApplication, name: String) {
        guard ProcessInfo.processInfo.environment["WALLET_GALLERY_CAPTURE"] == "1" else { return }
        guard app.state == .runningForeground else {
            return XCTFail("Gallery app is not foreground")
        }
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func recordGalleryRequest(_ kind: String, value: String) {
        guard ProcessInfo.processInfo.environment["WALLET_GALLERY_CAPTURE"] == "1" else { return }
        let digest = SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
        let attachment = XCTAttachment(string: digest)
        attachment.name = "WALLET_GALLERY_REQUEST_DIGEST-\(kind)-\(digest)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

private extension Set {
    var onlyElement: Element? { count == 1 ? first : nil }
}
