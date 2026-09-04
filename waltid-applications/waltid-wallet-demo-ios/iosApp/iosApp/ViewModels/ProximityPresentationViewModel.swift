@preconcurrency import CoreBluetooth
import Combine
import Foundation
import UIKit
import WalletSDK

protocol DemoProximityPresentationSession: Sendable {
    var states: AsyncStream<ProximityPresentationState> { get }
    func dispatch(_ action: ProximityPresentationAction) async throws -> ProximityPresentationActionResult
    func close() async
}

extension ProximityPresentationSession: DemoProximityPresentationSession {}

@MainActor
protocol ProximityWalletClient: AnyObject {
    func startProximityPresentation(
        configuration: ProximityPresentationConfiguration
    ) async throws -> any DemoProximityPresentationSession
}

@MainActor
protocol ProximityHostActionExecutor: AnyObject {
    func perform(
        _ action: ProximityPresentationRemediationAction
    ) async -> ProximityPresentationHostActionResult
}

struct ProximityDocumentSelection: Equatable {
    let requestIndex: Int
    let credentialID: String
    let disclosedElements: Set<ProximityElementReference>
}

@MainActor
final class ProximityPresentationViewModel: ObservableObject {
    @Published private(set) var active = false
    @Published private(set) var sessionState: ProximityPresentationState?
    @Published private(set) var selections: [ProximityDocumentSelection] = []
    @Published private(set) var continueAfterResponse = false
    @Published private(set) var hostActionInProgress: ProximityPresentationRemediationAction?
    @Published private(set) var actionErrorMessage: String?
    @Published private(set) var startupFailed = false

    private let client: any ProximityWalletClient
    private let configurationProvider: @MainActor () -> ProximityPresentationConfiguration
    private let hostActions: any ProximityHostActionExecutor
    private var session: (any DemoProximityPresentationSession)?
    private var observationTask: Task<Void, Never>?
    private var hostActionTask: Task<Void, Never>?
    private var sessionGeneration: UInt64 = 0

    init(
        client: any ProximityWalletClient,
        configurationProvider: @escaping @MainActor () -> ProximityPresentationConfiguration = {
            .init()
        },
        hostActions: (any ProximityHostActionExecutor)? = nil
    ) {
        self.client = client
        self.configurationProvider = configurationProvider
        self.hostActions = hostActions ?? IOSProximityHostActionExecutor()
    }

    var review: ProximityPresentationReview? {
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
        let configuration = configurationProvider()
        observationTask = Task { [weak self] in
            guard let self else { return }
            do {
                let started = try await client.startProximityPresentation(configuration: configuration)
                guard active, sessionGeneration == generation else {
                    await started.close()
                    return
                }
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
                ProximityPresentationSubmission(
                    documents: documents,
                    continueAfterResponse: continueAfterResponse
                )
            )
        )
    }

    func decline() {
        dispatch(.decline)
    }

    func retryPrerequisites() {
        dispatch(.retryPrerequisites)
    }

    func remediate(_ action: ProximityPresentationRemediationAction) {
        guard case .checkingPrerequisites(let capabilities) = sessionState,
              capabilities.remediationActions.contains(action),
              hostActionInProgress == nil,
              let session else {
            return
        }
        hostActionInProgress = action
        actionErrorMessage = nil
        let generation = sessionGeneration
        hostActionTask = Task { [weak self] in
            guard let self else { return }
            let outcome = await hostActions.perform(action)
            guard !Task.isCancelled, active, sessionGeneration == generation else { return }
            let result: ProximityPresentationActionResult
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
        guard sessionState != nil else {
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

    func dismiss() {
        sessionGeneration &+= 1
        observationTask?.cancel()
        observationTask = nil
        hostActionTask?.cancel()
        hostActionTask = nil
        let closing = session
        session = nil
        active = false
        sessionState = nil
        selections = []
        continueAfterResponse = false
        hostActionInProgress = nil
        actionErrorMessage = nil
        startupFailed = false
        if let closing {
            Task { await closing.close() }
        }
    }

    func restart() {
        guard isTerminal else { return }
        dismiss()
        start()
    }

    private func dispatch(_ action: ProximityPresentationAction) {
        guard let session else { return }
        actionErrorMessage = nil
        let generation = sessionGeneration
        Task { [weak self] in
            let result: ProximityPresentationActionResult
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

    private func publish(_ state: ProximityPresentationState) {
        let previousExchange = review?.exchange
        sessionState = state
        if case .reviewRequired(let review) = state, previousExchange != review.exchange {
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
    private var bluetoothManager: CBCentralManager?
    private var bluetoothContinuation: CheckedContinuation<ProximityPresentationHostActionResult, Never>?

    func perform(
        _ action: ProximityPresentationRemediationAction
    ) async -> ProximityPresentationHostActionResult {
        switch action {
        case .requestBluetoothPermission:
            return await requestBluetoothPermission()
        case .openApplicationSettings, .enableBluetooth:
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return .failed }
            return await UIApplication.shared.open(url) ? .completed : .failed
        case .retry:
            return .completed
        case .useSupportedDevice:
            return .cancelled
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        guard CBCentralManager.authorization != .notDetermined else { return }
        finishBluetoothRequest(
            CBCentralManager.authorization == .allowedAlways ? .completed : .cancelled
        )
    }

    private func requestBluetoothPermission() async -> ProximityPresentationHostActionResult {
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

    private func finishBluetoothRequest(_ result: ProximityPresentationHostActionResult) {
        let continuation = bluetoothContinuation
        bluetoothContinuation = nil
        bluetoothManager = nil
        continuation?.resume(returning: result)
    }
}

@MainActor
final class UnavailableProximityWalletClient: ProximityWalletClient {
    func startProximityPresentation(
        configuration: ProximityPresentationConfiguration
    ) async throws -> any DemoProximityPresentationSession {
        throw ProximityPresentationUnavailable()
    }
}

private struct ProximityPresentationUnavailable: Error {}

extension ProximityPresentationState {
    var engagements: [ProximityPresentationEngagement] {
        switch self {
        case .engagementReady(let engagements), .connecting(let engagements):
            return engagements
        default:
            return []
        }
    }

    var isTerminal: Bool {
        switch self {
        case .completed, .cancelled, .failed:
            return true
        default:
            return false
        }
    }
}

private extension ProximityPresentationReview {
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
