import Foundation
import WalletSDK

extension IdentityDocumentNamespace {
    /// Opens the host app's wallet from inside the provider extension.
    ///
    /// Bootstrap is required even though the host app already ran it: the extension is a separate
    /// process that has to open the same encrypted database and load the same device signing key
    /// before it can sign an mdoc response. It is idempotent, so it recovers rather than reissues.
    public func providerWallet(walletID: String = "default") async throws -> Wallet {
        let wallet = try await Wallet(configuration: try walletConfiguration(walletID: walletID))
        _ = try await wallet.bootstrap()
        return wallet
    }
}
