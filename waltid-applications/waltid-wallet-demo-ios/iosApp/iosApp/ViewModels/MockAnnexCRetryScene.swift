#if DEBUG
import SwiftUI
import WalletDemoIdentityDocumentSupport
import WalletSDK

/// Hosts the provider's production view with a synthetic system request for UI testing.
@available(iOS 26.0, *)
struct MockAnnexCRetryScene: View {
    @StateObject private var request = MockAnnexCRequest()

    var body: some View {
        VStack {
            AnnexCReviewView(context: request, showDcApiPresentationPreview: false, makeWallet: { request })
            Text("Delivered responses: \(request.delivered)")
        }
    }
}

@MainActor
private final class MockAnnexCRequest: ObservableObject, AnnexCRequestContext, AnnexCPresentationWallet {
    @Published private(set) var delivered = 0
    nonisolated var verifiedOrigin: URL? { URL(string: "https://reader.example") }
    nonisolated var requestSnapshot: MobileDocumentRequestSnapshot {
        .init(presentments: [.init(documentRequestSets: [.init(requests: [
            .init(documentType: "org.iso.18013.5.1.mDL", namespaces: ["org.iso.18013.5.1": ["family_name"]])
        ])])])
    }

    nonisolated func cancelRequest() {}

    func sendResponse(_ build: @escaping @Sendable @concurrent (RawAnnexCRequest) async throws -> Data) async throws {
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
        .init(requestID: "retry", verifiedOrigin: verifiedOrigin, parsedRequest: parsedRequest, credentialOptions: [
            .init(queryID: "annexC.0", credentialID: "cred-1", format: "mso_mdoc", issuer: nil, subject: nil,
                  label: "Mobile Driving Licence", credentialDataJSON: "{}")
        ], readerTrust: .pendingRawRequest)
    }

    func submitAnnexCPresentation(requestID: String, verifiedOrigin: String, deviceRequestBase64URL: String,
                                  encryptionInfoBase64URL: String, selectedCredentialOptions: [PresentationCredentialSelection]) async throws -> DigitalCredentialResponse {
        throw NSError(domain: "MockAnnexC", code: 1, userInfo: [NSLocalizedDescriptionKey: "Direct response failed"])
    }

    func submitStatusReviewedAnnexCPresentation(reviewedStatus: [String: String], requestID: String, verifiedOrigin: String,
        deviceRequestBase64URL: String, encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]) async throws -> DigitalCredentialResponse {
        guard let status = try await cachedCredentialStatuses()?.credentials.first?.value,
              reviewedStatus == ["cred-1": status.presentation.advisoryRevision],
              selectedCredentialOptions.map(\.credentialID) == ["cred-1"] else {
            throw WalletError.internalFailure("Expected the selected credential and its reviewed revision")
        }
        return DigitalCredentialResponse(protocolIdentifier: "org-iso-mdoc", dataJSON: #"{"response":"Aw"}"#)
    }
}
#endif
