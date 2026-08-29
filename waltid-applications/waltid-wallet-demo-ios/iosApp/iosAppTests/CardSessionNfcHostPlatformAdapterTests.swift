#if canImport(CoreNFC) && canImport(WalletCore) && os(iOS)
import Foundation
import WalletCore
@testable @_spi(KmpHostBridge) import WalletSDK
import XCTest

final class IOSNfcHostPlatformAdapterTests: XCTestCase {
    func testCapabilityReportsReadingAndCardSessionPrerequisitesWithoutSideEffects() async throws {
        let scenarios: [(FakeNfcEnvironment, String)] = [
            (FakeNfcEnvironment(reading: false), "nfc_reading_unavailable"),
            (FakeNfcEnvironment(supported: false), "nfc_card_session_unsupported"),
        ]

        for (environment, expectedCode) in scenarios {
            let capability = try await IOSNfcHostPlatformAdapter(environment: environment).__capability()
            let unavailable = try XCTUnwrap(
                capability as? Waltid_mdoc_proximity_mobileNfcHostAvailabilityUnavailable
            )
            XCTAssertEqual(unavailable.code, expectedCode)
            XCTAssertEqual(environment.reservationCount, 0)
            XCTAssertEqual(environment.sessionCount, 0)
        }
    }

    func testCapabilityDoesNotCreateSessionAndReportsSystemIneligibility() async throws {
        let environment = FakeNfcEnvironment(eligible: false)
        let adapter = IOSNfcHostPlatformAdapter(environment: environment)

        let capability = try await adapter.__capability()

        let unavailable = try XCTUnwrap(
            capability as? Waltid_mdoc_proximity_mobileNfcHostAvailabilityUnavailable
        )
        XCTAssertEqual(unavailable.code, "nfc_system_ineligible")
        XCTAssertEqual(environment.reservationCount, 0)
        XCTAssertEqual(environment.sessionCount, 0)
    }

    func testFrameworkNeutralBridgeRoutesOneSessionWithoutGeneratedKmpTypes() async throws {
        let cardSession = FakeNfcCardSession()
        let callbacks = BridgeNfcCallbacks(response: Data([0x90, 0x00]))
        let bridge = IOSNfcHostBridge(environment: FakeNfcEnvironment(session: cardSession))

        let preparation = await bridge.prepare(
            process: { command in await callbacks.process(command) },
            deactivate: { reason in await callbacks.deactivate(reason) }
        )
        guard case let .ready(preparedSession) = preparation else {
            return XCTFail("The framework-neutral CardSession bridge was unavailable")
        }

        let apdu = FakeNfcAPDU(payload: Data([0x00, 0xa4, 0x04, 0x00]))
        cardSession.emit(.readerDetected)
        cardSession.emit(.received(apdu))
        await assertEventually { await apdu.responseCount == 1 }
        await preparedSession.close(reason: .completed)

        let commands = await callbacks.commands
        let deactivations = await callbacks.deactivations
        let responses = await apdu.responses
        XCTAssertEqual(commands, [Data([0x00, 0xa4, 0x04, 0x00])])
        XCTAssertEqual(deactivations, [.completed])
        XCTAssertEqual(responses, [Data([0x90, 0x00])])
    }

