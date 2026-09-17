@preconcurrency import CoreBluetooth
import Combine
import Foundation
import UIKit
import WalletSDK

protocol DemoProximityPresentationSession: Sendable {
    var states: AsyncStream<ProximityState> { get }
    func dispatch(_ action: ProximityAction) async throws -> ProximityActionResult
    func close() async
}

extension ProximitySession: DemoProximityPresentationSession {}

@MainActor
protocol ProximityWalletClient: AnyObject {
    func proximityPresentationCapabilities(configuration: ProximityConfiguration) async throws -> ProximityCapabilities
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
    @Published private(set) var pendingReviewID: ProximityReviewID?

    private let client: any ProximityWalletClient
    private let configurationProvider: @MainActor () throws -> ProximityConfiguration
    private let hostActions: any ProximityHostActionExecutor
    private var session: (any DemoProximityPresentationSession)?
    private var cleanupTask: Task<Void, Never>?
    private var pendingConfiguration: ProximityConfiguration?
    private var observationTask: Task<Void, Never>?
    private var hostActionTask: Task<Void, Never>?
    private var sessionGeneration: UInt64 = 0

    init(
        client: any ProximityWalletClient,
        configurationProvider: @escaping @MainActor () throws -> ProximityConfiguration = {
            .init()
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
        guard pendingReviewID == nil, let review else { return false }
        return Set(selections.map(\.requestIndex)) == Set(review.documents.map(\.requestIndex))
            && selections.allSatisfy { !$0.disclosedElements.isEmpty }
    }

    var isTerminal: Bool {
        startupFailed || sessionState?.isTerminal == true
    }

    var qrPayload: String? {
        sessionState?.engagements.compactMap { engagement in
            guard case .qr(let payload) = engagement else { return nil }
            return payload
        }.first
    }

    func start() {
        guard !active else { return }
        active = true
        sessionState = nil
        selections = []
        continueAfterResponse = false
        actionErrorMessage = nil
        startupFailed = false
        sessionGeneration &+= 1
        let generation = sessionGeneration
        let configuration: ProximityConfiguration
        do {
            configuration = try configurationProvider()
        } catch {
            startupFailed = true
            actionErrorMessage = error.localizedDescription
            return
        }
        pendingConfiguration = configuration
        checkPrerequisitesAndStart(configuration, generation: generation)
    }

    private func checkPrerequisitesAndStart(
        _ configuration: ProximityConfiguration,
        generation: UInt64,
        automaticPermissionAttempted: Bool = false
    ) {
        observationTask?.cancel()
        observationTask = Task { [weak self, cleanupTask] in
            guard let self else { return }
            do {
                await cleanupTask?.value
                try Task.checkCancellation()
                guard active, sessionGeneration == generation else { return }
                let capabilities = try await client.proximityPresentationCapabilities(
                    configuration: configuration
                )
                guard active, sessionGeneration == generation else { return }
                publish(.checkingPrerequisites(capabilities))
                guard active, sessionGeneration == generation else { return }
                if !automaticPermissionAttempted,
                   capabilities.remediationActions.contains(.requestBluetoothPermission) { return }
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
        do {
            guard let document = review?.documents.first(where: { $0.requestIndex == requestIndex }),
                  let credential = document.credentialOptions.first(where: { $0.credentialID == credentialID }) else {
                return
            }
            replaceSelection(
                ProximityDocumentSelection(
                    requestIndex: requestIndex,
                    credentialID: credentialID,
                    disclosedElements: Set(try credential.requestedElements.map {
                        try ProximityElementReference(
                            namespace: $0.namespace,
                            elementIdentifier: $0.elementIdentifier
                        )
                    })
                )
            )
        } catch {
            actionErrorMessage = error.localizedDescription
        }
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
        do {
            guard canApprove, let review else { return }
            let documents = try review.documents.compactMap { document -> ProximityDocumentSubmission? in
                guard let selection = selections.first(where: { $0.requestIndex == document.requestIndex }) else {
                    return nil
                }
                return try ProximityDocumentSubmission(
                    requestIndex: selection.requestIndex,
                    credentialID: selection.credentialID,
                    disclosedElements: selection.disclosedElements
                )
            }
            guard documents.count == review.documents.count else { return }
            dispatch(
                .approve(
                    reviewID: review.reviewID,
                    submission: try ProximitySubmission(
                        documents: documents,
                        continueAfterResponse: continueAfterResponse
                    )
                )
            )
        } catch {
            actionErrorMessage = error.localizedDescription
        }
    }

    func decline() {
        guard let review else { return }
        dispatch(.decline(reviewID: review.reviewID))
    }

    func retryPrerequisites() {
        if session == nil, let pendingConfiguration, hostActionInProgress == nil {
            checkPrerequisitesAndStart(pendingConfiguration, generation: sessionGeneration)
        } else { dispatch(.retryPrerequisites) }
    }

    func remediate(_ action: ProximityRemediationAction) {
        guard case .checkingPrerequisites(let capabilities) = sessionState,
              capabilities.remediationActions.contains(action),
              hostActionInProgress == nil else {
            return
        }
        let generation = sessionGeneration
        guard let session else {
            guard let pendingConfiguration else { return }
            hostActionTask = Task { [weak self] in
                await self?.remediateBeforeSession(action, configuration: pendingConfiguration, generation: generation)
            }
            return
        }
        hostActionInProgress = action
        actionErrorMessage = nil
        hostActionTask = Task { [weak self] in
            guard let self else { return }
            let outcome = await hostActions.perform(action)
            guard !Task.isCancelled, active, sessionGeneration == generation else { return }
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
        guard session != nil, sessionState != nil else {
            dismiss()
            return
        }
        guard sessionState?.legalActions.contains(.cancel) == true else { return }
        dispatch(.cancel)
    }

    func handleLifecycleInterruption() {
        guard hostActionInProgress == nil else { return }
        guard case .checkingPrerequisites = sessionState else {
            cancel()
            return
        }
    }

    func closeAndAwait() async {
        dismiss()
        await cleanupTask?.value
    }

    func dismiss() {
        sessionGeneration &+= 1
        let starting = observationTask
        let hostAction = hostActionTask
        starting?.cancel()
        hostAction?.cancel()
        observationTask = nil
        hostActionTask = nil
        let closing = session
        session = nil
        pendingConfiguration = nil
        active = false
        sessionState = nil
        selections = []
        continueAfterResponse = false
        pendingReviewID = nil
        hostActionInProgress = nil
        actionErrorMessage = nil
        startupFailed = false
        let previous = cleanupTask
        cleanupTask = Task {
            await previous?.value
            await starting?.value
            await hostAction?.value
            await closing?.close()
        }
    }

    func restart() {
        guard isTerminal else { return }
        dismiss()
        start()
    }

    private func dispatch(_ action: ProximityAction) {
        guard let session else { return }
        let reviewID: ProximityReviewID?
        switch action {
        case let .approve(id, _), let .decline(id): reviewID = id
        default: reviewID = nil
        }
        if reviewID != nil, pendingReviewID != nil { return }
        if let reviewID { pendingReviewID = reviewID }
        actionErrorMessage = nil
        let generation = sessionGeneration
        Task { [weak self] in
            do {
                let result = try await session.dispatch(action)
                guard let self, active, sessionGeneration == generation else { return }
                guard reviewID == nil || review?.reviewID == reviewID else { return }
                if case .rejected(let error) = result {
                    pendingReviewID = nil
                    actionErrorMessage = error.message
                }
            } catch {
                guard let self, active, sessionGeneration == generation else { return }
                guard reviewID == nil || review?.reviewID == reviewID else { return }
                pendingReviewID = nil
                actionErrorMessage = Self.demoSessionFailureMessage
            }
        }
    }

    private func publish(_ state: ProximityState) {
        let previousReviewID = review?.reviewID
        sessionState = state
        if review?.reviewID != pendingReviewID { pendingReviewID = nil }
        actionErrorMessage = nil
        if case .reviewRequired(let review) = state, previousReviewID != review.reviewID {
            do {
                selections = try review.defaultSelections
            } catch {
                selections = []
                actionErrorMessage = error.localizedDescription
            }
            continueAfterResponse = false
        }
    }

    private func replaceSelection(_ selection: ProximityDocumentSelection) {
        guard pendingReviewID == nil else { return }
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
    func proximityPresentationCapabilities(configuration: ProximityConfiguration) async throws -> ProximityCapabilities {
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
        get throws {
            try documents.compactMap { document in
                guard let credential = document.credentialOptions.first else { return nil }
                return ProximityDocumentSelection(
                    requestIndex: document.requestIndex,
                    credentialID: credential.credentialID,
                    disclosedElements: Set(try credential.requestedElements.map {
                        try ProximityElementReference(
                            namespace: $0.namespace,
                            elementIdentifier: $0.elementIdentifier
                        )
                    })
                )
            }
        }
    }
}
