import Foundation
import WalletSDK

enum WalletTab: Hashable {
    case credentials
    case receive
    case present
}

private enum WalletDeepLinkScheme: String {
    case credentialOffer = "openid-credential-offer"
    case presentationRequest = "openid4vp"
}

private enum WalletStatusText {
    static let startingWallet = "Starting wallet..."
    static let walletReady = "Wallet ready"
    static let receivingCredential = "Receiving credential..."
    static let resolvingPresentation = "Resolving presentation..."
    static let presentingCredential = "Presenting credential..."
    static let bootstrappingWallet = "Bootstrapping wallet..."
    static let reviewPresentationRequest = "Review presentation request"
    static let presentationSent = "Presentation sent"
    static let presentationReviewCancelled = "Presentation review cancelled"
    static let presentationFinishedWithoutVerifierConfirmation = "Presentation finished without verifier confirmation"
    static let receiveFailed = "Receive failed"
    static let previewFailed = "Preview failed"
    static let presentFailed = "Present failed"
    static let bootstrapFailed = "Bootstrap failed"
    static let invalidOfferURL = "invalid offer URL"
    static let invalidRequestURL = "invalid request URL"
    static let selectAtLeastOneCredential = "select at least one credential"
    static let receivedCredentialsUnavailable = "received credentials are not available locally"

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
    @Published var offerUrl = ""
    @Published var presentationRequestUrl = ""
    @Published var presentationPreview: PresentationPreview?
    @Published var selectedPresentationCredentialIDs: Set<String> = []
    @Published var selectedTab: WalletTab = .credentials
    @Published var lastReceivedCredentialIDs: [String] = []
    @Published var receiveCompleted = false
    @Published var presentationCompleted = false
    @Published var receiveNavigationResetKey = 0
    @Published var presentationNavigationResetKey = 0
    private var statusTab: WalletTab?

    var receiveUrlEntryEnabled: Bool {
        !isLoading && !receiveCompleted
    }

    var receiveActionEnabled: Bool {
        isReady &&
            !offerUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            receiveUrlEntryEnabled
    }

    var receivedCredentials: [Credential] {
        var credentialsByID: [String: Credential] = [:]
        credentials.forEach { credential in
            credentialsByID[credential.id] = credential
        }
        return lastReceivedCredentialIDs.compactMap { credentialsByID[$0] }
    }

    var presentationUrlEntryEnabled: Bool {
        !isLoading && presentationPreview == nil && !presentationCompleted
    }

    var presentationPreviewActionEnabled: Bool {
        isReady &&
            !presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !credentials.isEmpty &&
            presentationUrlEntryEnabled
    }

    var presentationReviewEnabled: Bool {
        !isLoading && presentationPreview != nil && !presentationCompleted
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
        walletClient: (any WalletClient)? = nil
    ) {
        let configuration = WalletConfiguration(
            walletID: walletID,
            attestation: Self.attestationConfiguration(
                baseUrl: attestationBaseUrl,
                attesterPath: attestationAttesterPath,
                bearerToken: attestationBearerToken,
                hostHeader: attestationHostHeader
            )
        )
        self.walletClient = walletClient ?? SDKWalletClient(configuration: configuration)
        bootstrap()
    }

    func handleDeepLink(_ url: URL) {
        logE2E("Deep link received: \(url.scheme ?? "unknown")")
        switch url.scheme.flatMap(WalletDeepLinkScheme.init(rawValue:)) {
        case .credentialOffer:
            selectedTab = .receive
            offerUrl = url.absoluteString
            lastReceivedCredentialIDs = []
            receiveCompleted = false
            receiveNavigationResetKey += 1
            presentationPreview = nil
            selectedPresentationCredentialIDs = []
            presentationCompleted = false
            resetFlowStatusForIncomingURL()
        case .presentationRequest:
            selectedTab = .present
            presentationRequestUrl = url.absoluteString
            presentationPreview = nil
            selectedPresentationCredentialIDs = []
            presentationCompleted = false
            presentationNavigationResetKey += 1
            resetFlowStatusForIncomingURL()
        case nil:
            break
        }
    }

