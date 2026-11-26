import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletProviderConfiguration {
    func toKMPWalletProvider() -> WalletProviderConfig {
        let choice: WalletProviderConfig.Composition
        switch composition {
        case .walletInstance: choice = .walletInstance
        case .key: choice = .key
        case .walletInstanceAndKey: choice = .walletInstanceAndKey
        }
        return WalletProviderConfig(
            baseUrl: baseURL,
            servicePath: servicePath,
            bearerToken: bearerToken,
            hostHeader: hostHeader,
            composition: choice
        )
    }
}

extension WalletAttestationConfiguration {
    func toKMPAttestationConfiguration() -> WalletAttestationConfig {
        WalletAttestationConfig(
            baseUrl: baseURL,
            attesterPath: attesterPath,
            bearerToken: bearerToken,
            hostHeader: hostHeader
        )
    }
}
#endif
