#if canImport(CoreNFC) && canImport(WalletCore) && os(iOS)
import CoreNFC
import Foundation
@preconcurrency import WalletCore

enum IOSNfcCardSessionFailure: Error, Sendable, Equatable {
    case invalidated
    case userInvalidated
    case maximumDurationReached
    case transmissionError
    case systemUnavailable
    case accessNotAccepted
    case systemIneligible
    case emulationStopped
    case radioDisabled
}

enum IOSNfcEmulationStatus: Sendable, Equatable {
    case success
    case failure
}

protocol IOSNfcPresentmentIntent: Sendable {}

protocol IOSNfcCardSessionAPDU: Sendable {
    var payload: Data { get }
    func respond(response: Data) async throws
}

enum IOSNfcCardSessionEvent: Sendable {
    case sessionStarted
    case readerDetected
    case received(any IOSNfcCardSessionAPDU)
    case readerDeselected
    case sessionInvalidated(reason: IOSNfcCardSessionFailure)
}

protocol IOSNfcCardSession: AnyObject, Sendable {
    var events: AsyncThrowingStream<IOSNfcCardSessionEvent, Error> { get }
    func setAlertMessage(_ message: String)
    func isEmulationInProgress() async -> Bool
    func startEmulation() async throws
    func stopEmulation(status: IOSNfcEmulationStatus) async
    func invalidate()
}

protocol IOSNfcCardSessionEnvironment: Sendable {
    func readingAvailable() -> Bool
    func cardSessionSupported() -> Bool
    func cardSessionEligible() async -> Bool
    func acquirePresentmentIntent() async throws -> (any IOSNfcPresentmentIntent)?
    func makeCardSession() async throws -> any IOSNfcCardSession
}

protocol IOSNfcHostApduRouting: Sendable {
    func process(_ command: Data) async throws -> Data
    func deactivate(reason: IOSNfcHostBridgeCloseReason) async
}

/// Framework-neutral close reasons used by the Swift CardSession implementation.
///
/// Kotlin/Native gives the same Kotlin enum a different Swift identity in every independently
/// linked framework. This bridge type keeps Core NFC lifecycle state independent of those generated
/// types while framework-specific adapters perform the mechanical mapping at their boundary.
@_spi(KmpHostBridge)
public enum IOSNfcHostBridgeCloseReason: Sendable, Equatable {
    case completed
    case handoverCompleted
    case cancelled
    case lostRace
    case timeout
    case peerDisconnected
    case protocolError
    case platformUnavailable
}

/// Stable prerequisite or preparation failure exposed to KMP framework adapters.
@_spi(KmpHostBridge)
public struct IOSNfcHostBridgeUnavailable: Error, Sendable, Equatable {
    public let code: String
    public let message: String

    public init(code: String, message: String) {
        self.code = code
        self.message = message
    }
}

/// Side-effect-free CardSession capability result independent of a generated KMP framework.
@_spi(KmpHostBridge)
public enum IOSNfcHostBridgeAvailability: Sendable, Equatable {
    case available
    case unavailable(IOSNfcHostBridgeUnavailable)
}

/// Actual Core NFC modal-presentment transitions, independent of session configuration.
@_spi(KmpHostBridge)
public enum IOSNfcPresentmentEvent: Sendable, Equatable {
    case began
    case ended(IOSNfcHostBridgeCloseReason)
    case invalidated(IOSNfcHostBridgeCloseReason)
}

/// Synchronous observation prevents a background notification from overtaking stream delivery.
private final class IOSNfcPresentmentState: @unchecked Sendable {
    private let lock = NSLock()
    private var active = false
    private let observer: @Sendable (IOSNfcPresentmentEvent) -> Void

    init(observer: @escaping @Sendable (IOSNfcPresentmentEvent) -> Void) {
        self.observer = observer
    }

    var isActive: Bool {
        lock.lock()
        defer { lock.unlock() }
        return active
    }

    func update(_ event: IOSNfcPresentmentEvent) {
        lock.lock()
        active = event == .began
        lock.unlock()
        observer(event)
    }
}

/// Result of arming one generation-bound Swift CardSession bridge.
@_spi(KmpHostBridge)
public enum IOSNfcHostBridgePreparation: Sendable {
    case ready(IOSNfcHostBridgeSession)
    case unavailable(IOSNfcHostBridgeUnavailable)
}

