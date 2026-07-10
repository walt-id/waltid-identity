package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletPresentationPreview
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
                addedAt = credential.addedAt ?: "",
                credentialDataJson = credential.credentialDataJson,
            )
        }

    override suspend fun receive(offerUrl: String): List<String> = mobileWallet.receive(offerUrl)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        mobileWallet.present(requestUrl = requestUrl, did = did).let { result ->
            if (result.success) {
                WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent)
            } else {
                WalletDemoOperationResult.Failure(WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation)
            }
        }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreview =
        mobileWallet.previewPresentation(requestUrl).toDemoPreview()

    override suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialIds: List<String>,
        did: String?,
    ): WalletDemoOperationResult =
        mobileWallet.submitPresentation(
            requestUrl = requestUrl,
            selectedCredentialIds = selectedCredentialIds,
            did = did,
        ).let { result ->
            if (result.success) {
                WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent)
            } else {
                WalletDemoOperationResult.Failure(WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation)
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

private fun MobileWalletPresentationPreview.toDemoPreview(): WalletDemoPresentationPreview =
    WalletDemoPresentationPreview(
        verifierName = request.verifierName,
        clientId = request.clientId,
        responseUri = request.responseUri,
        state = request.state,
        nonce = request.nonce,
        credentialOptions = credentialOptions.map { option ->
            WalletDemoPresentationCredentialOption(
                queryId = option.queryId,
                credentialId = option.credentialId,
                label = option.label ?: option.format,
                issuer = option.issuer ?: CredentialDisplayText.Unknown,
                subject = option.subject,
                format = option.format,
                credentialDataJson = option.credentialDataJson,
                disclosures = option.disclosures.map { disclosure ->
                    WalletDemoPresentationDisclosure(
                        label = CredentialDisplayVocabulary.disclosureLabel(disclosure.name, disclosure.path),
                        path = disclosure.path,
                        valueJson = disclosure.valueJson,
                        displayValue = disclosure.displayValue,
                        selectivelyDisclosable = disclosure.selectivelyDisclosable,
                    )
                },
            )
        },
    )
