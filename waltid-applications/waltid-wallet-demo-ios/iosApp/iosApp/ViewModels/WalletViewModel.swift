import Foundation
import WalletSDK

enum WalletTab: Hashable {
    case credentials
    case receive
    case present
}

enum WalletInteractionKind: String, Equatable {
    case receive
    case present
}

enum WalletInteractionSuccessOutcome: Equatable {
    case credentialAdded
    case informationShared
    case offerDeclined
    case presentationRejected
}

enum WalletCaptureMode: Equatable {
    case scanner
    case manual
}

enum WalletRequestSource: Equatable {
    case qr
    case manual
    case deepLink
}

struct WalletIncomingRequest: Equatable {
    let kind: WalletInteractionKind
    let url: URL
    let source: WalletRequestSource
}

enum WalletInteractionState: Equatable {
    case idle
    case capturing(kind: WalletInteractionKind, mode: WalletCaptureMode, error: String? = nil)
    case validating(WalletIncomingRequest)
    case wrongRequestType(expected: WalletInteractionKind, request: WalletIncomingRequest)
    case resolving(WalletIncomingRequest)
    case reviewingOffer
    case reviewingPresentation
    case submitting(WalletInteractionKind)
    case declining(WalletInteractionKind)
    case success(kind: WalletInteractionKind, outcome: WalletInteractionSuccessOutcome, message: String)
    case failure(kind: WalletInteractionKind, message: String)

    var kind: WalletInteractionKind? {
        switch self {
        case .idle:
            return nil
        case .capturing(let kind, _, _), .wrongRequestType(let kind, _), .submitting(let kind),
             .declining(let kind), .success(let kind, _, _), .failure(let kind, _):
            return kind
        case .validating(let request), .resolving(let request):
            return request.kind
        case .reviewingOffer:
            return .receive
        case .reviewingPresentation:
            return .present
        }
    }
}

private enum WalletDeepLinkScheme: String {
    case credentialOffer = "openid-credential-offer"
    case presentationRequest = "openid4vp"
}

private enum WalletStatusText {
    static let startingWallet = "Starting wallet..."
    static let walletReady = "Wallet ready"
    static let resolvingCredentialOffer = "Resolving credential offer..."
    static let reviewCredentialOffer = "Review credential offer"
    static let credentialOfferDeclined = "Credential offer declined"
    static let receivingCredential = "Receiving credential..."
    static let resolvingPresentation = "Resolving presentation..."
    static let presentingCredential = "Presenting credential..."
    static let decliningPresentation = "Declining presentation..."
    static let bootstrappingWallet = "Bootstrapping wallet..."
    static let reviewPresentationRequest = "Review presentation request"
    static let reviewPresentationError = "Review presentation error"
    static let presentationSent = "Presentation sent"
    static let verifierNotified = "Verifier notified"
    static let presentationReviewCancelled = "Presentation review cancelled"
    static let presentationRejected = "Presentation rejected"
    static let presentationFinishedWithoutVerifierConfirmation = "Presentation finished without verifier confirmation"
    static let rejectionFinishedWithoutVerifierConfirmation = "Rejection finished without verifier confirmation"
    static let receiveFailed = "Receive failed"
    static let previewFailed = "Preview failed"
    static let presentFailed = "Present failed"
    static let rejectFailed = "Reject failed"
    static let presentationContinuationFailed = "Could not deliver the verifier response"
    static let bootstrapFailed = "Bootstrap failed"
    static let invalidOfferURL = "invalid offer URL"
    static let invalidRequestURL = "invalid request URL"
    static let selectCredentialForEveryRequest = "select a credential for every requested credential"
    static let receivedCredentialsUnavailable = "received credentials are not available locally"
    static let transactionDataProfilesUnavailable = "Transaction data profiles could not be loaded; transaction-data presentation requests will be rejected."

    static func receivedCredentials(_ count: Int) -> String {
        "Received \(count) credential(s)"
    }

    static func failure(_ prefix: String, _ reason: String) -> String {
        "\(prefix): \(reason)"
    }

    static func failure(_ prefix: String, _ error: Error) -> String {
        failure(prefix, error.localizedDescription)
    }
}

@MainActor
class WalletViewModel: ObservableObject {
    @Published var isReady = false
    @Published var did = ""
    @Published var credentials: [Credential] = []
    @Published var statusMessage = WalletStatusText.startingWallet
    @Published var isLoading = false
    @Published var isError = false
    @Published var offerUrl = "" {
        didSet {
            guard offerUrl != oldValue else { return }
            receiveTask?.cancel()
            if !receiveCompleted, let previewHandle = offerPreview?.previewHandle {
                Task { try? await walletClient.discardIssuancePreview(previewHandle) }
            }
            txCode = ""
            offerPreview = nil
        }
    }
    @Published var txCode = ""
    @Published var presentationRequestUrl = ""
    @Published private(set) var presentationReview: PresentationPreviewResult?
    @Published var selectedPresentationCredentialOptions: Set<PresentationCredentialSelection> = []
    @Published var selectedPresentationDisclosureOptions: Set<PresentationDisclosureSelection> = []
    @Published var selectedTab: WalletTab = .credentials
    @Published var offerPreview: OfferResolution?
    @Published var lastReceivedCredentialIDs: [String] = []
    @Published var receiveCompleted = false
    @Published var presentationCompleted = false
    @Published var receiveNavigationResetKey = 0
    @Published var presentationNavigationResetKey = 0
    @Published var inputFocusResetKey = 0
    @Published var transactionDataProfilesWarning: String?
    @Published private(set) var pendingPresentationContinuationURL: URL?
    @Published private(set) var pendingPresentationFormPostHTML: String?
    @Published var interactionState: WalletInteractionState = .idle
    @Published var pendingIncomingRequest: WalletIncomingRequest?
    @Published var replacementRequest: WalletIncomingRequest?
    private var statusTab: WalletTab?
    private var receiveTask: Task<Void, Never>?
    private var pendingPresentationSuccessMessage: String?
    private var pendingPresentationSuccessOutcome: WalletInteractionSuccessOutcome?
    private var presentationTask: Task<Void, Never>?

