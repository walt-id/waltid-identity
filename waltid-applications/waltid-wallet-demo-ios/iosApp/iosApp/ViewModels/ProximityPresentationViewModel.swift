@preconcurrency import CoreBluetooth
import Combine
import Foundation
import UIKit
import WalletSDK

protocol DemoProximityPresentationSession: Sendable {
    var systemPresentationActive: Bool { get }
    var connectedRoute: ProximityConnectedRoute? { get }
    var states: AsyncStream<ProximityState> { get }
    func presentNfc() async
    func dispatch(_ action: ProximityAction) async throws -> ProximityActionResult
    func close() async
}

extension DemoProximityPresentationSession {
    var connectedRoute: ProximityConnectedRoute? { nil }
}

extension ProximitySession: DemoProximityPresentationSession {}

@MainActor
protocol ProximityWalletClient: AnyObject {
    func proximityPresentationCapabilities(
        configuration: ProximityConfiguration
    ) async throws -> ProximityCapabilities

    func startProximityPresentation(
        configuration: ProximityConfiguration
    ) async throws -> any DemoProximityPresentationSession
}

@MainActor
protocol ProximityHostActionExecutor: AnyObject {
    func perform(
        _ action: ProximityRemediationAction
    ) async -> ProximityHostActionResult
}

struct ProximityDocumentSelection: Equatable {
    let requestIndex: Int
    let credentialID: String
    let disclosedElements: Set<ProximityElementReference>
}

@MainActor
final class ProximityPresentationViewModel: ObservableObject {
    @Published private(set) var active = false
    @Published private(set) var sessionState: ProximityState?
    @Published private(set) var selections: [ProximityDocumentSelection] = []
    @Published private(set) var continueAfterResponse = false
    @Published private(set) var hostActionInProgress: ProximityRemediationAction?
    @Published private(set) var actionErrorMessage: String?
    @Published private(set) var startupFailed = false
    @Published private(set) var capabilities: ProximityCapabilities?
    @Published private(set) var connectedRoute: ProximityConnectedRoute?
    @Published private(set) var preferredEngagement: ProximityEngagementMethod?

    private let client: any ProximityWalletClient
    private let configurationProvider: @MainActor () -> ProximityConfiguration
    private let hostActions: any ProximityHostActionExecutor
    private var session: (any DemoProximityPresentationSession)?
    private var observationTask: Task<Void, Never>?
    private var hostActionTask: Task<Void, Never>?
    private var cleanupTask: Task<Void, Never>?
    private var effectiveConfiguration: ProximityConfiguration?
    private var pendingConfiguration: ProximityConfiguration?
    private var sessionGeneration: UInt64 = 0

    init(
        client: any ProximityWalletClient,
        configurationProvider: @escaping @MainActor () -> ProximityConfiguration = {
            .init(
                session: .nfc(.init(
                    handover: .negotiatedHandover,
                    retrieval: .init(nfc: .init()),
                    qrFallback: .init(nfc: .init())
                ))
            )
        },
        hostActions: (any ProximityHostActionExecutor)? = nil
    ) {
        self.client = client
        self.configurationProvider = configurationProvider
        self.hostActions = hostActions ?? IOSProximityHostActionExecutor()
    }

    var review: ProximityReview? {
        guard case .reviewRequired(let review) = sessionState else { return nil }
        return review
    }

    var canApprove: Bool {
        guard let review else { return false }
        return Set(selections.map(\.requestIndex)) == Set(review.documents.map(\.requestIndex))
            && selections.allSatisfy { !$0.disclosedElements.isEmpty }
    }

    var isTerminal: Bool {
        startupFailed || sessionState?.isTerminal == true
    }

    var qrPayload: String? {
        guard displayedEngagement == .qr else { return nil }
        return sessionState?.engagements.compactMap { engagement in
            guard case .qr(let payload) = engagement else { return nil }
            return payload
        }.first
    }

