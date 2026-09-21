@preconcurrency import CoreBluetooth
import Combine
import Foundation
import UIKit
import WalletSDK
import WalletDemoIdentityDocumentSupport

protocol DemoProximityPresentationSession: Sendable {
    var systemPresentationActive: Bool { get }
    var connectedRoute: ProximityConnectedRoute? { get }
    var sharingPlan: ProximitySharingPlan? { get }
    var states: AsyncStream<ProximityState> { get }
    func presentNfc() async
    func dispatch(_ action: ProximityAction) async throws -> ProximityActionResult
    func close() async
}

extension DemoProximityPresentationSession {
    var connectedRoute: ProximityConnectedRoute? { nil }
    var sharingPlan: ProximitySharingPlan? { nil }
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
    @Published private(set) var pendingReviewID: ProximityReviewID?
    @Published private(set) var sessionState: ProximityState?
    @Published private(set) var selections: [ProximityDocumentSelection] = []
    @Published private(set) var continueAfterResponse = false
    @Published private(set) var hostActionInProgress: ProximityRemediationAction?
    @Published private(set) var actionErrorMessage: String?
    @Published private(set) var startupFailed = false
    @Published private(set) var capabilities: ProximityCapabilities?
    @Published private(set) var connectedRoute: ProximityConnectedRoute?
    @Published private(set) var preferredEngagement: ProximityEngagementMethod?
    @Published private(set) var approvalMode: WalletDemoProximityApprovalMode = .askEachTime
    @Published private(set) var preparedSharing: ProximityPreparedSharing?
    @Published private(set) var recentPlan: ProximitySharingPlan?
    @Published private(set) var refreshingEngagementChoices: [ProximityEngagementMethod] = []

    var refreshingEngagement: Bool { !refreshingEngagementChoices.isEmpty }

    var showsEngagement: Bool {
        if case .engagementReady = sessionState { return true }
        return refreshingEngagement
    }

    var canChangeApprovalMode: Bool {
        if case .engagementReady = sessionState { return preparedSharing == nil }
        return false
    }

    private let client: any ProximityWalletClient
    private let configurationProvider: @MainActor () throws -> ProximityConfiguration
    private let hostActions: any ProximityHostActionExecutor
    private var session: (any DemoProximityPresentationSession)?
    private var observationTask: Task<Void, Never>?
    private var hostActionTask: Task<Void, Never>?
    private var cleanupTask: Task<Void, Never>?
    private var effectiveConfiguration: ProximityConfiguration?
    private var pendingConfiguration: ProximityConfiguration?
    private var sessionGeneration: UInt64 = 0
    private var preparedEngagementLaunched = false

