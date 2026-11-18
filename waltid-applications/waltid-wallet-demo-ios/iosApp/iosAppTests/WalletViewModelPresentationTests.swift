import Combine
import Foundation
import WalletDemoSharingUI
@testable import WalletSDK
import XCTest
@testable import iosApp

final class WalletViewModelPresentationTests: XCTestCase {
    @MainActor
    func testClaimReviewBlocksSubmissionOnFailureAndRetryUsesCurrentRevision() async throws {
        let preparation = PendingClaimPreparation()
        let client = claimClient(preparation)
        let viewModel = try await paymentViewModel(client)
        try await waitUntilAsync { await preparation.count == 1 }
        XCTAssertFalse(viewModel.claimReview.canConfirm)
        viewModel.submitPresentation()
        let firstSubmissions = await client.submittedClaimSelections
        XCTAssertTrue(firstSubmissions.isEmpty)
        await preparation.fail(0)
        try await waitUntil { if case .failed = viewModel.claimReview { true } else { false } }
        viewModel.submitPresentation()
        let failedSubmissions = await client.submittedClaimSelections
        XCTAssertTrue(failedSubmissions.isEmpty)
        viewModel.retryPresentationClaimReview()
        try await waitUntilAsync { await preparation.count == 2 }
        await preparation.complete(1)
        try await waitUntil { if case .ready = viewModel.claimReview { true } else { false } }
        viewModel.submitPresentation()
        try await waitUntil { viewModel.presentationReview == nil && !viewModel.isLoading }
        let submissions = await client.submittedClaimSelections
        XCTAssertEqual(submissions.count, 1)
    }

    @MainActor
    func testSelectionChangeDiscardsLateClaimReviewAndSubmitsTheCurrentSelection() async throws {
        let preparation = PendingClaimPreparation()
        let client = claimClient(preparation)
        let viewModel = try await paymentViewModel(client)
        try await waitUntilAsync { await preparation.count == 1 }
        let credential = try XCTUnwrap(viewModel.selectedPresentationCredentialOptions.first)
        viewModel.togglePresentationDisclosure(.init(queryID: credential.queryID, credentialID: credential.credentialID, path: "$.given_name"))
        try await waitUntilAsync { await preparation.count == 2 }
        await preparation.complete(1)
        try await waitUntil { if case .ready = viewModel.claimReview { true } else { false } }
        let currentReview = viewModel.claimReview
        let stale = expectation(description: "Old projection must not replace current review")
        stale.isInverted = true
        let observation = viewModel.$claimReview.sink { if $0 != currentReview { stale.fulfill() } }
        await preparation.complete(0)
        await fulfillment(of: [stale], timeout: 0.2)
        observation.cancel()
        XCTAssertEqual(viewModel.claimReview, currentReview)
        let currentSelection = Array(viewModel.selectedPresentationDisclosureOptions)
        viewModel.submitPresentation()
        try await waitUntil { viewModel.presentationReview == nil && !viewModel.isLoading }
        let submissions = await client.submittedClaimSelections
        XCTAssertEqual(submissions, [currentSelection])
    }

    @MainActor
    func testLockCancelsClaimPreparationWithoutRestoringAnOldReview() async throws {
        let preparation = PendingClaimPreparation()
        let client = claimClient(preparation)
        let viewModel = try await paymentViewModel(client)
        try await waitUntilAsync { await preparation.count == 1 }
        viewModel.lock()
        await preparation.complete(0)
        try await waitUntil { viewModel.presentationReview == nil }
        XCTAssertEqual(viewModel.claimReview, .legacy)
        let submissions = await client.submittedClaimSelections
        XCTAssertTrue(submissions.isEmpty)
    }



    @MainActor
    func testPaymentPreparationBlocksSubmissionWhileLoadingAndAfterFailure() async throws {
        let preparation = PendingPaymentPreparation()
        let client = MockWalletClient(paymentConsent: { try await preparation.prepare() })
        let viewModel = try await paymentViewModel(client)
        try await waitUntilAsync { await preparation.count == 1 }
        XCTAssertEqual(viewModel.paymentReview, .loading)
        viewModel.submitPresentation()
        var submitted = await client.submittedPaymentConsentRevisions
        XCTAssertTrue(submitted.isEmpty)

        await preparation.complete(0, with: .failure(WalletError.internalFailure("Missing issuer labels")))
        try await waitUntil { if case .blocked = viewModel.paymentReview { true } else { false } }
        viewModel.submitPresentation()
        submitted = await client.submittedPaymentConsentRevisions
        XCTAssertTrue(submitted.isEmpty)
    }