    var engagementChoices: [ProximityEngagementMethod] {
        guard case .engagementReady(let engagements) = sessionState else { return [] }
        let hasNFC = engagements.contains { if case .nfc = $0 { return true }; return false }
        let hasQR = engagements.contains { if case .qr = $0 { return true }; return false }
        return (hasNFC ? [.nfc] : []) + (hasQR ? [.qr] : [])
    }

    var displayedEngagement: ProximityEngagementMethod? {
        if let preferredEngagement, engagementChoices.contains(preferredEngagement) { return preferredEngagement }
        return engagementChoices == [.qr] ? .qr : nil
    }

    func showEngagement(_ method: ProximityEngagementMethod) {
        guard hostActionInProgress == nil, engagementChoices.contains(method) else { return }
        preferredEngagement = method
        if method == .nfc, let session {
            let generation = sessionGeneration
            Task { [weak self] in
                guard let self, active, sessionGeneration == generation,
                      engagementChoices.contains(.nfc) else { return }
                await session.presentNfc()
            }
        }
    }

    func start() {
        guard !active else { return }
        active = true
        preferredEngagement = nil
        sessionState = nil
        selections = []
        continueAfterResponse = false
        actionErrorMessage = nil
        startupFailed = false
        sessionGeneration &+= 1
        let generation = sessionGeneration
        let configuration = configurationProvider()
        pendingConfiguration = configuration
        effectiveConfiguration = configuration
        checkPrerequisitesAndStart(configuration, generation: generation)
    }

    private func checkPrerequisitesAndStart(
        _ configuration: ProximityConfiguration,
        generation: UInt64,
        automaticPermissionAttempted: Bool = false
    ) {
        observationTask?.cancel()
        observationTask = Task { [weak self] in
            guard let self else { return }
            do {
                await cleanupTask?.value
                try Task.checkCancellation()
                guard active, sessionGeneration == generation else { return }
                let capabilities = try await client.proximityPresentationCapabilities(
                    configuration: configuration
                )
                guard active, sessionGeneration == generation else { return }
                self.capabilities = capabilities
                sessionState = .checkingPrerequisites(capabilities)
                if !automaticPermissionAttempted,
                   capabilities.remediationActions.contains(.requestBluetoothPermission) {
                    // Let the host explain the request before opening the OS permission prompt.
                    return
                }
                guard capabilities.mayStart else { return }
                let started = try await client.startProximityPresentation(configuration: configuration)
                guard active, sessionGeneration == generation else {
                    await started.close()
                    return
                }
                pendingConfiguration = nil
                session = started
                for await state in started.states {
                    try Task.checkCancellation()
                    guard active, sessionGeneration == generation else { return }
                    publish(state)
                    if state.isTerminal { break }
                }
            } catch is CancellationError {
                return
            } catch {
                guard active, sessionGeneration == generation else { return }
                startupFailed = true
                actionErrorMessage = Self.demoSessionFailureMessage
            }
        }
    }

    private func remediateBeforeSession(
        _ action: ProximityRemediationAction,
        configuration: ProximityConfiguration,
        generation: UInt64
    ) async {
        hostActionInProgress = action
        _ = await hostActions.perform(action)
        guard !Task.isCancelled, active, sessionGeneration == generation else { return }
        hostActionInProgress = nil
        checkPrerequisitesAndStart(
            configuration,
            generation: generation,
            automaticPermissionAttempted: true
        )
    }

    func selectCredential(requestIndex: Int, credentialID: String) {
        guard let document = review?.documents.first(where: { $0.requestIndex == requestIndex }),
              let credential = document.credentialOptions.first(where: { $0.credentialID == credentialID }) else {
            return
        }
        replaceSelection(
            ProximityDocumentSelection(
                requestIndex: requestIndex,
                credentialID: credentialID,
                disclosedElements: Set(credential.requestedElements.map {
                    ProximityElementReference(
                        namespace: $0.namespace,
                        elementIdentifier: $0.elementIdentifier
                    )
                })
            )
        )
    }