/// Prepared CardSession host that is independent of any generated Kotlin protocol identity.
@_spi(KmpHostBridge)
public final class IOSNfcHostBridgeSession: @unchecked Sendable {
    private let core: IOSCardSessionCore

    init(core: IOSCardSessionCore) {
        self.core = core
    }

    public func close(reason: IOSNfcHostBridgeCloseReason) async {
        await core.close(reason: reason)
    }

    deinit {
        let core = core
        Task {
            await core.close(reason: .cancelled)
        }
    }
}

/// Swift-native CardSession implementation shared by every independently linked KMP framework.
@_spi(KmpHostBridge)
public final class IOSNfcHostBridge: @unchecked Sendable {
    private let environment: any IOSNfcCardSessionEnvironment
    private let coordinator: CardSessionNfcHostCoordinator
    private let presentment: IOSNfcPresentmentState

    /// True only while Core NFC is entering or displaying its emulation UI.
    public var isPresenting: Bool { presentment.isActive }

    /// Opens Core NFC's sheet for the prepared session after an explicit user action.
    /// This preserves the engagement and keys already advertised to the reader.
    public func present() async {
        await coordinator.present()
    }

    public convenience init(
        onPresentment: @escaping @Sendable (IOSNfcPresentmentEvent) -> Void = { _ in }
    ) {
        self.init(environment: CoreNfcCardSessionEnvironment(), onPresentment: onPresentment)
    }

    init(
        environment: any IOSNfcCardSessionEnvironment,
        onPresentment: @escaping @Sendable (IOSNfcPresentmentEvent) -> Void = { _ in }
    ) {
        self.environment = environment
        let presentment = IOSNfcPresentmentState(observer: onPresentment)
        self.presentment = presentment
        coordinator = CardSessionNfcHostCoordinator(
            environment: environment,
            onPresentment: { event in presentment.update(event) }
        )
    }

    public func capability() async -> IOSNfcHostBridgeAvailability {
        guard environment.readingAvailable() else {
            return .unavailable(
                IOSNfcHostBridgeUnavailable(
                    code: "nfc_reading_unavailable",
                    message: "NFC card presentation is unavailable on this device"
                )
            )
        }
        guard environment.cardSessionSupported() else {
            return .unavailable(
                IOSNfcHostBridgeUnavailable(
                    code: "nfc_card_session_unsupported",
                    message: "This iPhone does not support NFC card presentation"
                )
            )
        }
        guard await environment.cardSessionEligible() else {
            return .unavailable(
                IOSNfcHostBridgeUnavailable(
                    code: "nfc_system_ineligible",
                    message: "NFC card presentation is unavailable in the current system environment"
                )
            )
        }
        return .available
    }

    public func prepare(
        process: @escaping @Sendable (Data) async throws -> Data,
        deactivate: @escaping @Sendable (IOSNfcHostBridgeCloseReason) async -> Void
    ) async -> IOSNfcHostBridgePreparation {
        if case let .unavailable(reason) = await capability() {
            return .unavailable(reason)
        }
        do {
            let session = try await coordinator.prepare(
                router: ClosureNfcHostApduRouter(process: process, deactivate: deactivate)
            )
            return .ready(session)
        } catch let failure as CardSessionNfcHostError {
            return .unavailable(
                IOSNfcHostBridgeUnavailable(code: failure.code, message: failure.message)
            )
        } catch {
            return .unavailable(
                IOSNfcHostBridgeUnavailable(
                    code: CardSessionNfcHostError.systemUnavailable.code,
                    message: CardSessionNfcHostError.systemUnavailable.message
                )
            )
        }
    }
}

private struct ClosureNfcHostApduRouter: IOSNfcHostApduRouting, @unchecked Sendable {
    let processClosure: @Sendable (Data) async throws -> Data
    let deactivateClosure: @Sendable (IOSNfcHostBridgeCloseReason) async -> Void

    init(
        process: @escaping @Sendable (Data) async throws -> Data,
        deactivate: @escaping @Sendable (IOSNfcHostBridgeCloseReason) async -> Void
    ) {
        processClosure = process
        deactivateClosure = deactivate
    }

    func process(_ command: Data) async throws -> Data {
        try await processClosure(command)
    }

    func deactivate(reason: IOSNfcHostBridgeCloseReason) async {
        await deactivateClosure(reason)
    }
}

