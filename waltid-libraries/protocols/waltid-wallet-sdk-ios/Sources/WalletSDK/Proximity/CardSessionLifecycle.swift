#if canImport(CoreNFC) && canImport(WalletCore) && os(iOS)
import Foundation

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
    private let environment: any IOSNfcCardSessionEnvironment
    private var nextGeneration: UInt64 = 1

    private var activeGeneration: UInt64?

    private weak var activeCore: IOSCardSessionCore?

    private let onPresentment: @Sendable (IOSNfcPresentmentEvent) -> Void

    private func release(generation: UInt64) {
        if activeGeneration == generation {
            activeGeneration = nil
            activeCore = nil
        }
    }
}

actor IOSCardSessionCore {
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

    func close(reason: IOSNfcHostBridgeCloseReason) async {
        if reason == .completed || reason == .handoverCompleted {
            // The common message send stages an APDU response before Core NFC has transmitted it.
            // Drain all GET RESPONSE fragments. For NFC retrieval, Core NFC returning from
            // respond does not guarantee the reader received the bytes; keep RF alive until deselection.
            successfulCloseRequested = true
            if reason == .completed && presenting && !closed {
                waitingForReaderDeselection = true
                cardSession?.setAlertMessage(String(localized: "Response submitted. Keep near reader until its result, then separate devices."))
            }
            await withTaskCancellationHandler {
                await awaitResponseDelivery()
            } onCancel: {
                Task { await self.close(reason: .cancelled) }
            }
        }
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
    private var waitingForReaderDeselection = false

    private var successfulCloseRequested = false

    private var responseContinuationExpected = false

    private var responseDeliveryWaiters: [CheckedContinuation<Void, Never>] = []

    private var apduDrainWaiters: [CheckedContinuation<Void, Never>] = []

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
                    let completed = waitingForReaderDeselection && !apduInFlight && !responseContinuationExpected
                    await finish(reason: completed ? .completed : .peerDisconnected,
                                 emulationStatus: completed ? .success : .failure, invalidate: true)
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
        if successfulCloseRequested {
            guard responseContinuationExpected, apdu.payload.count >= 2,
                  apdu.payload[apdu.payload.index(after: apdu.payload.startIndex)] == 0xc0 else { return false }
        }
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
        if response.count >= 2 {
            let status = response.suffix(2)
            if status.first == 0x61 { responseContinuationExpected = true }
            else if status.elementsEqual([0x90, 0x00]) { responseContinuationExpected = false }
        }
        finishAPDU()
        if !responseContinuationExpected && !waitingForReaderDeselection { resumeResponseDeliveryWaiters() }
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
        resumeResponseDeliveryWaiters()
    }

    private func awaitResponseDelivery() async {
        guard !closed, apduInFlight || responseContinuationExpected || waitingForReaderDeselection else { return }
        // Reader loss, cancellation, or CardSession's own maximum-duration event also releases this wait.
        await withCheckedContinuation { responseDeliveryWaiters.append($0) }
    }

    private func resumeResponseDeliveryWaiters() {
        let waiters = responseDeliveryWaiters
        responseDeliveryWaiters.removeAll()
        waiters.forEach { $0.resume() }
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
#endif