    func toggleElement(requestIndex: Int, element: ProximityElementReference) {
        guard let current = selections.first(where: { $0.requestIndex == requestIndex }),
              let credential = review?.documents.first(where: { $0.requestIndex == requestIndex })?
                .credentialOptions.first(where: { $0.credentialID == current.credentialID }),
              credential.requestedElements.contains(where: {
                  $0.namespace == element.namespace && $0.elementIdentifier == element.elementIdentifier
              }) else {
            return
        }
        var elements = current.disclosedElements
        if !elements.insert(element).inserted {
            elements.remove(element)
        }
        replaceSelection(
            ProximityDocumentSelection(
                requestIndex: requestIndex,
                credentialID: current.credentialID,
                disclosedElements: elements
            )
        )
    }

    func setContinueAfterResponse(_ enabled: Bool) {
        guard review != nil else { return }
        continueAfterResponse = enabled
        actionErrorMessage = nil
    }

    func approve() {
        guard canApprove, let review else { return }
        let documents = review.documents.compactMap { document -> ProximityDocumentSubmission? in
            guard let selection = selections.first(where: { $0.requestIndex == document.requestIndex }) else {
                return nil
            }
            return ProximityDocumentSubmission(
                requestIndex: selection.requestIndex,
                credentialID: selection.credentialID,
                disclosedElements: selection.disclosedElements
            )
        }
        guard documents.count == review.documents.count else { return }
        dispatch(
            .approve(
                reviewID: review.reviewID,
                submission: ProximitySubmission(
                    documents: documents,
                    continueAfterResponse: continueAfterResponse
                )
            )
        )
    }

    func decline() {
        guard let review else { return }
        dispatch(.decline(reviewID: review.reviewID))
    }

    func retryPrerequisites() {
        if session == nil, let pendingConfiguration {
            checkPrerequisitesAndStart(pendingConfiguration, generation: sessionGeneration)
        } else {
            dispatch(.retryPrerequisites)
        }
    }

    func continueWithAvailableConnection() {
        guard case .checkingPrerequisites(let capabilities) = sessionState,
              capabilities.mayStart, hostActionInProgress == nil, session == nil,
              let pendingConfiguration else { return }
        checkPrerequisitesAndStart(pendingConfiguration, generation: sessionGeneration, automaticPermissionAttempted: true)
    }

    func remediate(_ action: ProximityRemediationAction) {
        if case .failed(let error) = sessionState, error.remediationActions.contains(action),
           let effectiveConfiguration, hostActionInProgress == nil {
            replaceSession(effectiveConfiguration, action: action)
            return
        }
        guard case .checkingPrerequisites(let capabilities) = sessionState,
              capabilities.remediationActions.contains(action),
              hostActionInProgress == nil,
              session != nil || pendingConfiguration != nil else {
            return
        }
        hostActionInProgress = action
        actionErrorMessage = nil
        let generation = sessionGeneration
        hostActionTask = Task { [weak self] in
            guard let self else { return }
            let outcome = await hostActions.perform(action)
            guard !Task.isCancelled, active, sessionGeneration == generation else { return }
            if session == nil, let pendingConfiguration {
                hostActionInProgress = nil
                checkPrerequisitesAndStart(
                    pendingConfiguration,
                    generation: generation,
                    automaticPermissionAttempted: true
                )
                return
            }
            guard let session else { return }
            let result: ProximityActionResult
            do {
                result = try await session.dispatch(.reportRemediation(action, outcome))
            } catch {
                guard active, sessionGeneration == generation else { return }
                hostActionInProgress = nil
                actionErrorMessage = Self.demoSessionFailureMessage
                return
            }
            guard active, sessionGeneration == generation else { return }
            hostActionInProgress = nil
            if case .rejected(let error) = result {
                actionErrorMessage = error.message
            }
        }
    }

    func cancel() {
        guard session != nil else {
            dismiss()
            return
        }
        guard sessionState?.legalActions.contains(.cancel) == true else { return }
        dispatch(.cancel)
    }

