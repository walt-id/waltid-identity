package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletProximityConfiguration
import id.walt.wallet2.mobile.MobileWalletProximitySession

/** Narrow shared boundary used by the proximity journey controller. */
interface ProximityPresentationBackend {
    suspend fun startProximityPresentation(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximitySession
}

/** Mobile demo backend that adds the Wallet SDK proximity capability without transport internals. */
interface ProximityDemoWallet : DemoWallet, ProximityPresentationBackend

internal class LazyProximityDemoWallet(
    createWallet: suspend () -> ProximityDemoWallet,
) : LazyDemoWallet<ProximityDemoWallet>(createWallet), ProximityDemoWallet {
    override suspend fun startProximityPresentation(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximitySession =
        wallet().startProximityPresentation(configuration)
}
