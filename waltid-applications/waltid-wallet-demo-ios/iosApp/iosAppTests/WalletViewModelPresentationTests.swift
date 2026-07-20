import Foundation
import WalletSDK
import XCTest
@testable import iosApp

final class WalletViewModelPresentationTests: XCTestCase {

    @MainActor
    func testInvalidRequestCanBeDismissedLocallyOrReportedToVerifier() async throws {
        let request = try XCTUnwrap(URL(string: "openid4vp://invalid-request"))
        let previewError = PresentationPreviewError(
            request: PresentationRequestInfo(
                clientID: "https://verifier.example",
                verifierName: "Example Verifier"
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

        XCTAssertNil(viewModel.presentationError)
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
        let rejectedAfterDismiss = await walletClient.rejectedRequestURLs
        XCTAssertEqual(rejectedAfterDismiss, [])

        viewModel.presentationRequestUrl = request.absoluteString
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationError == previewError }
        viewModel.rejectPresentation()
        try await waitUntil { viewModel.presentationCompleted }

        XCTAssertNil(viewModel.presentationError)
        let rejectedAfterNotify = await walletClient.rejectedRequestURLs
        XCTAssertEqual(rejectedAfterNotify, [request])
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
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Presentation declined")
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

    @MainActor
    private func waitUntil(
        _ predicate: @escaping @MainActor () -> Bool,
        attempts: Int = 1_000
    ) async throws {
        for _ in 0..<attempts {
            if predicate() { return }
            await Task.yield()
        }
        XCTFail("Timed out waiting for wallet state")
    }
}
