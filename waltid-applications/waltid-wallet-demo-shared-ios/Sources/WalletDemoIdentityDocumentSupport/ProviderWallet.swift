import Foundation
import WalletSDK

extension IdentityDocumentNamespace {
    /// Opens the host app's wallet from inside the provider extension, read-only.
    ///
    /// Deliberately no `bootstrap()`. Bootstrap creates key material and a DID when none exist and
    /// then republishes the desired projection - all *write* behaviour, and all wrong here: the
    /// extension serves one request against state the host already owns, and a projection written from
    /// the extension would let a process that cannot see the wallet's credential store overwrite the
    /// host's authoritative desired state. Annex C needs neither: the preview reads credentials and the
    /// submission resolves the signing key, both of which go through the stores an opened wallet
    /// already has. ``SharedWalletConfigurationTests`` proves that on the simulator.
    ///
    /// - Parameter walletID: Wallet to open; defaults to the one the host published. There is no
    ///   `"default"` fallback, because opening the wrong database yields an empty picker rather than an
    ///   error the user can act on.
    public func providerWallet(walletID: String? = nil) async throws -> Wallet {
        let resolvedWalletID = try walletID ?? activeWalletID()
        return try await Wallet(configuration: try walletConfiguration(walletID: resolvedWalletID))
    }
}
