import ExtensionKit
import Foundation
import IdentityDocumentServices
import IdentityDocumentServicesUI
import SwiftUI
import WalletSDK

private enum ProviderConfiguration {
    static let appGroupIdentifier = IdentityDocumentSharedConfiguration.appGroupIdentifier

    static var keychainAccessGroup: String {
        guard let value = IdentityDocumentSharedConfiguration.keychainAccessGroup,
              !value.isEmpty else {
            preconditionFailure("The document provider requires a resolved shared Keychain access group")
        }
        return value
    }

    static var walletConfiguration: WalletConfiguration {
        WalletConfiguration(
            crossProcessAccess: WalletCrossProcessAccess(
                appGroupIdentifier: appGroupIdentifier,
                keychainAccessGroup: keychainAccessGroup
            )
        )
    }
}

@main
struct WaltIdentityDocumentProvider: IdentityDocumentProvider {
    var body: some IdentityDocumentRequestScene {
        ISO18013MobileDocumentRequestScene { context in
            AnnexCConsentView(context: context)
        }
    }

    func performRegistrationUpdates() async {
        // IdentityDocumentServices invokes this method again when registration state changes.
        try? await IdentityDocumentRegistrationCoordinator.update()
    }
}

private struct AnnexCConsentView: View {
    let context: ISO18013MobileDocumentRequestContext

    @State private var preview: AnnexCPresentationPreview?
    @State private var wallet: Wallet?
    @State private var failure: String?
    @State private var isSubmitting = false
    @State private var selectedCredentialIDsByQuery: [String: String] = [:]

