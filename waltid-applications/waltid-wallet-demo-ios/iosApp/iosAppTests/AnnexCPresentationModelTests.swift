import Foundation
import WalletDemoIdentityDocumentSupport
import WalletDemoSharingUI
@testable import WalletSDK
import XCTest

/// What the provider extension does with a request, without an iOS 26 request to do it with.
///
/// The Apple context is stubbed because Apple's own request type cannot be constructed in a test;
/// everything below it is the real orchestration the extension runs.
final class AnnexCPresentationModelTests: XCTestCase {

    @MainActor
    func testTheProviderReviewOpensWithOneCredentialChosenForEachRequestedDocument() async {
        let model = AnnexCPresentationModel(
            context: StubRequestContext(),
            makeWallet: { StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"])) }
        )

        await model.prepare()

        XCTAssertNil(model.failure)
        let review = model.review
        XCTAssertNotNil(review, "A prepared request must produce a review rather than an empty screen")
        XCTAssertEqual(review?.request.requester?.verifiedOrigin, "https://reader.example")
        XCTAssertEqual(model.selection.credentials.map(\.credentialID), ["cred-mdl"])
        XCTAssertTrue(model.hasCompleteSelection, "Share is available once every requested document is answered")
    }

    @MainActor
    func testEveryRequestedDocumentMustBeAnsweredBeforeShareBecomesAvailable() async {
        let model = AnnexCPresentationModel(
            context: StubRequestContext(),
            makeWallet: {
                StubWallet(preview: Self.preview(
                    documentTypes: ["org.iso.18013.5.1.mDL", "eu.europa.ec.eudi.pid.1"]
                ))
            }
        )
        await model.prepare()

        XCTAssertEqual(model.selection.credentials.count, 2)
        XCTAssertTrue(model.hasCompleteSelection)

        guard let pid = model.selection.credentials.first(where: { $0.queryID == "annexC.1" }) else {
            return XCTFail("The second requested document was never answered, so the fixture is wrong")
        }
        model.toggleCredential(pid)

        XCTAssertFalse(
            model.hasCompleteSelection,
            "A partial response would answer a different request than the one the reader made"
        )
    }

    @MainActor
    func testDeselectingTheOnlyCredentialWithdrawsShareRatherThanSharingNothing() async {
        let model = AnnexCPresentationModel(
            context: StubRequestContext(),
            makeWallet: { StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"])) }
        )
        await model.prepare()
        guard let chosen = model.selection.credentials.first else {
            return XCTFail("The review opened with nothing selected, so there is no deselection to test")
        }

        model.toggleCredential(chosen)

        XCTAssertTrue(model.selection.credentials.isEmpty)
        XCTAssertFalse(model.hasCompleteSelection)
    }

    @MainActor
    func testCancellingTellsThePlatformWithoutAskingForTheRawRequest() async {
        let context = StubRequestContext()
        let model = AnnexCPresentationModel(
            context: context,
            makeWallet: { StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"])) }
        )
        await model.prepare()

        model.cancel()

        XCTAssertEqual(context.cancelCount, 1)
        XCTAssertEqual(
            context.sendResponseCount,
            0,
            "Cancelling must not reach the stage that releases the raw request"
        )
    }

    @MainActor
    func testSharingHandsThePlatformTheSealedBytesBuiltFromTheRawRequestAndTheChosenCredentials() async {
        let context = StubRequestContext(rawRequest: Self.rawRequestJSON)
        let wallet = StubWallet(
            preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL", "eu.europa.ec.eudi.pid.1"]),
            responseJSON: #"{"response":"-_--An8"}"#
        )
        let model = AnnexCPresentationModel(context: context, makeWallet: { wallet })
        await model.prepare()

        await model.submit()

        XCTAssertNil(model.failure)
        XCTAssertEqual(context.sendResponseCount, 1, "The raw request may be requested exactly once")
        guard let submitted = wallet.submitted else {
            return XCTFail("The platform released the raw request but the wallet was never asked to seal a response")
        }
        // The request ID and origin come from stage 1's retained preview, not from the raw request:
        // that is what binds the sealed response to the request the user actually consented to.
        XCTAssertEqual(submitted.requestID, "request-1")
        XCTAssertEqual(submitted.verifiedOrigin, "https://reader.example")
        XCTAssertEqual(submitted.deviceRequestBase64URL, "ZGV2aWNlLXJlcXVlc3QtYnl0ZXM")
        XCTAssertEqual(submitted.encryptionInfoBase64URL, "ZW5jcnlwdGlvbi1pbmZvLWJ5dGVz")
        XCTAssertEqual(
            submitted.selectedCredentialOptions.map(\.queryID),
            ["annexC.0", "annexC.1"],
            "Documents have to be submitted in the order the request listed them, not in the selection's order"
        )
        XCTAssertEqual(submitted.selectedCredentialOptions.map(\.credentialID), ["cred-mdl", "cred-1"])
        XCTAssertEqual(
            context.sentResponse,
            Data([0xfb, 0xff, 0xbe, 0x02, 0x7f]),
            "Apple gets the decoded sealed bytes, not the wallet's base64url envelope"
        )
    }

    @MainActor
    func testAFailedSealLeavesTheRequestAnsweredNowhereAndTheReviewUsableAgain() async {
        let context = StubRequestContext(rawRequest: Self.rawRequestJSON)
        let wallet = StubWallet(
            preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"]),
            submitFailure: StubWalletFailure()
        )
        let model = AnnexCPresentationModel(context: context, makeWallet: { wallet })
        await model.prepare()

        await model.submit()

        XCTAssertEqual(model.failure, StubWalletFailure().localizedDescription)
        XCTAssertNil(context.sentResponse, "A wallet that could not seal a response must not hand the platform any bytes")
        XCTAssertFalse(
            model.isSubmitting,
            "Staying in the submitting state would leave the user with a spinner and no way to retry or cancel"
        )
    }

    @MainActor
    func testProviderSubmitsTheCapturedStatusRevisionWithoutUsingTheOrdinaryPath() async throws {
        let status = try WalletVisualFixtures().credentialStatus("revoked_current")
        let snapshot = CredentialStatusSnapshot(evaluatedAt: Date(), credentials: [
            CredentialStatusRecord(credentialID: "cred-mdl", storeIndex: 0, value: status),
        ], refreshingCredentialIDs: [])
        let context = StubRequestContext(rawRequest: Self.rawRequestJSON)
        let wallet = StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"]),
            responseJSON: #"{"response":"-_--An8"}"#, statusSnapshot: snapshot)
        let model = AnnexCPresentationModel(context: context, makeWallet: { wallet })
        await model.prepare()
        XCTAssertEqual(model.credentialStatusReadState, .available)
        XCTAssertEqual(model.credentialStatuses["cred-mdl"], status)
        let revisions = ["cred-mdl": status.presentation.advisoryRevision]

        await model.submit(reviewedStatus: revisions)

        XCTAssertNil(model.failure)
        XCTAssertEqual(wallet.reviewedStatus, revisions)
        XCTAssertEqual(wallet.ordinarySubmissions, 0, "The provider must not discard the reviewed binding")
        XCTAssertEqual(wallet.submitted?.selectedCredentialOptions.map(\.credentialID), ["cred-mdl"])
        XCTAssertEqual(context.sentResponse, Data([0xfb, 0xff, 0xbe, 0x02, 0x7f]))
        XCTAssertEqual(wallet.closed, 1)
    }

    @MainActor
    func testStatusChangeWithAnUnreadableRereadReturnsTheProviderToReviewWithoutSealing() async throws {
        let status = try WalletVisualFixtures().credentialStatus("revoked_current")
        let snapshot = CredentialStatusSnapshot(evaluatedAt: Date(), credentials: [
            CredentialStatusRecord(credentialID: "cred-mdl", storeIndex: 0, value: status),
        ], refreshingCredentialIDs: [])
        let context = StubRequestContext(rawRequest: Self.rawRequestJSON)
        let wallet = StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"]),
            submitFailure: WalletError.credentialStatusReviewChanged,
            statusSnapshot: snapshot, failStatusReread: true)
        let model = AnnexCPresentationModel(context: context, makeWallet: { wallet })
        await model.prepare()

        await model.submit(reviewedStatus: ["cred-mdl": status.presentation.advisoryRevision])

        XCTAssertFalse(model.isSubmitting)
        XCTAssertTrue(model.hasCompleteSelection)
        XCTAssertEqual(model.credentialStatusReadState, .unavailable)
        XCTAssertEqual(model.credentialStatuses["cred-mdl"], status, "A failed local read retains earlier evidence")
        XCTAssertEqual(model.statusReviewNotice, "Status changed, but its latest details are unavailable. Try again.")
        XCTAssertNil(context.sentResponse)
        XCTAssertEqual(wallet.ordinarySubmissions, 0)
        model.cancel()
        XCTAssertEqual(context.cancelCount, 1, "The recovered review must remain dismissible")
    }

    @MainActor
    func testAMissingVerifiedOriginFailsTheRequestRatherThanReviewingAnAnonymousCaller() async {
        let model = AnnexCPresentationModel(
            context: StubRequestContext(verifiedOrigin: nil),
            makeWallet: { StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"])) }
        )

        await model.prepare()

        XCTAssertNotNil(model.failure)
        XCTAssertNil(model.review, "There is nothing safe to review when the wallet cannot bind a response to an origin")
    }

    @MainActor
    func testCancellationDuringPreparationCannotRestoreAReviewOrAutoSubmit() async {
        for (pauseAtPreview, cancelTask) in [(false, false), (false, true), (true, false)] {
            let started = expectation(description: "Preparation suspended")
            let gate = PreparationGate(started: started)
            let context = StubRequestContext()
            let wallet = StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"]),
                                    previewGate: pauseAtPreview ? gate : nil)
            let model = AnnexCPresentationModel(context: context, makeWallet: {
                if !pauseAtPreview { await gate.wait() }
                return wallet
            })
            let preparation = Task { await model.prepare() }
            await fulfillment(of: [started], timeout: 5)
            await model.prepare() // A repeated SwiftUI task must not open a second request.
            if cancelTask { preparation.cancel() }
            else { model.cancel(); model.cancel() }
            await gate.release()
            await preparation.value
            await model.submit() // The preview-off path also cannot release a cancelled request.
            XCTAssertNil(model.review)
            XCTAssertNil(model.failure)
            XCTAssertEqual(context.cancelCount, 1)
            XCTAssertEqual(context.sendResponseCount, 0)
            let closed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in wallet.closed == 1 }, object: nil)
            let closure = await XCTWaiter.fulfillment(of: [closed], timeout: 5)
            XCTAssertEqual(closure, .completed, "Cancellation must close even a wallet opened after the request ended")
        }
    }

