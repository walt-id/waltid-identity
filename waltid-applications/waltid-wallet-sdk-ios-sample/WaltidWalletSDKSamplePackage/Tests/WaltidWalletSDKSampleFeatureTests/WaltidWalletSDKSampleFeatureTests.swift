import Testing
import WaltidWalletSDK
@testable import WaltidWalletSDKSampleFeature

@Test func sdkConfigurationDefaultsAreVisibleToConsumerPackage() async throws {
    let configuration = WalletConfiguration()

    #expect(configuration.walletID == "default")
    #expect(configuration.defaultKeyType == .secp256r1)
}
