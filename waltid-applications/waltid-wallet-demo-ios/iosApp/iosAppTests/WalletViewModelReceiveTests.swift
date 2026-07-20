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

    func testStaleOfferResolutionCannotOverwriteIncomingDeepLink() async throws {
        let client = TransactionCodeWalletClient(resolveDelayNanoseconds: 100_000_000)
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
}

private actor TransactionCodeWalletClient: WalletClient {
    private(set) var receiveCalls = 0
    private(set) var resolveCalls = 0
    private(set) var receivedTxCodes: [String] = []
    private var credentialIssued = false
    private let resolveDelayNanoseconds: UInt64
    private let transactionCode: TransactionCodeRequirement

    init(
        resolveDelayNanoseconds: UInt64 = 0,
        transactionCode: TransactionCodeRequirement = TransactionCodeRequirement(
            inputMode: .text,
            length: nil,
            description: "Enter the code from the issuer"
        )
    ) {
        self.resolveDelayNanoseconds = resolveDelayNanoseconds
        self.transactionCode = transactionCode
    }

    func bootstrap() async throws -> WalletBootstrapResult {
        WalletBootstrapResult(keyID: "key-1", did: "did:key:test")
    }

    func credentials() async throws -> [Credential] {
        credentialIssued ? [Self.credential] : []
    }

    func resolveOffer(offer: URL) async throws -> OfferResolution {
        resolveCalls += 1
        if resolveDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: resolveDelayNanoseconds)
        }
        return OfferResolution(
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

    func receive(offer: URL, txCode: String?) async throws -> [String] {
        receiveCalls += 1
        receivedTxCodes.append(txCode ?? "")
        credentialIssued = true
        return [Self.credential.id]
    }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        .transmitted(.succeeded(verifierResponseJSON: "{}"))
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        .ready(
            PresentationPreview(
                request: PresentationRequestInfo(clientID: nil, responseEncryption: .notRequired),
                credentialOptions: []
            )
        )
    }

    func submitPresentation(
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PresentationResult {
        .transmitted(.succeeded(verifierResponseJSON: "{}"))
    }

    func rejectPresentation(request: URL) async throws -> PresentationResult {
        .transmitted(.succeeded(verifierResponseJSON: "{}"))
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