    // MARK: - Stubs

    /// Stands in for Apple's context, and for the platform's half of the two-stage flow.
    ///
    /// ``sendResponse(_:)`` runs the closure with `rawRequest` and keeps what it returned: the wallet only
    /// sees the `DeviceRequest` and `EncryptionInfo` inside that closure, so counting calls alone would
    /// leave the path from released request to sealed bytes unexercised.
    private final class StubRequestContext: AnnexCRequestContext, @unchecked Sendable {
        private let origin: URL?
        private let rawRequest: Data?
        private let lock = NSLock()
        private var counts = (cancel: 0, sendResponse: 0)
        private var response: Data?

        var cancelCount: Int { lock.withLock { counts.cancel } }
        var sendResponseCount: Int { lock.withLock { counts.sendResponse } }
        /// Bytes the model handed back, or `nil` if it never produced any.
        var sentResponse: Data? { lock.withLock { response } }

        init(verifiedOrigin: URL? = URL(string: "https://reader.example"), rawRequest: Data? = nil) {
            origin = verifiedOrigin
            self.rawRequest = rawRequest
        }

        var requestSnapshot: MobileDocumentRequestSnapshot {
            MobileDocumentRequestSnapshot(presentments: [
                MobileDocumentPresentmentSnapshot(documentRequestSets: [
                    MobileDocumentRequestSetSnapshot(requests: [
                        AnnexCDocumentRequest(
                            documentType: "org.iso.18013.5.1.mDL",
                            namespaces: ["org.iso.18013.5.1": ["family_name"]]
                        ),
                    ]),
                ]),
            ])
        }