    func testRoutesOneAPDUAndClosesCompletedSessionExactlyOnce() async throws {
        let session = FakeNfcCardSession()
        let environment = FakeNfcEnvironment(session: session)
        let router = FakeNfcRouter(response: Data([0x90, 0x00]))
        let core = IOSCardSessionCore(generation: 1, environment: environment, router: router)
        let apdu = FakeNfcAPDU(payload: Data([0x00, 0xa4, 0x04, 0x00]))

        try await core.prepare()
        session.emit(.sessionStarted)
        session.emit(.readerDetected)
        session.emit(.received(apdu))

        await assertEventually { await apdu.responseCount == 1 }
        await core.close(reason: .completed)
        await core.close(reason: .cancelled)

        let commands = await router.commands
        let deactivations = await router.deactivations
        let responses = await apdu.responses
        XCTAssertEqual(commands, [Data([0x00, 0xa4, 0x04, 0x00])])
        XCTAssertEqual(deactivations, [.completed])
        XCTAssertEqual(responses, [Data([0x90, 0x00])])
        XCTAssertEqual(session.startCount, 1)
        XCTAssertEqual(session.stopStatuses, [.success])
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testCompletedHandoverStopsNfcEmulationAsSuccess() async throws {
        let session = FakeNfcCardSession()
        let router = FakeNfcRouter(response: Data([0x90, 0x00]))
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )

        try await core.prepare()
        session.emit(.readerDetected)
        await assertEventually { session.startCount == 1 }
        await core.close(reason: .handoverCompleted)

        let deactivations = await router.deactivations
        XCTAssertEqual(deactivations, [.handoverCompleted])
        XCTAssertEqual(session.stopStatuses, [.success])
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testSecondAPDUWhileFirstIsPendingIsRejectedWithoutReordering() async throws {
        let session = FakeNfcCardSession()
        let router = BlockingNfcRouter(response: Data([0x90, 0x00]))
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )
        let first = FakeNfcAPDU(payload: Data([0x00, 0xc3, 0x00, 0x00]))
        let second = FakeNfcAPDU(payload: Data([0x00, 0xc3, 0x00, 0x00]))

        try await core.prepare()
        session.emit(.readerDetected)
        session.emit(.received(first))
        await router.waitUntilEntered()
        session.emit(.received(second))

        await assertEventually { await second.responseCount == 1 }
        let rejectedResponses = await second.responses
        let pendingResponses = await first.responses
        XCTAssertEqual(rejectedResponses, [Data([0x69, 0x85])])
        XCTAssertEqual(pendingResponses, [])

        await router.release()
        await assertEventually { await first.responseCount == 1 }
        let completedResponses = await first.responses
        XCTAssertEqual(completedResponses, [Data([0x90, 0x00])])
        await core.close(reason: .completed)
    }

    func testRetriesSameLogicalResponseOnceAfterTransmissionError() async throws {
        let session = FakeNfcCardSession()
        let router = FakeNfcRouter(response: Data([0x61, 0x20]))
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )
        let apdu = FakeNfcAPDU(
            payload: Data([0x00, 0xc0, 0x00, 0x00]),
            failures: [.transmissionError]
        )

        try await core.prepare()
        session.emit(.readerDetected)
        session.emit(.received(apdu))

        await assertEventually { await apdu.responseCount == 2 }
        await core.close(reason: .completed)