/// Core NFC host-card-emulation boundary used by the shared proximity engine.
///
/// Apple does not expose a public API for reading the app's signed HCE entitlements. The adapter
/// therefore follows the documented fail-closed sequence: `readingAvailable`, `isSupported`, and
/// `isEligible` are all checked before `CardSession` is initialized.
public final class IOSNfcHostPlatformAdapter:
    Waltid_mdoc_proximity_mobileNfcHostPlatformAdapter,
    @unchecked Sendable {
    private let bridge: IOSNfcHostBridge

    var isPresenting: Bool { bridge.isPresenting }

    func present() async {
        await bridge.present()
    }

    /// Creates the Core NFC host adapter used by an iOS KMP wallet host.
    ///
    /// The Swift `Wallet` facade installs this adapter automatically. A host that independently
    /// links another Kotlin framework uses that framework's narrow adapter over the same Swift
    /// CardSession bridge because Kotlin/Native exports distinct nominal protocol identities.
    public convenience init() {
        self.init(bridge: IOSNfcHostBridge())
    }

    init(environment: any IOSNfcCardSessionEnvironment) {
        self.bridge = IOSNfcHostBridge(environment: environment)
    }

    private init(bridge: IOSNfcHostBridge) {
        self.bridge = bridge
    }

    public func __capability() async throws -> any Waltid_mdoc_proximity_mobileNfcHostAvailability {
        switch await bridge.capability() {
        case .available:
            return Waltid_mdoc_proximity_mobileNfcHostAvailabilityAvailable()
        case let .unavailable(reason):
            return unavailable(reason)
        }
    }

    public func __prepare(
        router: Waltid_mdoc_proximity_mobileNfcHostApduRouter,
        sessionScope _: any Kotlinx_coroutines_coreCoroutineScope
    ) async throws -> any Waltid_mdoc_proximity_mobileNfcHostPreparation {
        let routerBridge = WalletCoreNfcHostRouter(router: router)
        switch await bridge.prepare(
            process: { command in
                try await routerBridge.process(command)
            },
            deactivate: { reason in
                await routerBridge.deactivate(reason)
            }
        ) {
        case let .ready(session):
            return Waltid_mdoc_proximity_mobileNfcHostPreparationReady(
                session: IOSPreparedNfcHostSession(bridgeSession: session)
            )
        case let .unavailable(reason):
            return Waltid_mdoc_proximity_mobileNfcHostPreparationUnavailable(
                availability: unavailable(reason)
            )
        }
    }

    private func unavailable(
        _ reason: IOSNfcHostBridgeUnavailable
    ) -> Waltid_mdoc_proximity_mobileNfcHostAvailabilityUnavailable {
        Waltid_mdoc_proximity_mobileNfcHostAvailabilityUnavailable(
            code: reason.code,
            message: reason.message
        )
    }
}

/// Serializes access to the generated Kotlin router at the Swift concurrency boundary.
private actor WalletCoreNfcHostRouter {
    private let router: Waltid_mdoc_proximity_mobileNfcHostApduRouter

    init(router: Waltid_mdoc_proximity_mobileNfcHostApduRouter) {
        self.router = router
    }

    func process(_ command: Data) async throws -> Data {
        try await router.process(encodedCommand: command.nfcKotlinByteArray()).doCopy().nfcData()
    }

    func deactivate(_ reason: IOSNfcHostBridgeCloseReason) async {
        try? await router.deactivate(reason: reason.walletCoreReason)
    }
}

private extension Waltid_mdoc_proximityProximityCloseReason {
    var nfcHostBridgeReason: IOSNfcHostBridgeCloseReason {
        switch self {
        case .completed:
            return .completed
        case .handoverCompleted:
            return .handoverCompleted
        case .cancelled:
            return .cancelled
        case .lostRace:
            return .lostRace
        case .timeout:
            return .timeout
        case .peerDisconnected:
            return .peerDisconnected
        case .protocolError:
            return .protocolError
        case .platformUnavailable:
            return .platformUnavailable
        }
    }
}

private extension IOSNfcHostBridgeCloseReason {
    var walletCoreReason: Waltid_mdoc_proximityProximityCloseReason {
        switch self {
        case .completed:
            return .completed
        case .handoverCompleted:
            return .handoverCompleted
        case .cancelled:
            return .cancelled
        case .lostRace:
            return .lostRace
        case .timeout:
            return .timeout
        case .peerDisconnected:
            return .peerDisconnected
        case .protocolError:
            return .protocolError
        case .platformUnavailable:
            return .platformUnavailable
        }
    }
}

