#if DEBUG
import SwiftUI
import WalletDemoIdentityDocumentSupport
import WalletSDK

/// Hosts the provider's production view with a synthetic system request for UI testing.
@available(iOS 26.0, *)
struct MockAnnexCScene: View {
    @StateObject private var request = MockAnnexCRequest()

    var body: some View {
        VStack {
            AnnexCReviewView(context: request, showDcApiPresentationPreview: false, makeWallet: { request })
            Text("Response calls: \(request.responseCalls)")
            Text("Cancelled: \(request.cancelled ? "yes" : "no")")
            Text("Delivered responses: \(request.delivered)")
        }
    }
}

@MainActor
private final class MockAnnexCRequest: ObservableObject, AnnexCRequestContext, AnnexCPresentationWallet {
    @Published private(set) var responseCalls = 0
    @Published private(set) var cancelled = false
    @Published private(set) var delivered = 0
    nonisolated var verifiedOrigin: URL? { URL(string: "https://reader.example") }
    nonisolated var requestSnapshot: MdocRequestSnapshot {
        .init(presentments: [.init(documentRequestSets: [.init(requests: [
            .init(documentType: "org.iso.18013.5.1.mDL", namespaces: ["org.iso.18013.5.1": ["family_name"]])
        ])])])
    }

    nonisolated func cancelRequest() { Task { @MainActor in cancelled = true } }

    func sendResponse(_ build: @escaping @Sendable @concurrent (RawAnnexCRequest) async throws -> Data) async throws {
        responseCalls += 1
        let raw = Data(#"{"deviceRequest":"AQ","encryptionInfo":"Ag"}"#.utf8)
        let sealed = try await build(RawAnnexCRequest(data: raw))
        guard sealed == Data([3]) else { throw WalletError.internalFailure("Unexpected response") }
        delivered += 1
    }

    func cachedCredentialStatuses() async throws -> CredentialStatusSnapshot? {
        MockWalletClient.uiTestCredentialStatusSnapshot(ProcessInfo.processInfo.environment["E2E_MOCK_STATUS_JSON"])
    }

    func previewAnnexCPresentation(parsedRequest: AnnexCParsedRequest, verifiedOrigin: String,
                                   selectedRegistryEntryIDs: [String]) async throws -> AnnexCPresentationPreview {
        let credential = Credential(id: "cred-1", format: "mso_mdoc", credentialDataJSON: "{}", label: "Mobile Driving Licence")
        let option = try PresentationCredentialOption(queryID: "annexC.0", credential: credential, disclosures: [
            .init(path: "family_name", valueJSON: #""Example""#, disposition: .requiredSelective,
                  name: "Family name", displayValue: "Example")
        ])
        return AnnexCPresentationPreview(requestID: "annex-c-test", verifiedOrigin: verifiedOrigin,
            parsedRequest: parsedRequest, credentialOptions: [option], readerTrust: .pendingRawRequest)
    }

    func submitAnnexCPresentation(_ submission: AnnexCSubmission) async throws -> DigitalCredentialResponse {
        if ProcessInfo.processInfo.environment["E2E_ANNEX_C_SCENARIO"] == "retry" {
            throw NSError(domain: "MockAnnexC", code: 1, userInfo: [NSLocalizedDescriptionKey: "Direct response failed"])
        }
        return DigitalCredentialResponse(protocolIdentifier: "org-iso-mdoc", dataJSON: #"{"response":"Aw"}"#)
    }

    func submitStatusReviewedAnnexCPresentation(_ submission: AnnexCSubmission, reviewedStatus: [String: String]) async throws -> DigitalCredentialResponse {
        guard let status = try await cachedCredentialStatuses()?.credentials.first?.value,
              reviewedStatus == ["cred-1": status.presentation.advisoryRevision],
              submission.selectedCredentialOptions.map(\.credentialID) == ["cred-1"] else {
            throw WalletError.internalFailure("Expected the selected credential and its reviewed revision")
        }
        return DigitalCredentialResponse(protocolIdentifier: "org-iso-mdoc", dataJSON: #"{"response":"Aw"}"#)
    }
}
#endif