        let responses = await apdu.responses
        let deactivations = await router.deactivations
        XCTAssertEqual(responses, [Data([0x61, 0x20]), Data([0x61, 0x20])])
        XCTAssertEqual(deactivations, [.completed])
    }

    func testSecondTransmissionFailureTerminatesTheField() async throws {
        let session = FakeNfcCardSession()
        let router = FakeNfcRouter(response: Data([0x61, 0x20]))
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )
        let apdu = FakeNfcAPDU(
            payload: Data([0x00, 0xc0, 0x00, 0x00]),
            failures: [.transmissionError, .transmissionError]
        )

        try await core.prepare()
        session.emit(.readerDetected)
        session.emit(.received(apdu))

        await assertEventually { await router.deactivations == [.peerDisconnected] }
        let responses = await apdu.responses
        XCTAssertEqual(responses.count, 2)
        XCTAssertEqual(session.stopStatuses, [.failure])
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testReaderDeselectionCancelsPendingAPDUAndInvalidatesFieldAndRouter() async throws {
        let session = FakeNfcCardSession()
        let router = CancellationAwareNfcRouter()
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )
        let apdu = FakeNfcAPDU(payload: Data([0x00, 0xc3, 0x00, 0x00]))

        try await core.prepare()
        session.emit(.readerDetected)
        session.emit(.received(apdu))
        await router.waitUntilEntered()
        session.emit(.readerDeselected)

        await assertEventually { await router.deactivations == [.peerDisconnected] }
        let responses = await apdu.responses
        XCTAssertEqual(responses, [])
        XCTAssertEqual(session.stopStatuses, [.failure])
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testRouterFailureRespondsWithStatusAndTerminatesProtocol() async throws {
        let session = FakeNfcCardSession()
        let router = FakeNfcRouter(response: Data(), failure: TestFailure.failed)
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )
        let apdu = FakeNfcAPDU(payload: Data([0xff]))

        try await core.prepare()
        session.emit(.readerDetected)
        session.emit(.received(apdu))

        await assertEventually { await router.deactivations == [.protocolError] }
        let responses = await apdu.responses
        XCTAssertEqual(responses, [Data([0x6f, 0x00])])
        XCTAssertEqual(session.stopStatuses, [.failure])
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testCloseDropsStaleResponseAndWaitsForInFlightRouterCommand() async throws {
        let session = FakeNfcCardSession()
        let router = BlockingNfcRouter(response: Data([0x90, 0x00]))
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )
        let apdu = FakeNfcAPDU(payload: Data([0x00, 0xa4, 0x04, 0x00]))

        try await core.prepare()
        session.emit(.readerDetected)
        session.emit(.received(apdu))
        await router.waitUntilEntered()

        let close = Task { await core.close(reason: .cancelled) }
        await Task.yield()
        let deactivationsBeforeRelease = await router.deactivations
        XCTAssertEqual(deactivationsBeforeRelease, [])

        await router.release()
        await close.value

        let responses = await apdu.responses
        let deactivations = await router.deactivations
        XCTAssertEqual(responses, [])
        XCTAssertEqual(deactivations, [.cancelled])
        XCTAssertEqual(session.stopStatuses, [.failure])
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testMaximumDurationInvalidationMapsToTimeoutWithoutReinvalidating() async throws {
        let session = FakeNfcCardSession()
        let router = FakeNfcRouter(response: Data([0x90, 0x00]))
        let core = IOSCardSessionCore(
            generation: 1,
            environment: FakeNfcEnvironment(session: session),
            router: router
        )

        try await core.prepare()
        session.emit(.sessionInvalidated(reason: .maximumDurationReached))

        await assertEventually { await router.deactivations == [.timeout] }
        XCTAssertEqual(session.invalidateCount, 0)
    }

    func testCoordinatorPreservesKnownPreparationFailureCodes() async throws {
        let scenarios: [(FakeNfcEnvironment, CardSessionNfcHostError)] = [
            (
                FakeNfcEnvironment(reservationFailure: TestFailure.failed),
                .couldNotReserveContactlessInterface
            ),
            (
                FakeNfcEnvironment(reservationFailure: IOSNfcCardSessionFailure.accessNotAccepted),
                .accessNotAccepted
            ),
            (
                FakeNfcEnvironment(sessionFailure: IOSNfcCardSessionFailure.radioDisabled),
                .radioDisabled
            ),
        ]

        for (environment, expected) in scenarios {
            let coordinator = CardSessionNfcHostCoordinator(environment: environment)
            do {
                _ = try await coordinator.prepare(router: FakeNfcRouter(response: Data()))
                XCTFail("Preparation unexpectedly succeeded")
            } catch let failure as CardSessionNfcHostError {
                XCTAssertEqual(failure, expected)
                XCTAssertFalse(failure.code.isEmpty)
                XCTAssertFalse(failure.message.isEmpty)
            }
        }
    }

    func testInvalidPresentmentReservationFailsBeforeCardSessionCreation() async throws {
        let environment = FakeNfcEnvironment(reservationValid: false)
        let coordinator = CardSessionNfcHostCoordinator(environment: environment)

        do {
            _ = try await coordinator.prepare(router: FakeNfcRouter(response: Data()))
            XCTFail("Preparation unexpectedly succeeded")
        } catch let failure as CardSessionNfcHostError {
            XCTAssertEqual(failure, .systemUnavailable)
        }

        XCTAssertEqual(environment.reservationCount, 1)
        XCTAssertEqual(environment.sessionCount, 0)
    }

    func testExpiredPresentmentReservationRejectsReaderDetection() async throws {
        let session = FakeNfcCardSession()
        let environment = FakeNfcEnvironment(session: session)
        let router = FakeNfcRouter(response: Data([0x90, 0x00]))
        let core = IOSCardSessionCore(generation: 1, environment: environment, router: router)

        try await core.prepare()
        environment.invalidateReservation()
        session.emit(.readerDetected)

        await assertEventually { await router.deactivations == [.platformUnavailable] }
        XCTAssertEqual(session.startCount, 0)
        XCTAssertEqual(session.invalidateCount, 1)
    }

    func testCoordinatorRejectsConcurrentSessionAndReleasesClosedGeneration() async throws {
        let environment = FakeNfcEnvironment()
        let coordinator = CardSessionNfcHostCoordinator(environment: environment)
        let first = try await coordinator.prepare(router: FakeNfcRouter(response: Data()))

        do {
            _ = try await coordinator.prepare(router: FakeNfcRouter(response: Data()))
            XCTFail("Concurrent preparation unexpectedly succeeded")
        } catch let failure as CardSessionNfcHostError {
            XCTAssertEqual(failure, .sessionAlreadyActive)
            XCTAssertEqual(failure.code, "nfc_session_already_active")
        }

        await first.close(reason: .completed)
        _ = try await coordinator.prepare(router: FakeNfcRouter(response: Data()))
        XCTAssertEqual(environment.reservationCount, 2)
        XCTAssertEqual(environment.sessionCount, 2)
    }

    private func assertEventually(
        file: StaticString = #filePath,
        line: UInt = #line,
        _ condition: @escaping @Sendable () async -> Bool
    ) async {
        for _ in 0..<100 {
            if await condition() { return }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Condition was not satisfied before timeout", file: file, line: line)
    }
}

