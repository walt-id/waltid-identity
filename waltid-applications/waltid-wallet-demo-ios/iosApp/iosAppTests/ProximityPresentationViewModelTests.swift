import Foundation
import XCTest
import WalletDemoSharingUI
import ZXingCpp
@testable import iosApp
@testable import WalletSDK

final class ProximityPresentationViewModelTests: XCTestCase {
    @MainActor
    func testStartObservesSessionAndLifecycleCancelsActiveExchange() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        await session.emit(.preparing(profile: .iso180135Edition2DIS2026))
        await session.emit(.engagementReady([.qr(payload: "mdoc:device-engagement")]))
        try await waitUntil { viewModel.qrPayload == "mdoc:device-engagement" }

        XCTAssertEqual(client.startCount, 1)
        let configuration = try XCTUnwrap(client.lastConfiguration)
        guard case .qrAndNFC(.negotiatedHandover) = configuration.engagement else {
            return XCTFail("The demo must prepare QR and NFC Negotiated Handover")
        }
        guard case let .conventional(retrieval) = configuration.retrieval,
              retrieval.bluetoothLowEnergy != nil,
              retrieval.nfc != nil else {
            return XCTFail("The demo must prepare BLE and conventional NFC retrieval")
        }
        XCTAssertTrue(viewModel.active)

        viewModel.handleLifecycleInterruption()
        try await waitUntilAsync { await session.actions == [.cancel] }

        await session.emit(.cancelled)
        try await waitUntil { viewModel.isTerminal }
        XCTAssertEqual(viewModel.sessionState, .cancelled)
    }

    @MainActor
    func testStartupCancellationClosesSessionReturnedByLateStartWithoutRestoringState() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session, suspendStart: true)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        viewModel.cancel()
        client.resumeStart()

        try await waitUntilAsync { await session.closeCount == 1 }
        XCTAssertFalse(viewModel.active)
        XCTAssertNil(viewModel.sessionState)
        XCTAssertFalse(viewModel.startupFailed)
    }

    @MainActor
    func testCombinedReviewDefaultsAndApprovalPreserveExactHolderSelections() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )
        let review = combinedProximityReview()
        let familyName = ProximityElementReference(
            namespace: "org.iso.18013.5.1",
            elementIdentifier: "family_name"
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.reviewRequired(review))
        try await waitUntil { viewModel.review == review }

        XCTAssertEqual(viewModel.selections.count, 2)
        XCTAssertEqual(
            viewModel.selections.first(where: { $0.requestIndex == 0 })?.credentialID,
            "payment-a"
        )
        XCTAssertTrue(viewModel.canApprove)

        viewModel.selectCredential(requestIndex: 0, credentialID: "payment-b")
        XCTAssertEqual(
            viewModel.selections.first(where: { $0.requestIndex == 0 })?.credentialID,
            "payment-b"
        )
        viewModel.toggleElement(requestIndex: 0, element: familyName)
        XCTAssertFalse(viewModel.canApprove)
        viewModel.toggleElement(requestIndex: 0, element: familyName)
        XCTAssertTrue(viewModel.canApprove)

        viewModel.setContinueAfterResponse(true)
        viewModel.approve()
        try await waitUntilAsync { await session.actions.count == 1 }
        let actions = await session.actions
        guard case .approve(let submission) = try XCTUnwrap(actions.first) else {
            return XCTFail("Expected an approval action")
        }
        XCTAssertEqual(submission.documents.count, 2)
        XCTAssertTrue(submission.continueAfterResponse)
        XCTAssertEqual(
            submission.documents.first(where: { $0.requestIndex == 0 })?.credentialID,
            "payment-b"
        )
        XCTAssertEqual(
            submission.documents.first(where: { $0.requestIndex == 1 })?.credentialID,
            "proof-credential"
        )

        await session.emit(.awaitingNextRequest(completedExchanges: 1))
        try await waitUntil { viewModel.sessionState == .awaitingNextRequest(completedExchanges: 1) }
        await session.emit(.reviewRequired(combinedProximityReview(exchange: 2)))
        try await waitUntil { viewModel.review?.exchange == 2 }

        XCTAssertFalse(viewModel.continueAfterResponse)
        XCTAssertEqual(
            viewModel.selections.first(where: { $0.requestIndex == 0 })?.credentialID,
            "payment-a"
        )
    }

    func testQRCodeRendererRoundTripsRealisticLongDeviceEngagementPayload() throws {
        let payload = "mdoc:" + String(repeating: "A7v9kQ2_x-", count: 120)
        let image = try XCTUnwrap(
            WalletQRCodeRenderer.proximityImage(payload: payload)
        )
        let cgImage = try XCTUnwrap(image.cgImage)
        let result = try XCTUnwrap(try ZXIBarcodeReader().read(cgImage).first)

        XCTAssertEqual(result.text, payload)
        XCTAssertEqual(result.bytes as Data, Data(payload.utf8))
    }

    func testQRCodeRendererRejectsUnsupportedProximityPayloadsAndOversizeText() {
        XCTAssertNil(WalletQRCodeRenderer.proximityImage(payload: "https://example.com"))
        XCTAssertNil(WalletQRCodeRenderer.proximityImage(payload: "mdoc:é"))
        XCTAssertNil(
            WalletQRCodeRenderer.proximityImage(
                payload: "mdoc:" + String(repeating: "A", count: 4_000)
            )
        )
    }

    @MainActor
    func testConfigurationProviderIsResolvedOncePerSession() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        var policy = ProximityPresentationReaderPolicy.allowAnonymousOrUntrusted
        var resolutionCount = 0
        let viewModel = ProximityPresentationViewModel(
            client: client,
            configurationProvider: {
                resolutionCount += 1
                return ProximityPresentationConfiguration(readerPolicy: policy)
            },
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        XCTAssertEqual(resolutionCount, 1)
        XCTAssertEqual(client.configurations.single?.readerPolicy, .allowAnonymousOrUntrusted)

        policy = .requireTrusted
        viewModel.start()
        XCTAssertEqual(client.startCount, 1)
        XCTAssertEqual(resolutionCount, 1)

        viewModel.dismiss()
        try await waitUntilAsync { await session.closeCount == 1 }
        viewModel.start()
        try await waitUntil { client.startCount == 2 }
        XCTAssertEqual(resolutionCount, 2)
        XCTAssertEqual(client.configurations.last?.readerPolicy, .requireTrusted)
    }
}

