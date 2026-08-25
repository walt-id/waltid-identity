import Foundation
import WalletDemoSharingUI
import WalletSDK
import XCTest
@testable import iosApp

final class WalletViewModelPresentationTests: XCTestCase {

    @MainActor
    func testInvalidRequestCanBeDismissedLocallyOrReportedToVerifier() async throws {
        let request = try XCTUnwrap(URL(string: "openid4vp://invalid-request"))
        let previewHandle = PresentationPreviewHandle(value: "invalid-presentation-preview")
        let previewError = PresentationPreviewError(
            previewHandle: previewHandle,
            request: PresentationRequestContext(
                clientID: "https://verifier.example",
                verifierMetadata: VerifierMetadata(
                    display: MetadataDisplay(
                        name: "Example Verifier",
                        locale: "en",
                        logoURI: nil,
                        logoAltText: nil
                    ),
                    clientURI: nil,
                    policyURI: nil,
                    termsOfServiceURI: nil
                ),
                requestAuthentication: .unauthenticated,
                responseEncryption: .notRequired
            ),
            code: .invalidTransactionData,
            message: "Unsupported transaction data type"
        )
        let walletClient = MockWalletClient(
            presentationPreviewResult: .invalid(previewError)
        )
        let viewModel = WalletViewModel(
            walletID: "invalid-presentation-\(UUID().uuidString)",
            walletClient: walletClient
        )

        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = request.absoluteString
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationError == previewError }

        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertFalse(viewModel.presentationUrlEntryEnabled)
        XCTAssertTrue(viewModel.presentationReviewEnabled)
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Review presentation error")

        viewModel.startNewPresentationFlow()
        try await waitUntilAsync {
            await walletClient.discardedPresentationPreviewHandles == [previewHandle]
        }

        XCTAssertNil(viewModel.presentationError)
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
        let rejectedAfterDismiss = await walletClient.rejectedPresentationPreviewHandles
        XCTAssertEqual(rejectedAfterDismiss, [])