enum CardSessionNfcHostError: LocalizedError, Sendable, Equatable {
    case sessionAlreadyActive
    case accessNotAccepted
    case radioDisabled
    case systemIneligible
    case systemUnavailable

    init(_ failure: IOSNfcCardSessionFailure) {
        switch failure {
        case .accessNotAccepted:
            self = .accessNotAccepted
        case .radioDisabled:
            self = .radioDisabled
        case .systemIneligible:
            self = .systemIneligible
        case .invalidated,
             .userInvalidated,
             .maximumDurationReached,
             .transmissionError,
             .systemUnavailable,
             .emulationStopped:
            self = .systemUnavailable
        }
    }

    var code: String {
        switch self {
        case .sessionAlreadyActive:
            return "nfc_session_already_active"
        case .accessNotAccepted:
            return "nfc_access_not_accepted"
        case .radioDisabled:
            return "nfc_powered_off"
        case .systemIneligible:
            return "nfc_system_ineligible"
        case .systemUnavailable:
            return "nfc_system_unavailable"
        }
    }

    var message: String {
        switch self {
        case .sessionAlreadyActive:
            return "An NFC card presentation is already active"
        case .accessNotAccepted:
            return "NFC card presentation access has not been accepted"
        case .radioDisabled:
            return "NFC is powered off"
        case .systemIneligible:
            return "NFC card presentation is unavailable in the current system environment"
        case .systemUnavailable:
            return "The NFC card presentation service is temporarily unavailable"
        }
    }

    var errorDescription: String? { message }
}

actor CardSessionNfcHostCoordinator {
    private let environment: any IOSNfcCardSessionEnvironment
    private var nextGeneration: UInt64 = 1
    private var activeGeneration: UInt64?
    private weak var activeCore: IOSCardSessionCore?
    private let onPresentment: @Sendable (IOSNfcPresentmentEvent) -> Void

    init(
        environment: any IOSNfcCardSessionEnvironment,
        onPresentment: @escaping @Sendable (IOSNfcPresentmentEvent) -> Void = { _ in }
    ) {
        self.environment = environment
        self.onPresentment = onPresentment
    }

    func prepare(router: any IOSNfcHostApduRouting) async throws -> IOSNfcHostBridgeSession {
        guard activeGeneration == nil else {
            throw CardSessionNfcHostError.sessionAlreadyActive
        }
        let generation = nextGeneration
        guard generation != UInt64.max else {
            throw CardSessionNfcHostError.systemUnavailable
        }
        nextGeneration += 1
        activeGeneration = generation

        let core = IOSCardSessionCore(
            generation: generation,
            environment: environment,
            router: router,
            onPresentment: onPresentment,
            onClose: { [weak self] closedGeneration in
                await self?.release(generation: closedGeneration)
            }
        )
        do {
            try await core.prepare()
            if activeGeneration == generation { activeCore = core }
            return IOSNfcHostBridgeSession(core: core)
        } catch let failure as CardSessionNfcHostError {
            release(generation: generation)
            throw failure
        } catch let failure as IOSNfcCardSessionFailure {
            release(generation: generation)
            throw CardSessionNfcHostError(failure)
        } catch {
            release(generation: generation)
            throw CardSessionNfcHostError.systemUnavailable
        }
    }

    func present() async {
        await activeCore?.present()
    }

    private func release(generation: UInt64) {
        if activeGeneration == generation {
            activeGeneration = nil
            activeCore = nil
        }
    }
}

final class IOSPreparedNfcHostSession:
    Waltid_mdoc_proximity_mobilePreparedNfcHostSession,
    @unchecked Sendable {
    private let bridgeSession: IOSNfcHostBridgeSession

    init(bridgeSession: IOSNfcHostBridgeSession) {
        self.bridgeSession = bridgeSession
    }

    func __close(reason: Waltid_mdoc_proximityProximityCloseReason) async throws {
        await bridgeSession.close(reason: reason.nfcHostBridgeReason)
    }
}

