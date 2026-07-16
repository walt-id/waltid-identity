import XCTest

@MainActor
final class MockWalletUITests: XCTestCase {
    private static let didClientID = "decentralized_identifier:did:jwk:abc"
    private static let x509SanDnsClientID = "x509_san_dns:verifier.example"

    func testUrlEditorsAreTopControlsInReceiveAndPresentTabs() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        XCTAssertTrue(offerInput.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.offerScanButton"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["wallet.status"].waitForExistence(timeout: 10))
        XCTAssertLessThan(
            offerInput.frame.minY,
            app.staticTexts["wallet.status"].frame.minY,
            "Receive URL entry should be the first control in the tab"
        )

        ui.tapTab(label: "Present")
        let presentationInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        XCTAssertTrue(presentationInput.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.presentationScanButton"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["wallet.status"].waitForExistence(timeout: 10))
        XCTAssertLessThan(
            presentationInput.frame.minY,
            app.staticTexts["wallet.status"].frame.minY,
            "Presentation URL entry should be the first control in the tab"
        )
    }

    func testDeepLinksRouteToReceiveAndPresentTabs() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        let offerUrl = "openid-credential-offer://mock"
        ui.openDeepLink(offerUrl)
        XCTAssertTrue(app.tabBars.buttons["Receive"].isSelected)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
        )
        XCTAssertTrue(app.buttons["wallet.receiveButton"].isEnabled)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")

