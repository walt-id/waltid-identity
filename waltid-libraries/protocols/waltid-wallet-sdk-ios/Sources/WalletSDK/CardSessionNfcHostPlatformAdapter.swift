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

protocol IOSNfcPresentmentReservation: Sendable {
    var isValid: Bool { get }
}

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
    func reserveContactlessInterface() async throws -> any IOSNfcPresentmentReservation
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

    public convenience init() {
        self.init(environment: CoreNfcCardSessionEnvironment())
    }

    init(environment: any IOSNfcCardSessionEnvironment) {
        self.environment = environment
        coordinator = CardSessionNfcHostCoordinator(environment: environment)
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
        default:
            return .protocolError
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
    case couldNotReserveContactlessInterface
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
        case .couldNotReserveContactlessInterface:
            return "nfc_contactless_reservation_failed"
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
        case .couldNotReserveContactlessInterface:
            return "The contactless interface could not be reserved"
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

    init(environment: any IOSNfcCardSessionEnvironment) {
        self.environment = environment
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
            onClose: { [weak self] closedGeneration in
                await self?.release(generation: closedGeneration)
            }
        )
        do {
            try await core.prepare()
            return IOSNfcHostBridgeSession(core: core)
        } catch let failure as CardSessionNfcHostError {
            activeGeneration = nil
            throw failure
        } catch let failure as IOSNfcCardSessionFailure {
            activeGeneration = nil
            throw CardSessionNfcHostError(failure)
        } catch {
            activeGeneration = nil
            throw CardSessionNfcHostError.systemUnavailable
        }
    }

    private func release(generation: UInt64) {
        if activeGeneration == generation {
            activeGeneration = nil
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
    private var reservation: (any IOSNfcPresentmentReservation)?
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
        onClose: @escaping @Sendable (UInt64) async -> Void = { _ in }
    ) {
        self.generation = generation
        self.environment = environment
        self.router = router
        self.onClose = onClose
    }

    func prepare() async throws {
        guard !closed, cardSession == nil else {
            throw CardSessionNfcHostError.sessionAlreadyActive
        }
        do {
            reservation = try await environment.reserveContactlessInterface()
        } catch let failure as IOSNfcCardSessionFailure {
            throw failure
        } catch {
            throw CardSessionNfcHostError.couldNotReserveContactlessInterface
        }
        guard reservation?.isValid == true else {
            reservation = nil
            throw CardSessionNfcHostError.systemUnavailable
        }

        do {
            cardSession = try await environment.makeCardSession()
        } catch {
            reservation = nil
            throw error
        }

        eventTask = Task { [weak self] in
            guard let self else { return }
            await self.runEventLoop()
        }
    }

    func close(reason: IOSNfcHostBridgeCloseReason) async {
        await finish(reason: reason, emulationStatus: status(for: reason), invalidate: true)
    }

    private func runEventLoop() async {
        guard let cardSession else { return }
        do {
            for try await event in cardSession.events {
                if Task.isCancelled { return }
                switch event {
                case .sessionStarted:
                    cardSession.setAlertMessage(String(localized: "Ready to present credential"))
                case .readerDetected:
                    guard reservation?.isValid == true else {
                        await finish(reason: .platformUnavailable, emulationStatus: .failure, invalidate: true)
                        return
                    }
                    try await cardSession.startEmulation()
                    cardSession.setAlertMessage(String(localized: "Presenting credential to nearby reader"))
                case let .received(apdu):
                    guard reservation?.isValid == true else {
                        await finish(reason: .platformUnavailable, emulationStatus: .failure, invalidate: true)
                        return
                    }
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

        await awaitAPDUDrain()
        await router.deactivate(reason: reason)
        if let session = cardSession {
            if let emulationStatus, await session.isEmulationInProgress() {
                await session.stopEmulation(status: emulationStatus)
            }
            if invalidate {
                session.invalidate()
            }
        }
        cardSession = nil
        reservation = nil
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
private final class CoreNfcPresentmentReservation: IOSNfcPresentmentReservation, @unchecked Sendable {
    let assertion: NFCPresentmentIntentAssertion

    init(assertion: NFCPresentmentIntentAssertion) {
        self.assertion = assertion
    }

    var isValid: Bool { assertion.isValid }
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

    func reserveContactlessInterface() async throws -> any IOSNfcPresentmentReservation {
        guard #available(iOS 17.4, *) else {
            throw IOSNfcCardSessionFailure.systemUnavailable
        }
        do {
            return CoreNfcPresentmentReservation(
                assertion: try await NFCPresentmentIntentAssertion.acquire()
            )
        } catch NFCPresentmentIntentAssertion.Error.systemEligibilityFailed {
            throw IOSNfcCardSessionFailure.systemIneligible
        } catch {
            throw IOSNfcCardSessionFailure.systemUnavailable
        }
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
