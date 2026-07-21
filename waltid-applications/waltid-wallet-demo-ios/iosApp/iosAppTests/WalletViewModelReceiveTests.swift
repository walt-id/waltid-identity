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

        let receiveCallsBeforeCode = await client.receiveCalls
        let resolveCalls = await client.resolveCalls
        XCTAssertFalse(viewModel.acceptOfferEnabled)
        XCTAssertEqual(receiveCallsBeforeCode, 0)
        XCTAssertEqual(resolveCalls, 1)

        viewModel.acceptOffer()
        await Task.yield()
        let receiveCallsAfterRejectedAccept = await client.receiveCalls
        XCTAssertEqual(receiveCallsAfterRejectedAccept, 0)

        viewModel.txCode = " abc-123 "
        XCTAssertTrue(viewModel.acceptOfferEnabled)
        viewModel.acceptOffer()
        try await waitUntil { viewModel.receiveCompleted }

        let receiveCalls = await client.receiveCalls
        let receivedTxCodes = await client.receivedTxCodes
        XCTAssertEqual(receiveCalls, 1)
        XCTAssertEqual(receivedTxCodes, ["abc-123"])
        XCTAssertEqual(viewModel.receivedCredentials.map(\.id), ["credential-1"])
        XCTAssertEqual(viewModel.txCode, "")
        XCTAssertNil(viewModel.offerPreview?.transactionCode)
        guard case .success(.receive, .credentialAdded, _) = viewModel.interactionState else {
            return XCTFail("Expected credential-added terminal state")
        }
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

    func testDecliningOfferUsesAnHonestTerminalOutcome() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(walletClient: client)
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview != nil }
        viewModel.declineOffer()

        guard case .success(.receive, .offerDeclined, let message) = viewModel.interactionState else {
            return XCTFail("Expected offer-declined terminal state")
        }
        XCTAssertEqual(message, "Credential offer declined")
        XCTAssertNil(viewModel.offerPreview)
    }

    func testNumericTransactionCodeIsFilteredCappedAndValidated() async throws {
        let client = TransactionCodeWalletClient(
            transactionCode: TransactionCodeRequirement(inputMode: .numeric, length: 6, description: nil)
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

    func testIncomingDeepLinkRequiresExplicitReplacementOfActiveRequest() async throws {
        let client = TransactionCodeWalletClient(resolveDelayNanoseconds: 100_000_000)
        let viewModel = WalletViewModel(walletClient: client)
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example/original"
        viewModel.previewOffer()
        let replacementURL = URL(string: "openid-credential-offer://issuer.example/replacement")!
        viewModel.handleDeepLink(replacementURL)

        XCTAssertEqual(viewModel.offerUrl, "openid-credential-offer://issuer.example/original")
        XCTAssertEqual(viewModel.replacementRequest?.url, replacementURL)

        viewModel.replaceCurrentRequest()
        try await waitUntil { viewModel.offerPreview != nil }
        XCTAssertEqual(viewModel.offerUrl, "openid-credential-offer://issuer.example/replacement")
        XCTAssertNil(viewModel.replacementRequest)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
    }

    func testReplacingIssuanceWithPresentationDiscardsPreview() async throws {
        let client = TransactionCodeWalletClient(startsWithCredential: true)
        let viewModel = WalletViewModel(walletClient: client)
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example"
        viewModel.previewOffer()
        try await waitUntil { viewModel.offerPreview != nil }
        let receiveResetKey = viewModel.receiveNavigationResetKey

        let presentationURL = URL(string: "openid4vp://verifier.example")!
        viewModel.handleDeepLink(presentationURL)

        XCTAssertNotNil(viewModel.offerPreview)
        XCTAssertEqual(viewModel.replacementRequest?.url, presentationURL)

        viewModel.replaceCurrentRequest()
        try await waitUntilAsync {
            let handles = await client.discardedIssuancePreviewHandles
            return !handles.isEmpty
        }

        let discardedHandles = await client.discardedIssuancePreviewHandles
        XCTAssertEqual(discardedHandles, [IssuancePreviewHandle(value: "transaction-code-preview")])
        XCTAssertNil(viewModel.offerPreview)
        XCTAssertEqual(viewModel.receiveNavigationResetKey, receiveResetKey + 1)
        XCTAssertTrue(viewModel.txCode.isEmpty)
        XCTAssertEqual(viewModel.selectedTab, .present)
    }

    func testCaptureRejectsWrongFlowUntilUserSwitches() async throws {
        let client = TransactionCodeWalletClient(startsWithCredential: true)
        let viewModel = WalletViewModel(walletClient: client)
        try await waitUntil { viewModel.isReady }

        viewModel.startReceiveCapture()
        viewModel.submitCapturedRequest("openid4vp://verifier.example", source: .qr)

        guard case .wrongRequestType(let expected, let request) = viewModel.interactionState else {
            return XCTFail("Expected a recoverable wrong-flow state")
        }
        XCTAssertEqual(expected, .receive)
        XCTAssertEqual(request.kind, .present)

        viewModel.switchToDetectedRequest()
        try await waitUntil { viewModel.presentationPreview != nil }
        let previewCalls = await client.presentationPreviewCalls
        XCTAssertEqual(previewCalls, 1)
    }

    func testDuplicateScannerCallbacksResolveOnlyOnce() async throws {
        let client = TransactionCodeWalletClient(resolveDelayNanoseconds: 100_000_000)
        let viewModel = WalletViewModel(walletClient: client)
        try await waitUntil { viewModel.isReady }

        viewModel.startReceiveCapture()
        viewModel.submitCapturedRequest("openid-credential-offer://issuer.example", source: .qr)
        viewModel.submitCapturedRequest("openid-credential-offer://issuer.example", source: .qr)
        try await waitUntil { viewModel.offerPreview != nil }

        let resolveCalls = await client.resolveCalls
        XCTAssertEqual(resolveCalls, 1)
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
    private(set) var receiveCalls = 0
    private(set) var resolveCalls = 0
    private(set) var presentationPreviewCalls = 0
    private(set) var presentationSubmitCalls = 0
    private(set) var presentationRejectCalls = 0
    private(set) var receivedTxCodes: [String] = []
    private(set) var discardedIssuancePreviewHandles: [IssuancePreviewHandle] = []
    private(set) var discardedPresentationPreviewHandles: [PresentationPreviewHandle] = []
    private var credentialIssued = false
    private let resolveDelayNanoseconds: UInt64
    private let transactionCode: TransactionCodeRequirement
    private let startsWithCredential: Bool
    private let presentationPreviewDelayNanoseconds: UInt64
    private let presentationActionDelayNanoseconds: UInt64

    init(
        resolveDelayNanoseconds: UInt64 = 0,
        transactionCode: TransactionCodeRequirement = TransactionCodeRequirement(
            inputMode: .text,
            length: nil,
            description: "Enter the code from the issuer"
        ),
        startsWithCredential: Bool = false,
        presentationPreviewDelayNanoseconds: UInt64 = 0,
        presentationActionDelayNanoseconds: UInt64 = 0
    ) {
        self.resolveDelayNanoseconds = resolveDelayNanoseconds
        self.transactionCode = transactionCode
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

    func resolveOffer(offer: URL) async throws -> OfferResolution {
        resolveCalls += 1
        if resolveDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: resolveDelayNanoseconds)
        }
        return OfferResolution(
            previewHandle: IssuancePreviewHandle(value: "transaction-code-preview"),
            issuer: IssuerMetadata(
                credentialIssuer: "https://issuer.example",
                display: MetadataDisplay(
                    name: "Example Issuer",
                    locale: "en",
                    logoURI: nil,
                    logoAltText: nil
                )
            ),
            offeredCredentials: [
                OfferedCredentialMetadata(
                    configurationID: "ExampleCredential",
                    format: "vc+sd-jwt",
                    scope: nil,
                    vct: "ExampleCredential",
                    doctype: nil,
                    display: nil,
                    claims: []
                )
            ],
            transactionCode: transactionCode
        )
    }

    func receive(previewHandle: IssuancePreviewHandle, txCode: String?) async throws -> [String] {
        receiveCalls += 1
        receivedTxCodes.append(txCode ?? "")
        credentialIssued = true
        return [Self.credential.id]
    }

    func discardIssuancePreview(_ previewHandle: IssuancePreviewHandle) async throws {
        discardedIssuancePreviewHandles.append(previewHandle)
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
                    clientID: nil,
                    responseEncryption: .notRequired
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
