import Foundation
import OSLog
import WalletSDK

/// Wallet operations the Annex C two-stage flow needs.
///
/// A protocol rather than a concrete `Wallet` so the orchestration below - which is where the
/// selection and staging rules live - can be exercised without a database, a Keychain, or a device.
public protocol AnnexCPresentationWallet: Sendable {
    /// Stage 1: retain the pre-consent request and report what could satisfy it.
    func previewAnnexCPresentation(
        parsedRequest: AnnexCParsedRequest,
        verifiedOrigin: String,
        selectedRegistryEntryIDs: [String]
    ) async throws -> AnnexCPresentationPreview

    /// Stage 2: match the raw request against the retained preview and seal the response.
    func submitAnnexCPresentation(
        requestID: String,
        verifiedOrigin: String,
        deviceRequestBase64URL: String,
        encryptionInfoBase64URL: String,
        selectedCredentialOptions: [PresentationCredentialSelection]
    ) async throws -> DigitalCredentialResponse
}

extension Wallet: AnnexCPresentationWallet {}

/// Drives one Annex C presentation from Apple's request to the sealed response.
///
/// Both demos share this so that consent, selection, and the two-stage submission behave identically;
/// only the SwiftUI around it differs. `@MainActor` because it is the state backing a view.
@MainActor
public final class AnnexCPresentationModel: ObservableObject {
    /// Consent state once the wallet has evaluated the request; `nil` while preparing.
    @Published public private(set) var preview: AnnexCPresentationPreview?
    /// User-facing failure text, if the flow cannot continue.
    @Published public private(set) var failure: String?
    /// Whether a response is being built and sealed.
    @Published public private(set) var isSubmitting = false
    /// Chosen credential per DCQL query ID.
    @Published public var selectedCredentialIDsByQuery: [String: String] = [:]

    private let context: any AnnexCRequestContext
    private let makeWallet: @Sendable () async throws -> any AnnexCPresentationWallet
    private let log = Logger(subsystem: "id.walt.wallet.identity-document", category: "presentation")
    private var wallet: (any AnnexCPresentationWallet)?

    /// Creates a presentation model.
    ///
    /// - Parameters:
    ///   - context: Apple's request context, or a stand-in in tests.
    ///   - makeWallet: Opens the shared wallet. Deferred rather than injected as a value because the
    ///     extension must not touch the encrypted database until it has a request to serve.
    public nonisolated init(
        context: any AnnexCRequestContext,
        makeWallet: @escaping @Sendable () async throws -> any AnnexCPresentationWallet
    ) {
        self.context = context
        self.makeWallet = makeWallet
    }

    /// DCQL query IDs the user must decide on, in stable order.
    public var queryIDs: [String] {
        guard let preview else { return [] }
        return Array(Set(preview.credentialOptions.map(\.queryID))).sorted()
    }

    /// Whether every requested document has a credential chosen for it.
    public var hasCompleteSelection: Bool {
        guard preview != nil else { return false }
        return queryIDs.allSatisfy { queryID in
            guard let credentialID = selectedCredentialIDsByQuery[queryID] else { return false }
            return options(for: queryID).contains { $0.credentialID == credentialID }
        }
    }

    /// Credentials that satisfy one query.
    public func options(for queryID: String) -> [PresentationCredentialOption] {
        preview?.credentialOptions.filter { $0.queryID == queryID } ?? []
    }

    /// Runs stage 1: parses the request, opens the shared wallet, and builds the consent preview.
    public func prepare() async {
        guard preview == nil, failure == nil else { return }
        do {
            guard let origin = context.verifiedOrigin else {
                throw IdentityDocumentSupportFailure.missingVerifiedOrigin
            }
            let parsed = try parsedRequest(from: context.requestSnapshot)
            let wallet = try await makeWallet()
            self.wallet = wallet
            let prepared = try await wallet.previewAnnexCPresentation(
                parsedRequest: parsed,
                // The platform-asserted origin is passed through verbatim: it is what the wallet must
                // bind the response to, and any normalization here could only make it disagree with
                // what the verifier sees.
                verifiedOrigin: origin.absoluteString,
                selectedRegistryEntryIDs: []
            )
            preview = prepared
            // Pre-select only where there is nothing to choose, so a real choice is never made for
            // the user.
            selectedCredentialIDsByQuery = Dictionary(
                uniqueKeysWithValues: Set(prepared.credentialOptions.map(\.queryID)).compactMap { queryID in
                    let options = prepared.credentialOptions.filter { $0.queryID == queryID }
                    return options.count == 1 ? (queryID, options[0].credentialID) : nil
                }
            )
        } catch {
            log.error("Annex C preview failed: \(error.localizedDescription, privacy: .public)")
            failure = error.localizedDescription
        }
    }

    /// Runs stage 2: requests the raw request and returns the sealed response to the platform.
    public func submit() async {
        guard let wallet, let preview, !isSubmitting else { return }
        let selections = queryIDs.compactMap { queryID -> PresentationCredentialSelection? in
            guard let credentialID = selectedCredentialIDsByQuery[queryID] else { return nil }
            return options(for: queryID).first { $0.credentialID == credentialID }?.selection
        }
        guard selections.count == queryIDs.count else {
            failure = IdentityDocumentSupportFailure.missingCredentialSelection.localizedDescription
            return
        }
        isSubmitting = true
        do {
            try await context.sendResponse { rawRequest in
                let response = try await wallet.submitAnnexCPresentation(
                    requestID: preview.requestID,
                    verifiedOrigin: preview.verifiedOrigin,
                    deviceRequestBase64URL: rawRequest.deviceRequest,
                    encryptionInfoBase64URL: rawRequest.encryptionInfo,
                    selectedCredentialOptions: selections
                )
                return try encryptedResponseData(fromResponseJSON: response.dataJSON)
            }
        } catch {
            log.error("Annex C response failed: \(error.localizedDescription, privacy: .public)")
            failure = error.localizedDescription
            isSubmitting = false
        }
    }

    /// Dismisses the request without releasing anything.
    public func cancel() {
        context.cancelRequest()
    }
}