    var presentationPreview: PresentationPreview? {
        if case .ready(let preview)? = presentationReview { return preview }
        return nil
    }

    var presentationError: PresentationPreviewError? {
        if case .invalid(let error)? = presentationReview { return error }
        return nil
    }

    var receiveUrlEntryEnabled: Bool {
        !isLoading && offerPreview == nil && !receiveCompleted
    }

    var receiveActionEnabled: Bool {
        isReady &&
            !offerUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            receiveUrlEntryEnabled
    }

    var offerReviewEnabled: Bool {
        !isLoading && offerPreview != nil && !receiveCompleted
    }

    var acceptOfferEnabled: Bool {
        offerReviewEnabled && hasValidTransactionCode
    }

    private var hasValidTransactionCode: Bool {
        offerPreview?.transactionCode?.accepts(txCode) ?? true
    }

    var receivedCredentials: [Credential] {
        var credentialsByID: [String: Credential] = [:]
        credentials.forEach { credential in
            credentialsByID[credential.id] = credential
        }
        return lastReceivedCredentialIDs.compactMap { credentialsByID[$0] }
    }

    var presentationUrlEntryEnabled: Bool {
        !isLoading && presentationReview == nil && !presentationCompleted
    }

    var presentationPreviewActionEnabled: Bool {
        isReady &&
            !presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            presentationUrlEntryEnabled
    }

    var presentationReviewEnabled: Bool {
        !isLoading && presentationReview != nil && !presentationCompleted
    }

    var presentationCredentialSelectionComplete: Bool {
        presentationPreview?.hasCompleteCredentialSelection(selectedPresentationCredentialOptions) == true
    }

    func statusMessage(for tab: WalletTab) -> String {
        statusApplies(to: tab) ? statusMessage : fallbackStatusMessage(for: tab)
    }

    func statusIsLoading(for tab: WalletTab) -> Bool {
        isLoading && statusApplies(to: tab)
    }

    func statusIsError(for tab: WalletTab) -> Bool {
        isError && statusApplies(to: tab)
    }

    private let walletClient: any WalletClient

    init(
        walletID: String = "default",
        attestationBaseUrl: String? = nil,
        attestationAttesterPath: String? = nil,
        attestationBearerToken: String? = nil,
        attestationHostHeader: String? = nil,
        transactionDataProfilesUrl: String? = nil,
        walletClient: (any WalletClient)? = nil
    ) {
        let transactionDataProfiles: TransactionDataProfilesConfiguration
        if walletClient == nil {
            transactionDataProfiles = Self.resolveTransactionDataProfiles(from: transactionDataProfilesUrl)
        } else {
            transactionDataProfiles = TransactionDataProfilesConfiguration(profiles: [])
        }
        let configuration = WalletConfiguration(
            walletID: walletID,
            attestation: Self.attestationConfiguration(
                baseUrl: attestationBaseUrl,
                attesterPath: attestationAttesterPath,
                bearerToken: attestationBearerToken,
                hostHeader: attestationHostHeader
            ),
            transactionDataProfiles: transactionDataProfiles.profiles
        )
        self.walletClient = walletClient ?? SDKWalletClient(configuration: configuration)
        transactionDataProfilesWarning = transactionDataProfiles.warning
        bootstrap()
    }

    private static func resolveTransactionDataProfiles(from urlString: String?) -> TransactionDataProfilesConfiguration {
        guard let trimmed = urlString?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty,
              let url = URL(string: trimmed) else {
            return transactionDataProfilesUnavailable("TRANSACTION_DATA_PROFILES_URL is not configured")
        }

        let semaphore = DispatchSemaphore(value: 0)
        var fetchResult: Result<[WalletTransactionDataProfile], Error>?
        URLSession.shared.dataTask(with: url) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                fetchResult = .failure(error)
                return
            }

            guard let status = (response as? HTTPURLResponse)?.statusCode else {
                fetchResult = .failure(TransactionDataProfileFetchError.missingResponse)
                return
            }
            guard (200..<300).contains(status) else {
                fetchResult = .failure(TransactionDataProfileFetchError.httpStatus(status))
                return
            }
            guard let data else {
                fetchResult = .failure(TransactionDataProfileFetchError.missingBody)
                return
            }

