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
    @Published var offerUrl = ""
    @Published var presentationRequestUrl = ""
    @Published var presentationPreview: PresentationPreview?
    @Published var selectedPresentationCredentialOptions: Set<PresentationCredentialSelection> = []
    @Published var selectedPresentationDisclosureOptions: Set<PresentationDisclosureSelection> = []
    @Published var selectedTab: WalletTab = .credentials
    @Published var lastReceivedCredentialIDs: [String] = []
    @Published var receiveCompleted = false
    @Published var presentationCompleted = false
    @Published var receiveNavigationResetKey = 0
    @Published var presentationNavigationResetKey = 0
    @Published var inputFocusResetKey = 0
    @Published var transactionDataProfilesWarning: String?
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

    func handleDeepLink(_ url: URL) {
        resetInputFocus()
        logE2E("Deep link received: \(url.scheme ?? "unknown")")
        switch url.scheme.flatMap(WalletDeepLinkScheme.init(rawValue:)) {
        case .credentialOffer:
            selectedTab = .receive
            offerUrl = url.absoluteString
            lastReceivedCredentialIDs = []
            receiveCompleted = false
            receiveNavigationResetKey += 1
            presentationPreview = nil
            selectedPresentationCredentialOptions = []
            selectedPresentationDisclosureOptions = []
            presentationCompleted = false
            resetFlowStatusForIncomingURL()
        case .presentationRequest:
            selectedTab = .present
            presentationRequestUrl = url.absoluteString
            presentationPreview = nil
            selectedPresentationCredentialOptions = []
            selectedPresentationDisclosureOptions = []
            presentationCompleted = false
            presentationNavigationResetKey += 1
            resetFlowStatusForIncomingURL()
        case nil:
            break
        }
    }

    func startNewReceiveFlow() {
        resetInputFocus()
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
        resetInputFocus()
        presentationRequestUrl = ""
        presentationPreview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        presentationNavigationResetKey += 1
        isLoading = false
        isError = false
        statusTab = nil
        statusMessage = WalletStatusText.walletReady
    }

    func receiveCredential() {
        resetInputFocus()
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
        resetInputFocus()
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.previewFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }

        setLoading(WalletStatusText.resolvingPresentation, tab: .present)
        presentationPreview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
        presentationCompleted = false
        Task {
            do {
                let preview = try await walletClient.previewPresentation(request: request)
                presentationPreview = preview
                selectedPresentationCredentialOptions = preview.defaultCredentialSelection()
                selectedPresentationDisclosureOptions = []
                setSuccess(WalletStatusText.reviewPresentationRequest, tab: .present)
            } catch {
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
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.invalidRequestURL), tab: .present)
            return
        }
        guard presentationCredentialSelectionComplete else {
            setError(WalletStatusText.failure(WalletStatusText.presentFailed, WalletStatusText.selectCredentialForEveryRequest), tab: .present)
            return
        }
        let selectedDisclosureOptions = selectedPresentationDisclosureOptions
            .forSelectedCredentials(selectedPresentationCredentialOptions)

        setLoading(WalletStatusText.presentingCredential, tab: .present)
        Task {
            do {
                let result = try await walletClient.submitPresentation(
                    request: request,
                    selectedCredentialOptions: Array(selectedPresentationCredentialOptions),
                    selectedDisclosureOptions: Array(selectedDisclosureOptions),
                    did: did.isEmpty ? nil : did
                )
                selectedPresentationCredentialOptions = []
                selectedPresentationDisclosureOptions = []
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
        resetInputFocus()
        presentationPreview = nil
        selectedPresentationCredentialOptions = []
        selectedPresentationDisclosureOptions = []
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
