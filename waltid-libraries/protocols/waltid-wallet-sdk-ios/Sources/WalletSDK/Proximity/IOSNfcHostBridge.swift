#if canImport(CoreNFC) && canImport(WalletCore) && os(iOS)
import Foundation

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

/// Result of arming one generation-bound Swift CardSession bridge.
@_spi(KmpHostBridge)
public enum IOSNfcHostBridgePreparation: Sendable {
    case ready(IOSNfcHostBridgeSession)
    case unavailable(IOSNfcHostBridgeUnavailable)
}

/// Prepared CardSession host that is independent of any generated Kotlin protocol identity.
@_spi(KmpHostBridge)
public final class IOSNfcHostBridgeSession: @unchecked Sendable {
    public func close(reason: IOSNfcHostBridgeCloseReason) async {
        await core.close(reason: reason)
    }

    init(core: IOSCardSessionCore) {
        self.core = core
    }

    deinit {
        let core = core
        Task {
            await core.close(reason: .cancelled)
        }
    }

    private let core: IOSCardSessionCore
}

/// Swift-native CardSession implementation shared by every independently linked KMP framework.
@_spi(KmpHostBridge)
public final class IOSNfcHostBridge: @unchecked Sendable {
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

    private let environment: any IOSNfcCardSessionEnvironment

    private let coordinator: CardSessionNfcHostCoordinator

    private let presentment: IOSNfcPresentmentState
}

protocol IOSNfcHostApduRouting: Sendable {
    func process(_ command: Data) async throws -> Data
    func deactivate(reason: IOSNfcHostBridgeCloseReason) async
}

/// Synchronous observation prevents a background notification from overtaking stream delivery.
private final class IOSNfcPresentmentState: @unchecked Sendable {
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

    private let lock = NSLock()

    private var active = false

    private let observer: @Sendable (IOSNfcPresentmentEvent) -> Void
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
#endif
