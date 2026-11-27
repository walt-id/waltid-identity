#if canImport(WalletCore) && os(iOS) && WALLET_SDK_BRIDGE_FIXTURES
import XCTest
@testable import WalletSDK
@preconcurrency import WalletCore

final class SigningRecoveryTests: XCTestCase {
    private func open(_ issuer: SigningRecoveryIssuer) async throws -> WalletSDK.Wallet {
        WalletCoreRuntime.initializeIfNeeded()
        let core = try await issuer.openWallet()
        return WalletSDK.Wallet(bridge: KMPWalletCoreBridge(bridge: WalletSdkBridge(wallet: core)))
    }

    func testFreshApprovalAfterReopenStoresExactlyOnce() async throws {
        let issuer = SigningRecoveryIssuer()
        let wallet = try await open(issuer)
        let offer = try await wallet.startIssuance(.init(offer: XCTUnwrap(URL(string: issuer.offer)), clientID: "wallet", redirectURI: XCTUnwrap(URL(string: "wallet://callback")), keyID: "holder"))
        guard case .failed(_, let failure, let committed, let pending) = try await wallet.continuePreAuthorizedIssuance(sessionID: offer.id)
        else { return XCTFail("Expected signing dismissal") }
        XCTAssertTrue(failure.canRetryOffer && failure.signingApprovalRequired)
        XCTAssertTrue(committed.isEmpty && pending.isEmpty)
        XCTAssertEqual([issuer.exchanges, issuer.prompts, issuer.signatures, issuer.deliveries], [1, 1, 0, 0])
        try await wallet.close()

        let reopened = try await open(issuer)
        guard case .stored(_, let ids) = try await reopened.retryIssuanceSigning(sessionID: offer.id)
        else { return XCTFail("Expected the retained signing grant to finish") }
        let credentials = try await reopened.credentials()
        XCTAssertEqual(ids.count, 1)
        XCTAssertEqual(ids, credentials.map(\.id))
        guard case .failed(_, let used, _, _) = try await reopened.retryIssuanceSigning(sessionID: offer.id) else { return XCTFail("Approval was reused") }
        XCTAssertEqual(used.code, .invalidSession)
        XCTAssertEqual([issuer.exchanges, issuer.prompts, issuer.signatures, issuer.deliveries], [1, 2, 1, 1])
        try await reopened.close()
    }
}
#endif