        let presentationUrl = "openid4vp://mock"
        ui.openDeepLink(presentationUrl)
        XCTAssertTrue(app.tabBars.buttons["Present"].isSelected)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.presentationInput",
                fallbackLabel: "OpenID4VP request URL",
                value: presentationUrl,
                timeout: 10
            )
        )
    }

    func testPresentTabExplainsWhyPreviewIsUnavailableWithoutCredentials() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )

        XCTAssertFalse(app.buttons["wallet.presentButton"].isEnabled)
        XCTAssertTrue(app.staticTexts["No credentials available"].waitForExistence(timeout: 10))
    }

    func testCredentialOfferDeepLinksResetReceiveDetailStackWhenUrlIsUnchanged() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        let offerUrl = "openid-credential-offer://mock"
        ui.openDeepLink(offerUrl)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))

        ui.openDeepLink(offerUrl)
        XCTAssertTrue(app.tabBars.buttons["Receive"].isSelected)
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
        )
        XCTAssertTrue(app.buttons["wallet.receiveButton"].isEnabled)
    }

    func testPresentationDeepLinksResetPresentDetailStackWhenUrlIsUnchanged() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        let presentationUrl = "openid4vp://mock"
        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: presentationUrl
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))

        ui.openDeepLink(presentationUrl)
        XCTAssertTrue(app.tabBars.buttons["Present"].isSelected)
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.presentationInput",
                fallbackLabel: "OpenID4VP request URL",
                value: presentationUrl,
                timeout: 10
            )
        )
    }

    func testPresentationDisclosureImagesRenderAsImages() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Portrait"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["$.portrait"].exists)
        ui.assertExists(identifier: ui.claimImageIdentifier(path: "disclosures[1].portrait"), timeout: 10)

        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Portrait"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["$.portrait"].exists)
        ui.assertExists(identifier: ui.claimImageIdentifier(path: "disclosures[1].portrait"), timeout: 10)
    }

    func testCredentialDetailsStayScopedToCredentialsTabNavigationStack() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        XCTAssertTrue(app.tabBars.buttons["Credentials"].isSelected)
        XCTAssertTrue(app.staticTexts["No credentials yet"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Credentials"].isSelected)

        ui.tapTab(label: "Receive")
        XCTAssertFalse(app.staticTexts["Credential details"].exists)

        ui.tapTab(label: "Credentials")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
    }

    func testReceiveAndPresentDisableUrlControlsWhileLoading() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_WALLET_DELAY_MS": "1500",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Resolving credential offer", "Receive failed"], timeout: 10),
            "Resolving credential offer..."
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL").isEnabled)
        XCTAssertFalse(app.buttons["wallet.receiveButton"].isEnabled)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Receiving credential", "Receive failed"], timeout: 10),
            "Receiving credential..."
        )
        XCTAssertFalse(app.buttons["wallet.offerAcceptButton"].isEnabled)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Resolving presentation", "Preview failed"], timeout: 10),
            "Resolving presentation..."
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL").isEnabled)
        XCTAssertFalse(app.buttons["wallet.presentButton"].isEnabled)
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
    }

    func testCredentialCardsExposeStableTappableButtonIdentifier() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        let card = app.buttons["wallet.credentialCard.cred-1"]
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertTrue(card.isHittable)
        card.tap()
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
    }

    func testTabbedReceiveAndPresentFlowUsesMockWallet() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Credentials")
        XCTAssertTrue(app.staticTexts["No credentials yet"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL").isEnabled)
        XCTAssertTrue(app.buttons["wallet.receiveNewButton"].waitForExistence(timeout: 10))
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Expires 2026-06-17"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example Issuer"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["jwt_vc_json"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.images["Credential image"].waitForExistence(timeout: 10))
        ui.tapNavigationBack()
        ui.tapButton(identifier: "wallet.receiveNewButton", fallbackLabel: "New receive")
        let resetOfferInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        XCTAssertTrue(resetOfferInput.isEnabled)
        XCTAssertTrue(["", "Credential offer URL"].contains(resetOfferInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.receiveButton"].isEnabled)

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.images["Credential portrait"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Ada Lovelace"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Expires 2026-06-17"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        XCTAssertFalse(ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL").isEnabled)
        XCTAssertTrue(app.staticTexts["Example Verifier"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Payment Authorization"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Amount"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["129.90"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Currency"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["EUR"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Payee"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example Merchant"].waitForExistence(timeout: 10))
        assertVerifierTechnicalDetailsCollapsedUntilRequested(app: app, ui: ui)
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        assertPresentationActionsFollowReviewContent(app: app)

        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Expires 2026-06-17"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        ui.tapNavigationBack()

        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Presentation sent", "Present failed"], timeout: 10),
            "Presentation sent"
        )
        ui.tapTab(label: "Credentials")
        XCTAssertFalse(app.staticTexts["Presentation sent"].isHittable)
        ui.tapTab(label: "Receive")
        XCTAssertFalse(app.staticTexts["Presentation sent"].isHittable)
        ui.tapTab(label: "Present")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Presentation sent", "Present failed"], timeout: 10),
            "Presentation sent"
        )
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationCancelButton"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationNewButton"].waitForExistence(timeout: 10))
        assertPresentationNewActionPrecedesReadOnlyReview(app: app)
        ui.tapButton(identifier: "wallet.presentationNewButton", fallbackLabel: "New presentation")
        let resetPresentationInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        XCTAssertTrue(resetPresentationInput.isEnabled)
        XCTAssertTrue(["", "OpenID4VP request URL"].contains(resetPresentationInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.presentButton"].isEnabled)
    }

    func testPresentationShowsReadableVerifierFallbackForDidClientIDs() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_DID_VERIFIER": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertTrue(app.staticTexts["DID verifier"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts[Self.didClientID].exists)
        ui.tapButton(identifier: "wallet.verifierTechnicalDetailsToggle", fallbackLabel: "Show technical details")
        XCTAssertTrue(app.staticTexts[Self.didClientID].waitForExistence(timeout: 10))
    }

    func testPresentationShowsReadableVerifierFallbackForX509SanDnsClientIDs() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_DNS_VERIFIER": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        XCTAssertTrue(app.staticTexts["verifier.example"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts[Self.x509SanDnsClientID].exists)
        ui.tapButton(identifier: "wallet.verifierTechnicalDetailsToggle", fallbackLabel: "Show technical details")
        XCTAssertTrue(app.staticTexts[Self.x509SanDnsClientID].waitForExistence(timeout: 10))
    }

    func testCredentialDetailsStayScopedToReceiveTabNavigationStack() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertFalse(app.staticTexts["Credential details"].exists)

        ui.tapTab(label: "Receive")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
    }

    func testCredentialDetailsStayScopedToPresentTabNavigationStack() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertFalse(app.staticTexts["Credential details"].exists)

        ui.tapTab(label: "Present")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
    }

    func testPresentationDetailsResolveDuplicateCredentialOptionsIndependently() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_DUPLICATE_PRESENTATION_OPTIONS": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL"),
            value: "openid-credential-offer://mock"
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        ui.tapButton(identifier: "wallet.offerAcceptButton", fallbackLabel: "Accept")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )

        ui.tapTab(label: "Present")
        ui.replaceText(
            in: ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL"),
            value: "openid4vp://mock"
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )

        let identityToggleID = "wallet.presentationDisclosure.8:identity6:cred-112:$.given_name"
        let ageToggleID = "wallet.presentationDisclosure.3:age6:cred-113:$.age_over_18"
        ui.assertExists(identifier: identityToggleID, timeout: 10)
        let identityToggle = app.switches[identityToggleID]
        XCTAssertEqual(identityToggle.value as? String, "0")
        identityToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        XCTAssertEqual(identityToggle.value as? String, "1")
        ui.assertExists(identifier: ageToggleID, timeout: 10)
        XCTAssertEqual(app.switches[ageToggleID].value as? String, "0")

        ui.tapElement(identifier: "wallet.credentialCard.3:age6:cred-1")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Age disclosure"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Over 18"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Identity disclosure"].exists)
    }

    private func assertPresentationActionsFollowReviewContent(app: XCUIApplication) {
        let verifier = app.staticTexts["Example Verifier"]
        let credential = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.credentialCard."))
            .firstMatch
        let share = app.buttons["wallet.presentationSubmitButton"]

        XCTAssertTrue(verifier.waitForExistence(timeout: 10), "Verifier details are missing")
        XCTAssertTrue(credential.waitForExistence(timeout: 10), "Shared credential card is missing")
        XCTAssertTrue(share.waitForExistence(timeout: 10), "Share action is missing")
        XCTAssertLessThan(
            verifier.frame.minY,
            share.frame.minY,
            "Share action should be below verifier details so the verifier is reviewed before consent"
        )
        XCTAssertLessThan(
            credential.frame.minY,
            share.frame.minY,
            "Share action should be below shared credential details so the credential is reviewed before consent"
        )
    }

    private func assertVerifierTechnicalDetailsCollapsedUntilRequested(app: XCUIApplication, ui: WalletE2EUI) {
        XCTAssertFalse(app.staticTexts["Client ID"].exists, "Technical verifier fields should not be expanded by default")
        ui.tapButton(identifier: "wallet.verifierTechnicalDetailsToggle", fallbackLabel: "Show technical details")
        XCTAssertTrue(app.staticTexts["Client ID"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["https://verifier.example/response"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["state-123"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["nonce-456"].waitForExistence(timeout: 10))
    }

    private func assertPresentationNewActionPrecedesReadOnlyReview(app: XCUIApplication) {
        let elements = app.descendants(matching: .any).allElementsBoundByIndex
        let newActionIndex = elements.firstIndex { $0.identifier == "wallet.presentationNewButton" }
        let verifierIndex = elements.firstIndex { $0.label == "Example Verifier" }

        XCTAssertNotNil(newActionIndex, "New presentation action is missing")
        XCTAssertNotNil(verifierIndex, "Read-only presentation review is missing")
        XCTAssertLessThan(
            newActionIndex ?? Int.max,
            verifierIndex ?? Int.min,
            "New presentation action should precede the read-only review so starting over stays easy"
        )
    }
}