actor IOSCardSessionCore {
    private static let conditionsNotSatisfied = Data([0x69, 0x85])
    private static let unknownErrorResponse = Data([0x6f, 0x00])

    private let generation: UInt64
    private let environment: any IOSNfcCardSessionEnvironment
    private let router: any IOSNfcHostApduRouting
    private let onClose: @Sendable (UInt64) async -> Void
    private let onPresentment: @Sendable (IOSNfcPresentmentEvent) -> Void
    private enum PresentmentPhase {
        case awaitingSession, requested, ready, presenting
    }
    private var presentmentPhase = PresentmentPhase.awaitingSession
    private var presenting: Bool { presentmentPhase == .presenting }
    private var presentmentIntent: (any IOSNfcPresentmentIntent)?
    private var cardSession: (any IOSNfcCardSession)?
    private var eventTask: Task<Void, Never>?
    private var apduTask: Task<Void, Never>?
    private var closed = false
    private var apduInFlight = false
    private var apduDrainWaiters: [CheckedContinuation<Void, Never>] = []

    init(
        generation: UInt64,
        environment: any IOSNfcCardSessionEnvironment,
        router: any IOSNfcHostApduRouting,
        onPresentment: @escaping @Sendable (IOSNfcPresentmentEvent) -> Void = { _ in },
        onClose: @escaping @Sendable (UInt64) async -> Void = { _ in }
    ) {
        self.generation = generation
        self.environment = environment
        self.router = router
        self.onPresentment = onPresentment
        self.onClose = onClose
    }

    func prepare() async throws {
        try await withTaskCancellationHandler {
            do {
                try await prepareResources()
            } catch {
                await close(reason: .cancelled)
                throw error
            }
        } onCancel: {
            Task { await self.close(reason: .cancelled) }
        }
    }

    private func prepareResources() async throws {
        guard !closed, cardSession == nil else {
            throw CardSessionNfcHostError.sessionAlreadyActive
        }
        try Task.checkCancellation()
        // The optional assertion suppresses the default contactless app for up to 15 seconds.
        // It neither owns the emulation UI nor changes the host's background policy.
        do {
            let intent = try await environment.acquirePresentmentIntent()
            try Task.checkCancellation()
            guard !closed else { throw CancellationError() }
            presentmentIntent = intent
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            presentmentIntent = nil
        }
        try Task.checkCancellation()
        guard !closed else { throw CancellationError() }
        let created = try await environment.makeCardSession()
        guard !closed, !Task.isCancelled else {
            created.invalidate()
            throw CancellationError()
        }
        cardSession = created
        eventTask = Task { [weak self] in
            guard let self else { return }
            await self.runEventLoop()
        }
    }

    func close(reason: IOSNfcHostBridgeCloseReason) async {
        await finish(reason: reason, emulationStatus: status(for: reason), invalidate: true)
    }

    func present() async {
        guard !closed else { return }
        switch presentmentPhase {
        case .awaitingSession, .requested:
            presentmentPhase = .requested
        case .ready:
            await startPresentment()
        case .presenting:
            break
        }
    }

    private func startPresentment() async {
        guard !closed, !presenting, let cardSession else { return }
        // Core NFC can background the host before startEmulation returns.
        presentmentPhase = .presenting
        onPresentment(.began)
        cardSession.setAlertMessage(String(localized: "Hold your iPhone near the reader"))
        do {
            try await cardSession.startEmulation()
            guard !closed else {
                await cardSession.stopEmulation(status: .failure)
                cardSession.invalidate()
                return
            }
            cardSession.setAlertMessage(String(localized: "Presenting credential to nearby reader"))
        } catch {
            await finish(reason: .platformUnavailable, emulationStatus: .failure, invalidate: true)
        }
    }

    private func runEventLoop() async {
        guard let cardSession else { return }
        do {
            for try await event in cardSession.events {
                if Task.isCancelled || closed { return }
                switch event {
                case .sessionStarted:
                    cardSession.setAlertMessage(String(localized: "Ready to present credential"))
                    if presentmentPhase == .requested {
                        await startPresentment()
                    } else if presentmentPhase == .awaitingSession {
                        presentmentPhase = .ready
                    }
                case .readerDetected:
                    await startPresentment()
                    if closed { return }
                case let .received(apdu):
                    if !startProcessing(apdu: apdu) {
                        await respondIgnoringFailure(Self.conditionsNotSatisfied, to: apdu)
                    }
                case .readerDeselected:
                    await finish(reason: .peerDisconnected, emulationStatus: .failure, invalidate: true)
                    return
                case let .sessionInvalidated(reason):
                    await finish(
                        reason: closeReason(for: reason),
                        emulationStatus: nil,
                        invalidate: false
                    )
                    return
                }
            }
            await finish(reason: .platformUnavailable, emulationStatus: nil, invalidate: false)
        } catch is CancellationError {
            // Explicit close owns cleanup and common-router deactivation.
        } catch {
            await finish(reason: .platformUnavailable, emulationStatus: .failure, invalidate: true)
        }
    }

    private func startProcessing(apdu: any IOSNfcCardSessionAPDU) -> Bool {
        guard !closed, !apduInFlight else { return false }
        apduInFlight = true
        apduTask = Task { [weak self] in
            await self?.process(apdu: apdu)
        }
        return true
    }

    private func process(apdu: any IOSNfcCardSessionAPDU) async {
        let response: Data
        do {
            response = try await router.process(apdu.payload)
        } catch is CancellationError {
            if !closed {
                await respondIgnoringFailure(Self.unknownErrorResponse, to: apdu)
            }
            finishAPDU()
            await finish(reason: .cancelled, emulationStatus: .failure, invalidate: true)
            return
        } catch {
            if !closed {
                await respondIgnoringFailure(Self.unknownErrorResponse, to: apdu)
            }
            finishAPDU()
            await finish(reason: .protocolError, emulationStatus: .failure, invalidate: true)
            return
        }

        guard !closed else {
            finishAPDU()
            return
        }
        do {
            try await send(response, to: apdu)
        } catch {
            finishAPDU()
            await finish(reason: .peerDisconnected, emulationStatus: .failure, invalidate: true)
            return
        }
        finishAPDU()
    }

    private func send(_ response: Data, to apdu: any IOSNfcCardSessionAPDU) async throws {
        do {
            try await apdu.respond(response: response)
        } catch IOSNfcCardSessionFailure.transmissionError {
            // Apple permits retrying the same logical response after a transmission error.
            try await apdu.respond(response: response)
        }
    }

    private func respondIgnoringFailure(
        _ response: Data,
        to apdu: any IOSNfcCardSessionAPDU
    ) async {
        try? await send(response, to: apdu)
    }

    private func finish(
        reason: IOSNfcHostBridgeCloseReason,
        emulationStatus: IOSNfcEmulationStatus?,
        invalidate: Bool
    ) async {
        guard !closed else { return }
        closed = true

        let task = eventTask
        eventTask = nil
        task?.cancel()
        apduTask?.cancel()

        if let session = cardSession {
            if let emulationStatus, await session.isEmulationInProgress() {
                await session.stopEmulation(status: emulationStatus)
            }
            if invalidate {
                session.invalidate()
            }
        }
        if presenting || !invalidate {
            presentmentPhase = .ready
            onPresentment(invalidate ? .ended(reason) : .invalidated(reason))
        }
        cardSession = nil
        presentmentIntent = nil
        await awaitAPDUDrain()
        await router.deactivate(reason: reason)
        await onClose(generation)
    }

    private func finishAPDU() {
        guard apduInFlight else { return }
        apduInFlight = false
        apduTask = nil
        let waiters = apduDrainWaiters
        apduDrainWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }

    private func awaitAPDUDrain() async {
        guard apduInFlight else { return }
        await withCheckedContinuation { continuation in
            apduDrainWaiters.append(continuation)
        }
    }

    private func closeReason(
        for error: IOSNfcCardSessionFailure
    ) -> IOSNfcHostBridgeCloseReason {
        switch error {
        case .userInvalidated:
            return .cancelled
        case .maximumDurationReached:
            return .timeout
        case .transmissionError, .emulationStopped, .invalidated:
            return .peerDisconnected
        case .systemUnavailable,
             .accessNotAccepted,
             .systemIneligible,
             .radioDisabled:
            return .platformUnavailable
        }
    }

    private func status(
        for reason: IOSNfcHostBridgeCloseReason
    ) -> IOSNfcEmulationStatus {
        switch reason {
        case .completed, .handoverCompleted:
            return .success
        case .cancelled,
             .lostRace,
             .timeout,
             .peerDisconnected,
             .protocolError,
             .platformUnavailable:
            return .failure
        }
    }
}

