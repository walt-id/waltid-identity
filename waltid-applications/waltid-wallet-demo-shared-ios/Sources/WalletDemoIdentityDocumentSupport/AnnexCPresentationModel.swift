import Foundation
import OSLog
import WalletDemoSharingUI
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
    /// What the user is being asked to share, in the wallet's shared review vocabulary.
    ///
    /// Derived from ``preview`` so the review UI never sees the Annex C preview handle, the retained
    /// request ID or the platform request context - those stay here, with the orchestration that has
    /// to submit them.
    @Published public private(set) var review: SharingReviewModel?
    /// Credentials and disclosures the user has chosen.
    @Published public private(set) var selection = SharingSelection()

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

    /// Whether every requested document has a credential chosen for it.
    public var hasCompleteSelection: Bool {
        review?.hasCompleteCredentialSelection(selection.credentials) == true
    }

    /// Applies a credential toggle using the wallet's shared selection rules.
    public func toggleCredential(_ credential: PresentationCredentialSelection) {
        guard let review else { return }
        selection = review.toggling(credential: credential, in: selection)
    }

    /// Applies a disclosure toggle using the wallet's shared selection rules.
    public func toggleDisclosure(_ disclosure: PresentationDisclosureSelection) {
        guard let review else { return }
        selection = review.toggling(disclosure: disclosure, in: selection)
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
            let review = prepared.sharingReview()
            self.review = review
            // The same opening selection every other transport gets, rather than a provider-specific
            // rule: the choice is visible and changeable in the review, and a wallet that pre-selected
            // differently here would answer the same request differently depending on how it was asked.
            selection = SharingSelection(credentials: review.defaultCredentialSelection())
        } catch {
            log.error("Annex C preview failed: \(error.localizedDescription, privacy: .public)")
            failure = error.localizedDescription
        }
    }

    /// Runs stage 2: requests the raw request and returns the sealed response to the platform.
    public func submit() async {
        guard let wallet, let preview, !isSubmitting else { return }
        guard hasCompleteSelection else {
            failure = IdentityDocumentSupportFailure.missingCredentialSelection.localizedDescription
            return
        }
        // Submitted in preview order rather than in the set's order, so the response documents follow
        // the order the request listed them in.
        let selections = preview.credentialOptions
            .map(\.selection)
            .filter(selection.credentials.contains)
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
