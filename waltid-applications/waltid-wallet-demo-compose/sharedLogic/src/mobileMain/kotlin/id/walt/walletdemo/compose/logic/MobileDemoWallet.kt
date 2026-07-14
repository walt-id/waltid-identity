package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.WalletAttestationConfig

internal class MobileDemoWallet(
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
                issuer = credential.issuer ?: CredentialDisplayText.Unknown,
                subject = credential.subject,
                label = credential.label ?: credential.format,
                addedAt = credential.addedAt,
                credentialDataJson = credential.credentialDataJson,
            )
        }

    override suspend fun receive(offerUrl: String): List<String> = mobileWallet.receive(offerUrl)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        mobileWallet.present(requestUrl = requestUrl, did = did).let { result ->
            if (result.success) {
                WalletDemoOperationResult.Success("Presentation sent")
            } else {
                WalletDemoOperationResult.Failure("Presentation finished without verifier confirmation")
            }
        }
}

internal fun DemoWalletConfig.toWalletAttestationConfig(): WalletAttestationConfig? =
    attestationBaseUrl.takeIf { it.isNotBlank() }?.let {
        WalletAttestationConfig(
            baseUrl = it,
            attesterPath = attestationAttesterPath,
            bearerToken = attestationBearerToken,
            hostHeader = attestationHostHeader,
        )
    }