private func combinedProximityReview(exchange: Int = 1) -> ProximityPresentationReview {
    let familyName = ProximityRequestedElement(
        namespace: "org.iso.18013.5.1",
        elementIdentifier: "family_name",
        intentToRetain: true,
        satisfiesRequestedElements: []
    )
    let eligibility = ProximityRequestedElement(
        namespace: "org.waltid.example.proof",
        elementIdentifier: "eligible",
        intentToRetain: false,
        satisfiesRequestedElements: []
    )
    func credential(
        id: String,
        label: String,
        elements: [ProximityRequestedElement]
    ) -> ProximityCredentialOption {
        ProximityCredentialOption(
            credentialID: id,
            label: label,
            issuer: "Example issuer",
            validUntil: .distantFuture,
            deviceAuthentication: .signature,
            requestedElements: elements
        )
    }
    return ProximityPresentationReview(
        exchange: exchange,
        documents: [
            ProximityDocumentReview(
                requestIndex: 0,
                documentType: "org.waltid.example.payment",
                credentialOptions: [
                    credential(id: "payment-a", label: "Payment credential A", elements: [familyName]),
                    credential(id: "payment-b", label: "Payment credential B", elements: [familyName]),
                ]
            ),
            ProximityDocumentReview(
                requestIndex: 1,
                documentType: "org.waltid.example.proof",
                credentialOptions: [
                    credential(id: "proof-credential", label: "Proof of eligibility", elements: [eligibility])
                ]
            ),
        ],
        readerAuthentication: [],
        useCases: [],
        applicationAuthorizations: []
    )
}

@MainActor
private final class FakeProximityWalletClient: ProximityWalletClient {
    private let session: any DemoProximityPresentationSession
    private let suspendStart: Bool
    private var startContinuation: CheckedContinuation<Void, Never>?
    private(set) var startCount = 0
    private(set) var configurations: [ProximityPresentationConfiguration] = []
    var lastConfiguration: ProximityPresentationConfiguration? { configurations.last }

    init(session: any DemoProximityPresentationSession, suspendStart: Bool = false) {
        self.session = session
        self.suspendStart = suspendStart
    }

    func startProximityPresentation(
        configuration: ProximityPresentationConfiguration
    ) async throws -> any DemoProximityPresentationSession {
        startCount += 1
        configurations.append(configuration)
        if suspendStart {
            await withCheckedContinuation { startContinuation = $0 }
        }
        return session
    }

    func resumeStart() {
        let continuation = startContinuation
        startContinuation = nil
        continuation?.resume()
    }
}

private extension Collection {
    var single: Element? { count == 1 ? first : nil }
}

private actor FakeProximitySession: DemoProximityPresentationSession {
    nonisolated let states: AsyncStream<ProximityPresentationState>
    private let continuation: AsyncStream<ProximityPresentationState>.Continuation
    private(set) var actions: [ProximityPresentationAction] = []
    private(set) var closeCount = 0

    init() {
        var continuation: AsyncStream<ProximityPresentationState>.Continuation!
        states = AsyncStream { continuation = $0 }
        self.continuation = continuation
    }

    func emit(_ state: ProximityPresentationState) {
        continuation.yield(state)
    }

    func dispatch(_ action: ProximityPresentationAction) async throws -> ProximityPresentationActionResult {
        actions.append(action)
        return .accepted
    }

    func close() {
        closeCount += 1
        continuation.finish()
    }
}

@MainActor
private final class FakeProximityHostActionExecutor: ProximityHostActionExecutor {
    func perform(
        _ action: ProximityPresentationRemediationAction
    ) async -> ProximityPresentationHostActionResult {
        .completed
    }
}

@MainActor
private func waitUntil(
    timeout: TimeInterval = 2,
    condition: @escaping @MainActor () -> Bool
) async throws {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition() {
        if Date() >= deadline { XCTFail("Timed out waiting for condition"); return }
        try await Task.sleep(nanoseconds: 10_000_000)
    }
}

private func waitUntilAsync(
    timeout: TimeInterval = 2,
    condition: @escaping () async -> Bool
) async throws {
    let deadline = Date().addingTimeInterval(timeout)
    while !(await condition()) {
        if Date() >= deadline { XCTFail("Timed out waiting for condition"); return }
        try await Task.sleep(nanoseconds: 10_000_000)
    }
}