    var body: some View {
        NavigationStack {
            Group {
                if let failure {
                    ContentUnavailableView("Unable to present", systemImage: "exclamationmark.shield", description: Text(failure))
                } else if let preview {
                    List {
                        Section("Requesting website") {
                            Text(preview.verifiedOrigin)
                        }
                        Section("Requested information") {
                            ForEach(Array(preview.parsedRequest.documents.enumerated()), id: \.offset) { _, document in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(document.documentType).font(.headline)
                                    Text(document.namespaces.values.flatMap { $0 }.sorted().joined(separator: ", "))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        Section("Credentials") {
                            ForEach(queryIDs(in: preview), id: \.self) { queryID in
                                let options = credentialOptions(for: queryID, in: preview)
                                Picker(
                                    "Credential",
                                    selection: Binding<String?>(
                                        get: { selectedCredentialIDsByQuery[queryID] },
                                        set: { selectedCredentialIDsByQuery[queryID] = $0 }
                                    )
                                ) {
                                    Text("Choose a credential").tag(nil as String?)
                                    ForEach(options) { option in
                                        Text(credentialTitle(option)).tag(option.credentialID as String?)
                                    }
                                }
                                if let credentialID = selectedCredentialIDsByQuery[queryID],
                                   let selected = options.first(where: { $0.credentialID == credentialID }) {
                                    Text(credentialDetail(selected))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        Section("Reader trust") {
                            Text(readerTrustDescription(preview.readerTrust))
                        }
                    }
                } else {
                    ProgressView("Preparing request")
                }
            }
            .navigationTitle("Share documents")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { context.cancel() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Share") { submit() }
                        .disabled(preview == nil || !hasCompleteSelection || isSubmitting)
                }
            }
            .task { await prepare() }
        }
    }

    private func prepare() async {
        guard preview == nil, failure == nil else { return }
        do {
            guard let origin = context.requestingWebsiteOrigin else {
                throw ProviderFailure.missingVerifiedOrigin
            }
            let parsed = try parse(context.request)
            let wallet = try await Wallet(configuration: ProviderConfiguration.walletConfiguration)
            _ = try await wallet.bootstrap()
            self.wallet = wallet
            let preparedPreview = try await wallet.previewAnnexCPresentation(
                parsedRequest: parsed,
                verifiedOrigin: try canonicalOrigin(origin)
            )
            preview = preparedPreview
            selectedCredentialIDsByQuery = Dictionary(
                uniqueKeysWithValues: queryIDs(in: preparedPreview).compactMap { queryID in
                    let options = credentialOptions(for: queryID, in: preparedPreview)
                    return options.count == 1 ? (queryID, options[0].credentialID) : nil
                }
            )
        } catch {
            failure = error.localizedDescription
        }
    }

    private func submit() {
        guard let wallet, let preview else { return }
        let selections = queryIDs(in: preview).compactMap { queryID -> PresentationCredentialSelection? in
            guard let credentialID = selectedCredentialIDsByQuery[queryID],
                  let option = credentialOptions(for: queryID, in: preview).first(where: {
                      $0.credentialID == credentialID
                  }) else { return nil }
            return option.selection
        }
        guard selections.count == queryIDs(in: preview).count else {
            failure = ProviderFailure.missingCredentialSelection.localizedDescription
            return
        }
        isSubmitting = true
        Task {
            do {
                try await context.sendResponse { rawRequest in
                    let raw = try RawAnnexCRequest(data: rawRequest.requestData)
                    let response = try await wallet.submitAnnexCPresentation(
                        requestID: preview.requestID,
                        verifiedOrigin: preview.verifiedOrigin,
                        deviceRequestBase64URL: raw.deviceRequest,
                        encryptionInfoBase64URL: raw.encryptionInfo,
                        selectedCredentialOptions: selections
                    )
                    let responseObject = try JSONSerialization.jsonObject(
                        with: Data(response.dataJSON.utf8)
                    ) as? [String: Any]
                    guard let encodedResponse = responseObject?["response"] as? String,
                          let encryptedResponse = Data(base64URLEncoded: encodedResponse) else {
                        throw ProviderFailure.invalidResponseEncoding
                    }
                    return ISO18013MobileDocumentResponse(responseData: encryptedResponse)
                }
            } catch {
                failure = error.localizedDescription
                isSubmitting = false
            }
        }
    }

    private var hasCompleteSelection: Bool {
        guard let preview else { return false }
        return queryIDs(in: preview).allSatisfy { queryID in
            guard let credentialID = selectedCredentialIDsByQuery[queryID] else { return false }
            return credentialOptions(for: queryID, in: preview).contains { $0.credentialID == credentialID }
        }
    }

    private func queryIDs(in preview: AnnexCPresentationPreview) -> [String] {
        Array(Set(preview.credentialOptions.map(\.queryID))).sorted()
    }

    private func credentialOptions(
        for queryID: String,
        in preview: AnnexCPresentationPreview
    ) -> [PresentationCredentialOption] {
        preview.credentialOptions.filter { $0.queryID == queryID }
    }

    private func credentialTitle(_ option: PresentationCredentialOption) -> String {
        option.label ?? option.issuer ?? "Credential"
    }

    private func credentialDetail(_ option: PresentationCredentialOption) -> String {
        [option.issuer, option.subject, option.credentialID]
            .compactMap { $0 }
            .joined(separator: " · ")
    }

    private func parse(_ request: ISO18013MobileDocumentRequest) throws -> AnnexCParsedRequest {
        var documents: [AnnexCDocumentRequest] = []
        for presentment in request.presentmentRequests {
            guard presentment.documentRequestSets.count == 1,
                  let requestSet = presentment.documentRequestSets.first else {
                throw ProviderFailure.alternativeRequestSetsUnsupported
            }
            documents.append(contentsOf: requestSet.requests.map { document in
                AnnexCDocumentRequest(
                    documentType: document.documentType,
                    namespaces: document.namespaces.mapValues { elements in
                        elements.keys.sorted()
                    }
                )
            })
        }
        guard !documents.isEmpty else { throw ProviderFailure.emptyRequest }
        return AnnexCParsedRequest(documents: documents)
    }

    private func canonicalOrigin(_ url: URL) throws -> String {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased() else {
            throw ProviderFailure.missingVerifiedOrigin
        }
        components.scheme = scheme
        components.host = host
        components.path = ""
        components.query = nil
        components.fragment = nil
        if (scheme == "https" && components.port == 443) || (scheme == "http" && components.port == 80) {
            components.port = nil
        }
        guard let origin = components.string else { throw ProviderFailure.missingVerifiedOrigin }
        return origin
    }

    private func readerTrustDescription(_ trust: ReaderTrust) -> String {
        switch trust {
        case .notApplicable: return "Not applicable"
        case .unverified(let reason): return "Unverified: \(reason)"
        case .trusted(let subject): return "Trusted: \(subject)"
        }
    }
}

private struct RawAnnexCRequest: Decodable {
    let deviceRequest: String
    let encryptionInfo: String

    init(data: Data) throws {
        self = try JSONDecoder().decode(Self.self, from: data)
    }
}

private extension Data {
    init?(base64URLEncoded value: String) {
        var normalized = value.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        normalized.append(String(repeating: "=", count: (4 - normalized.count % 4) % 4))
        self.init(base64Encoded: normalized)
    }
}

private enum ProviderFailure: LocalizedError {
    case alternativeRequestSetsUnsupported
    case emptyRequest
    case invalidResponseEncoding
    case missingCredentialSelection
    case missingVerifiedOrigin

    var errorDescription: String? {
        switch self {
        case .alternativeRequestSetsUnsupported: return "Alternative document request sets are not supported"
        case .emptyRequest: return "The request does not contain any documents"
        case .invalidResponseEncoding: return "The encrypted response could not be encoded"
        case .missingCredentialSelection: return "Choose one credential for every requested document"
        case .missingVerifiedOrigin: return "IdentityDocumentServices did not assert a website origin"
        }
    }
}
