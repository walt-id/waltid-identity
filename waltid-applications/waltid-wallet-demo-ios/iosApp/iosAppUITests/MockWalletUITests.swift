import XCTest

@MainActor
final class MockWalletUITests: XCTestCase {
    private static let didClientID = "decentralized_identifier:did:jwk:abc"

    func testCredentialsHomeExposesOneScanEntry() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        XCTAssertTrue(app.buttons["wallet.scanAction"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        ui.tapTab(label: "Receive")
        XCTAssertTrue(app.textFields["wallet.scanInput"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.scanSubmit"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["wallet.offerScanButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationScanButton"].exists)
    }

    func testSettingsOwnsWalletIdentityAndConfirmsReset() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        XCTAssertFalse(app.staticTexts["did:key:mock"].exists)

        let settings = app.buttons["wallet.settingsAction"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()

        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsScreen"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["wallet.did"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["wallet.keyId"].waitForExistence(timeout: 10))

        let reset = app.buttons["wallet.resetAction"]
        XCTAssertTrue(reset.waitForExistence(timeout: 10))
        reset.tap()
        XCTAssertTrue(app.staticTexts["Reset wallet?"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Cancel"].waitForExistence(timeout: 10))
        app.buttons["wallet.resetConfirm"].firstMatch.tap()
        XCTAssertTrue(app.descendants(matching: .any)["wallet.settingsScreen"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["wallet.did"].waitForExistence(timeout: 10))
    }

    func testDeepLinksRouteToCredentialAndInformationReviewSheets() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

        let offerUrl = "openid-credential-offer://mock"
        ui.openDeepLink(offerUrl)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
        XCTAssertTrue(app.staticTexts["Example Issuer"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Example"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["jwt_vc_json"].exists, "Credential format belongs in Technical details")
        let addCredential = app.buttons["Add credential"]
        XCTAssertTrue(addCredential.waitForExistence(timeout: 10))
        XCTAssertTrue(addCredential.isEnabled)
        addCredential.tap()
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Received", "Receive failed"], timeout: 10),
            "Received 1 credential(s)"
        )
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")

        let presentationUrl = "openid4vp://mock"
        ui.openDeepLink(presentationUrl)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.presentationInput",
                fallbackLabel: "OpenID4VP request URL",
                value: presentationUrl,
                timeout: 10
            )
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
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
        let interactionSheet = app.descendants(matching: .any)["wallet.interactionSheet"]
        let transactionCodeSection = interactionSheet.descendants(matching: .any)["wallet.reviewIsland.required-action"]
        XCTAssertTrue(transactionCodeSection.waitForExistence(timeout: 10))
        XCTAssertFalse(interactionSheet.staticTexts["wallet.status"].exists)

        let accept = app.buttons["Add credential"]
        let decline = app.buttons["Decline"]
        XCTAssertTrue(accept.waitForExistence(timeout: 10))
        XCTAssertFalse(accept.isEnabled)
        XCTAssertTrue(decline.waitForExistence(timeout: 10))
        XCTAssertTrue(decline.isEnabled)
        XCTAssertEqual(accept.frame.midY, decline.frame.midY, accuracy: 1)
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

        XCTAssertTrue(app.staticTexts["Age attestations"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["18 or older"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["65 or older"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Always included"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["May be included"].waitForExistence(timeout: 10))
        ui.assertExists(identifier: "wallet.offerSupportedClaims")
        XCTAssertTrue(app.staticTexts["Travel document data"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Document security object (SOD)"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["DG1: Machine-readable zone"].waitForExistence(timeout: 10))

        ui.tapButton(identifier: "wallet.offerSupportedClaims", fallbackLabel: "Information")
        XCTAssertFalse(app.staticTexts["18 or older"].exists)
        ui.tapButton(identifier: "wallet.offerSupportedClaims", fallbackLabel: "Information")
        XCTAssertTrue(app.staticTexts["18 or older"].waitForExistence(timeout: 10))
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

        let continueButton = app.buttons["wallet.scanSubmit"]
        XCTAssertTrue(continueButton.isEnabled)
        continueButton.tap()
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
        )
        let share = app.buttons["wallet.presentationSubmitButton"]
        let cancel = app.buttons["wallet.presentationCancelButton"]
        let reject = app.buttons["wallet.presentationRejectButton"]
        XCTAssertTrue(share.waitForExistence(timeout: 10))
        XCTAssertTrue(cancel.waitForExistence(timeout: 10))
        XCTAssertTrue(reject.isEnabled)
        XCTAssertEqual(share.frame.midY, cancel.frame.midY, accuracy: 1)
        XCTAssertEqual(share.frame.midY, reject.frame.midY, accuracy: 1)
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
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
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

        ui.openDeepLink(offerUrl)
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.offerInput",
                fallbackLabel: "Credential offer URL",
                value: offerUrl,
                timeout: 10
            )
        )
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review credential offer", "Receive failed"], timeout: 10),
            "Review credential offer"
        )
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
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
        XCTAssertTrue(
            ui.waitForTextInputValue(
                identifier: "wallet.presentationInput",
                fallbackLabel: "OpenID4VP request URL",
                value: presentationUrl,
                timeout: 10
            )
        )
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Preview")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Review presentation request", "Preview failed"], timeout: 10),
            "Review presentation request"
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
        let credentialIsland = app.buttons["wallet.reviewIslandToggle.credential"]
        XCTAssertTrue(credentialIsland.waitForExistence(timeout: 10))
        XCTAssertTrue(credentialIsland.label.contains("Example Credential"))
        XCTAssertEqual(
            app.staticTexts.matching(NSPredicate(format: "label == %@", "Use this credential")).count,
            1
        )
        XCTAssertTrue(app.staticTexts["Portrait"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["$.portrait"].exists)
        XCTAssertTrue(app.images["Credential image"].waitForExistence(timeout: 10))

        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Requested disclosures"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Portrait"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["$.portrait"].exists)
        XCTAssertTrue(app.images["Credential image"].waitForExistence(timeout: 10))
    }

    func testCredentialDetailsReturnToCredentialsHome() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )
        XCTAssertTrue(app.buttons["wallet.scanAction"].waitForExistence(timeout: 10))
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

        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Given name"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["wallet.scanAction"].exists)

        ui.tapNavigationBack()
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
        XCTAssertTrue(app.buttons["wallet.scanAction"].waitForExistence(timeout: 10))
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
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
        XCTAssertFalse(app.textFields["wallet.scanInput"].exists)
        XCTAssertFalse(app.buttons["wallet.scanSubmit"].exists)
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
        XCTAssertFalse(app.textFields["wallet.scanInput"].exists)
        XCTAssertFalse(app.buttons["wallet.scanSubmit"].exists)
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

    func testUnifiedScanReceiveAndPresentFlowUsesMockWallet() {
        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch(environment: ["E2E_MOCK_WALLET": "1"])

        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: 10),
            "Wallet ready"
        )

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
        XCTAssertFalse(app.descendants(matching: .any)["wallet.interactionSheet"].exists)
        XCTAssertTrue(app.buttons["wallet.scanAction"].waitForExistence(timeout: 10))
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Mobile driving licence"].waitForExistence(timeout: 10))
        ui.tapElement(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.staticTexts["Credential details"].waitForExistence(timeout: 10))
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
        ui.tapNavigationBack()
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
        XCTAssertTrue(app.staticTexts["Example Verifier"].waitForExistence(timeout: 10))
        assertVerifierDetailsAreGroupedInTheActorIsland(app: app, ui: ui)
        XCTAssertTrue(app.staticTexts["Protected response"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["ECDH-ES"].exists)
        XCTAssertFalse(app.staticTexts["A256GCM"].exists)
        XCTAssertFalse(app.staticTexts["thumbprint-1"].exists)
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

        ui.tapButton(identifier: "wallet.presentationSubmitButton", fallbackLabel: "Share information")
        XCTAssertEqual(
            ui.waitForStatus(prefixes: ["Presentation sent", "Present failed"], timeout: 10),
            "Presentation sent"
        )
        XCTAssertFalse(app.buttons["wallet.presentationSubmitButton"].exists)
        XCTAssertFalse(app.buttons["wallet.presentationRejectButton"].exists)
        XCTAssertTrue(app.buttons["wallet.presentationNewButton"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Example Verifier"].exists)
        ui.tapTab(label: "Credentials")
        XCTAssertFalse(app.descendants(matching: .any)["wallet.interactionSheet"].exists)
        XCTAssertTrue(app.buttons["wallet.scanAction"].waitForExistence(timeout: 10))
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
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

        XCTAssertTrue(app.staticTexts["No message-level encryption requested"].waitForExistence(timeout: 10))
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

        XCTAssertTrue(app.descendants(matching: .any)["wallet.reviewIsland.verifier"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts[Self.didClientID].exists)
        ui.tapButton(identifier: "wallet.reviewIslandToggle.verifier", fallbackLabel: "Verifier")
        ui.tapButton(identifier: "wallet.reviewIslandTechnicalDetails.verifier", fallbackLabel: "Technical details")
        XCTAssertTrue(app.staticTexts[Self.didClientID].waitForExistence(timeout: 10))
    }

    func testCompletedIssuanceReturnsToCredentialsHome() {
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

        XCTAssertFalse(app.descendants(matching: .any)["wallet.interactionSheet"].exists)
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertTrue(app.buttons["wallet.scanAction"].waitForExistence(timeout: 10))
    }

    func testPresentationDetailsAreDiscardedWhenReviewCloses() {
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

        ui.tapNavigationBack()
        ui.tapButton(identifier: "Close", fallbackLabel: "Close")
        ui.assertExists(identifierPrefix: "wallet.credentialCard.")
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["wallet.interactionSheet"].exists)

        ui.tapTab(label: "Present")
        XCTAssertTrue(app.textFields["wallet.scanInput"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Credential details"].exists)
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

        let identityToggleID = "wallet.presentationDisclosureToggle.8:identity6:cred-112:$.given_name"
        let ageToggleID = "wallet.presentationDisclosureToggle.3:age6:cred-113:$.age_over_18"
        ui.assertExists(identifier: identityToggleID, timeout: 10)
        let identityToggle = app.switches[identityToggleID]
        XCTAssertEqual(identityToggle.value as? String, "0")
        ui.tapElement(identifier: identityToggleID)
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
        let verifier = app.descendants(matching: .any)["wallet.reviewIsland.verifier"].firstMatch
        let credential = app.descendants(matching: .any)["wallet.reviewIsland.credential"].firstMatch
        let information = app.descendants(matching: .any)["wallet.reviewIsland.information"].firstMatch
        let share = app.buttons["wallet.presentationSubmitButton"]

        XCTAssertTrue(verifier.waitForExistence(timeout: 10), "Verifier island is missing")
        XCTAssertTrue(credential.waitForExistence(timeout: 10), "Credential island is missing")
        XCTAssertTrue(information.waitForExistence(timeout: 10), "Information island is missing")
        XCTAssertTrue(share.waitForExistence(timeout: 10), "Share action is missing")
        XCTAssertTrue(share.isHittable, "Sticky Share action should remain reachable while review content scrolls")
        XCTAssertLessThan(
            verifier.frame.minY,
            credential.frame.minY,
            "Credential selection should follow the Verifier"
        )
        XCTAssertLessThan(
            credential.frame.minY,
            information.frame.minY,
            "Requested information should follow credential selection"
        )
    }

    private func assertVerifierTechnicalDetailsCollapsedUntilRequested(app: XCUIApplication, ui: WalletE2EUI) {
        XCTAssertFalse(app.staticTexts["Client ID"].exists, "Technical verifier fields should not be expanded by default")
        XCTAssertTrue(app.buttons["wallet.reviewIslandTechnicalDetails.verifier"].waitForExistence(timeout: 10))
        ui.tapButton(identifier: "wallet.reviewIslandTechnicalDetails.verifier", fallbackLabel: "Technical details")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.reviewTechnicalDetailsPage"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Client ID"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["https://verifier.example/response"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["state-123"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["nonce-456"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["ECDH-ES"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["A256GCM"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["thumbprint-1"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["wallet.presentationSubmitButton"].isHittable)
        ui.tapButton(identifier: "wallet.reviewTechnicalDetailsBack", fallbackLabel: "Back to review")
        XCTAssertTrue(app.descendants(matching: .any)["wallet.reviewIsland.verifier"].waitForExistence(timeout: 10))
    }

    private func assertVerifierDetailsAreGroupedInTheActorIsland(app: XCUIApplication, ui: WalletE2EUI) {
        XCTAssertTrue(app.descendants(matching: .any)["wallet.reviewIsland.verifier"].waitForExistence(timeout: 10))
        if !app.descendants(matching: .any)["https://verifier.example"].exists {
            ui.tapButton(identifier: "wallet.reviewIslandToggle.verifier", fallbackLabel: "Example Verifier")
        }
        XCTAssertTrue(app.descendants(matching: .any)["https://verifier.example"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["https://verifier.example/privacy"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["https://verifier.example/terms"].waitForExistence(timeout: 10))
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