    func startNewReceiveFlow() {
        offerUrl = ""
        lastReceivedCredentialIDs = []
        receiveCompleted = false
        receiveNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
    }

    func startNewPresentationFlow() {
        presentationRequestUrl = ""
        presentationPreview = nil
        selectedPresentationCredentialIDs = []
        presentationCompleted = false
        presentationNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
    }

    func receiveCredential() {
        let trimmedOfferUrl = offerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offer = URL(string: trimmedOfferUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.receiveFailed, WalletStatusText.invalidOfferURL), tab: .receive)
            return
        }
        let previousCredentials = credentials

        setLoading(WalletStatusText.receivingCredential, tab: .receive)
        Task {
            do {
                let credentialIDs = try await walletClient.receive(offer: offer)
                let refreshedCredentials = try await walletClient.credentials()
                let receivedCredentialIDs = Self.resolvedReceivedCredentialIDs(
                    returnedCredentialIDs: credentialIDs,
                    previousCredentials: previousCredentials,
                    refreshedCredentials: refreshedCredentials
                )
                let refreshedCredentialIDs = Set(refreshedCredentials.map(\.id))
                let displayableReceivedCredentialIDs = receivedCredentialIDs.filter { refreshedCredentialIDs.contains($0) }
                guard !displayableReceivedCredentialIDs.isEmpty else {
                    credentials = refreshedCredentials
                    lastReceivedCredentialIDs = []
                    receiveCompleted = false
                    setError(WalletStatusText.failure(WalletStatusText.receiveFailed, WalletStatusText.receivedCredentialsUnavailable), tab: .receive)
                    return
                }

                credentials = refreshedCredentials
                lastReceivedCredentialIDs = displayableReceivedCredentialIDs
                receiveCompleted = true
                setSuccess(WalletStatusText.receivedCredentials(displayableReceivedCredentialIDs.count), tab: .receive)
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.receiveFailed, error), tab: .receive)
            }
        }
    }

    func presentCredential() {
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
                presentationCompleted = result.success
                setSuccess(
                    result.success
                        ? WalletStatusText.presentationSent
                        : WalletStatusText.presentationFinishedWithoutVerifierConfirmation,
                    tab: .present
                )
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.presentFailed, error), tab: .present)
            }
        }
    }

    func previewPresentation() {
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.previewFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }

        setLoading(WalletStatusText.resolvingPresentation, tab: .present)
        presentationPreview = nil
        selectedPresentationCredentialIDs = []
        presentationCompleted = false
        Task {
            do {
                let preview = try await walletClient.previewPresentation(request: request)
                presentationPreview = preview
                selectedPresentationCredentialIDs = Set(preview.credentialOptions.map(\.credentialID))
                setSuccess(WalletStatusText.reviewPresentationRequest, tab: .present)
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.previewFailed, error), tab: .present)
            }
        }
    }

    func togglePresentationCredential(_ credentialID: String) {
        if selectedPresentationCredentialIDs.contains(credentialID) {
            selectedPresentationCredentialIDs.remove(credentialID)
        } else {
            selectedPresentationCredentialIDs.insert(credentialID)
        }
    }

    func submitPresentation() {
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }
        guard !selectedPresentationCredentialIDs.isEmpty else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.selectAtLeastOneCredential), tab: .present)
            return
        }

        setLoading(WalletStatusText.presentingCredential, tab: .present)
        Task {
            do {
                let result = try await walletClient.submitPresentation(
                    request: request,
                    selectedCredentialIDs: Array(selectedPresentationCredentialIDs),
                    did: did.isEmpty ? nil : did
                )
                selectedPresentationCredentialIDs = []
                presentationCompleted = result.success
                setSuccess(
                    result.success
                        ? WalletStatusText.presentationSent
                        : WalletStatusText.presentationFinishedWithoutVerifierConfirmation,
                    tab: .present
                )
            } catch {
                setError(WalletStatusText.failure(WalletStatusText.presentFailed, error), tab: .present)
            }
        }
    }

    func cancelPresentationReview() {
        presentationPreview = nil
        selectedPresentationCredentialIDs = []
        presentationCompleted = false
        presentationNavigationResetKey += 1
        setSuccess(WalletStatusText.presentationReviewCancelled, tab: .present)
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
