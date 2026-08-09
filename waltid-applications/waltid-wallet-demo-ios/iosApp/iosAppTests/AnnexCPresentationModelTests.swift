import Foundation
import WalletDemoIdentityDocumentSupport
import WalletDemoSharingUI
import WalletSDK
import XCTest

/// What the provider extension does with a request, without an iOS 26 request to do it with.
///
/// The Apple context is stubbed because Apple's own request type cannot be constructed in a test;
/// everything below it - the review the user sees, the selection rules, and which actions are
/// available - is the real orchestration the extension runs.
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

    private final class StubRequestContext: AnnexCRequestContext, @unchecked Sendable {
        private let origin: URL?
        private let lock = NSLock()
        private var counts = (cancel: 0, sendResponse: 0)

        var cancelCount: Int { lock.withLock { counts.cancel } }
        var sendResponseCount: Int { lock.withLock { counts.sendResponse } }

        init(verifiedOrigin: URL? = URL(string: "https://reader.example")) {
            origin = verifiedOrigin
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
        }
    }

    private struct StubWallet: AnnexCPresentationWallet {
        let preview: AnnexCPresentationPreview

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
            DigitalCredentialResponse(protocolIdentifier: "org-iso-mdoc", dataJSON: "{}")
        }
    }

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