    func handleLifecycleInterruption() {
        guard hostActionInProgress == nil else { return }
        // CardSession presents system UI in a separate full-screen process. That transition can
        // background the host application while HCE is active, so the protocol session must stay
        // alive until CardSession, the reader, the user, or the protocol timeout closes it.
        guard session?.systemPresentationActive != true else { return }
        guard case .checkingPrerequisites = sessionState else {
            cancel()
            return
        }
    }

    func dismiss() {
        sessionGeneration &+= 1
        observationTask?.cancel()
        observationTask = nil
        hostActionTask?.cancel()
        hostActionTask = nil
        let closing = session
        session = nil
        pendingConfiguration = nil
        effectiveConfiguration = nil
        capabilities = nil
        connectedRoute = nil
        preferredEngagement = nil
        active = false
        sessionState = nil
        selections = []
        continueAfterResponse = false
        hostActionInProgress = nil
        actionErrorMessage = nil
        startupFailed = false
        scheduleClose(closing)
    }

    func restart() {
        guard isTerminal else { return }
        guard let effectiveConfiguration else { return }
        replaceSession(effectiveConfiguration)
    }

    private func replaceSession(
        _ configuration: ProximityConfiguration,
        action: ProximityRemediationAction? = nil
    ) {
        sessionGeneration &+= 1
        let generation = sessionGeneration
        observationTask?.cancel()
        hostActionTask?.cancel()
        let closing = session
        session = nil
        effectiveConfiguration = configuration
        pendingConfiguration = configuration
        active = true
        sessionState = nil
        selections = []
        continueAfterResponse = false
        connectedRoute = nil
        preferredEngagement = nil
        actionErrorMessage = nil
        startupFailed = false
        hostActionInProgress = action
        scheduleClose(closing)
        observationTask = Task { [weak self, cleanupTask] in
            await cleanupTask?.value
            guard let self, !Task.isCancelled, active, sessionGeneration == generation else { return }
            if let action {
                _ = await hostActions.perform(action)
                guard !Task.isCancelled, active, sessionGeneration == generation else { return }
                hostActionInProgress = nil
            }
            checkPrerequisitesAndStart(configuration, generation: generation, automaticPermissionAttempted: action != nil)
        }
    }

    private func scheduleClose(_ closing: (any DemoProximityPresentationSession)?) {
        guard let closing else { return }
        let previous = cleanupTask
        cleanupTask = Task {
            await previous?.value
            await closing.close()
        }
    }

    private func dispatch(_ action: ProximityAction) {
        guard let session else { return }
        actionErrorMessage = nil
        let generation = sessionGeneration
        Task { [weak self] in
            let result: ProximityActionResult
            do {
                result = try await session.dispatch(action)
            } catch {
                guard let self else { return }
                guard active, sessionGeneration == generation else { return }
                actionErrorMessage = Self.demoSessionFailureMessage
                return
            }
            guard let self else { return }
            guard active, sessionGeneration == generation else { return }
            if case .rejected(let error) = result {
                actionErrorMessage = error.message
            }
        }
    }

    private func publish(_ state: ProximityState) {
        let previousReviewID = review?.reviewID
        sessionState = state
        connectedRoute = session?.connectedRoute ?? connectedRoute
        if case .checkingPrerequisites(let latest) = state { capabilities = latest }
        if case .reviewRequired(let review) = state, previousReviewID != review.reviewID {
            selections = review.defaultSelections
            continueAfterResponse = false
        }
        actionErrorMessage = nil
    }

    private func replaceSelection(_ selection: ProximityDocumentSelection) {
        selections = (selections.filter { $0.requestIndex != selection.requestIndex } + [selection])
            .sorted { $0.requestIndex < $1.requestIndex }
        actionErrorMessage = nil
    }

    private static let demoSessionFailureMessage = String(
        localized: "The in-person presentation could not be started"
    )
}