@available(iOS 17.4, *)
private final class CoreNfcPresentmentIntent: IOSNfcPresentmentIntent, @unchecked Sendable {
    let assertion: NFCPresentmentIntentAssertion

    init(assertion: NFCPresentmentIntentAssertion) {
        self.assertion = assertion
    }

}

private final class CoreNfcCardSessionEnvironment: IOSNfcCardSessionEnvironment, @unchecked Sendable {
    func readingAvailable() -> Bool {
        NFCReaderSession.readingAvailable
    }

    func cardSessionSupported() -> Bool {
        guard #available(iOS 17.4, *) else { return false }
        return CardSession.isSupported
    }

    func cardSessionEligible() async -> Bool {
        guard #available(iOS 17.4, *) else { return false }
        return await CardSession.isEligible
    }

    func acquirePresentmentIntent() async throws -> (any IOSNfcPresentmentIntent)? {
        guard #available(iOS 17.4, *) else { return nil }
        return CoreNfcPresentmentIntent(
            assertion: try await NFCPresentmentIntentAssertion.acquire()
        )
    }

    func makeCardSession() async throws -> any IOSNfcCardSession {
        guard #available(iOS 17.4, *) else {
            throw IOSNfcCardSessionFailure.systemUnavailable
        }
        do {
            return CoreNfcCardSession(session: try await CardSession())
        } catch let error as CardSession.Error {
            throw error.walletFailure
        } catch {
            throw IOSNfcCardSessionFailure.systemUnavailable
        }
    }
}

