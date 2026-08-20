import Foundation
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
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Wallet ready")
        XCTAssertEqual(viewModel.statusMessage(for: .receive), "Wallet ready")
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
