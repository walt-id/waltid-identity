#if canImport(WalletCore) && os(iOS)
import WalletCore

/// Kotlin marks its runtime available before eager cryptography-provider registration finishes.
/// Serialize the SDK's independent first-entry paths so a competing call cannot freeze a partial
/// provider list. Swift's static initialization waits for the first caller; later calls are cheap.
enum WalletCoreRuntime {
    // This stateless singleton enters Kotlin without consuming a crypto provider.
    private static let initialized: Void = {
        _ = WalletCore.ProximityReaderTrustSettingsCodec.shared
    }()

    static func initializeIfNeeded() {
        _ = initialized
    }
}
#endif
