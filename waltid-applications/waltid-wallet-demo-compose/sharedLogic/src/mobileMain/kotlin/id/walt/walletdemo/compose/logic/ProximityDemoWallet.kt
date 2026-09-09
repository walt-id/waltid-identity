package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityConfiguration
import id.walt.wallet2.mobile.ProximitySession

/** Narrow shared boundary used by the proximity journey controller. */
interface ProximityPresentationBackend {
    suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities

    suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): ProximitySession
}

/** Mobile demo backend that adds the Wallet SDK proximity capability without transport internals. */
interface ProximityDemoWallet : DemoWallet, ProximityPresentationBackend

internal class LazyProximityDemoWallet(
    createWallet: suspend () -> ProximityDemoWallet,
) : LazyDemoWallet<ProximityDemoWallet>(createWallet), ProximityDemoWallet {
    override suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities =
        wallet().proximityPresentationCapabilities(configuration)

    override suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): ProximitySession =
        wallet().startProximityPresentation(configuration)
}