private enum TestFailure: Error {
    case failed
}

private final class FakeNfcReservation: IOSNfcPresentmentReservation, @unchecked Sendable {
    private let lock = NSLock()
    private var valid: Bool

    init(valid: Bool = true) {
        self.valid = valid
    }

    var isValid: Bool { lock.withLock { valid } }

    func invalidate() {
        lock.withLock { valid = false }
    }
}

private final class FakeNfcEnvironment: IOSNfcCardSessionEnvironment, @unchecked Sendable {
    private let lock = NSLock()
    private let reading: Bool
    private let supported: Bool
    private let eligible: Bool
    private let session: any IOSNfcCardSession
    private let reservation: FakeNfcReservation
    private let reservationFailure: Error?
    private let sessionFailure: Error?
    private var storedReservationCount = 0
    private var storedSessionCount = 0

    init(
        reading: Bool = true,
        supported: Bool = true,
        eligible: Bool = true,
        session: any IOSNfcCardSession = FakeNfcCardSession(),
        reservationValid: Bool = true,
        reservationFailure: Error? = nil,
        sessionFailure: Error? = nil
    ) {
        self.reading = reading
        self.supported = supported
        self.eligible = eligible
        self.session = session
        reservation = FakeNfcReservation(valid: reservationValid)
        self.reservationFailure = reservationFailure
        self.sessionFailure = sessionFailure
    }

    var reservationCount: Int { lock.withLock { storedReservationCount } }
    var sessionCount: Int { lock.withLock { storedSessionCount } }

    func invalidateReservation() {
        reservation.invalidate()
    }

    func readingAvailable() -> Bool { reading }
    func cardSessionSupported() -> Bool { supported }
    func cardSessionEligible() async -> Bool { eligible }

    func reserveContactlessInterface() async throws -> any IOSNfcPresentmentReservation {
        lock.withLock { storedReservationCount += 1 }
        if let reservationFailure { throw reservationFailure }
        return reservation
    }

    func makeCardSession() async throws -> any IOSNfcCardSession {
        lock.withLock { storedSessionCount += 1 }
        if let sessionFailure { throw sessionFailure }
        return session
    }
}

private final class FakeNfcCardSession: IOSNfcCardSession, @unchecked Sendable {
    private let lock = NSLock()
    private let continuation: AsyncThrowingStream<IOSNfcCardSessionEvent, Error>.Continuation
    let events: AsyncThrowingStream<IOSNfcCardSessionEvent, Error>
    private var emulating = false
    private var storedStartCount = 0
    private var storedStopStatuses: [IOSNfcEmulationStatus] = []
    private var storedInvalidateCount = 0
    private var storedMessages: [String] = []

    init() {
        var captured: AsyncThrowingStream<IOSNfcCardSessionEvent, Error>.Continuation?
        events = AsyncThrowingStream { captured = $0 }
        continuation = captured!
    }

    var startCount: Int { lock.withLock { storedStartCount } }
    var stopStatuses: [IOSNfcEmulationStatus] { lock.withLock { storedStopStatuses } }
    var invalidateCount: Int { lock.withLock { storedInvalidateCount } }

