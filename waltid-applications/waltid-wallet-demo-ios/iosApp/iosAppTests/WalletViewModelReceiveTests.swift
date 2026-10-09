import Foundation
import WalletSDK
import WalletDemoSharingUI
import XCTest
@testable import iosApp

@MainActor
final class WalletViewModelReceiveTests: XCTestCase {
    func testResolvedHTTPSOfferPreparesOnceAndKeepsItsSelectionUntilClosed() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady && !model.isLoading }
        let url = URL(string: "https://issuer.example/offer?credential_offer_uri=https%3A%2F%2Fissuer.example%2F1")!
        model.openResolvedLink(url, kind: .offer)
        model.prepareExternalFlow()
        try await waitUntil { model.offerPreview != nil && !model.isLoading }
        let preview = try XCTUnwrap(model.offerPreview)
        let configuration = try XCTUnwrap(preview.credentials.first).configurationID
        model.updateIssuanceCopies(configuration, 0)
        model.txCode = "1234"
        model.openResolvedLink(url, kind: .offer)
        model.prepareExternalFlow()
        let calls = await client.issuanceStartCalls
        XCTAssertEqual(calls, 1)
        XCTAssertEqual(model.issuanceCopyCounts[configuration], 0)
        XCTAssertEqual(model.txCode, "1234")
        XCTAssertTrue(model.closeExternalFlow())
        XCTAssertEqual(model.selectedTab, .credentials)
        XCTAssertNil(model.externalFlow)
    }

    func testExternalLinkCannotReplaceNearbySession() {
        let model = WalletViewModel(walletClient: TransactionCodeWalletClient(transactionCode: nil), identityDocumentRegistrationUpdate: {})
        model.proximityPresentation.start()
        defer { model.proximityPresentation.dismiss() }
        XCTAssertTrue(model.proximityPresentation.active)
        model.handleDeepLink(URL(string: "openid4vp://external")!)
        XCTAssertNil(model.externalFlow)
        XCTAssertNotNil(model.incomingLinkNotice)
    }

    func testCallbackWithoutSessionExplainsRecoveryWithoutReplayingAnOffer() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        let url = URL(string: "openid://callback?code=orphan&state=lost")!
        model.handleDeepLink(url)
        XCTAssertEqual(model.externalFlow, .unavailableCallback(url))
        model.unlockForTests()
        try await waitUntil { model.isReady && !model.isLoading }
        model.prepareExternalFlow()
        let started = await client.issuanceStartCalls
        let continued = await client.issuanceContinuationCalls
        XCTAssertEqual(started, 0)
        XCTAssertEqual(continued, 0)
        XCTAssertTrue(model.closeExternalFlow())
    }

    func testExternalOfferWaitsForUnlockPreparesOnceAndKeepsReceiptUntilClosed() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        let url = URL(string: "openid-credential-offer://external")!
        model.handleDeepLink(url)
        model.prepareExternalFlow()
        let before = await client.issuanceStartCalls
        XCTAssertEqual(before, 0)
        model.unlockForTests()
        try await waitUntil { model.isReady && !model.isLoading }
        model.prepareExternalFlow()
        try await waitUntil { model.offerPreview != nil && !model.isLoading }
        model.prepareExternalFlow()
        model.handleDeepLink(url)
        model.prepareExternalFlow()
        let prepared = await client.issuanceStartCalls
        let accepted = await client.issuanceContinuationCalls
        XCTAssertEqual(prepared, 1)
        XCTAssertEqual(accepted, 0)
        model.acceptOffer()
        try await waitUntil { !model.isLoading }
        XCTAssertEqual(model.selectedTab, .receive)
        XCTAssertNotNil(model.issuanceReceipt)
        XCTAssertEqual(model.lastReceivedCredentialIDs, ["credential-1"])
        XCTAssertTrue(model.closeExternalFlow())
        XCTAssertFalse(model.closeExternalFlow())
        XCTAssertEqual(model.selectedTab, .credentials)
    }

    func testExternalLinkCannotInterruptSending() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil, startsWithCredential: true,
            presentationActionDelayNanoseconds: 300_000_000)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady && !model.isLoading }
        let url = URL(string: "openid4vp://external")!
        model.handleDeepLink(url)
        model.prepareExternalFlow()
        try await waitUntil { model.presentationPreview != nil && !model.isLoading }
        model.submitPresentation()
        XCTAssertFalse(model.closeExternalFlow())
        model.handleDeepLink(URL(string: "openid-credential-offer://replacement")!)
        XCTAssertEqual(model.externalFlow?.url, url)
        XCTAssertEqual(model.selectedTab, .present)
        XCTAssertNotNil(model.incomingLinkNotice)
        try await waitUntil { !model.isLoading }
        let calls = await client.presentationSubmitCalls
        XCTAssertEqual(calls, 1)
        XCTAssertTrue(model.closeExternalFlow())
    }

    func testClosingExternalPreviewDiscardsLateResolution() async throws {
        let client = TransactionCodeWalletClient(issuanceStartDelayNanoseconds: 200_000_000, transactionCode: nil)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady && !model.isLoading }
        model.handleDeepLink(URL(string: "openid-credential-offer://external")!)
        model.prepareExternalFlow()
        try await waitUntilAsync { await client.issuanceStartCalls == 1 }
        XCTAssertTrue(model.closeExternalFlow())
        try await waitUntilAsync { await client.cancelledIssuanceSessionIDs.count == 1 }
        XCTAssertNil(model.offerPreview)
        XCTAssertNil(model.externalFlow)
        let accepted = await client.issuanceContinuationCalls
        XCTAssertEqual(accepted, 0)
    }

    func testUncertainContinuationCannotResumeAndStatusRefreshOnlyReads() async throws {
        for status in [IssuanceContinuationStatus.remoteOutcomeUncertain, .storageOutcomeUncertain] {
            let client = TransactionCodeWalletClient(transactionCode: nil)
            let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
            model.unlockForTests()
            try await waitUntil { model.isReady }
            let pending = DeferredCredential(id: "pending", credentialConfigurationID: nil, intervalSeconds: nil, status: status)
            model.deferredCredentials = [pending]
            model.resumeDeferredCredential(pending)
            await Task.yield()
            XCTAssertFalse(model.isLoading)
            let before = await client.resumedDeferredCredentialIDs
            XCTAssertTrue(before.isEmpty)
            model.refreshIssuanceStatus()
            try await waitUntil { !model.isLoading }
            XCTAssertTrue(model.deferredCredentials.isEmpty)
            let after = await client.resumedDeferredCredentialIDs
            XCTAssertTrue(after.isEmpty)
        }
    }

    func testReceiptKeepsEarlierSavedIdsAcrossContinuation() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil, deferWithProgress: true)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady }
        let pending = DeferredCredential(id: "pending", credentialConfigurationID: "ExampleCredential", intervalSeconds: 5)
        model.deferredCredentials = [pending]
        model.issuanceReceipt = IssuanceReceipt(pendingIDs: ["pending"])
        model.lastReceivedCredentialIDs = ["earlier-saved"]
        model.resumeDeferredCredential(pending)
        try await waitUntil { !model.isLoading }
        XCTAssertEqual(model.lastReceivedCredentialIDs, ["earlier-saved", "credential-1"])
        XCTAssertEqual(model.issuanceReceipt?.pendingIDs, ["pending"])
        model.resumeDeferredCredential(pending)
        try await waitUntil { !model.isLoading }
        XCTAssertEqual(model.lastReceivedCredentialIDs, ["earlier-saved", "credential-1"])
    }

    func testRetainedPresentationDoesNotEraseNewIntervalOrUncertainFailure() {
        let pending = DeferredCredential(id: "pending", credentialConfigurationID: "pid", intervalSeconds: 15)
        let lost = IssuanceOutcome.failed(sessionID: "session", error: .init(code: .remoteOutcomeUncertain, message: "Response lost"),
            storedCredentialIDs: ["saved"], deferredCredentials: [pending])
        guard case let .failed(_, _, saved, blocked) = lost.withContinuations([], resumingID: pending.id) else { return XCTFail() }
        XCTAssertEqual(saved, ["saved"])
        XCTAssertEqual(blocked.first?.status, .remoteOutcomeUncertain)
        let retained = DeferredCredential(id: pending.id, credentialConfigurationID: "pid", intervalSeconds: 5,
            status: .awaitingLocalSave, displayMetadataJSON: "{}")
        guard case let .failed(_, _, _, latest) = lost.withContinuations([retained], resumingID: pending.id) else { return XCTFail() }
        XCTAssertEqual(latest.first?.intervalSeconds, 15)
        XCTAssertEqual(latest.first?.status, .awaitingLocalSave)
        XCTAssertEqual(latest.first?.displayMetadataJSON, "{}")
    }

    func testCopySelectionIsExplicitBoundedAndForwardedForBothGrants() async throws {
        for grant in [IssuanceGrant.preAuthorizedCode, .authorizationCode] {
            let client = TransactionCodeWalletClient(transactionCode: nil, issuanceGrant: grant, batchSize: 3)
            let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
            model.unlockForTests()
            try await waitUntil { model.isReady }
            model.offerUrl = "openid-credential-offer://issuer.example"
            model.previewOffer()
            try await waitUntil { model.offerPreview != nil && !model.isLoading }
            XCTAssertEqual(model.issuanceCopyCounts["ExampleCredential"], 1)
            model.updateIssuanceCopies("ExampleCredential", 0)
            XCTAssertFalse(model.acceptOfferEnabled)
            model.updateIssuanceCopies("ExampleCredential", 99)
            XCTAssertEqual(model.issuanceCopyCounts["ExampleCredential"], 3)
            model.acceptOffer()
            try await waitUntil { !model.isLoading }
            let selections = await client.receivedIssuanceSelections
            XCTAssertEqual(selections.compactMap { $0 }.first?.first?.holders, .newKeys(count: 3))
        }
    }

    func testMixedImmediateAndDeferredOutcomeRefreshesSavedCredentials() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil, deferWithProgress: true)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady }
        model.offerUrl = "openid-credential-offer://issuer.example"
        model.previewOffer()
        try await waitUntil { model.offerPreview != nil && !model.isLoading }
        model.acceptOffer()
        try await waitUntil { !model.isLoading }
        XCTAssertEqual(model.credentials.map(\.id), ["credential-1"])
        XCTAssertEqual(model.lastReceivedCredentialIDs, ["credential-1"])
        XCTAssertEqual(model.deferredCredentials.map(\.id), ["pending"])
        XCTAssertFalse(model.isError)
        XCTAssertEqual(model.statusMessage, "Saved credentials: 1. Pending targets: 1.")
        XCTAssertNil(model.offerPreview)
    }

    func testPartialFailureKeepsSavedCredentialsAndRecoversDeferredHandlesAfterReopening() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil, failWithProgress: true)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady }
        model.offerUrl = "openid-credential-offer://issuer.example"
        model.previewOffer()
        try await waitUntil { model.offerPreview != nil && !model.isLoading }
        model.acceptOffer()
        try await waitUntil { !model.isLoading }
        XCTAssertEqual(model.credentials.map(\.id), ["credential-1"])
        XCTAssertEqual(model.deferredCredentials.map(\.id), ["pending"])
        XCTAssertNil(model.offerPreview)
        XCTAssertFalse(model.acceptOfferEnabled)
        XCTAssertTrue(model.statusMessage.contains("Saved credentials: 1. Pending targets: 1. Failed targets: 1. Not attempted: 2."))
        let reopened = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        reopened.unlockForTests()
        try await waitUntil { reopened.isReady }
        XCTAssertEqual(reopened.deferredCredentials, model.deferredCredentials)
    }

    func testMixedDeferredResumeRefreshesSavedCredentialsAndReportsPendingTargets() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil, deferWithProgress: true)
        let model = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        model.unlockForTests()
        try await waitUntil { model.isReady }
        let pending = DeferredCredential(id: "pending", credentialConfigurationID: "ExampleCredential", intervalSeconds: 5)
        model.deferredCredentials = [pending]
        XCTAssertTrue(model.credentials.isEmpty)
        model.resumeDeferredCredential(pending)
        try await waitUntil { !model.isLoading }
        XCTAssertEqual(model.credentials.map(\.id), ["credential-1"])
        XCTAssertEqual(model.lastReceivedCredentialIDs, ["credential-1"])
        XCTAssertEqual(model.deferredCredentials, [pending])
        XCTAssertEqual(model.statusMessage, "Saved credentials: 1. Pending targets: 1.")
    }

    func testRequiredTransactionCodeIsPromptedAndForwardedOnce() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.selectedTab = .receive
        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview?.transactionCode != nil }
        viewModel.previewOffer()
        await Task.yield()

        let receiveCallsBeforeCode = await client.issuanceContinuationCalls
        let resolveCalls = await client.issuanceStartCalls
        XCTAssertFalse(viewModel.acceptOfferEnabled)
        XCTAssertEqual(receiveCallsBeforeCode, 0)
        XCTAssertEqual(resolveCalls, 1)

        viewModel.acceptOffer()
        await Task.yield()
        let receiveCallsAfterRejectedAccept = await client.issuanceContinuationCalls
        XCTAssertEqual(receiveCallsAfterRejectedAccept, 0)

        viewModel.txCode = " abc-123 "
        XCTAssertTrue(viewModel.acceptOfferEnabled)
        viewModel.acceptOffer()
        try await waitUntil { viewModel.receiveCompleted && !viewModel.isLoading }
        XCTAssertEqual(viewModel.selectedTab, .receive)
        XCTAssertNotNil(viewModel.issuanceReceipt)

        let receiveCalls = await client.issuanceContinuationCalls
        let receivedTxCodes = await client.receivedTxCodes
        XCTAssertEqual(receiveCalls, 1)
        XCTAssertEqual(receivedTxCodes, ["abc-123"])
        XCTAssertEqual(viewModel.receivedCredentials.map(\.id), ["credential-1"])
        XCTAssertEqual(viewModel.txCode, "")
        XCTAssertNil(viewModel.offerPreview?.transactionCode)
    }

    func testChangingOfferClearsTransactionCodeState() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example/first"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview?.transactionCode != nil }
        viewModel.txCode = "1234"

        viewModel.offerUrl = "openid-credential-offer://issuer.example/second"

        XCTAssertNil(viewModel.offerPreview?.transactionCode)
        XCTAssertEqual(viewModel.txCode, "")
    }

    func testNumericTransactionCodeIsFilteredCappedAndValidated() async throws {
        let client = TransactionCodeWalletClient(
            transactionCode: IssuanceTransactionCode(inputMode: "numeric", length: 6, descriptionText: nil)
        )
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview?.transactionCode != nil }
        viewModel.updateTxCode("12a34")

        XCTAssertEqual(viewModel.txCode, "1234")
        XCTAssertFalse(viewModel.acceptOfferEnabled)

        viewModel.updateTxCode("12a345678")

        XCTAssertEqual(viewModel.txCode, "123456")
        XCTAssertTrue(viewModel.acceptOfferEnabled)
    }

    func testAuthorizationCodeOfferOpensIssuerSignInContinuation() async throws {
        let client = TransactionCodeWalletClient(transactionCode: nil, issuanceGrant: .authorizationCode)
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview?.grant == .authorizationCode }

        viewModel.acceptOffer()
        try await waitUntil { viewModel.authorizationRequestURL != nil }

        XCTAssertEqual(viewModel.authorizationRequestURL, URL(string: "https://issuer.example/authorize"))
        let issuanceContinuationCalls = await client.issuanceContinuationCalls
        XCTAssertEqual(issuanceContinuationCalls, 0)
    }

    func testStaleIssuanceStartCannotOverwriteIncomingDeepLink() async throws {
        let client = TransactionCodeWalletClient(issuanceStartDelayNanoseconds: 100_000_000)
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example/original"
        viewModel.previewOffer()
        viewModel.handleDeepLink(URL(string: "openid-credential-offer://issuer.example/replacement")!)
        try await Task.sleep(nanoseconds: 200_000_000)

        XCTAssertEqual(viewModel.offerUrl, "openid-credential-offer://issuer.example/replacement")
        XCTAssertNil(viewModel.offerPreview?.transactionCode)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
    }

    func testLockClearsActiveOfferReviewAndDoesNotRebootstrap() async throws {
        let client = TransactionCodeWalletClient()
        let pinStore = InMemoryDemoPinStore()
        try await pinStore.setPin("1234")
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {}, pinStore: pinStore)
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        let bootstrapCallsAfterUnlock = await client.bootstrapCalls
        XCTAssertEqual(bootstrapCallsAfterUnlock, 1)

        viewModel.selectedTab = .receive
        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview != nil }
        viewModel.receiveCompleted = true
        viewModel.presentationCompleted = true
        let receiveResetKey = viewModel.receiveNavigationResetKey
        let presentationResetKey = viewModel.presentationNavigationResetKey

        viewModel.lock()

        XCTAssertEqual(viewModel.auth, .login)
        XCTAssertTrue(viewModel.isReady)
        XCTAssertEqual(viewModel.offerUrl, "")
        XCTAssertEqual(viewModel.txCode, "")
        XCTAssertNil(viewModel.offerPreview)
        XCTAssertNil(viewModel.presentationReview)
        XCTAssertTrue(viewModel.selectedPresentationCredentialOptions.isEmpty)
        XCTAssertTrue(viewModel.selectedPresentationDisclosureOptions.isEmpty)
        XCTAssertTrue(viewModel.lastReceivedCredentialIDs.isEmpty)
        XCTAssertFalse(viewModel.receiveCompleted)
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertNil(viewModel.pendingPresentationContinuationURL)
        XCTAssertNil(viewModel.pendingPresentationFormPostHTML)
        XCTAssertEqual(viewModel.receiveNavigationResetKey, receiveResetKey + 1)
        XCTAssertEqual(viewModel.presentationNavigationResetKey, presentationResetKey + 1)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")

        viewModel.pin = "1234"
        viewModel.submitPin()
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertTrue(viewModel.isReady)
        let bootstrapCallsAfterRelock = await client.bootstrapCalls
        XCTAssertEqual(bootstrapCallsAfterRelock, bootstrapCallsAfterUnlock)
    }

    func testPresentationDeepLinkCancelsActiveIssuanceSession() async throws {
        let client = TransactionCodeWalletClient(startsWithCredential: true)
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview != nil }
        let receiveResetKey = viewModel.receiveNavigationResetKey

        viewModel.handleDeepLink(URL(string: "openid4vp://verifier.example")!)
        try await waitUntilAsync {
            let sessionIDs = await client.cancelledIssuanceSessionIDs
            return !sessionIDs.isEmpty
        }

        let cancelledSessionIDs = await client.cancelledIssuanceSessionIDs
        XCTAssertEqual(cancelledSessionIDs, ["transaction-code-session"])
        XCTAssertNil(viewModel.offerPreview)
        XCTAssertEqual(viewModel.receiveNavigationResetKey, receiveResetKey + 1)
        XCTAssertEqual(viewModel.selectedTab, .present)
    }

    func testPresentationPreviewIsSingleFlight() async throws {
        let client = TransactionCodeWalletClient(
            startsWithCredential: true,
            presentationPreviewDelayNanoseconds: 100_000_000
        )
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.presentationRequestUrl = "openid4vp://verifier.example"
        viewModel.previewPresentation()
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }
        viewModel.previewPresentation()
        await Task.yield()

        let previewCalls = await client.presentationPreviewCalls
        XCTAssertEqual(previewCalls, 1)
    }

    func testStartingNewPresentationDiscardsLateResolvedPreview() async throws {
        let client = TransactionCodeWalletClient(
            startsWithCredential: true,
            presentationPreviewDelayNanoseconds: 100_000_000
        )
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.presentationRequestUrl = "openid4vp://verifier.example"
        viewModel.previewPresentation()
        viewModel.startNewPresentationFlow()
        try await waitUntilAsync {
            let handles = await client.discardedPresentationPreviewHandles
            return !handles.isEmpty
        }

        let discardedHandles = await client.discardedPresentationPreviewHandles
        XCTAssertEqual(discardedHandles, [PresentationPreviewHandle(value: "transaction-code-presentation-preview")])
        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testPresentationActionsAreSingleFlightAndCannotOverwriteReset() async throws {
        let client = TransactionCodeWalletClient(
            startsWithCredential: true,
            presentationActionDelayNanoseconds: 100_000_000
        )
        let viewModel = WalletViewModel(walletClient: client, identityDocumentRegistrationUpdate: {})
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.presentationRequestUrl = "openid4vp://verifier.example"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }

        viewModel.submitPresentation()
        viewModel.submitPresentation()
        viewModel.rejectPresentation()
        try await waitUntilAsync { await client.presentationSubmitCalls == 1 }

        viewModel.startNewPresentationFlow()
        try await Task.sleep(nanoseconds: 150_000_000)

        let submitCalls = await client.presentationSubmitCalls
        let rejectCalls = await client.presentationRejectCalls
        XCTAssertEqual(submitCalls, 1)
        XCTAssertEqual(rejectCalls, 0)
        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")
    }

    func testOptionalSetupPersistsAndAppliesSelectedSigningProtection() async throws {
        let client = TransactionCodeWalletClient()
        let store = InMemoryWalletDemoSigningProtectionStore()
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: store,
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )

        viewModel.selectSigningProtection(.none)
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        XCTAssertEqual(store.load(), WalletDemoSigningProtection.none)
        XCTAssertEqual(viewModel.appliedSigningProtection, WalletDemoSigningProtection.none)
        let protections = await client.bootstrappedSigningProtections
        XCTAssertEqual(protections, [.none])
    }

    func testPinSetupDoesNotDependOnSigningBiometricEnrollment() async throws {
        let client = TransactionCodeWalletClient()
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)
        let viewModel = WalletViewModel(
            signingProtectionMode: .required,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )

        try await waitUntil { viewModel.biometricSigningAvailability == .biometricNotEnrolled }
        viewModel.unlockForTests()
        try await waitUntil { viewModel.auth == .unlocked }
        XCTAssertNil(viewModel.pinError)
    }

    func testUnavailableBiometricSigningCannotBeSelectedButNoneRemainsSelectable() async throws {
        let client = TransactionCodeWalletClient()
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        try await waitUntil { viewModel.biometricSigningAvailability == .biometricNotEnrolled }

        viewModel.selectSigningProtection(.none)
        viewModel.selectSigningProtection(.biometric)

        XCTAssertEqual(viewModel.selectedSigningProtection, .none)
    }

    func testForegroundWarningTracksAppliedBiometricSigningAvailability() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(.biometric),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)

        viewModel.handleApplicationBecameActive()
        try await waitUntil { viewModel.signingProtectionWarning != nil }

        XCTAssertEqual(viewModel.biometricSigningAvailability, .biometricNotEnrolled)
        XCTAssertTrue(viewModel.signingProtectionWarning?.contains("cannot be used again") == true)

        viewModel.dismissSigningProtectionWarning()
        XCTAssertNil(viewModel.signingProtectionWarning)

        await client.setSigningProtectionAvailability(.available)
        viewModel.handleApplicationBecameActive()
        try await waitUntil { viewModel.biometricSigningAvailability == .available }
        XCTAssertNil(viewModel.signingProtectionWarning)
    }

    func testDisabledModeStillWarnsUntilExistingBiometricWalletIsReprovisioned() async throws {
        let client = TransactionCodeWalletClient()
        await client.setNextReportedSigningProtection(.biometric)
        let viewModel = WalletViewModel(
            signingProtectionMode: .disabled,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(
                WalletDemoSigningProtection.none
            ),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)

        viewModel.handleApplicationBecameActive()
        try await waitUntil { viewModel.signingProtectionWarning != nil }

        XCTAssertEqual(viewModel.appliedSigningProtection, .biometric)
        XCTAssertTrue(viewModel.signingProtectionWarning?.contains("reset the wallet") == true)
    }

    func testRequiredModeWarningDoesNotOfferAProhibitedSigningChoice() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(
            signingProtectionMode: .required,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(.biometric),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)

        viewModel.handleApplicationBecameActive()
        try await waitUntil { viewModel.signingProtectionWarning != nil }

        XCTAssertTrue(viewModel.signingProtectionWarning?.contains("required by app configuration") == true)
        XCTAssertFalse(viewModel.signingProtectionWarning?.contains("reset the wallet") == true)
    }

    func testForegroundWarningWaitsUntilPinUnlock() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(.biometric),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        viewModel.lock()
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)

        viewModel.handleApplicationBecameActive()
        try await waitUntil { viewModel.biometricSigningAvailability == .biometricNotEnrolled }
        XCTAssertNil(viewModel.signingProtectionWarning)

        viewModel.pin = "1234"
        viewModel.submitPin()
        try await waitUntil { viewModel.auth == .unlocked }

        XCTAssertTrue(viewModel.signingProtectionWarning?.contains("cannot be used again") == true)
    }

    func testUnavailableSigningProtectionDoesNotReplaceWallet() async throws {
        let client = TransactionCodeWalletClient()
        let store = InMemoryWalletDemoSigningProtectionStore(WalletDemoSigningProtection.none)
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: store,
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        await client.setSigningProtectionAvailability(.biometricNotEnrolled)

        viewModel.requestSigningProtectionChange(.biometric)
        try await waitUntil { viewModel.signingProtectionError != nil }

        XCTAssertEqual(viewModel.selectedSigningProtection, .none)
        XCTAssertNil(viewModel.pendingSigningProtectionChange)
        let deleteCalls = await client.deleteLocalDataCalls
        XCTAssertEqual(deleteCalls, 0)
    }

    func testConfirmedSigningProtectionChangeReprovisionsWallet() async throws {
        let client = TransactionCodeWalletClient()
        let store = InMemoryWalletDemoSigningProtectionStore(.biometric)
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: store,
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }

        viewModel.requestSigningProtectionChange(WalletDemoSigningProtection.none)
        try await waitUntil {
            viewModel.pendingSigningProtectionChange == WalletDemoSigningProtection.none
        }
        viewModel.confirmSigningProtectionChange()
        try await waitUntilAsync { await client.deleteLocalDataCalls == 1 }
        try await waitUntil { viewModel.isReady && !viewModel.isChangingSigningProtection }

        XCTAssertEqual(store.load(), WalletDemoSigningProtection.none)
        XCTAssertEqual(viewModel.appliedSigningProtection, WalletDemoSigningProtection.none)
        let protections = await client.bootstrappedSigningProtections
        XCTAssertEqual(protections, [.biometric, .none])
    }

    func testFailedReprovisionKeepsTargetAndCanBeRetried() async throws {
        let client = TransactionCodeWalletClient()
        let store = InMemoryWalletDemoSigningProtectionStore(.biometric)
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: store,
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        await client.failNextBootstrap()

        viewModel.requestSigningProtectionChange(WalletDemoSigningProtection.none)
        try await waitUntil {
            viewModel.pendingSigningProtectionChange == WalletDemoSigningProtection.none
        }
        viewModel.confirmSigningProtectionChange()
        try await waitUntil { !viewModel.isChangingSigningProtection && viewModel.signingProtectionError != nil }

        XCTAssertFalse(viewModel.isReady)
        XCTAssertEqual(store.load(), WalletDemoSigningProtection.none)

        viewModel.requestSigningProtectionChange(WalletDemoSigningProtection.none)
        try await waitUntil { viewModel.isReady }

        XCTAssertEqual(viewModel.appliedSigningProtection, WalletDemoSigningProtection.none)
        let deleteCalls = await client.deleteLocalDataCalls
        XCTAssertEqual(deleteCalls, 2)
    }

    func testReprovisionFailsClosedWhenWalletReportsDifferentAppliedPolicy() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(
            signingProtectionMode: .optional,
            signingProtectionStore: InMemoryWalletDemoSigningProtectionStore(.biometric),
            walletClient: client,
            identityDocumentRegistrationUpdate: {}
        )
        viewModel.unlockForTests()
        try await waitUntil { viewModel.isReady }
        await client.setNextReportedSigningProtection(.biometric)

        viewModel.requestSigningProtectionChange(WalletDemoSigningProtection.none)
        try await waitUntil {
            viewModel.pendingSigningProtectionChange == WalletDemoSigningProtection.none
        }
        viewModel.confirmSigningProtectionChange()
        try await waitUntil { !viewModel.isChangingSigningProtection && viewModel.signingProtectionError != nil }

        XCTAssertFalse(viewModel.isReady)
        XCTAssertTrue(
            viewModel.signingProtectionError?.contains("did not apply the selected signing protection") == true
        )
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 20_000_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !condition() {
            guard DispatchTime.now().uptimeNanoseconds < deadline else {
                XCTFail("Timed out waiting for wallet state")
                return
            }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    private func waitUntilAsync(
        timeoutNanoseconds: UInt64 = 20_000_000_000,
        condition: @escaping () async -> Bool
    ) async throws {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !(await condition()) {
            guard DispatchTime.now().uptimeNanoseconds < deadline else {
                XCTFail("Timed out waiting for wallet client state")
                return
            }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
    }
}

private actor TransactionCodeWalletClient: WalletClient {
    private(set) var issuanceContinuationCalls = 0
    private(set) var issuanceStartCalls = 0
    private(set) var presentationPreviewCalls = 0
    private(set) var presentationSubmitCalls = 0
    private(set) var presentationRejectCalls = 0
    private(set) var receivedTxCodes: [String] = []
    private(set) var cancelledIssuanceSessionIDs: [String] = []
    private(set) var discardedPresentationPreviewHandles: [PresentationPreviewHandle] = []
    private(set) var bootstrappedSigningProtections: [WalletDemoSigningProtection] = []
    private(set) var preflightedSigningProtections: [WalletDemoSigningProtection] = []
    private(set) var deleteLocalDataCalls = 0
    private var signingProtectionAvailability: WalletDemoSigningProtectionAvailability = .available
    private var shouldFailNextBootstrap = false
    private var nextReportedSigningProtection: WalletDemoSigningProtection?
    private var credentialIssued = false
    private let issuanceStartDelayNanoseconds: UInt64
    private let transactionCode: IssuanceTransactionCode?
    private let batchSize: Int?
    private let failWithProgress: Bool
    private let deferWithProgress: Bool
    private(set) var receivedIssuanceSelections: [[IssuanceCredentialSelection]?] = []
    private(set) var resumedDeferredCredentialIDs: [String] = []
    private let issuanceGrant: IssuanceGrant
    private let startsWithCredential: Bool
    private let presentationPreviewDelayNanoseconds: UInt64
    private let presentationActionDelayNanoseconds: UInt64

    init(
        issuanceStartDelayNanoseconds: UInt64 = 0,
        transactionCode: IssuanceTransactionCode? = IssuanceTransactionCode(
            inputMode: "text",
            length: nil,
            descriptionText: "Enter the code from the issuer"
        ),
        issuanceGrant: IssuanceGrant = .preAuthorizedCode,
        batchSize: Int? = nil,
        failWithProgress: Bool = false,
        deferWithProgress: Bool = false,
        startsWithCredential: Bool = false,
        presentationPreviewDelayNanoseconds: UInt64 = 0,
        presentationActionDelayNanoseconds: UInt64 = 0
    ) {
        self.issuanceStartDelayNanoseconds = issuanceStartDelayNanoseconds
        self.transactionCode = transactionCode
        self.issuanceGrant = issuanceGrant
        self.batchSize = batchSize
        self.failWithProgress = failWithProgress
        self.deferWithProgress = deferWithProgress
        self.startsWithCredential = startsWithCredential
        self.presentationPreviewDelayNanoseconds = presentationPreviewDelayNanoseconds
        self.presentationActionDelayNanoseconds = presentationActionDelayNanoseconds
    }

    private(set) var bootstrapCalls = 0

    func bootstrap(signingProtection: WalletDemoSigningProtection) async throws -> WalletDemoBootstrapResult {
        bootstrapCalls += 1
        bootstrappedSigningProtections.append(signingProtection)
        if shouldFailNextBootstrap {
            shouldFailNextBootstrap = false
            throw TestFailure.bootstrap
        }
        let reportedSigningProtection = nextReportedSigningProtection ?? signingProtection
        nextReportedSigningProtection = nil
        return WalletDemoBootstrapResult(
            keyID: "key-1",
            did: "did:key:test",
            publicJWK: #"{"kty":"OKP","crv":"Ed25519","x":"test"}"#,
            keyUseAuthorizationPolicy: reportedSigningProtection.authorizationPolicy
        )
    }

    func signingProtectionAvailability(
        _ signingProtection: WalletDemoSigningProtection
    ) async throws -> WalletDemoSigningProtectionAvailability {
        preflightedSigningProtections.append(signingProtection)
        return signingProtectionAvailability
    }

    func setSigningProtectionAvailability(_ availability: WalletDemoSigningProtectionAvailability) {
        signingProtectionAvailability = availability
    }

    func failNextBootstrap() {
        shouldFailNextBootstrap = true
    }

    func setNextReportedSigningProtection(_ protection: WalletDemoSigningProtection) {
        nextReportedSigningProtection = protection
    }

    func credentials() async throws -> [Credential] {
        let available = startsWithCredential || credentialIssued ? [Self.credential] : []
        return available.filter { !deletedCredentialIDs.contains($0.id) }
    }

    func listDeferredIssuance() async throws -> [DeferredCredential] {
        (failWithProgress || deferWithProgress) && credentialIssued ? [.init(id: "pending", credentialConfigurationID: "ExampleCredential", intervalSeconds: 5)] : []
    }

    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession {
        issuanceStartCalls += 1
        if issuanceStartDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: issuanceStartDelayNanoseconds)
        }
        return IssuanceSession(
            id: "transaction-code-session",
            offer: IssuanceOfferPreview(
                grant: issuanceGrant,
                issuer: IssuanceIssuerPreview(
                    identifier: "https://issuer.example",
                    name: "Example Issuer",
                    locale: nil,
                    logoURI: nil,
                    logoAltText: nil,
                    metadataProvenance: .unsigned
                ),
                credentials: [IssuanceCredentialPreview(configurationID: "ExampleCredential", format: "vc+sd-jwt", name: "Example credential", descriptionText: nil, logoURI: nil)],
                transactionCode: transactionCode,
                batchSize: batchSize
            )
        )
    }

    func beginAuthorizationIssuance(sessionID: String, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization {
        receivedIssuanceSelections.append(credentials)
        return IssuanceAuthorization(
            url: URL(string: "https://issuer.example/authorize")!,
            state: "test-state",
            redirectURI: URL(string: "openid://")!,
            pkce: .init(codeChallenge: "test-challenge", codeChallengeMethod: "S256"),
            pushedAuthorizationRequestUsed: false
        )
    }

    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceOutcome {
        issuanceContinuationCalls += 1
        receivedIssuanceSelections.append(credentials)
        receivedTxCodes.append(transactionCode ?? "")
        credentialIssued = true
        deletedCredentialIDs.remove(Self.credential.id)
        if deferWithProgress {
            return .deferred(sessionID: sessionID, storedCredentialIDs: [Self.credential.id],
                credentials: try await listDeferredIssuance())
        }
        if failWithProgress {
            return .failed(sessionID: sessionID, error: .init(code: .network, message: "Later target failed",
                targetFailure: .init(target: .init(configurationID: "ExampleCredential", credentialIdentifier: "second"),
                    stage: .request, notAttempted: [.init(configurationID: "OtherCredential"), .init(configurationID: "AnotherCredential")])), storedCredentialIDs: [Self.credential.id],
                deferredCredentials: try await listDeferredIssuance())
        }
        return .stored(sessionID: sessionID, credentialIDs: [Self.credential.id])
    }

    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome {
        try await continuePreAuthorizedIssuance(sessionID: sessionID, transactionCode: nil, credentials: nil)
    }

    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome {
        cancelledIssuanceSessionIDs.append(sessionID)
        return .cancelled(sessionID: sessionID)
    }

    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome {
        resumedDeferredCredentialIDs.append(deferredCredentialID)
        if deferWithProgress {
            credentialIssued = true
            return .deferred(sessionID: "session", storedCredentialIDs: [Self.credential.id],
                credentials: try await listDeferredIssuance())
        }
        return .failed(
            sessionID: "transaction-code-session",
            error: .init(code: .invalidSession, message: "No deferred credential in this test"),
            storedCredentialIDs: [], deferredCredentials: []
        )
    }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        .transmitted(.succeeded(verifierResponseJSON: "{}"))
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        presentationPreviewCalls += 1
        if presentationPreviewDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: presentationPreviewDelayNanoseconds)
        }
        return .ready(
            PresentationPreview(
                previewHandle: PresentationPreviewHandle(value: "transaction-code-presentation-preview"),
                request: PresentationRequestInfo(
                    clientID: "https://verifier.example",
                    requestAuthentication: .unauthenticated,
                    nonce: "nonce-1",
                    responseEncryption: .notRequired,
                ),
                credentialOptions: [
                    PresentationCredentialOption(
                        queryID: "pid",
                        credentialID: Self.credential.id,
                        format: Self.credential.format,
                        issuer: Self.credential.issuer,
                        subject: Self.credential.subject,
                        label: Self.credential.label,
                        credentialDataJSON: Self.credential.credentialDataJSON,
                        disclosures: []
                    )
                ]
            )
        )
    }

    func preparePaymentConsent(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PaymentConsent? { nil }

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?,
        paymentConsentRevision: String?
    ) async throws -> PresentationResult {
        presentationSubmitCalls += 1
        if presentationActionDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: presentationActionDelayNanoseconds)
        }
        return .transmitted(.succeeded(verifierResponseJSON: "{}"))
    }

    func rejectPresentation(previewHandle: PresentationPreviewHandle) async throws -> PresentationResult {
        presentationRejectCalls += 1
        return .transmitted(.succeeded(verifierResponseJSON: "{}"))
    }

    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws {
        discardedPresentationPreviewHandles.append(previewHandle)
    }

    func deleteCredential(id: String) async throws -> Bool {
        let existed = (try await credentials()).contains { $0.id == id }
        if existed {
            deletedCredentialIDs.insert(id)
        }
        return existed
    }

    func deleteLocalData() async throws {
        deleteLocalDataCalls += 1
        credentialIssued = false
        deletedCredentialIDs.insert(Self.credential.id)
    }

    private var deletedCredentialIDs: Set<String> = []

    private enum TestFailure: Error {
        case bootstrap
    }

    private static let credential = Credential(
        id: "credential-1",
        format: "vc+sd-jwt",
        issuer: "https://issuer.example",
        subject: nil,
        label: "PID",
        addedAt: nil,
        credentialDataJSON: "{}"
    )
}