            do {
                let profiles = try JSONDecoder().decode([RemoteTransactionDataProfile].self, from: data)
                guard !profiles.isEmpty else {
                    fetchResult = .failure(TransactionDataProfileFetchError.emptyProfiles)
                    return
                }
                fetchResult = .success(
                    profiles.map {
                        WalletTransactionDataProfile(
                            type: $0.type,
                            displayName: $0.displayName,
                            fields: $0.fields
                        )
                    }
                )
            } catch {
                fetchResult = .failure(error)
            }
        }.resume()

        guard semaphore.wait(timeout: .now() + 3) == .success else {
            return transactionDataProfilesUnavailable("Timed out fetching transaction data profiles from \(url.absoluteString)")
        }

        switch fetchResult {
        case .success(let profiles):
            return TransactionDataProfilesConfiguration(profiles: profiles)
        case .failure(let error):
            return transactionDataProfilesUnavailable("Could not fetch transaction data profiles from \(url.absoluteString): \(error)")
        case nil:
            return transactionDataProfilesUnavailable("Could not fetch transaction data profiles from \(url.absoluteString)")
        }
    }

    private static func transactionDataProfilesUnavailable(_ reason: String) -> TransactionDataProfilesConfiguration {
        NSLog("[WalletE2E] Transaction data profiles unavailable: \(reason)")
        return TransactionDataProfilesConfiguration(
            profiles: [],
            warning: WalletStatusText.transactionDataProfilesUnavailable
        )
    }

    private struct TransactionDataProfilesConfiguration {
        let profiles: [WalletTransactionDataProfile]
        let warning: String?

        init(profiles: [WalletTransactionDataProfile], warning: String? = nil) {
            self.profiles = profiles
            self.warning = warning
        }
    }

    private struct RemoteTransactionDataProfile: Decodable {
        let type: String
        let displayName: String
        let fields: [String]
    }

    private enum TransactionDataProfileFetchError: Error {
        case emptyProfiles
        case httpStatus(Int)
        case missingBody
        case missingResponse
    }

    var interactionSheetPresented: Bool {
        interactionState != .idle
    }

    func startReceiveCapture() {
        startCapture(.receive)
    }

    func startPresentCapture() {
        startCapture(.present)
    }

    private func startCapture(_ kind: WalletInteractionKind) {
        guard isReady, interactionState == .idle else { return }
        resetFlow(kind)
        interactionState = .capturing(kind: kind, mode: .scanner)
    }

    func showManualEntry() {
        guard let kind = interactionState.kind else { return }
        interactionState = .capturing(kind: kind, mode: .manual)
    }

    func showScanner() {
        guard let kind = interactionState.kind else { return }
        interactionState = .capturing(kind: kind, mode: .scanner)
    }

    func submitCapturedRequest(_ value: String, source: WalletRequestSource) {
        guard case .capturing(let expectedKind, _, _) = interactionState else { return }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let detectedKind = Self.interactionKind(for: url) else {
            interactionState = .capturing(
                kind: expectedKind,
                mode: .manual,
                error: expectedKind == .receive
                    ? "Enter a valid OpenID credential offer link."
                    : "Enter a valid OpenID presentation request link."
            )
            return
        }

        let request = WalletIncomingRequest(kind: detectedKind, url: url, source: source)
        interactionState = .validating(request)
        guard detectedKind == expectedKind else {
            interactionState = .wrongRequestType(expected: expectedKind, request: request)
            return
        }
        resolveIncomingRequest(request)
    }

    func switchToDetectedRequest() {
        guard case .wrongRequestType(_, let request) = interactionState else { return }
        resolveIncomingRequest(request)
    }

    func retryInteraction() {
        guard let kind = interactionState.kind else { return }
        interactionState = .capturing(kind: kind, mode: .manual)
    }

    func cancelInteraction() {
        receiveTask?.cancel()
        presentationTask?.cancel()
        discardIssuancePreviewIfPresent()
        discardPresentationPreviewIfPresent()
        offerPreview = nil
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        txCode = ""
        offerUrl = ""
        presentationRequestUrl = ""
        lastReceivedCredentialIDs = []
        receiveCompleted = false
        presentationCompleted = false
        replacementRequest = nil
        interactionState = .idle
        setSuccess(WalletStatusText.walletReady)
    }

    func finishInteraction() {
        let kind = interactionState.kind
        interactionState = .idle
        if kind == .receive {
            startNewReceiveFlow()
        } else if kind == .present {
            startNewPresentationFlow()
        }
    }

    func keepCurrentRequest() {
        replacementRequest = nil
    }

    func replaceCurrentRequest() {
        guard let request = replacementRequest else { return }
        replacementRequest = nil
        cancelInteraction()
        resolveIncomingRequest(request)
    }

    func handleDeepLink(_ url: URL) {
        resetInputFocus()
        logE2E("Deep link received: \(url.scheme ?? "unknown")")
        guard let kind = Self.interactionKind(for: url) else { return }
        let request = WalletIncomingRequest(kind: kind, url: url, source: .deepLink)
        guard isReady else {
            pendingIncomingRequest = request
            return
        }
        guard interactionState == .idle else {
            replacementRequest = request
            return
        }
        resolveIncomingRequest(request)
    }

    private static func interactionKind(for url: URL) -> WalletInteractionKind? {
        switch url.scheme.flatMap(WalletDeepLinkScheme.init(rawValue:)) {
        case .credentialOffer:
            return .receive
        case .presentationRequest:
            return .present
        case nil:
            return nil
        }
    }

    private func resolveIncomingRequest(_ request: WalletIncomingRequest) {
        resetFlow(request.kind)
        interactionState = .resolving(request)
        switch request.kind {
        case .receive:
            selectedTab = .receive
            offerUrl = request.url.absoluteString
            previewOffer()
        case .present:
            selectedTab = .present
            presentationRequestUrl = request.url.absoluteString
            previewPresentation()
        }
    }

    private func resetFlow(_ kind: WalletInteractionKind) {
        receiveTask?.cancel()
        presentationTask?.cancel()
        discardIssuancePreviewIfPresent()
        discardPresentationPreviewIfPresent()
        resetInputFocus()
        txCode = ""
        offerPreview = nil
        lastReceivedCredentialIDs = []
        receiveCompleted = false
        receiveNavigationResetKey += 1
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        presentationNavigationResetKey += 1
        resetFlowStatusForIncomingURL()
        selectedTab = kind == .receive ? .receive : .present
    }

    func startNewReceiveFlow() {
        receiveTask?.cancel()
        resetInputFocus()
        offerUrl = ""
        txCode = ""
        offerPreview = nil
        lastReceivedCredentialIDs = []
        receiveCompleted = false
        receiveNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
        interactionState = .idle
    }

    func startNewPresentationFlow() {
        presentationTask?.cancel()
        discardPresentationPreviewIfPresent()
        resetInputFocus()
        presentationRequestUrl = ""
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        clearPendingPresentationContinuation()
        presentationNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
        interactionState = .idle
    }

    func previewOffer() {
        resetInputFocus()
        guard !isLoading, offerPreview == nil, !receiveCompleted else { return }
        let trimmedOfferUrl = offerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offer = URL(string: trimmedOfferUrl) else {
            interactionState = .failure(kind: .receive, message: WalletStatusText.invalidOfferURL)
            setError(WalletStatusText.failure(WalletStatusText.receiveFailed, WalletStatusText.invalidOfferURL), tab: .receive)
            return
        }
        let incomingRequest = WalletIncomingRequest(kind: .receive, url: offer, source: .manual)
        interactionState = .resolving(incomingRequest)
        let request = ReceiveRequest(offerURL: offer.absoluteString, navigationResetKey: receiveNavigationResetKey)
        let previousPreviewHandle = receiveCompleted ? nil : offerPreview?.previewHandle
        setLoading(WalletStatusText.resolvingCredentialOffer, tab: .receive)
        receiveTask = Task {
            var newPreviewHandle: IssuancePreviewHandle?
            do {
                let resolution = try await walletClient.resolveOffer(offer: offer)
                newPreviewHandle = resolution.previewHandle
                try Task.checkCancellation()
                guard isCurrent(request) else {
                    try? await walletClient.discardIssuancePreview(resolution.previewHandle)
                    return
                }
                if let previousPreviewHandle {
                    try? await walletClient.discardIssuancePreview(previousPreviewHandle)
                }
                offerPreview = resolution
                newPreviewHandle = nil
                interactionState = .reviewingOffer
                setSuccess(WalletStatusText.reviewCredentialOffer, tab: .receive)
            } catch is CancellationError {
                if let newPreviewHandle {
                    try? await walletClient.discardIssuancePreview(newPreviewHandle)
                }
                return
            } catch {
                if let newPreviewHandle {
                    try? await walletClient.discardIssuancePreview(newPreviewHandle)
                }
                if isCurrent(request) {
                    interactionState = .failure(kind: .receive, message: error.localizedDescription)
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
                }
            }
        }
    }

    func acceptOffer() {
        resetInputFocus()
        guard acceptOfferEnabled else { return }
        let trimmedOfferUrl = offerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offer = URL(string: trimmedOfferUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.receiveFailed, WalletStatusText.invalidOfferURL), tab: .receive)
            return
        }
        let trimmedTxCode = txCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let previewHandle = offerPreview?.previewHandle else { return }
        let previousCredentials = credentials
        let request = ReceiveRequest(offerURL: offer.absoluteString, navigationResetKey: receiveNavigationResetKey)

        interactionState = .submitting(.receive)
        setLoading(WalletStatusText.receivingCredential, tab: .receive)
        receiveTask = Task {
            do {
                try await completeReceive(
                    previewHandle: previewHandle,
                    txCode: offerPreview?.transactionCode == nil ? nil : trimmedTxCode,
                    previousCredentials: previousCredentials,
                    request: request
                )
            } catch is CancellationError {
                return
            } catch {
                if isCurrent(request) {
                    interactionState = .reviewingOffer
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
                }
            }
        }
    }

    func declineOffer() {
        receiveTask?.cancel()
        let previewHandle = offerPreview?.previewHandle
        offerPreview = nil
        txCode = ""
        receiveNavigationResetKey += 1
        interactionState = .success(
            kind: .receive,
            outcome: .offerDeclined,
            message: WalletStatusText.credentialOfferDeclined
        )
        setSuccess(WalletStatusText.credentialOfferDeclined, tab: .receive)
        if let previewHandle {
            Task { try? await walletClient.discardIssuancePreview(previewHandle) }
        }
    }

    private func completeReceive(
        previewHandle: IssuancePreviewHandle,
        txCode: String?,
        previousCredentials: [Credential],
        request: ReceiveRequest
    ) async throws {
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        setLoading(WalletStatusText.receivingCredential, tab: .receive)
        let credentialIDs = try await walletClient.receive(previewHandle: previewHandle, txCode: txCode)
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let refreshedCredentials = try await walletClient.credentials()
        try Task.checkCancellation()
        guard isCurrent(request) else { return }
        let receivedCredentialIDs = Self.resolvedReceivedCredentialIDs(
            returnedCredentialIDs: credentialIDs,
            previousCredentials: previousCredentials,
            refreshedCredentials: refreshedCredentials
        )
        let refreshedCredentialIDs = Set(refreshedCredentials.map(\.id))
        let displayableReceivedCredentialIDs = receivedCredentialIDs.filter { refreshedCredentialIDs.contains($0) }
        guard !displayableReceivedCredentialIDs.isEmpty else {
            credentials = refreshedCredentials
            offerPreview = nil
            lastReceivedCredentialIDs = []
            receiveCompleted = false
            interactionState = .failure(
                kind: .receive,
                message: WalletStatusText.receivedCredentialsUnavailable
            )
            setError(
                WalletStatusText.failure(
                    WalletStatusText.receiveFailed,
                    WalletStatusText.receivedCredentialsUnavailable
                ),
                tab: .receive
            )
            return
        }

        credentials = refreshedCredentials
        offerPreview = nil
        lastReceivedCredentialIDs = displayableReceivedCredentialIDs
        self.txCode = ""
        receiveCompleted = true
        interactionState = .success(
            kind: .receive,
            outcome: .credentialAdded,
            message: WalletStatusText.receivedCredentials(displayableReceivedCredentialIDs.count)
        )
        setSuccess(WalletStatusText.receivedCredentials(displayableReceivedCredentialIDs.count), tab: .receive)
    }

    func updateTxCode(_ value: String) {
        txCode = offerPreview?.transactionCode?.normalizeInput(value) ?? value
    }

    private func isCurrent(_ request: ReceiveRequest) -> Bool {
        receiveNavigationResetKey == request.navigationResetKey &&
            offerUrl.trimmingCharacters(in: .whitespacesAndNewlines) == request.offerURL
    }

    private struct ReceiveRequest {
        let offerURL: String
        let navigationResetKey: Int
    }

    func presentCredential() {
        resetInputFocus()
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }

        setLoading(WalletStatusText.presentingCredential, tab: .present)
        Task {
            do {
                let result = try await walletClient.present(
                    request: request,
                    did: did.isEmpty ? nil : did
                )
                handlePresentationResult(
                    result,
                    successMessage: WalletStatusText.presentationSent,
                    failureMessage: WalletStatusText.presentationFinishedWithoutVerifierConfirmation
                )
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.presentFailed, error), tab: .present)
            }
        }
    }

    func previewPresentation() {
        resetInputFocus()
        guard !isLoading, presentationReview == nil, !presentationCompleted else { return }
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            interactionState = .failure(kind: .present, message: WalletStatusText.invalidRequestURL)
            setError(WalletStatusText.failure(WalletStatusText.previewFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }

        interactionState = .resolving(
            WalletIncomingRequest(kind: .present, url: request, source: .manual)
        )
        let navigationResetKey = presentationNavigationResetKey
        let requestURL = trimmedRequestUrl
        setLoading(WalletStatusText.resolvingPresentation, tab: .present)
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        clearPendingPresentationContinuation()
        presentationTask = Task {
            var newPreviewHandle: PresentationPreviewHandle?
            do {
                let result = try await walletClient.previewPresentation(request: request)
                newPreviewHandle = result.previewHandle
                try Task.checkCancellation()
                guard
                    presentationNavigationResetKey == navigationResetKey,
                    presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines) == requestURL
                else {
                    try? await walletClient.discardPresentationPreview(result.previewHandle)
                    return
                }
                presentationReview = result
                newPreviewHandle = nil
                interactionState = .reviewingPresentation
                switch result {
                case .ready(let preview):
                    selectedPresentationCredentialOptions = preview.defaultCredentialSelection()
                    selectedPresentationDisclosureOptions = []
                    setSuccess(WalletStatusText.reviewPresentationRequest, tab: .present)
                case .invalid:
                    selectedPresentationCredentialOptions = []
                    selectedPresentationDisclosureOptions = []
                    setSuccess(WalletStatusText.reviewPresentationError, tab: .present)
                }
            } catch is CancellationError {
                if let newPreviewHandle {
                    try? await walletClient.discardPresentationPreview(newPreviewHandle)
                }
            } catch {
                if let newPreviewHandle {
                    try? await walletClient.discardPresentationPreview(newPreviewHandle)
                }
                guard !Task.isCancelled else { return }
                interactionState = .failure(kind: .present, message: error.localizedDescription)
                setError(WalletStatusText.failure(WalletStatusText.previewFailed, error), tab: .present)
            }
        }
    }

    func togglePresentationCredential(_ selection: PresentationCredentialSelection) {
        let wasSelected = selectedPresentationCredentialOptions.contains(selection)
        let option = presentationPreview?
            .credentialOptions
            .first { $0.selection == selection }

        if wasSelected {
            selectedPresentationCredentialOptions.remove(selection)
        } else {
            if option?.multiple != true {
                selectedPresentationCredentialOptions = Set(
                    selectedPresentationCredentialOptions.filter { $0.queryID != selection.queryID }
                )
            }
            selectedPresentationCredentialOptions.insert(selection)
        }

        let retainedDisclosures: Set<PresentationDisclosureSelection>
        if option?.multiple == true {
            retainedDisclosures = Set(
                selectedPresentationDisclosureOptions.filter {
                    $0.queryID != selection.queryID || $0.credentialID != selection.credentialID
                }
            )
        } else {
            retainedDisclosures = Set(
                selectedPresentationDisclosureOptions.filter { $0.queryID != selection.queryID }
            )
        }
        selectedPresentationDisclosureOptions = retainedDisclosures
            .forSelectedCredentials(selectedPresentationCredentialOptions)
    }

    func togglePresentationDisclosure(_ selection: PresentationDisclosureSelection) {
        if selectedPresentationDisclosureOptions.contains(selection) {
            selectedPresentationDisclosureOptions.remove(selection)
        } else {
            selectedPresentationDisclosureOptions.insert(selection)
        }
        selectedPresentationDisclosureOptions = selectedPresentationDisclosureOptions
            .forSelectedCredentials(selectedPresentationCredentialOptions)
    }

    func submitPresentation() {
        resetInputFocus()
        guard !isLoading else { return }
        guard let previewHandle = presentationPreview?.previewHandle else { return }
        guard presentationCredentialSelectionComplete else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.selectCredentialForEveryRequest), tab: .present)
            return
        }
        let selectedDisclosureOptions = selectedPresentationDisclosureOptions
            .forSelectedCredentials(selectedPresentationCredentialOptions)
        let selectedCredentialOptions = Array(selectedPresentationCredentialOptions)
        let selectedDid = did.isEmpty ? nil : did

        interactionState = .submitting(.present)
        setLoading(WalletStatusText.presentingCredential, tab: .present)
        presentationTask = Task {
            do {
                let result = try await walletClient.submitPresentation(
                    previewHandle: previewHandle,
                    selectedCredentialOptions: selectedCredentialOptions,
                    selectedDisclosureOptions: Array(selectedDisclosureOptions),
                    did: selectedDid
                )
                try Task.checkCancellation()
                presentationReview = nil
                selectedPresentationCredentialOptions = []
                selectedPresentationDisclosureOptions = []
                handlePresentationResult(
                    result,
                    successMessage: WalletStatusText.presentationSent,
                    failureMessage: WalletStatusText.presentationFinishedWithoutVerifierConfirmation
                )
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                presentationReview = nil
                selectedPresentationCredentialOptions = []
                selectedPresentationDisclosureOptions = []
                presentationCompleted = false
                interactionState = .failure(kind: .present, message: error.localizedDescription)
                setError(WalletStatusText.failure(WalletStatusText.presentFailed, error), tab: .present)
            }
        }
    }

    func rejectPresentation() {
        resetInputFocus()
        guard !isLoading else { return }
        guard let presentationReview else { return }
        let previewHandle = presentationReview.previewHandle
        let isReportingError: Bool
        if case .invalid = presentationReview {
            isReportingError = true
        } else {
            isReportingError = false
        }

        interactionState = .declining(.present)
        setLoading(WalletStatusText.decliningPresentation, tab: .present)
        presentationTask = Task {
            do {
                let result = try await walletClient.rejectPresentation(previewHandle: previewHandle)
                try Task.checkCancellation()
                finishRejection()
                handlePresentationResult(
                    result,
                    successMessage: isReportingError
                        ? WalletStatusText.verifierNotified
                        : WalletStatusText.presentationRejected,
                    failureMessage: WalletStatusText.rejectionFinishedWithoutVerifierConfirmation,
                    successOutcome: .presentationRejected
                )
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                finishRejection()
                presentationCompleted = false
                interactionState = .failure(kind: .present, message: error.localizedDescription)
                setError(WalletStatusText.failure(WalletStatusText.rejectFailed, error), tab: .present)
            }
        }
    }

    private func finishRejection() {
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationNavigationResetKey += 1
    }

    func cancelPresentationReview() {
        resetInputFocus()
        guard !isLoading, let previewHandle = presentationReview?.previewHandle else { return }
        presentationTask?.cancel()
        presentationReview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        presentationNavigationResetKey += 1
        interactionState = .idle
        setSuccess(WalletStatusText.presentationReviewCancelled, tab: .present)
        Task { try? await walletClient.discardPresentationPreview(previewHandle) }
    }

    func completePresentationContinuation() {
        guard
            let successMessage = pendingPresentationSuccessMessage,
            let successOutcome = pendingPresentationSuccessOutcome
        else { return }
        clearPendingPresentationContinuation()
        presentationCompleted = true
        interactionState = .success(kind: .present, outcome: successOutcome, message: successMessage)
        setSuccess(successMessage, tab: .present)
    }

    func failPresentationContinuation(_ reason: String) {
        guard pendingPresentationSuccessMessage != nil else { return }
        clearPendingPresentationContinuation()
        presentationCompleted = false
        interactionState = .failure(kind: .present, message: reason)
        setError(
            WalletStatusText.failure(WalletStatusText.presentationContinuationFailed, reason),
            tab: .present
        )
    }

    private func handlePresentationResult(
        _ result: PresentationResult,
        successMessage: String,
        failureMessage: String,
        successOutcome: WalletInteractionSuccessOutcome = .informationShared
    ) {
        clearPendingPresentationContinuation()
        switch result {
        case .transmitted(.failed):
            presentationCompleted = false
            interactionState = .failure(kind: .present, message: failureMessage)
            setError(failureMessage, tab: .present)
        case .prepared(.openURL(let url)):
            pendingPresentationSuccessMessage = successMessage
            pendingPresentationSuccessOutcome = successOutcome
            pendingPresentationContinuationURL = url
            presentationCompleted = false
        case .prepared(.submitForm(let html)):
            pendingPresentationSuccessMessage = successMessage
            pendingPresentationSuccessOutcome = successOutcome
            pendingPresentationFormPostHTML = html
            presentationCompleted = false
        case .transmitted(.succeeded(_, let redirectURL)):
            if let redirectURL {
                pendingPresentationSuccessMessage = successMessage
                pendingPresentationSuccessOutcome = successOutcome
                pendingPresentationContinuationURL = redirectURL
                presentationCompleted = false
            } else {
                presentationCompleted = true
                interactionState = .success(kind: .present, outcome: successOutcome, message: successMessage)
                setSuccess(successMessage, tab: .present)
            }
        }
    }

    private func clearPendingPresentationContinuation() {
        pendingPresentationContinuationURL = nil
        pendingPresentationFormPostHTML = nil
        pendingPresentationSuccessMessage = nil
        pendingPresentationSuccessOutcome = nil
    }

    private func discardIssuancePreviewIfPresent() {
        guard !receiveCompleted else { return }
        guard let previewHandle = offerPreview?.previewHandle else { return }
        Task { try? await walletClient.discardIssuancePreview(previewHandle) }
    }

    private func discardPresentationPreviewIfPresent() {
        guard !presentationCompleted else { return }
        guard let previewHandle = presentationReview?.previewHandle else { return }
        Task { try? await walletClient.discardPresentationPreview(previewHandle) }
    }

    private func bootstrap() {
        setLoading(WalletStatusText.bootstrappingWallet)
        logE2E("Bootstrap started")
        Task {
            do {
                logE2E("Bootstrap: calling wallet.bootstrap()")
                let result = try await walletClient.bootstrap()
                logE2E("Bootstrap: success, DID: \(result.did)")

                logE2E("Bootstrap: calling wallet.credentials()")
                let list = try await walletClient.credentials()
                logE2E("Bootstrap: listCredentials returned \(list.count) credentials")

                did = result.did
                credentials = list
                isReady = true
                setSuccess(WalletStatusText.walletReady)
                if let pendingIncomingRequest {
                    self.pendingIncomingRequest = nil
                    resolveIncomingRequest(pendingIncomingRequest)
                }
                logE2E("Bootstrap: completed successfully, wallet is ready")
            } catch {
                logE2E("Bootstrap: FAILED with error: \(error.localizedDescription)")
                setError(WalletStatusText.failure(WalletStatusText.bootstrapFailed, error))
            }
        }
    }

    private static func attestationConfiguration(
        baseUrl: String?,
        attesterPath: String?,
        bearerToken: String?,
        hostHeader: String?
    ) -> WalletAttestationConfiguration? {
        guard let baseUrl = baseUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !baseUrl.isEmpty else {
            return nil
        }

        return WalletAttestationConfiguration(
            baseURL: baseUrl,
            attesterPath: attesterPath ?? "",
            bearerToken: bearerToken ?? "",
            hostHeader: hostHeader ?? ""
        )
    }

    private func setLoading(_ message: String, tab: WalletTab? = nil) {
        isLoading = true
        isError = false
        statusTab = tab
        statusMessage = message
        logE2E("STATUS \(message)")
    }

    private func setSuccess(_ message: String, tab: WalletTab? = nil) {
        isLoading = false
        isError = false
        statusTab = tab
        statusMessage = message
        logE2E("STATUS \(message)")
    }

    private static func resolvedReceivedCredentialIDs(
        returnedCredentialIDs: [String],
        previousCredentials: [Credential],
        refreshedCredentials: [Credential]
    ) -> [String] {
        let refreshedIDs = Set(refreshedCredentials.map(\.id))
        let returnedResolvedIDs = returnedCredentialIDs.filter { refreshedIDs.contains($0) }
        if !returnedResolvedIDs.isEmpty {
            return returnedResolvedIDs
        }

        let previousIDs = Set(previousCredentials.map(\.id))
        let newCredentialIDs = refreshedCredentials
            .map(\.id)
            .filter { !previousIDs.contains($0) }
        return newCredentialIDs.isEmpty ? returnedCredentialIDs : newCredentialIDs
    }

    private func setError(_ message: String, tab: WalletTab? = nil) {
        isLoading = false
        isError = true
        statusTab = tab
        statusMessage = message
        logE2E("STATUS \(message)")
    }

    private func resetFlowStatusForIncomingURL() {
        isLoading = !isReady
        isError = false
        statusTab = nil
        statusMessage = isReady ? WalletStatusText.walletReady : WalletStatusText.startingWallet
        logE2E("STATUS \(statusMessage)")
    }

    private func resetInputFocus() {
        inputFocusResetKey += 1
    }

    private func statusApplies(to tab: WalletTab) -> Bool {
        statusTab == nil || statusTab == tab
    }

    private func fallbackStatusMessage(for tab: WalletTab) -> String {
        switch tab {
        case .credentials:
            return baseStatusMessage
        case .receive:
            if receiveCompleted && !lastReceivedCredentialIDs.isEmpty {
                return WalletStatusText.receivedCredentials(lastReceivedCredentialIDs.count)
            }
            return baseStatusMessage
        case .present:
            if presentationCompleted {
                return WalletStatusText.presentationSent
            }
            if presentationPreview != nil {
                return WalletStatusText.reviewPresentationRequest
            }
            if presentationError != nil {
                return WalletStatusText.reviewPresentationError
            }
            return baseStatusMessage
        }
    }

    private var baseStatusMessage: String {
        isReady ? WalletStatusText.walletReady : WalletStatusText.startingWallet
    }

    private func logE2E(_ message: String) {
        NSLog("[WalletE2E] \(message)")
    }
}

