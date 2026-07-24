import Foundation
import WalletSDK
import XCTest
@testable import iosApp

@MainActor
final class WalletViewModelReceiveTests: XCTestCase {
    func testRequiredTransactionCodeIsPromptedAndForwardedOnce() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(walletClient: client)
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
        try await waitUntil { viewModel.receiveCompleted }

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
        let viewModel = WalletViewModel(walletClient: client)
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
        let viewModel = WalletViewModel(walletClient: client)
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
        let viewModel = WalletViewModel(walletClient: client)
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
        let viewModel = WalletViewModel(walletClient: client)
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

    func testPresentationDeepLinkCancelsActiveIssuanceSession() async throws {
        let client = TransactionCodeWalletClient(startsWithCredential: true)
        let viewModel = WalletViewModel(walletClient: client)
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
        let viewModel = WalletViewModel(walletClient: client)
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
        let viewModel = WalletViewModel(walletClient: client)
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
        let viewModel = WalletViewModel(walletClient: client)
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

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
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
        timeoutNanoseconds: UInt64 = 2_000_000_000,
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
    private var credentialIssued = false
    private let issuanceStartDelayNanoseconds: UInt64
    private let transactionCode: IssuanceTransactionCode?
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
        startsWithCredential: Bool = false,
        presentationPreviewDelayNanoseconds: UInt64 = 0,
        presentationActionDelayNanoseconds: UInt64 = 0
    ) {
        self.issuanceStartDelayNanoseconds = issuanceStartDelayNanoseconds
        self.transactionCode = transactionCode
        self.issuanceGrant = issuanceGrant
        self.startsWithCredential = startsWithCredential
        self.presentationPreviewDelayNanoseconds = presentationPreviewDelayNanoseconds
        self.presentationActionDelayNanoseconds = presentationActionDelayNanoseconds
    }

    func bootstrap() async throws -> WalletBootstrapResult {
        WalletBootstrapResult(keyID: "key-1", did: "did:key:test")
    }

    func credentials() async throws -> [Credential] {
        startsWithCredential || credentialIssued ? [Self.credential] : []
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
                issuer: IssuanceIssuerPreview(identifier: "https://issuer.example", name: "Example Issuer", locale: nil, logoURI: nil, logoAltText: nil),
                credentials: [IssuanceCredentialPreview(configurationID: "ExampleCredential", format: "vc+sd-jwt", name: "Example credential", descriptionText: nil, logoURI: nil)],
                transactionCode: transactionCode
            ),
            authorization: issuanceGrant == .authorizationCode
                ? IssuanceAuthorization(
                    url: URL(string: "https://issuer.example/authorize")!,
                    state: "test-state",
                    redirectURI: request.redirectURI,
                    pkce: .init(codeChallenge: "test-challenge", codeChallengeMethod: "S256"),
                    pushedAuthorizationRequestUsed: false
                )
                : nil
        )
    }

    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?) async throws -> IssuanceOutcome {
        issuanceContinuationCalls += 1
        receivedTxCodes.append(transactionCode ?? "")
        credentialIssued = true
        return .stored(sessionID: sessionID, credentialIDs: [Self.credential.id])
    }

    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome {
        try await continuePreAuthorizedIssuance(sessionID: sessionID, transactionCode: nil)
    }

    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome {
        cancelledIssuanceSessionIDs.append(sessionID)
        return .cancelled(sessionID: sessionID)
    }

    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome {
        .failed(
            sessionID: "transaction-code-session",
            error: .init(code: .invalidSession, message: "No deferred credential in this test"),
            storedCredentialIDs: []
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

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
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
