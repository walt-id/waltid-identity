import XCTest

@MainActor
final class MockWalletUITests: XCTestCase {
    private static let didClientID = "decentralized_identifier:did:jwk:abc"

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
        XCTAssertTrue(app.staticTexts["Example Issuer"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example credential"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["jwt_vc_json"].waitForExistence(timeout: 10))
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

    func testTransactionCodeOfferCanBeDeclinedWithoutCode() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_TX_CODE_REQUIRED": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        ui.tapTab(label: "Receive")
        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: "openid-credential-offer://mock")
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        XCTAssertTrue(app.secureTextFields["wallet.txCodeInput"].waitForExistence(timeout: 10))
        let transactionCodeSection = app.staticTexts["wallet.offerTransactionCodeSection"]
        XCTAssertTrue(transactionCodeSection.waitForExistence(timeout: 10))
        XCTAssertLessThan(
            app.staticTexts["wallet.status"].frame.minY,
            transactionCodeSection.frame.minY,
            "Receive status should precede the offer review"
        )

        let accept = app.buttons["Accept"]
        let decline = app.buttons["Decline"]
        XCTAssertTrue(accept.waitForExistence(timeout: 10))
        XCTAssertFalse(accept.isEnabled)
        XCTAssertTrue(decline.waitForExistence(timeout: 10))
        XCTAssertTrue(decline.isEnabled)
        decline.tap()

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Credential offer declined", "Receive failed"], timeout: 10),
            "Credential offer declined"
        )
        XCTAssertTrue(app.buttons["wallet.receiveButton"].waitForExistence(timeout: 10))
    }

    func testPresentationDeclineSendsProtocolRejection() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10), "Wallet ready")

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
        XCTAssertEqual(ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10), "Received 1 credential(s)")

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

        ui.tapButton(identifier: "wallet.presentationRejectButton", fallbackLabel: "Reject")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Presentation rejected", "Reject failed"], timeout: 10),
            "Presentation rejected"
        )
        XCTAssertFalse(app.buttons["wallet.presentationRejectButton"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationNewButton"].waitForExistence(timeout: 10))
    }

    func testOfferClaimsUseSemanticGroupsAndInclusionLabels() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_MDOC_METADATA": "1",
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

        XCTAssertFalse(app.staticTexts["18 or older"].exists)
        ui.tapButton(identifier: "wallet.offerSupportedClaims", fallbackLabel: "Supported claims (5)")
        XCTAssertTrue(app.staticTexts["Age attestations"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["18 or older"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["65 or older"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Always included"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["May be included"].waitForExistence(timeout: 10))
        ui.assertExists(identifier: "wallet.offerSupportedClaims")
        XCTAssertTrue(app.staticTexts["Travel document data"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Document security object (SOD)"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["DG1: Machine-readable zone"].waitForExistence(timeout: 10))
    }

    func testPresentTabAllowsPreviewAndDeclineWithoutCredentials() {
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

        XCTAssertTrue(app.buttons["wallet.presentButton"].isEnabled)
        XCTAssertTrue(app.staticTexts["No credentials available"].waitForExistence(timeout: 10))
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        XCTAssertTrue(app.buttons["wallet.presentationRejectButton"].isEnabled)
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
        XCTAssertTrue(app.otherElements["wallet.credentialDetailsScreen"].waitForExistence(timeout: 10))

        ui.openDeepLink(offerUrl)
        XCTAssertTrue(app.tabBars.buttons["Receive"].isSelected)
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)
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
        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)

        ui.openDeepLink(presentationUrl)
        XCTAssertTrue(app.tabBars.buttons["Present"].isSelected)
        XCTAssertFalse(app.staticTexts["Requested disclosures"].exists)
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

        XCTAssertFalse(app.staticTexts["Requested disclosures"].exists)
        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Portrait"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["$.portrait"].exists)
        XCTAssertTrue(app.images["Credential image"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)
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
        XCTAssertTrue(app.otherElements["wallet.credentialDetailsScreen"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Credentials"].isSelected)

        ui.tapTab(label: "Receive")
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)

        ui.tapTab(label: "Credentials")
        XCTAssertTrue(app.otherElements["wallet.credentialDetailsScreen"].waitForExistence(timeout: 10))
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
        XCTAssertTrue(app.otherElements["wallet.credentialDetailsScreen"].waitForExistence(timeout: 10))
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
        XCTAssertTrue(app.tabBars.buttons["Credentials"].isSelected)
        XCTAssertFalse(app.buttons["wallet.receiveNewButton"].exists)
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Expires 2026-06-17"].waitForExistence(timeout: 10))
        ui.tapButton(
            identifier: "wallet.claimGroupDisclosure.About_this_credential",
            fallbackLabel: "4 entries"
        )
        XCTAssertTrue(app.staticTexts["Example Issuer"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["jwt_vc_json"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.images["Credential image"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Receive")
        let resetOfferInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        XCTAssertTrue(resetOfferInput.waitForExistence(timeout: 10))
        XCTAssertTrue(resetOfferInput.isEnabled)
        XCTAssertTrue(["", "Credential offer URL"].contains(resetOfferInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.receiveButton"].isEnabled)

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))

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
        XCTAssertFalse(ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL").exists)
        XCTAssertTrue(app.staticTexts["Example Verifier"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.presentationRequesterDetailsToggle"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Required"].exists)
        XCTAssertFalse(app.staticTexts["ECDH-ES"].exists)
        XCTAssertTrue(app.staticTexts["Payment Authorization"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Amount"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["129.90"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Currency"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["EUR"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Payee"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example Merchant"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["wallet.verifierTechnicalDetailsToggle"].exists)
        ui.assertExists(identifierPrefix: "wallet.presentationCredential.")
        assertPresentationActionsFollowReviewContent(app: app)

        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)

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
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationRejectButton"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationNewButton"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Example Verifier"].exists)
        ui.tapButton(identifier: "wallet.presentationNewButton", fallbackLabel: "New presentation")
        let resetPresentationInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        XCTAssertTrue(resetPresentationInput.isEnabled)
        XCTAssertTrue(["", "OpenID4VP request URL"].contains(resetPresentationInput.value as? String))
        XCTAssertFalse(app.buttons["wallet.presentButton"].isEnabled)
    }

    func testPresentationShowsUnencryptedResponseState() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: [
            "E2E_MOCK_WALLET": "1",
            "E2E_MOCK_UNENCRYPTED_RESPONSE": "1",
        ])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        receiveMockCredential(app: app, ui: ui)
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

        XCTAssertFalse(app.staticTexts["Not requested"].exists)
        XCTAssertFalse(app.staticTexts["Key management algorithm"].exists)
        XCTAssertFalse(app.staticTexts["Verifier key thumbprint"].exists)
    }

    func testPresentationWithoutVerifierDisplayKeepsClientIDInTechnicalDetails() {
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

        XCTAssertTrue(app.descendants(matching: .any)["wallet.presentationVerifierSection"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts[Self.didClientID].exists)
        ui.tapButton(identifier: "wallet.presentationRequesterDetailsToggle", fallbackLabel: "Show Verifier details")
        XCTAssertTrue(app.staticTexts[Self.didClientID].waitForExistence(timeout: 10))
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

        XCTAssertTrue(app.tabBars.buttons["Credentials"].isSelected)
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))

        ui.tapTab(label: "Receive")
        XCTAssertTrue(ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL").waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Given name"].exists)

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
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

        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.")
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)

        ui.tapTab(label: "Credentials")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)

        ui.tapTab(label: "Present")
        XCTAssertTrue(app.staticTexts["Example Verifier"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)
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

        XCTAssertFalse(app.switches["wallet.presentationDisclosureToggle.8:identity6:cred-112:$.given_name"].exists)
        XCTAssertFalse(app.switches["wallet.presentationDisclosureToggle.3:age6:cred-113:$.age_over_18"].exists)

        ui.tapElement(identifierPrefix: "wallet.presentationClaimsToggle.3:age6:cred-1")
        XCTAssertTrue(app.staticTexts["Age disclosure"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Over 18"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Identity disclosure"].exists)
        XCTAssertFalse(app.otherElements["wallet.credentialDetailsScreen"].exists)
    }

    private func assertPresentationActionsFollowReviewContent(app: XCUIApplication) {
        let verifier = app.descendants(matching: .any)["wallet.presentationVerifierSection"].firstMatch
        let credential = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "wallet.presentationCredential."))
            .firstMatch
        let share = app.buttons["wallet.presentationSubmitButton"]

        XCTAssertTrue(verifier.waitForExistence(timeout: 10), "Verifier name is missing")
        XCTAssertTrue(credential.waitForExistence(timeout: 10), "Shared credential card is missing")
        XCTAssertTrue(share.waitForExistence(timeout: 10), "Share action is missing")
        XCTAssertFalse(app.descendants(matching: .any)["wallet.presentationResponseProtectionSection"].firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.presentationTechnicalDetailsSection"].firstMatch.exists)
        XCTAssertLessThan(
            verifier.frame.minY,
            credential.frame.minY,
            "Credential should follow verifier name"
        )
        XCTAssertLessThan(
            credential.frame.minY,
            share.frame.minY,
            "Share action should be below shared credential details so the credential is reviewed before consent"
        )
    }

    private func receiveMockCredential(app: XCUIApplication, ui: WalletE2EUI) {
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
    }
}
