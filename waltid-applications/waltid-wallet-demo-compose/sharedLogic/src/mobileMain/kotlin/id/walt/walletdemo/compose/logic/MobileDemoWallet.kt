package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationRequestInfo
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.WalletAttestationConfig

internal class MobileDemoWallet(
    private val mobileWallet: MobileWallet,
    private val warning: String? = null,
) : DemoWallet {
    override suspend fun bootstrap(): WalletDemoBootstrapResult =
        mobileWallet.bootstrap().let { result ->
            WalletDemoBootstrapResult(
                keyId = result.keyId,
                did = result.did,
                warning = warning,
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

    override suspend fun resolveOffer(offerUrl: String): WalletDemoOfferPreview =
        mobileWallet.resolveOffer(offerUrl).let { resolution ->
            WalletDemoOfferPreview(
                transactionCodeRequired = resolution.transactionCodeRequired,
                credentialIssuer = resolution.credentialIssuer,
                offeredCredentials = resolution.offeredCredentials,
            )
        }

    override suspend fun receive(offerUrl: String, txCode: String?): List<String> =
        mobileWallet.receive(offerUrl, txCode = txCode)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        mobileWallet.present(requestUrl = requestUrl, did = did).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult =
        when (val result = mobileWallet.previewPresentation(requestUrl)) {
            is MobileWalletPresentationPreviewResult.Ready ->
                WalletDemoPresentationPreviewResult.Ready(result.preview.toDemoPreview())

            is MobileWalletPresentationPreviewResult.Invalid ->
                WalletDemoPresentationPreviewResult.Invalid(
                    WalletDemoPresentationError(
                        verifier = result.request.toDemoVerifierDetails(),
                        errorCode = result.errorCode.errorCode,
                        message = result.message,
                    )
                )
        }

    override suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult =
        mobileWallet.submitPresentation(
            requestUrl = requestUrl,
            selectedCredentialOptions = selectedCredentialOptions.map {
                MobileWalletPresentationCredentialSelection(
                    queryId = it.queryId,
                    credentialId = it.credentialId,
                )
            },
            selectedDisclosureOptions = selectedDisclosureOptions.map {
                MobileWalletPresentationDisclosureSelection(
                    queryId = it.queryId,
                    credentialId = it.credentialId,
                    path = it.path,
                )
            },
            did = did,
        ).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )

    override suspend fun rejectPresentation(requestUrl: String): WalletDemoOperationResult =
        mobileWallet.rejectPresentation(requestUrl = requestUrl).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationDeclined,
            failureMessage = WalletDisplayText.RejectionFinishedWithoutVerifierConfirmation,
        )

}

private fun MobileWalletPresentationResult.toDemoOperationResult(
    successMessage: String,
    failureMessage: String,
): WalletDemoOperationResult =
    when (this) {
        is MobileWalletPresentationResult.Prepared.OpenUrl -> WalletDemoOperationResult.Success(
            successMessage,
            WalletDemoPresentationContinuation.Url(url),
        )

        is MobileWalletPresentationResult.Prepared.SubmitForm -> WalletDemoOperationResult.Success(
            successMessage,
            WalletDemoPresentationContinuation.FormPostHtml(html),
        )

        is MobileWalletPresentationResult.Transmitted.Succeeded -> WalletDemoOperationResult.Success(
            successMessage,
            redirectUrl?.let(WalletDemoPresentationContinuation::Url),
        )

        is MobileWalletPresentationResult.Transmitted.Failed -> WalletDemoOperationResult.Failure(failureMessage)
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
    request.toDemoVerifierDetails().let { verifier ->
        WalletDemoPresentationPreview(
            verifierName = verifier.name,
            clientId = verifier.clientId,
            responseUri = verifier.responseUri,
            state = verifier.state,
            nonce = verifier.nonce,
            transactionData = verifier.transactionData,
            credentialOptions = credentialOptions.map { option ->
                WalletDemoPresentationCredentialOption(
                    queryId = option.queryId,
                    credentialId = option.credentialId,
                    multiple = option.multiple,
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
                            required = disclosure.required,
                            selectable = disclosure.selectable,
                        )
                    },
                )
            },
            credentialRequirements = credentialRequirements.map { requirement ->
                WalletDemoPresentationCredentialRequirement(options = requirement.options)
            },
        )
    }

private fun MobileWalletPresentationRequestInfo.toDemoVerifierDetails(): VerifierDetails =
    VerifierDetails(
        name = verifierName,
        clientId = clientId,
        responseUri = responseUri,
        state = state,
        nonce = nonce,
        transactionData = CredentialDisplayNormalizer.transactionDataGroups(
            transactionData.map { item ->
                WalletDemoTransactionDataItem(
                    type = item.type,
                    displayName = item.displayName,
                    credentialQueryIds = item.credentialQueryIds,
                    supportedFields = item.supportedFields,
                    rawJson = item.rawJson,
                    detailsJson = item.detailsJson,
                )
            }
        ),
    )