    @MainActor
    func testPaymentSelectionChangeDiscardsLateConsentAndSubmitsCurrentRevision() async throws {
        let preparation = PendingPaymentPreparation()
        let client = MockWalletClient(paymentConsent: { try await preparation.prepare() })
        let viewModel = try await paymentViewModel(client)
        try await waitUntilAsync { await preparation.count == 1 }
        let credential = try XCTUnwrap(viewModel.selectedPresentationCredentialOptions.first)
        viewModel.togglePresentationDisclosure(PresentationDisclosureSelection(
            queryID: credential.queryID, credentialID: credential.credentialID, path: "$.given_name"
        ))
        XCTAssertEqual(viewModel.paymentReview, .loading)
        try await waitUntilAsync { await preparation.count == 2 }
        await preparation.complete(1, with: .success(paymentConsent("current")))
        try await waitUntil { viewModel.paymentReview.consent?.revision == "current" }

        let stale = expectation(description: "Stale consent must not replace the current review")
        stale.isInverted = true
        let observation = viewModel.$paymentReview.sink { state in
            if state.consent?.revision == "stale" { stale.fulfill() }
        }
        await preparation.complete(0, with: .success(paymentConsent("stale")))
        await fulfillment(of: [stale], timeout: 0.2)
        withExtendedLifetime(observation) {}
        XCTAssertEqual(viewModel.paymentReview.consent?.revision, "current")
        viewModel.submitPresentation()
        try await waitUntil { viewModel.presentationReview == nil && !viewModel.isLoading }
        let submitted = await client.submittedPaymentConsentRevisions
        XCTAssertEqual(submitted, ["current"])
    }

    @MainActor
    func testLockPreventsLatePaymentPreparationFromRestoringConsent() async throws {
        let preparation = PendingPaymentPreparation()
        let client = MockWalletClient(paymentConsent: { try await preparation.prepare() })
        let viewModel = try await paymentViewModel(client)
        try await waitUntilAsync { await preparation.count == 1 }
        viewModel.lock()
        XCTAssertEqual(viewModel.paymentReview, .notRequired)
        XCTAssertNil(viewModel.presentationPreview)
        let restored = expectation(description: "Locked wallet must not restore payment consent")
        restored.isInverted = true
        let observation = viewModel.$paymentReview.sink { state in
            if state.consent != nil { restored.fulfill() }
        }
        await preparation.complete(0, with: .success(paymentConsent("late")))
        await fulfillment(of: [restored], timeout: 0.2)
        withExtendedLifetime(observation) {}
        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertEqual(viewModel.paymentReview, .notRequired)
        let submitted = await client.submittedPaymentConsentRevisions
        XCTAssertTrue(submitted.isEmpty)
    }

    private func claimClient(_ preparation: PendingClaimPreparation) -> MockWalletClient {
        let option = PresentationCredentialOption(queryID: "pid", credentialID: "claim-credential", format: "dc+sd-jwt",
            issuer: nil, subject: nil, label: "Employee", credentialDataJSON: #"{"given_name":"Ada"}"#,
            disclosures: [.init(path: "$.given_name", name: "Given name", valueJSON: #""Ada""#,
                displayValue: nil, selectivelyDisclosable: true)])
        let preview = PresentationPreview(previewHandle: .init(value: "claim-preview"),
            request: .init(clientID: "https://verifier.example", requestAuthentication: .unauthenticated,
                nonce: "fixture", responseEncryption: .notRequired), credentialOptions: [option])
        return MockWalletClient(presentationPreviewResult: .ready(preview), claimPreparation: { credentials, disclosures in
            try await preparation.prepare(credentials, disclosures)
        })
    }

