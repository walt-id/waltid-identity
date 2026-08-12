import Foundation
import WalletDemoIdentityDocumentSupport
import WalletDemoSharingUI
import WalletSDK
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
    func testAMissingVerifiedOriginFailsTheRequestRatherThanReviewingAnAnonymousCaller() async {
        let model = AnnexCPresentationModel(
            context: StubRequestContext(verifiedOrigin: nil),
            makeWallet: { StubWallet(preview: Self.preview(documentTypes: ["org.iso.18013.5.1.mDL"])) }
        )

        await model.prepare()

        XCTAssertNotNil(model.failure)
        XCTAssertNil(model.review, "There is nothing safe to review when the wallet cannot bind a response to an origin")
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
        private let lock = NSLock()
        private var recorded: SubmittedPresentation?

        /// Stage 2's arguments, or `nil` if the wallet was never asked to seal anything.
        var submitted: SubmittedPresentation? { lock.withLock { recorded } }

        init(
            preview: AnnexCPresentationPreview,
            responseJSON: String = "{}",
            submitFailure: (any Error)? = nil
        ) {
            self.preview = preview
            self.responseJSON = responseJSON
            self.submitFailure = submitFailure
        }

        func previewAnnexCPresentation(
            parsedRequest: AnnexCParsedRequest,
            verifiedOrigin: String,
            selectedRegistryEntryIDs: [String]
        ) async throws -> AnnexCPresentationPreview {
            preview
        }

        func submitAnnexCPresentation(
            requestID: String,
            verifiedOrigin: String,
            deviceRequestBase64URL: String,
            encryptionInfoBase64URL: String,
            selectedCredentialOptions: [PresentationCredentialSelection]
        ) async throws -> DigitalCredentialResponse {
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