@available(iOS 17.4, *)
private final class CoreNfcCardSession: IOSNfcCardSession, @unchecked Sendable {
    private let session: CardSession

    init(session: CardSession) {
        self.session = session
    }

    var events: AsyncThrowingStream<IOSNfcCardSessionEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task { [session] in
                do {
                    for try await event in session.eventStream {
                        continuation.yield(event.walletEvent)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func setAlertMessage(_ message: String) {
        session.alertMessage = message
    }

    func isEmulationInProgress() async -> Bool {
        await session.isEmulationInProgress
    }

    func startEmulation() async throws {
        do {
            try await session.startEmulation()
        } catch let error as CardSession.Error {
            throw error.walletFailure
        }
    }

    func stopEmulation(status: IOSNfcEmulationStatus) async {
        await session.stopEmulation(status: status == .success ? .success : .failure)
    }

    func invalidate() {
        session.invalidate()
    }
}

@available(iOS 17.4, *)
private struct CoreNfcCardSessionAPDU: IOSNfcCardSessionAPDU {
    let apdu: CardSession.APDU

    var payload: Data { apdu.payload }

    func respond(response: Data) async throws {
        do {
            try await apdu.respond(response: response)
        } catch let error as CardSession.Error {
            throw error.walletFailure
        }
    }
}

@available(iOS 17.4, *)
private extension CardSession.Event {
    var walletEvent: IOSNfcCardSessionEvent {
        switch self {
        case .sessionStarted:
            return .sessionStarted
        case .readerDetected:
            return .readerDetected
        case let .received(apdu):
            return .received(CoreNfcCardSessionAPDU(apdu: apdu))
        case .readerDeselected:
            return .readerDeselected
        case let .sessionInvalidated(reason):
            return .sessionInvalidated(reason: reason.walletFailure)
        @unknown default:
            return .sessionInvalidated(reason: .systemUnavailable)
        }
    }
}

@available(iOS 17.4, *)
private extension CardSession.Error {
    var walletFailure: IOSNfcCardSessionFailure {
        switch self {
        case .invalidated:
            return .invalidated
        case .userInvalidated:
            return .userInvalidated
        case .maxSessionDurationReached:
            return .maximumDurationReached
        case .transmissionError:
            return .transmissionError
        case .systemNotAvailable:
            return .systemUnavailable
        case .accessNotAccepted:
            return .accessNotAccepted
        case .systemEligibilityFailed:
            return .systemIneligible
        case .emulationStopped:
            return .emulationStopped
        case .radioDisabled:
            return .radioDisabled
        @unknown default:
            return .systemUnavailable
        }
    }
}

private extension Data {
    func nfcKotlinByteArray() -> KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            result.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return result
    }
}

private extension KotlinByteArray {
    func nfcData() -> Data {
        Data((0..<Int(size)).map { UInt8(bitPattern: get(index: Int32($0))) })
    }
}
#endif