    @MainActor
    private func paymentViewModel(_ client: MockWalletClient) async throws -> WalletViewModel {
        let viewModel = WalletViewModel(walletID: "payment-review-\(UUID().uuidString)", walletClient: client)
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }
        return viewModel
    }

    private func paymentConsent(_ revision: String) -> PaymentConsent {
        PaymentConsent(revision: revision, locale: "en", title: "Payment", securityHint: nil,
                       affirmativeAction: "Pay", denialAction: nil, requiresUnsignedRequestWarning: false, fields: [])
    }

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

        viewModel.cancelPresentationReview()
        try await waitUntilAsync {
            await walletClient.discardedPresentationPreviewHandles == [previewHandle]
        }

        XCTAssertNil(viewModel.presentationError)
        XCTAssertEqual(viewModel.presentationRequestUrl, "")
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
        let rejectedAfterDismiss = await walletClient.rejectedPresentationPreviewHandles
        XCTAssertEqual(rejectedAfterDismiss, [])

        viewModel.presentationRequestUrl = request.absoluteString
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationError == previewError }
        viewModel.rejectPresentation()
        try await waitUntil { viewModel.presentationError == nil && viewModel.presentationRequestUrl.isEmpty && !viewModel.isLoading }

        XCTAssertNil(viewModel.presentationError)
        let rejectedAfterNotify = await walletClient.rejectedPresentationPreviewHandles
        XCTAssertEqual(rejectedAfterNotify, [previewHandle])
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Verifier notified")
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
        XCTAssertTrue(viewModel.presentationCompleted)
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
        XCTAssertEqual(viewModel.presentationRequestUrl, "")
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
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
        XCTAssertTrue(viewModel.presentationCompleted)
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
    func testResetClearsPendingContinuationBeforeDeleteLocalDataCompletes() async throws {
        let continuationURL = try XCTUnwrap(URL(string: "wallet-demo://presentation-complete"))
        let viewModel = WalletViewModel(
            walletID: "reset-continuation-delay-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                rejectionResult: .prepared(.openURL(continuationURL)),
                deleteLocalDataDelayMilliseconds: 500
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
        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        XCTAssertNil(viewModel.pendingPresentationFormPostHTML)

        viewModel.completePresentationContinuation()
        viewModel.failPresentationContinuation("late callback")

        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.auth, .unlocked)

        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isError)
    }

    @MainActor
    func testResetClearsPendingContinuationWhenDeleteLocalDataFails() async throws {
        enum DeleteFailure: Error { case boom }
        let continuationURL = try XCTUnwrap(URL(string: "wallet-demo://presentation-complete"))
        let viewModel = WalletViewModel(
            walletID: "reset-continuation-fail-\(UUID().uuidString)",
            walletClient: MockWalletClient(
                rejectionResult: .prepared(.openURL(continuationURL)),
                deleteLocalDataError: DeleteFailure.boom
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
        viewModel.completePresentationContinuation()
        viewModel.failPresentationContinuation("late callback")

        try await waitUntil { viewModel.isError }

        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        XCTAssertNil(viewModel.pendingPresentationFormPostHTML)
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertEqual(viewModel.auth, .unlocked)
        XCTAssertFalse(viewModel.isReady)
        XCTAssertTrue(viewModel.credentials.isEmpty)
        XCTAssertTrue(viewModel.keyID.isEmpty)
    }

    @MainActor
    func testDidAndKeyAreCapturedWithoutIdleBannerAndOperationStatusCanBeDismissed() async throws {
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
        for tab in [WalletTab.credentials, .receive, .present] {
            XCTAssertFalse(viewModel.isStatusVisible(for: tab))
        }
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Wallet ready")

        viewModel.offerUrl = "openid-credential-offer://mock"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview != nil }
        viewModel.declineOffer()
        XCTAssertTrue(viewModel.isStatusVisible(for: .credentials))
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Credential offer declined")
        viewModel.dismissStatus()
        XCTAssertFalse(viewModel.isStatusVisible(for: .credentials))

        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        XCTAssertFalse(viewModel.isStatusVisible(for: .credentials))
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
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        try await waitUntilAsync { await counter.count >= 1 }
        let afterBootstrap = await counter.count

        viewModel.deleteCredential(id: "cred-1")
        try await waitUntil { viewModel.credentials.isEmpty }
        try await waitUntilAsync { await counter.count >= afterBootstrap + 1 }
        let afterDelete = await counter.count
        XCTAssertEqual(afterDelete, afterBootstrap + 1)

        // Reset returns to PIN setup and reconciles after the wipe. Bootstrap, and
        // its second registration update, wait until the wallet is unlocked again.
        viewModel.resetWallet()
        try await waitUntil { viewModel.auth == .setup && !viewModel.isReady }
        try await waitUntilAsync { await counter.count >= afterDelete + 1 }
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady && viewModel.credentials.isEmpty }
        try await waitUntilAsync { await counter.count >= afterDelete + 2 }
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

/// Deliberately completes even after cancellation to exercise the view model's stale-result guard.
private actor PendingPaymentPreparation {
    private var continuations: [CheckedContinuation<PaymentConsent?, Error>] = []
    var count: Int { continuations.count }

    func prepare() async throws -> PaymentConsent? {
        try await withCheckedThrowingContinuation { continuations.append($0) }
    }

    func complete(_ index: Int, with result: Result<PaymentConsent?, Error>) {
        continuations[index].resume(with: result)
    }
}

private actor PendingClaimPreparation {
    private var pending: [([PresentationCredentialSelection], [PresentationDisclosureSelection], CheckedContinuation<PreparedPresentationReview, Error>)] = []
    var count: Int { pending.count }
    func prepare(_ credentials: [PresentationCredentialSelection], _ disclosures: [PresentationDisclosureSelection]) async throws -> PreparedPresentationReview {
        try await withCheckedThrowingContinuation { pending.append((credentials, disclosures, $0)) }
    }
    func fail(_ index: Int) { pending[index].2.resume(throwing: WalletError.internalFailure("Projection unavailable")) }
    func complete(_ index: Int) {
        let selected = !pending[index].1.isEmpty
        let claims = pending[index].0.map { credential in
            PresentationCredentialClaims(queryID: credential.queryID, credentialID: credential.credentialID, format: "dc+sd-jwt",
                claimsJSON: selected ? #"{"given_name":"Ada"}"# : "{}", wholeCredential: false, locations: [
                    .init(sourcePathJSON: "[]", receivedPathJSON: "[]", sharedBy: [], alwaysCarried: true),
                    .init(sourcePathJSON: #"["given_name"]"#, receivedPathJSON: #"["given_name"]"#,
                          sharedBy: ["$.given_name"], alwaysCarried: false)].prefix(selected ? 2 : 1).map { $0 })
        }
        pending[index].2.resume(returning: .init(credentials: claims))
    }
}
