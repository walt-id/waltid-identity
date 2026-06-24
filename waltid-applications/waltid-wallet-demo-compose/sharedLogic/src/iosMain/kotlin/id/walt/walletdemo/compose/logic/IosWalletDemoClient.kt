package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine

fun createIosWalletDemoClient(
    config: WalletDemoClientConfig = WalletDemoClientConfig(),
): WalletDemoClient {
    WebDataFetcherManager.globalDefaultConfiguration = WebDataFetchingConfiguration(http = HttpEngine.Native)

    return NativeWalletDemoClient(
        MobileWalletFactory().create(
            MobileWalletConfig(
                walletId = config.walletId,
                attestationConfig = config.toNativeAttestationConfig(),
            )
        )
    )
}

private class NativeWalletDemoClient(
    private val client: MobileWallet,
) : WalletDemoClient {
    override suspend fun bootstrap(): WalletDemoBootstrapResult =
        client.bootstrap().let { result ->
            WalletDemoBootstrapResult(
                keyId = result.keyId,
                did = result.did,
            )
        }

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        client.credentials().map { credential ->
            WalletDemoCredential(
                id = credential.id,
                format = credential.format,
                issuer = credential.issuer ?: "Unknown",
                label = credential.label ?: credential.format,
                addedAt = credential.addedAt ?: "",
            )
        }

    override suspend fun receive(offerUrl: String): List<String> = client.receive(offerUrl)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        client.present(requestUrl = requestUrl, did = did).let { result ->
            WalletDemoOperationResult(
                success = result.success,
                message = if (result.success) "Presentation sent" else "Presentation finished without verifier confirmation",
            )
        }
}

private fun WalletDemoClientConfig.toNativeAttestationConfig(): WalletAttestationConfig? =
    attestationBaseUrl.takeIf { it.isNotBlank() }?.let {
        WalletAttestationConfig(
            enterpriseBaseUrl = it,
            attesterPath = attestationAttesterPath,
            bearerToken = attestationBearerToken,
            enterpriseHostHeader = attestationHostHeader,
        )
    }
