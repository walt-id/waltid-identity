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
        viewModel.receiveCredential()
        try await waitUntil { viewModel.txCodeRequired }

        let receiveCallsBeforeCode = await client.receiveCalls
        XCTAssertFalse(viewModel.receiveActionEnabled)
        XCTAssertEqual(receiveCallsBeforeCode, 0)

        viewModel.txCode = " 1234 "
        XCTAssertTrue(viewModel.receiveActionEnabled)
        viewModel.receiveCredential()
        try await waitUntil { viewModel.receiveCompleted }

        let receiveCalls = await client.receiveCalls
        let receivedTxCodes = await client.receivedTxCodes
        XCTAssertEqual(receiveCalls, 1)
        XCTAssertEqual(receivedTxCodes, ["1234"])
        XCTAssertEqual(viewModel.receivedCredentials.map(\.id), ["credential-1"])
    }

    func testChangingOfferClearsTransactionCodeState() async throws {
        let client = TransactionCodeWalletClient()
        let viewModel = WalletViewModel(walletClient: client)
        try await waitUntil { viewModel.isReady }

        viewModel.offerUrl = "openid-credential-offer://issuer.example/first"
        viewModel.receiveCredential()
        try await waitUntil { viewModel.txCodeRequired }
        viewModel.txCode = "1234"

        viewModel.offerUrl = "openid-credential-offer://issuer.example/second"

        XCTAssertFalse(viewModel.txCodeRequired)
        XCTAssertEqual(viewModel.txCode, "")
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
    private(set) var receivedTxCodes: [String] = []
    private var credentialIssued = false

    func bootstrap() async throws -> WalletBootstrapResult {
        WalletBootstrapResult(keyID: "key-1", did: "did:key:test")
    }

    func credentials() async throws -> [Credential] {
        credentialIssued ? [Self.credential] : []
    }

    func resolveOffer(offer: URL) async throws -> OfferResolution {
        OfferResolution(txCodeRequired: true)
    }

    func receive(offer: URL, txCode: String?) async throws -> [String] {
        receiveCalls += 1
        receivedTxCodes.append(txCode ?? "")
        credentialIssued = true
        return [Self.credential.id]
    }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    }

    func previewPresentation(request: URL) async throws -> PresentationPreview {
        PresentationPreview(request: PresentationRequestInfo(clientID: nil), credentialOptions: [])
    }

    func submitPresentation(
        request: URL,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PresentationResult {
        PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
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