    init(
        client: any ProximityWalletClient,
        configurationProvider: @escaping @MainActor () throws -> ProximityConfiguration = {
            try .init(
                session: .nfc(.init(
                    handover: .negotiatedHandover,
                    retrieval: .init(nfc: .init(), wifiAware: true),
                    qrFallback: .init(nfc: .init(), wifiAware: true)
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
        switch sessionState {
        case .reviewRequired(let review, _): return review
        case .preparationRequired(let plan, _): return plan.review
        default: return nil
        }
    }

    var preparingApproval: Bool {
        if case .preparationRequired = sessionState { return true }
        return false
    }

    var canApprove: Bool {
        guard pendingReviewID == nil, let review else { return false }
        return Set(selections.map(\.requestIndex)) == Set(review.documents.map(\.requestIndex))
            && selections.allSatisfy { selected in
                !selected.disclosedElements.isEmpty && review.documents.first(where: { $0.requestIndex == selected.requestIndex })?
                    .requiredElements.isSubset(of: selected.disclosedElements) == true
            }
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
        guard case .engagementReady(let engagements) = sessionState else { return refreshingEngagementChoices }
        let hasNFC = engagements.contains { if case .nfc = $0 { return true }; return false }
        let hasQR = engagements.contains { if case .qr = $0 { return true }; return false }
        return (hasNFC ? [.nfc] : []) + (hasQR ? [.qr] : [])
    }

    var displayedEngagement: ProximityEngagementMethod? {
        if let preferredEngagement, engagementChoices.contains(preferredEngagement) { return preferredEngagement }
        return engagementChoices == [.qr] ? .qr : nil
    }

    func showEngagement(_ method: ProximityEngagementMethod) {
        guard case .engagementReady = sessionState,
              hostActionInProgress == nil, engagementChoices.contains(method) else { return }
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

    private func readConfiguration() -> ProximityConfiguration? {
        do { return try configurationProvider() }
        catch {
            startupFailed = session == nil
            actionErrorMessage = error.localizedDescription
            return nil
        }
    }

    func start() {
        guard !active else { return }
        active = true
        preferredEngagement = nil
        refreshingEngagementChoices = []
        sessionState = nil
        selections = []
        continueAfterResponse = false
        actionErrorMessage = nil
        startupFailed = false
        sessionGeneration &+= 1
        let generation = sessionGeneration
        guard let configuration = readConfiguration() else { return }
        switch configuration.approval {
        case .askEachTime: approvalMode = .askEachTime
        case .prepareBeforeSharing: approvalMode = .prepareSharing
        case .prepared(let sharing): approvalMode = .prepareSharing; preparedSharing = sharing
        }
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
                self.capabilities = capabilities
                publish(.checkingPrerequisites(capabilities))
                guard active, sessionGeneration == generation else { return }
                let usesPreparedApproval: Bool
                if case .prepared = configuration.approval { usesPreparedApproval = true } else { usesPreparedApproval = false }
                if !usesPreparedApproval, !automaticPermissionAttempted,
                   capabilities.remediationActions.contains(.requestBluetoothPermission) {
                    // Let the host explain the request before opening the OS permission prompt.
                    return
                }
                guard capabilities.mayStart || usesPreparedApproval else { return }
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
                refreshingEngagementChoices = []
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
        guard pendingReviewID == nil else { return }
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
        guard review?.documents.first(where: { $0.requestIndex == requestIndex })?.requiredElements.contains(element) != true else { return }
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
        guard review != nil, !preparingApproval else { return }
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
            let submission = try ProximitySubmission(documents: documents, continueAfterResponse: continueAfterResponse)
            if case .preparationRequired(let plan, _) = sessionState {
                do {
                    switch try plan.approve(submission) {
                    case .prepared(let sharing):
                        guard let configuration = effectiveConfiguration else { return }
                        replaceSession(configuration.withApproval(.prepared(sharing)))
                    case .rejected(let error): actionErrorMessage = error.message
                    }
                } catch { actionErrorMessage = Self.demoSessionFailureMessage }
            } else { dispatch(.approve(reviewID: review.reviewID, submission: submission)) }
        } catch {
            actionErrorMessage = error.localizedDescription
        }
    }

    func decline() {
        if preparingApproval { dismiss(); return }
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
            replaceSession(effectiveConfiguration.withApproval(approvalMode.approval), action: action)
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
        if preparingApproval { dismiss(); return }
        guard session != nil else {
            dismiss()
            return
        }
        guard let sessionState else { dismiss(); return }
        guard sessionState.legalActions.contains(.cancel) else { return }
        dispatch(.cancel)
    }

    func handleLifecycleInterruption() {
        // CardSession presents system UI in a separate full-screen process. That transition can
        // background the host application while HCE is active, so the protocol session must stay
        // alive until CardSession, the reader, the user, or the protocol timeout closes it.
        guard session?.systemPresentationActive != true else { return }
        if preparedSharing != nil { dismiss(); return }
        guard hostActionInProgress == nil else { return }
        guard case .checkingPrerequisites = sessionState else {
            cancel()
            return
        }
    }

    func closeAndAwait() async {
        let starting = observationTask
        let hostAction = hostActionTask
        let revoking = preparedSharing
        dismiss()
        await starting?.value
        await hostAction?.value
        await revoking?.revoke()
        await cleanupTask?.value
    }

    func dismiss() {
        sessionGeneration &+= 1
        let starting = observationTask
        let hostAction = hostActionTask
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
        let revoking = preparedSharing
        preparedSharing = nil
        recentPlan = nil
        active = false
        pendingReviewID = nil
        refreshingEngagementChoices = []
        sessionState = nil
        selections = []
        continueAfterResponse = false
        hostActionInProgress = nil
        actionErrorMessage = nil
        startupFailed = false
        scheduleClose(closing, starting: starting, hostAction: hostAction, revoking: revoking)
    }

    func restart() {
        guard isTerminal else { return }
        guard let effectiveConfiguration else { return }
        if case .prepared = effectiveConfiguration.approval, showRecentRequest() { return }
        guard let configuration = readConfiguration() else { return }
        let approval = configuration.approval
        if case .askEachTime = approval { approvalMode = .askEachTime }
        else { approvalMode = .prepareSharing }
        replaceSession(configuration)
    }

    func reviewRecentRequest() { _ = showRecentRequest() }

    private func showRecentRequest() -> Bool {
        guard isTerminal, let plan = recentPlan, !plan.isExpired else { return false }
        publish(.preparationRequired(plan))
        do { selections = try plan.review.defaultSelections }
        catch { actionErrorMessage = error.localizedDescription; return false }
        preparedSharing = nil
        return true
    }

    /// Applies saved settings only before connection, without issuing or changing an approval.
    func refreshPreferences() {
        guard active, preparedSharing == nil, hostActionInProgress == nil,
              let previous = effectiveConfiguration else { return }
        switch sessionState {
        case .engagementReady: break
        case .checkingPrerequisites(let capabilities) where !capabilities.mayStart ||
            capabilities.remediationActions.contains(.requestBluetoothPermission): break
        default: return
        }
        guard let configuration = readConfiguration() else { return }
        let mode: WalletDemoProximityApprovalMode
        if case .askEachTime = configuration.approval { mode = .askEachTime }
        else { mode = .prepareSharing }
        let sameTransport = previous.session == configuration.session
        guard !sameTransport || approvalMode != mode else { return }
        approvalMode = mode
        replaceSession(configuration, preserveEngagement: sameTransport && showsEngagement)
    }

    private func replaceSession(
        _ configuration: ProximityConfiguration,
        action: ProximityRemediationAction? = nil,
        preserveEngagement: Bool = false
    ) {
        let previousChoices = engagementChoices
        // Preserve QR visibility; the replacement NFC sheet needs a fresh explicit choice.
        let previousMethod = displayedEngagement == .qr ? ProximityEngagementMethod.qr : nil
        sessionGeneration &+= 1
        let generation = sessionGeneration
        let starting = observationTask
        let hostAction = hostActionTask
        starting?.cancel()
        hostAction?.cancel()
        let closing = session
        session = nil
        effectiveConfiguration = configuration
        let previousApproval = preparedSharing
        if case .prepared(let sharing) = configuration.approval { preparedSharing = sharing }
        else { preparedSharing = nil }
        let revoking = previousApproval !== preparedSharing ? previousApproval : nil
        pendingConfiguration = configuration
        active = true
        refreshingEngagementChoices = preserveEngagement ? previousChoices : []
        sessionState = nil
        selections = []
        continueAfterResponse = false
        preferredEngagement = preserveEngagement ? previousMethod : (preparedSharing != nil ? connectedRoute?.engagement : nil)
        connectedRoute = nil
        preparedEngagementLaunched = false
        actionErrorMessage = nil
        startupFailed = false
        hostActionInProgress = action
        scheduleClose(closing, starting: starting, hostAction: hostAction, revoking: revoking)
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

    private func scheduleClose(
        _ closing: (any DemoProximityPresentationSession)?,
        starting: Task<Void, Never>? = nil,
        hostAction: Task<Void, Never>? = nil,
        revoking: ProximityPreparedSharing? = nil
    ) {
        guard closing != nil || starting != nil || hostAction != nil || revoking != nil else { return }
        let previous = cleanupTask
        cleanupTask = Task {
            await previous?.value
            await starting?.value
            await hostAction?.value
            await revoking?.revoke()
            await closing?.close()
        }
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
        switch state {
        case .preparing: break
        case .checkingPrerequisites(let capabilities) where capabilities.mayStart && !capabilities.remediationActions.contains(.requestBluetoothPermission): break
        default: refreshingEngagementChoices = []
        }
        connectedRoute = session?.connectedRoute ?? connectedRoute
        if case .checkingPrerequisites(let latest) = state { capabilities = latest }
        if case .preparationRequired(let plan, _) = state { recentPlan = plan }
        else if let plan = session?.sharingPlan { recentPlan = plan }
        if let review, previousReviewID != review.reviewID {
            do { selections = try review.defaultSelections }
            catch { selections = []; actionErrorMessage = error.localizedDescription }
            continueAfterResponse = false
        }
        refreshPreferences()
        if case .engagementReady = state, preparedSharing != nil, !preparedEngagementLaunched,
           let preferredEngagement, engagementChoices.contains(preferredEngagement) {
            preparedEngagementLaunched = true
            showEngagement(preferredEngagement)
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
        case .requestNearbyWifiPermission, .requestLocalNetworkPermission, .enableWifi:
            return .cancelled
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
        case .preparationRequired, .completed, .noData, .cancelled, .failed:
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
                guard document.credentialOptions.count == 1, let credential = document.credentialOptions.first else { return nil }
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
