import XCTest
import TestHelpers

/// End-to-end UI test for the wallet demo app against EUDI public backend.
///
/// Tests the full user flow: launch app → receive credential → present credential
/// Uses XCUIApplication for UI interaction and TestHelpers for backend operations.
///
/// This is an E2E test (slow, requires UI automation) - runs nightly or on-demand.
@MainActor
final class EudiPublicBackendE2ETests: XCTestCase {
    private let backend = EudiPublicBackend()

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60         // 1 min - wallet bootstrap
    private let credentialOperationTimeout: TimeInterval = 90 // 1.5 min - receive/present
    private let verifierPollingTimeout: TimeInterval = 30     // 30 sec - backend verification

    func testReceiveAndPresentAgainstEudiPublicBackends() async throws {
        let config = EudiPublicBackendConfig.fromEnvironment()
        let offerURL: String
        if let configuredOffer = config.offerURL, !configuredOffer.isEmpty {
            offerURL = configuredOffer
        } else {
            offerURL = try await backend.generatePreAuthorizedOffer(credentialID: config.credentialID)
        }

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)
        ui.launch()

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: offerURL)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        let verifier = try await backend.createVerifierTransaction(credentialID: config.credentialID)
        let presentInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        ui.replaceText(in: presentInput, value: verifier.authorizationRequestURI)
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Present")

        let presentStatus = ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(
            presentStatus!.starts(with: "Presentation finished"),
            "Presentation finished without verifier confirmation: \(presentStatus!)"
        )

        try await backend.waitForVerifierSuccess(transactionID: verifier.transactionID, timeoutSeconds: verifierPollingTimeout)
    }

}
