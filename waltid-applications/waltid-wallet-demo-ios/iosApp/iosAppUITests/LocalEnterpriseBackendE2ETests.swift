import XCTest
import TestHelpers

/// End-to-end UI test for the wallet demo app against local enterprise backend.
///
/// Tests the full user flow: launch app → receive credential → present credential
/// Uses XCUIApplication for UI interaction and TestHelpers for backend operations.
///
/// This is an E2E test (slow, requires UI automation + local infrastructure) - runs locally only.
/// Requires: enterprise stack running + ngrok tunnel
@MainActor
final class LocalEnterpriseBackendE2ETests: XCTestCase {
    private let backend = LocalEnterpriseBackend()

    // Timeouts (aligned with Android for cross-platform consistency)
    private let walletReadyTimeout: TimeInterval = 60         // 1 min - wallet bootstrap
    private let credentialOperationTimeout: TimeInterval = 90 // 1.5 min - receive/present
    private let verifierPollingTimeout: TimeInterval = 30     // 30 sec - backend verification

    func testReceiveAndPresentAgainstLocalEnterpriseBackend() async throws {
        try LocalEnterpriseBackendConfig.requireExplicitLocalEnterpriseRun()
        let config = try LocalEnterpriseBackendConfig.fromEnvironment()
        let attested = LocalEnterpriseBackendConfig.isAttested()

        let preparedBackend = try await prepareBackend(config: config)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)

        if attested {
            let attestationBaseURL = LocalEnterpriseBackendConfig.attestationBaseURL()
            ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": preparedBackend.token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            ui.launch()
        }

        let readyStatus = ui.waitForStatus(
            prefixes: ["Wallet ready", "Bootstrap failed"],
            timeout: walletReadyTimeout
        )
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet did not become ready, status: \(readyStatus ?? "nil")")

        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: preparedBackend.offerURL)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed, status: \(receiveStatus ?? "nil")")

        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        let presentInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        ui.replaceText(in: presentInput, value: preparedBackend.verifier.bootstrapAuthorizationRequestURL)
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Present")

        let presentStatus = ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed: \(presentStatus!)")
        XCTAssertFalse(presentStatus!.starts(with: "Receive failed"), "Receive failed after present: \(presentStatus!)")

        let verifierStatus = try await backend.waitForVerifierStatus(config: config, sessionID: preparedBackend.verifier.sessionID, timeoutSeconds: verifierPollingTimeout)
        XCTAssertEqual(verifierStatus, "SUCCESSFUL", "Verifier status was \(verifierStatus)")
    }

    func testCredentialsPersistAcrossAppRestart() async throws {
        try LocalEnterpriseBackendConfig.requireExplicitLocalEnterpriseRun()
        let config = try LocalEnterpriseBackendConfig.fromEnvironment()
        let attested = LocalEnterpriseBackendConfig.isAttested()

        let preparedBackend = try await prepareBackend(config: config)

        let app = XCUIApplication()
        let ui = WalletE2EUI(app: app)

        if attested {
            let attestationBaseURL = LocalEnterpriseBackendConfig.attestationBaseURL()
            ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": preparedBackend.token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            ui.launch()
        }

        // Phase 1: Bootstrap + receive
        let readyStatus = ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: walletReadyTimeout)
        XCTAssertEqual(readyStatus, "Wallet ready", "Wallet not ready: \(readyStatus ?? "nil")")

        let offerInput = ui.textInput(identifier: "wallet.offerInput", fallbackLabel: "Credential offer URL")
        ui.replaceText(in: offerInput, value: preparedBackend.offerURL)
        ui.tapButton(identifier: "wallet.receiveButton", fallbackLabel: "Receive")

        let receiveStatus = ui.waitForStatus(
            prefixes: ["Received", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertTrue(receiveStatus?.starts(with: "Received") == true, "Receive failed: \(receiveStatus ?? "nil")")
        XCTAssertFalse(app.staticTexts["No credentials"].exists)

        // Phase 2: Terminate the app
        app.terminate()
        try await Task.sleep(nanoseconds: 2_000_000_000)

        // Phase 3: Relaunch and verify credentials survived
        if attested {
            let attestationBaseURL = LocalEnterpriseBackendConfig.attestationBaseURL()
            ui.launch(attestation: [
                "ATTESTATION_BASE_URL": attestationBaseURL,
                "ATTESTATION_ATTESTER_PATH": "\(config.tenantPath).client-attester",
                "ATTESTATION_BEARER_TOKEN": preparedBackend.token,
                "ATTESTATION_HOST_HEADER": "\(config.organization).enterprise.localhost",
            ])
        } else {
            ui.launch()
        }

        let readyAfterRestart = ui.waitForStatus(prefixes: ["Wallet ready", "Bootstrap failed"], timeout: walletReadyTimeout)
        XCTAssertEqual(readyAfterRestart, "Wallet ready", "Wallet not ready after restart: \(readyAfterRestart ?? "nil")")
        XCTAssertFalse(app.staticTexts["No credentials"].exists, "Credentials not persisted — 'No credentials' shown after restart")

        // Phase 4: Present from persisted credential
        let presentInput = ui.textInput(identifier: "wallet.presentationInput", fallbackLabel: "OpenID4VP request URL")
        ui.replaceText(in: presentInput, value: preparedBackend.verifier.bootstrapAuthorizationRequestURL)
        ui.tapButton(identifier: "wallet.presentButton", fallbackLabel: "Present")

        let presentStatus = ui.waitForStatus(
            prefixes: ["Presentation sent", "Presentation finished", "Present failed", "Receive failed", "Bootstrap failed"],
            timeout: credentialOperationTimeout
        )
        XCTAssertNotNil(presentStatus)
        XCTAssertFalse(presentStatus!.starts(with: "Present failed"), "Present failed after restart: \(presentStatus!)")

        let verifierStatus = try await backend.waitForVerifierStatus(config: config, sessionID: preparedBackend.verifier.sessionID, timeoutSeconds: verifierPollingTimeout)
        XCTAssertEqual(verifierStatus, "SUCCESSFUL", "Verifier status after restart: \(verifierStatus)")
    }

    private struct PreparedBackend {
        let token: String
        let offerURL: String
        let verifier: LocalEnterpriseVerifierSession
    }

    private func prepareBackend(config: LocalEnterpriseBackendConfig) async throws -> PreparedBackend {
        do {
            let token = try await backend.getAdminToken(config: config)
            let offerURL = try await backend.createPreAuthorizedOffer(config: config, token: token)
            let verifier = try await backend.createVerifierSession(config: config, token: token)
            return PreparedBackend(token: token, offerURL: offerURL, verifier: verifier)
        } catch {
            throw WalletE2ESetupError(message: """
            Local Enterprise E2E environment is not ready.
            Expected quickstart baseline: org=\(config.organization), tenant=\(config.tenant), issuerProfile=\(config.issuerProfile), verifier=\(config.verifier).
            From waltid-enterprise-quickstart run docker compose up, then cd cli && npm install.
            Provision with HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system,
            then HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all.
            For an existing database, rerun the --setup-all command.
            Create mobile helper resources explicitly with scripts/e2e-local-enterprise.sh --prepare-only.
            Then rerun this test through scripts/e2e-local-enterprise.sh so ngrok, issuer2-noattest, and verifier2-mobile are checked first.
            Original error: \(error.localizedDescription)
            """)
        }
    }
}
