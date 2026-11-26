#if canImport(CoreNFC) && canImport(WalletCore) && os(iOS)
import CoreNFC
import Foundation

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

final class CoreNfcCardSessionEnvironment: IOSNfcCardSessionEnvironment, @unchecked Sendable {
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
private final class CoreNfcPresentmentIntent: IOSNfcPresentmentIntent, @unchecked Sendable {
    let assertion: NFCPresentmentIntentAssertion

    init(assertion: NFCPresentmentIntentAssertion) {
        self.assertion = assertion
    }

}

@available(iOS 17.4, *)
private final class CoreNfcCardSession: IOSNfcCardSession, @unchecked Sendable {
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

    private let session: CardSession
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
#endif