@MainActor
private final class IOSProximityHostActionExecutor: NSObject, ProximityHostActionExecutor,
    @preconcurrency CBCentralManagerDelegate {
    private var settingsContinuation: CheckedContinuation<ProximityHostActionResult, Never>?
    private var settingsObserver: NSObjectProtocol?
    private var bluetoothManager: CBCentralManager?
    private var bluetoothContinuation: CheckedContinuation<ProximityHostActionResult, Never>?

    func perform(
        _ action: ProximityRemediationAction
    ) async -> ProximityHostActionResult {
        switch action {
        case .requestBluetoothPermission:
            return await requestBluetoothPermission()
        case .openApplicationSettings, .enableBluetooth:
            return await openSettingsAndWaitForReturn()
        case .enableNFC:
            // iOS does not expose an app-addressable NFC power control.
            return .cancelled
        case .retry:
            return .completed
        case .useSupportedDevice:
            return .cancelled
        }
    }

    private func openSettingsAndWaitForReturn() async -> ProximityHostActionResult {
        guard settingsContinuation == nil, let url = URL(string: UIApplication.openSettingsURLString) else { return .failed }
        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                settingsContinuation = continuation
                settingsObserver = NotificationCenter.default.addObserver(
                    forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
                ) { [weak self] _ in
                    Task { @MainActor in self?.finishSettings(.completed) }
                }
                Task { @MainActor [weak self] in
                    if !(await UIApplication.shared.open(url)) { self?.finishSettings(.failed) }
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in self?.finishSettings(.cancelled) }
        }
    }

    private func finishSettings(_ result: ProximityHostActionResult) {
        if let settingsObserver { NotificationCenter.default.removeObserver(settingsObserver) }
        settingsObserver = nil
        let continuation = settingsContinuation
        settingsContinuation = nil
        continuation?.resume(returning: result)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        guard CBCentralManager.authorization != .notDetermined else { return }
        finishBluetoothRequest(
            CBCentralManager.authorization == .allowedAlways ? .completed : .cancelled
        )
    }

    private func requestBluetoothPermission() async -> ProximityHostActionResult {
        switch CBCentralManager.authorization {
        case .allowedAlways:
            return .completed
        case .denied, .restricted:
            return .cancelled
        case .notDetermined:
            return await withTaskCancellationHandler {
                await withCheckedContinuation { continuation in
                    bluetoothContinuation = continuation
                    bluetoothManager = CBCentralManager(delegate: self, queue: .main)
                }
            } onCancel: {
                Task { @MainActor [weak self] in
                    self?.finishBluetoothRequest(.cancelled)
                }
            }
        @unknown default:
            return .failed
        }
    }

    private func finishBluetoothRequest(_ result: ProximityHostActionResult) {
        let continuation = bluetoothContinuation
        bluetoothContinuation = nil
        bluetoothManager = nil
        continuation?.resume(returning: result)
    }
}

@MainActor
final class UnavailableProximityWalletClient: ProximityWalletClient {
    func proximityPresentationCapabilities(
        configuration: ProximityConfiguration
    ) async throws -> ProximityCapabilities {
        throw ProximityPresentationUnavailable()
    }

    func startProximityPresentation(
        configuration: ProximityConfiguration
    ) async throws -> any DemoProximityPresentationSession {
        throw ProximityPresentationUnavailable()
    }
}

private struct ProximityPresentationUnavailable: Error {}

extension ProximityState {
    var engagements: [ProximityEngagement] {
        switch self {
        case .engagementReady(let engagements), .connecting(let engagements):
            return engagements
        default:
            return []
        }
    }

    var isTerminal: Bool {
        switch self {
        case .completed, .noData, .cancelled, .failed:
            return true
        default:
            return false
        }
    }
}

private extension ProximityReview {
    var defaultSelections: [ProximityDocumentSelection] {
        documents.compactMap { document in
            guard let credential = document.credentialOptions.first else { return nil }
            return ProximityDocumentSelection(
                requestIndex: document.requestIndex,
                credentialID: credential.credentialID,
                disclosedElements: Set(credential.requestedElements.map {
                    ProximityElementReference(
                        namespace: $0.namespace,
                        elementIdentifier: $0.elementIdentifier
                    )
                })
            )
        }
    }
}
