package id.walt.walletdemo.compose.logic

import android.content.Context
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {
    WebDataFetcherManager.globalDefaultConfiguration = WebDataFetchingConfiguration(http = HttpEngine.OkHttp)

    return MobileDemoWallet(
        MobileWalletFactory(context).create(
            MobileWalletConfig(
                walletId = config.walletId,
                attestationConfig = config.toWalletAttestationConfig(),
            )
        )
    )
}

private class MobileDemoWallet(
    private val mobileWallet: MobileWallet,
) : DemoWallet {
    override suspend fun bootstrap(): WalletDemoBootstrapResult =
        mobileWallet.bootstrap().let { result ->
            WalletDemoBootstrapResult(
                keyId = result.keyId,
                did = result.did,
            )
        }

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        mobileWallet.credentials().map { credential ->
            WalletDemoCredential(
                id = credential.id,
                format = credential.format,
                issuer = credential.issuer ?: "Unknown",
                label = credential.label ?: credential.format,
                addedAt = credential.addedAt ?: "",
            )
        }

    override suspend fun receive(offerUrl: String): List<String> = mobileWallet.receive(offerUrl)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        mobileWallet.present(requestUrl = requestUrl, did = did).let { result ->
            WalletDemoOperationResult(
                success = result.success,
                message = if (result.success) "Presentation sent" else "Presentation finished without verifier confirmation",
            )
        }
}

private fun DemoWalletConfig.toWalletAttestationConfig(): WalletAttestationConfig? =
    attestationBaseUrl.takeIf { it.isNotBlank() }?.let {
        WalletAttestationConfig(
            enterpriseBaseUrl = it,
            attesterPath = attestationAttesterPath,
            bearerToken = attestationBearerToken,
            enterpriseHostHeader = attestationHostHeader,
        )
    }