        var verifiedOrigin: URL? { origin }

        func cancelRequest() {
            lock.withLock { counts.cancel += 1 }
        }

        // Annotated explicitly because this target compiles with approachable concurrency while the
        // support package does not, so the inferred isolation of the closure would not match.
        func sendResponse(
            _ build: @escaping @Sendable @concurrent (RawAnnexCRequest) async throws -> Data
        ) async throws {
            lock.withLock { counts.sendResponse += 1 }
            guard let rawRequest else { return }
            let sealed = try await build(RawAnnexCRequest(data: rawRequest))
            lock.withLock { response = sealed }
        }
    }

    /// The wallet's failure, distinguishable in an assertion from any error the orchestration invents.
    private struct StubWalletFailure: LocalizedError {
        var errorDescription: String? { "Stub wallet refused to seal a response" }
    }

    /// Everything the wallet was asked to seal, kept so stage 2's arguments can be asserted exactly.
    private struct SubmittedPresentation: Sendable {
        let requestID: String
        let verifiedOrigin: String
        let deviceRequestBase64URL: String
        let encryptionInfoBase64URL: String
        let selectedCredentialOptions: [PresentationCredentialSelection]
    }

    private final class StubWallet: AnnexCPresentationWallet, @unchecked Sendable {
        private let preview: AnnexCPresentationPreview
        private let responseJSON: String
        private let submitFailure: (any Error)?
        private let previewGate: PreparationGate?
        private let statusSnapshot: CredentialStatusSnapshot?
        private let failStatusReread: Bool
        private let lock = NSLock()
        private var recorded: SubmittedPresentation?
        private var recordedStatus: [String: String]?
        private var statusReads = 0
        private var ordinaryCount = 0
        private var closeCount = 0