        viewModel.presentationRequestUrl = request.absoluteString
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationError == previewError }
        viewModel.rejectPresentation()
        try await waitUntil { viewModel.presentationCompleted }

        XCTAssertNil(viewModel.presentationError)
        let rejectedAfterNotify = await walletClient.rejectedPresentationPreviewHandles
        XCTAssertEqual(rejectedAfterNotify, [previewHandle])
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Verifier notified")
    }

    @MainActor
    func testRejectPresentationSendsProtocolResponseAndScopesStatus() async throws {
        let continuationURL = try XCTUnwrap(URL(string: "wallet-demo://presentation-complete"))
        let viewModel = WalletViewModel(
            walletID: "reject-presentation-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                rejectionResult: .prepared(.openURL(continuationURL))
            )
        )

        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }

        viewModel.rejectPresentation()
        try await waitUntil { viewModel.pendingPresentationContinuationURL == continuationURL }

        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertEqual(viewModel.selectedPresentationCredentialOptions, [])
        XCTAssertEqual(viewModel.selectedPresentationDisclosureOptions, [])
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertTrue(viewModel.statusIsLoading(for: .present))
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Declining presentation...")
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "")
        XCTAssertEqual(viewModel.statusMessage(for: .receive), "")
        XCTAssertEqual(viewModel.pendingPresentationContinuationURL, continuationURL)

        viewModel.completePresentationContinuation()

        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        XCTAssertTrue(viewModel.presentationCompleted)
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Presentation rejected")
    }

    @MainActor
    func testFormPostRejectionRemainsPendingAndSurfacesDeliveryFailure() async throws {
        let html = "<form method=\"post\" action=\"https://verifier.example/response\"></form>"
        let viewModel = WalletViewModel(
            walletID: "reject-form-post-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                rejectionResult: .prepared(.submitForm(html: html))
            )
        )

        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }

        viewModel.rejectPresentation()
        try await waitUntil { viewModel.pendingPresentationFormPostHTML == html }

        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        viewModel.failPresentationContinuation("network unavailable")

        XCTAssertNil(viewModel.pendingPresentationFormPostHTML)
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertTrue(viewModel.statusIsError(for: .present))
        XCTAssertEqual(
            viewModel.statusMessage(for: .present),
            "Could not deliver the verifier response: network unavailable"
        )
    }

    @MainActor
    func testLockClearsPendingContinuationAndIgnoresLateCallbacks() async throws {
        let continuationURL = try XCTUnwrap(URL(string: "wallet-demo://presentation-complete"))
        let viewModel = WalletViewModel(
            walletID: "lock-continuation-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                rejectionResult: .prepared(.openURL(continuationURL))
            )
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }
        viewModel.rejectPresentation()
        try await waitUntil { viewModel.pendingPresentationContinuationURL == continuationURL }

        viewModel.lock()

        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        XCTAssertNil(viewModel.pendingPresentationFormPostHTML)
        XCTAssertFalse(viewModel.presentationCompleted)

        viewModel.completePresentationContinuation()
        viewModel.failPresentationContinuation("late callback")

        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")
    }

    @MainActor
    func testResetClearsPendingContinuationAndIgnoresLateCallbacks() async throws {
        let continuationURL = try XCTUnwrap(URL(string: "wallet-demo://presentation-complete"))
        let viewModel = WalletViewModel(
            walletID: "reset-continuation-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                rejectionResult: .prepared(.openURL(continuationURL))
            )
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }
        viewModel.rejectPresentation()
        try await waitUntil { viewModel.pendingPresentationContinuationURL == continuationURL }

        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }

        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        XCTAssertNil(viewModel.pendingPresentationFormPostHTML)
        XCTAssertFalse(viewModel.presentationCompleted)

        viewModel.completePresentationContinuation()
        viewModel.failPresentationContinuation("late callback")

        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.auth, .setup)
    }

    @MainActor
    func testDidAndKeyAreCapturedAndStatusCanBeDismissed() async throws {
        let viewModel = WalletViewModel(
            walletID: "settings-\(UUID().uuidString)",
            walletClient: MockWalletClient()
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        XCTAssertEqual(viewModel.did, "did:key:mock")
        XCTAssertEqual(viewModel.keyID, "mock-key-1")
        XCTAssertFalse(viewModel.publicJWK.isEmpty)
        XCTAssertFalse(viewModel.publicJWK.contains("\"d\""))
        XCTAssertTrue(viewModel.isStatusVisible(for: .credentials))
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Wallet ready")

        viewModel.dismissStatus()
        XCTAssertFalse(viewModel.isStatusVisible(for: .credentials))

        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady && viewModel.isStatusVisible(for: .credentials) }
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Wallet ready")
    }

    @MainActor
    func testResetWalletRebootstrapsAFreshSession() async throws {
        let viewModel = WalletViewModel(
            walletID: "reset-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                storedCredentials: [
                    Credential(
                        id: "cred-1",
                        format: "jwt_vc_json",
                        issuer: "Example Issuer",
                        subject: nil,
                        label: "Example Credential",
                        addedAt: nil,
                        credentialDataJSON: "{}"
                    )
                ]
            )
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        XCTAssertEqual(viewModel.credentials.map(\.id), ["cred-1"])

        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady && viewModel.credentials.isEmpty }
        XCTAssertEqual(viewModel.did, "did:key:mock")
        XCTAssertEqual(viewModel.keyID, "mock-key-1")
    }

    @MainActor
    func testDeleteCredentialRemovesItFromTheWallet() async throws {
        let viewModel = WalletViewModel(
            walletID: "delete-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                storedCredentials: [
                    Credential(
                        id: "cred-1",
                        format: "jwt_vc_json",
                        issuer: "Example Issuer",
                        subject: nil,
                        label: "Example Credential",
                        addedAt: nil,
                        credentialDataJSON: "{}"
                    )
                ]
            )
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.deleteCredential(id: "cred-1")
        try await waitUntil { viewModel.credentials.isEmpty }
    }

    @MainActor
    func testDeleteCredentialDiscardsAnActivePresentationReview() async throws {
        let walletClient = MockWalletClient(
            storedCredentials: [
                Credential(
                    id: "cred-1",
                    format: "jwt_vc_json",
                    issuer: "Example Issuer",
                    subject: nil,
                    label: "Example Credential",
                    addedAt: nil,
                    credentialDataJSON: "{}"
                )
            ]
        )
        let viewModel = WalletViewModel(
            walletID: "delete-review-\(UUID().uuidString)",
            walletClient: walletClient
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationReviewEnabled }

        viewModel.deleteCredential(id: "cred-1")
        try await waitUntil { viewModel.credentials.isEmpty && viewModel.presentationReview == nil }

        XCTAssertFalse(viewModel.presentationReviewEnabled)
        XCTAssertEqual(viewModel.selectedPresentationCredentialOptions, [])
        let discarded = await walletClient.discardedPresentationPreviewHandles
        XCTAssertEqual(discarded.count, 1)
    }

    @MainActor
    func testPresentationDetailsDeleteUsesStoreCredentialId() async throws {
        let walletClient = MockWalletClient(
            storedCredentials: [
                Credential(
                    id: "cred-1",
                    format: "jwt_vc_json",
                    issuer: "Example Issuer",
                    subject: nil,
                    label: "Example Credential",
                    addedAt: nil,
                    credentialDataJSON: "{}"
                )
            ]
        )
        let viewModel = WalletViewModel(
            walletID: "delete-details-\(UUID().uuidString)",
            walletClient: walletClient
        )
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationReviewEnabled }

        guard case .ready(let preview) = viewModel.presentationReview else {
            return XCTFail("Expected a ready presentation preview")
        }
        let option = try XCTUnwrap(preview.credentialOptions.first)
        let details = CredentialDisplayNormalizer.details(for: option)
        XCTAssertEqual(details.id, option.selection.id)
        XCTAssertEqual(details.credentialId, option.credentialID)
        XCTAssertNotEqual(details.id, details.credentialId)

        viewModel.deleteCredential(id: details.credentialId)
        try await waitUntil { viewModel.credentials.isEmpty && viewModel.presentationReview == nil }
        XCTAssertFalse(viewModel.presentationReviewEnabled)
    }

    @MainActor
    func testDeleteAndResetReconcileIdentityDocumentRegistrations() async throws {
        let counter = RegistrationUpdateCounter()
        let viewModel = WalletViewModel(
            walletID: "registration-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                storedCredentials: [
                    Credential(
                        id: "cred-1",
                        format: "jwt_vc_json",
                        issuer: "Example Issuer",
                        subject: nil,
                        label: "Example Credential",
                        addedAt: nil,
                        credentialDataJSON: "{}"
                    )
                ]
            ),
            identityDocumentRegistrationUpdate: { await counter.increment() }
        )
        try await waitUntil { viewModel.isReady }
        try await waitUntilAsync { await counter.count >= 1 }
        let afterBootstrap = await counter.count

        viewModel.deleteCredential(id: "cred-1")
        try await waitUntil { viewModel.credentials.isEmpty }
        try await waitUntilAsync { await counter.count >= afterBootstrap + 1 }
        let afterDelete = await counter.count
        XCTAssertEqual(afterDelete, afterBootstrap + 1)

        // After delete the wallet is already ready and empty, so waiting only on
        // those flags returns before reset's wipe reconcile and bootstrap run.
        // Reset publishes one update after deleteLocalData and another after bootstrap.
        viewModel.resetWallet()
        try await waitUntilAsync { await counter.count >= afterDelete + 2 }
        try await waitUntil { viewModel.isReady && viewModel.credentials.isEmpty }
        let afterReset = await counter.count
        XCTAssertGreaterThanOrEqual(afterReset, afterDelete + 2)
    }

    /// Waits for `predicate`, bounded by elapsed time rather than by a yield count.
    ///
    /// Yielding alone bounds nothing: opening a wallet does real keychain and file work off this
    /// actor, so a fixed number of yields is a machine-speed-dependent deadline that a loaded CI
    /// runner can miss while the wallet is still perfectly healthy. Sleeping between polls also stops
    /// the spin from starving the work it is waiting for.
    @MainActor
    private func waitUntil(
        timeoutNanoseconds: UInt64 = 20_000_000_000,
        _ predicate: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !predicate() {
            guard DispatchTime.now().uptimeNanoseconds < deadline else {
                XCTFail("Timed out waiting for wallet state")
                return
            }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    private func waitUntilAsync(
        timeoutNanoseconds: UInt64 = 20_000_000_000,
        _ predicate: @escaping () async -> Bool
    ) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !(await predicate()) {
            guard DispatchTime.now().uptimeNanoseconds < deadline else {
                XCTFail("Timed out waiting for wallet client state")
                return
            }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
    }
}

private actor RegistrationUpdateCounter {
    private(set) var count = 0

    func increment() {
        count += 1
    }
}
