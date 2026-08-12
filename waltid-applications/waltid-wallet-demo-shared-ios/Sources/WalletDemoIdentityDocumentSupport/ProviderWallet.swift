import Foundation
import WalletSDK

extension IdentityDocumentNamespace {
    /// Opens host-owned wallet state for the provider without bootstrapping.
    ///
    /// Provider extensions must not create wallet state or republish the host's projection.
    ///
    /// - Parameter walletID: Wallet to open; defaults to the one the host published. There is no
    ///   `"default"` fallback, because opening the wrong database yields an empty picker rather than an
    ///   error the user can act on.
    public func providerWallet(walletID: String? = nil) async throws -> Wallet {
        let resolvedWalletID = try walletID ?? activeWalletID()
        return try await Wallet(configuration: try walletConfiguration(walletID: resolvedWalletID))
    }
}