private extension PresentationPreviewResult {
    var previewHandle: PresentationPreviewHandle {
        switch self {
        case .ready(let preview): preview.previewHandle
        case .invalid(let error): error.previewHandle
        }
    }
}

private extension PresentationPreview {
    func hasCompleteCredentialSelection(_ selectedCredentialOptions: Set<PresentationCredentialSelection>) -> Bool {
        let optionBySelection = Dictionary(uniqueKeysWithValues: credentialOptions.map { ($0.selection, $0) })
        let selectedOptions = selectedCredentialOptions.compactMap { optionBySelection[$0] }
        guard !selectedOptions.isEmpty else { return false }
        let selectedCountsByQueryID = Dictionary(grouping: selectedOptions, by: \.queryID).mapValues(\.count)
        guard !selectedOptions.contains(where: { option in
            (selectedCountsByQueryID[option.queryID] ?? 0) > 1 && !option.multiple
        }) else {
            return false
        }

        let selectedQueryIDs = Set(selectedOptions.map(\.queryID))
        if credentialRequirements.isEmpty { return true }
        return credentialRequirements.allSatisfy { requirement in
            requirement.options.contains { option in
                !option.isEmpty && option.allSatisfy { selectedQueryIDs.contains($0) }
            }
        }
    }

    func defaultCredentialSelection() -> Set<PresentationCredentialSelection> {
        var firstSelectionByQueryID: [String: PresentationCredentialSelection] = [:]
        var orderedQueryIDs: [String] = []
        for option in credentialOptions where firstSelectionByQueryID[option.queryID] == nil {
            firstSelectionByQueryID[option.queryID] = option.selection
            orderedQueryIDs.append(option.queryID)
        }
        guard let firstQueryID = orderedQueryIDs.first else { return [] }
        if credentialRequirements.isEmpty {
            return Set([firstSelectionByQueryID[firstQueryID]].compactMap { $0 })
        }

        var selectedQueryIDs: [String] = []
        for requirement in credentialRequirements {
            let queryIDs = requirement.options.first { option in
                !option.isEmpty && option.allSatisfy { firstSelectionByQueryID[$0] != nil }
            } ?? requirement.options.first?.filter { firstSelectionByQueryID[$0] != nil }
            for queryID in queryIDs ?? [] where !selectedQueryIDs.contains(queryID) {
                selectedQueryIDs.append(queryID)
            }
        }
        return Set(selectedQueryIDs.compactMap { firstSelectionByQueryID[$0] })
    }
}

private extension Set where Element == PresentationDisclosureSelection {
    func forSelectedCredentials(
        _ selectedCredentialOptions: Set<PresentationCredentialSelection>
    ) -> Set<PresentationDisclosureSelection> {
        return filter {
            let disclosure = $0
            return selectedCredentialOptions.contains {
                $0.queryID == disclosure.queryID && $0.credentialID == disclosure.credentialID
            }
        }
    }
}

private extension TransactionCodeRequirement {
    func normalizeInput(_ value: String) -> String {
        let normalized: String
        switch inputMode {
        case .numeric:
            normalized = value.filter { $0.isASCII && $0.isNumber }
        case .text:
            normalized = value
        }
        return length.map { String(normalized.prefix($0)) } ?? normalized
    }

    func accepts(_ value: String) -> Bool {
        let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return false }
        if let length, normalized.count != length { return false }
        return inputMode != .numeric || normalized.allSatisfy { $0.isASCII && $0.isNumber }
    }
}

#if DEBUG
extension WalletViewModel {
    static func mockForUITests() -> WalletViewModel {
        WalletViewModel(
            walletID: "mock-wallet",
            walletClient: MockWalletClient()
        )
    }
}
#endif