    func emit(_ event: IOSNfcCardSessionEvent) {
        continuation.yield(event)
    }

    func setAlertMessage(_ message: String) {
        lock.withLock { storedMessages.append(message) }
    }

    func isEmulationInProgress() async -> Bool {
        lock.withLock { emulating }
    }

    func startEmulation() async throws {
        lock.withLock {
            storedStartCount += 1
            emulating = true
        }
    }

    func stopEmulation(status: IOSNfcEmulationStatus) async {
        lock.withLock {
            storedStopStatuses.append(status)
            emulating = false
        }
    }

    func invalidate() {
        lock.withLock { storedInvalidateCount += 1 }
    }
}

private actor FakeNfcAPDU: IOSNfcCardSessionAPDU {
    nonisolated let payload: Data
    private var failures: [IOSNfcCardSessionFailure]
    private(set) var responses: [Data] = []

    init(payload: Data, failures: [IOSNfcCardSessionFailure] = []) {
        self.payload = payload
        self.failures = failures
    }

    var responseCount: Int { responses.count }

    func respond(response: Data) async throws {
        responses.append(response)
        if !failures.isEmpty {
            throw failures.removeFirst()
        }
    }
}

private actor FakeNfcRouter: IOSNfcHostApduRouting {
    private let response: Data
    private let failure: Error?
    private(set) var commands: [Data] = []
    private(set) var deactivations: [IOSNfcHostBridgeCloseReason] = []

    init(response: Data, failure: Error? = nil) {
        self.response = response
        self.failure = failure
    }

    func process(_ command: Data) async throws -> Data {
        commands.append(command)
        if let failure { throw failure }
        return response
    }

    func deactivate(reason: IOSNfcHostBridgeCloseReason) async {
        deactivations.append(reason)
    }
}

private actor BridgeNfcCallbacks {
    private let response: Data
    private(set) var commands: [Data] = []
    private(set) var deactivations: [IOSNfcHostBridgeCloseReason] = []

    init(response: Data) {
        self.response = response
    }

    func process(_ command: Data) -> Data {
        commands.append(command)
        return response
    }

    func deactivate(_ reason: IOSNfcHostBridgeCloseReason) {
        deactivations.append(reason)
    }
}

private actor BlockingNfcRouter: IOSNfcHostApduRouting {
    private let response: Data
    private var enteredContinuation: CheckedContinuation<Void, Never>?
    private var releaseContinuation: CheckedContinuation<Void, Never>?
    private var entered = false
    private var released = false
    private(set) var deactivations: [IOSNfcHostBridgeCloseReason] = []

    init(response: Data) {
        self.response = response
    }

    func process(_ command: Data) async throws -> Data {
        entered = true
        enteredContinuation?.resume()
        enteredContinuation = nil
        if !released {
            await withCheckedContinuation { releaseContinuation = $0 }
        }
        return response
    }

    func waitUntilEntered() async {
        if entered { return }
        await withCheckedContinuation { enteredContinuation = $0 }
    }

    func release() {
        released = true
        releaseContinuation?.resume()
        releaseContinuation = nil
    }

    func deactivate(reason: IOSNfcHostBridgeCloseReason) async {
        deactivations.append(reason)
    }
}

private actor CancellationAwareNfcRouter: IOSNfcHostApduRouting {
    private var enteredContinuation: CheckedContinuation<Void, Never>?
    private var entered = false
    private(set) var deactivations: [IOSNfcHostBridgeCloseReason] = []

    func process(_ command: Data) async throws -> Data {
        entered = true
        enteredContinuation?.resume()
        enteredContinuation = nil
        try await Task.sleep(nanoseconds: 60_000_000_000)
        return Data([0x90, 0x00])
    }

    func waitUntilEntered() async {
        if entered { return }
        await withCheckedContinuation { enteredContinuation = $0 }
    }

    func deactivate(reason: IOSNfcHostBridgeCloseReason) async {
        deactivations.append(reason)
    }
}

private extension NSLock {
    func withLock<Result>(_ body: () -> Result) -> Result {
        lock()
        defer { unlock() }
        return body()
    }
}
#endif