        /// Stage 2's arguments, or `nil` if the wallet was never asked to seal anything.
        var submitted: SubmittedPresentation? { lock.withLock { recorded } }
        var reviewedStatus: [String: String]? { lock.withLock { recordedStatus } }
        var ordinarySubmissions: Int { lock.withLock { ordinaryCount } }
        var closed: Int { lock.withLock { closeCount } }

        init(
            preview: AnnexCPresentationPreview,
            responseJSON: String = "{}",
            submitFailure: (any Error)? = nil,
            previewGate: PreparationGate? = nil,
            statusSnapshot: CredentialStatusSnapshot? = nil,
            failStatusReread: Bool = false
        ) {
            self.preview = preview
            self.responseJSON = responseJSON
            self.submitFailure = submitFailure
            self.previewGate = previewGate
            self.statusSnapshot = statusSnapshot
            self.failStatusReread = failStatusReread
        }

        func cachedCredentialStatuses() async throws -> CredentialStatusSnapshot? {
            let shouldFail = lock.withLock { statusReads += 1; return failStatusReread && statusReads > 1 }
            if shouldFail { throw StubWalletFailure() }
            return statusSnapshot
        }

        func closeStatusWallet() async throws { lock.withLock { closeCount += 1 } }

        func previewAnnexCPresentation(
            parsedRequest: AnnexCParsedRequest,
            verifiedOrigin: String,
            selectedRegistryEntryIDs: [String]
        ) async throws -> AnnexCPresentationPreview {
            await previewGate?.wait()
            return preview
        }

        func submitAnnexCPresentation(
            requestID: String,
            verifiedOrigin: String,
            deviceRequestBase64URL: String,
            encryptionInfoBase64URL: String,
            selectedCredentialOptions: [PresentationCredentialSelection]
        ) async throws -> DigitalCredentialResponse {
            lock.withLock { ordinaryCount += 1 }
            return try seal(requestID: requestID, verifiedOrigin: verifiedOrigin,
                deviceRequestBase64URL: deviceRequestBase64URL, encryptionInfoBase64URL: encryptionInfoBase64URL,
                selectedCredentialOptions: selectedCredentialOptions)
        }

        func submitStatusReviewedAnnexCPresentation(reviewedStatus: [String: String], requestID: String, verifiedOrigin: String,
            deviceRequestBase64URL: String, encryptionInfoBase64URL: String,
            selectedCredentialOptions: [PresentationCredentialSelection]) async throws -> DigitalCredentialResponse {
            lock.withLock { recordedStatus = reviewedStatus }
            return try seal(requestID: requestID, verifiedOrigin: verifiedOrigin,
                deviceRequestBase64URL: deviceRequestBase64URL, encryptionInfoBase64URL: encryptionInfoBase64URL,
                selectedCredentialOptions: selectedCredentialOptions)
        }

        private func seal(requestID: String, verifiedOrigin: String, deviceRequestBase64URL: String,
            encryptionInfoBase64URL: String, selectedCredentialOptions: [PresentationCredentialSelection]) throws -> DigitalCredentialResponse {
            lock.withLock {
                recorded = SubmittedPresentation(
                    requestID: requestID,
                    verifiedOrigin: verifiedOrigin,
                    deviceRequestBase64URL: deviceRequestBase64URL,
                    encryptionInfoBase64URL: encryptionInfoBase64URL,
                    selectedCredentialOptions: selectedCredentialOptions
                )
            }
            if let submitFailure { throw submitFailure }
            return DigitalCredentialResponse(protocolIdentifier: "org-iso-mdoc", dataJSON: responseJSON)
        }
    }

    /// What the platform releases after consent: base64url `DeviceRequest` and `DCAPIEncryptionInfo`.
    private static let rawRequestJSON = Data(#"""
    {
      "deviceRequest": "ZGV2aWNlLXJlcXVlc3QtYnl0ZXM",
      "encryptionInfo": "ZW5jcnlwdGlvbi1pbmZvLWJ5dGVz"
    }
    """#.utf8)

    private static func preview(
        documentTypes: [String],
        credentialOptions: [PresentationCredentialOption]? = nil
    ) -> AnnexCPresentationPreview {
        AnnexCPresentationPreview(
            requestID: "request-1",
            verifiedOrigin: "https://reader.example",
            parsedRequest: AnnexCParsedRequest(
                documents: documentTypes.map {
                    AnnexCDocumentRequest(documentType: $0, namespaces: ["org.iso.18013.5.1": ["family_name"]])
                }
            ),
            credentialOptions: credentialOptions ?? documentTypes.enumerated().map { index, _ in
                option(queryID: "annexC.\(index)", credentialID: index == 0 ? "cred-mdl" : "cred-\(index)")
            },
            readerTrust: .pendingRawRequest
        )
    }

    private static func option(queryID: String, credentialID: String) -> PresentationCredentialOption {
        PresentationCredentialOption(
            queryID: queryID,
            credentialID: credentialID,
            format: "mso_mdoc",
            issuer: "https://issuer.example",
            subject: "did:example:holder",
            label: "Mobile Driving Licence",
            credentialDataJSON: #"{"org.iso.18013.5.1":{"family_name":"Doe"}}"#,
            disclosures: [
                PresentationDisclosure(
                    path: "org.iso.18013.5.1.family_name",
                    name: "family_name",
                    valueJSON: "\"Doe\"",
                    displayValue: "Doe",
                    selectivelyDisclosable: true,
                    required: true,
                    selectable: false
                ),
            ]
        )
    }
}

private actor PreparationGate {
    private let started: XCTestExpectation
    private var continuation: CheckedContinuation<Void, Never>?
    private var released = false

    init(started: XCTestExpectation) { self.started = started }
    func wait() async {
        started.fulfill()
        if released { return }
        await withCheckedContinuation { continuation = $0 }
    }
    func release() {
        released = true
        continuation?.resume()
        continuation = nil
    }
}
