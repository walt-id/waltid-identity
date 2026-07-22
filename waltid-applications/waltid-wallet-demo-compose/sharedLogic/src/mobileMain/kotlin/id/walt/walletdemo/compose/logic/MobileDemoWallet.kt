package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletCredentialClaimMetadata
import id.walt.wallet2.mobile.MobileWalletMetadataDisplay
import id.walt.wallet2.mobile.MobileWalletOfferedCredentialMetadata
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewHandle
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationRequestInfo
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.MobileWalletResponseEncryption
import id.walt.wallet2.mobile.MobileWalletTransactionCodeInputMode
import id.walt.wallet2.mobile.MobileWalletTransactionCodeRequirement
import id.walt.wallet2.mobile.MobileWalletTransactionDataItem
import id.walt.wallet2.mobile.MobileWalletVerifierMetadata
import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceTransactionCode
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
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
                issuer = credential.issuer,
                subject = credential.subject,
                label = credential.label ?: credential.format,
                addedAt = credential.addedAt,
                credentialDataJson = credential.credentialDataJson,
            )
        }

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession = mobileWallet.startIssuance(
        MobileWalletIssuanceRequest(
            offerUrl = offerUrl,
            redirectUri = redirectUri,
            did = did,
        )
    ).let { session ->
        WalletDemoIssuanceSession(
            id = session.id,
            grant = when (session.offer.grant) {
                WalletIssuanceGrant.PRE_AUTHORIZED_CODE -> WalletDemoIssuanceGrant.PreAuthorizedCode
                WalletIssuanceGrant.AUTHORIZATION_CODE -> WalletDemoIssuanceGrant.AuthorizationCode
            },
            preview = WalletDemoOfferPreview(
                issuer = WalletDemoIssuerMetadata(
                    credentialIssuer = session.offer.issuer.identifier,
                    display = WalletDemoMetadataDisplay(
                        name = session.offer.issuer.name,
                        logoUri = session.offer.issuer.logoUri,
                        logoAltText = session.offer.issuer.logoAltText,
                        description = null,
                    ),
                ),
                offeredCredentials = session.offer.credentials.map { credential ->
                    WalletDemoOfferedCredentialMetadata(
                        configurationId = credential.configurationId,
                        format = credential.format,
                        vct = null,
                        doctype = null,
                        display = WalletDemoMetadataDisplay(
                            name = credential.name,
                            logoUri = credential.logoUri,
                            logoAltText = null,
                            description = credential.descriptionText,
                        ),
                        claims = emptyList(),
                    )
                },
                transactionCode = session.offer.transactionCode?.toDemoRequirement(),
                requiresIssuerAuthentication = session.offer.grant == WalletIssuanceGrant.AUTHORIZATION_CODE,
            ),
            authorizationUrl = session.authorization?.url,
        )
    }

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletDemoIssuanceOutcome =
        mobileWallet.continuePreAuthorizedIssuance(sessionId, transactionCode).toDemoOutcome()

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome =
        mobileWallet.continueAuthorizationIssuance(sessionId, callbackUri).toDemoOutcome()

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome =
        mobileWallet.cancelIssuance(sessionId).toDemoOutcome()

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome =
        mobileWallet.resumeDeferredIssuance(deferredCredentialId).toDemoOutcome()

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
                        previewHandle = WalletDemoPresentationPreviewHandle(result.previewHandle.value),
                        verifierMetadata = result.request.verifierMetadata?.toDemoMetadata(),
                        clientId = result.request.clientId,
                        responseUri = result.request.responseUri,
                        state = result.request.state,
                        nonce = result.request.nonce,
                        responseEncryption = result.request.responseEncryption.toDemoResponseEncryption(),
                        transactionData = emptyList(),
                        errorCode = result.errorCode.errorCode,
                        message = result.message,
                    )
                )
        }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult =
        mobileWallet.submitPresentation(
            previewHandle = MobileWalletPresentationPreviewHandle(previewHandle.value),
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

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) =
        mobileWallet.discardPresentationPreview(MobileWalletPresentationPreviewHandle(previewHandle.value))

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult =
        mobileWallet.rejectPresentation(
            previewHandle = MobileWalletPresentationPreviewHandle(previewHandle.value),
        ).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationRejected,
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

private fun WalletIssuanceOutcome.toDemoOutcome(): WalletDemoIssuanceOutcome =
    when (this) {
        is WalletIssuanceOutcome.Stored -> WalletDemoIssuanceOutcome.Stored(credentialIds)
        is WalletIssuanceOutcome.Deferred -> WalletDemoIssuanceOutcome.Deferred(
            storedCredentialIds = storedCredentialIds,
            credentials = credentials.map { credential ->
                WalletDemoDeferredCredential(
                    id = credential.id,
                    credentialConfigurationId = credential.credentialConfigurationId,
                    intervalSeconds = credential.intervalSeconds,
                )
            },
        )
        is WalletIssuanceOutcome.Cancelled -> WalletDemoIssuanceOutcome.Cancelled
        is WalletIssuanceOutcome.Failed -> WalletDemoIssuanceOutcome.Failed(error.message)
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
        previewHandle = WalletDemoPresentationPreviewHandle(previewHandle.value),
        verifierMetadata = request.verifierMetadata?.toDemoMetadata(),
        clientId = request.clientId,
        responseUri = request.responseUri,
        state = request.state,
        nonce = request.nonce,
        responseEncryption = request.responseEncryption.toDemoResponseEncryption(),
        transactionData = request.transactionData.toDemoTransactionDataGroups(),
        credentialOptions = credentialOptions.map { option ->
            WalletDemoPresentationCredentialOption(
                queryId = option.queryId,
                credentialId = option.credentialId,
                multiple = option.multiple,
                label = option.label ?: option.format,
                issuer = option.issuer,
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

private fun List<MobileWalletTransactionDataItem>.toDemoTransactionDataGroups(): List<ClaimGroup> =
    CredentialDisplayNormalizer.transactionDataGroups(
        map { item ->
            WalletDemoTransactionDataItem(
                type = item.type,
                displayName = item.displayName,
                credentialQueryIds = item.credentialQueryIds,
                supportedFields = item.supportedFields,
                rawJson = item.rawJson,
                detailsJson = item.detailsJson,
            )
        }
    )

private fun MobileWalletResponseEncryption.toDemoResponseEncryption(): WalletDemoResponseEncryption =
    when (this) {
        MobileWalletResponseEncryption.NotRequired -> WalletDemoResponseEncryption.NotRequired
        is MobileWalletResponseEncryption.Required -> WalletDemoResponseEncryption.Required(
            keyManagementAlgorithm = keyManagementAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )
    }

private fun MobileWalletMetadataDisplay.toDemoMetadataDisplay(): WalletDemoMetadataDisplay =
    WalletDemoMetadataDisplay(
        name = name,
        logoUri = logoUri,
        logoAltText = logoAltText,
        description = description,
    )

private fun MobileWalletOfferedCredentialMetadata.toDemoMetadata(): WalletDemoOfferedCredentialMetadata =
    WalletDemoOfferedCredentialMetadata(
        configurationId = configurationId,
        format = format,
        vct = vct,
        doctype = doctype,
        display = display?.toDemoMetadataDisplay(),
        claims = claims.map(MobileWalletCredentialClaimMetadata::toDemoMetadata),
    )

private fun MobileWalletCredentialClaimMetadata.toDemoMetadata(): WalletDemoCredentialClaimMetadata =
    WalletDemoCredentialClaimMetadata(
        path = path,
        mandatory = mandatory,
        displayName = displayName,
    )

private fun MobileWalletTransactionCodeRequirement.toDemoRequirement(): WalletDemoTransactionCodeRequirement =
    WalletDemoTransactionCodeRequirement(
        inputMode = when (inputMode) {
            MobileWalletTransactionCodeInputMode.Numeric -> WalletDemoTransactionCodeInputMode.Numeric
            MobileWalletTransactionCodeInputMode.Text -> WalletDemoTransactionCodeInputMode.Text
        },
        length = length,
        description = description,
    )

private fun WalletIssuanceTransactionCode.toDemoRequirement(): WalletDemoTransactionCodeRequirement =
    WalletDemoTransactionCodeRequirement(
        inputMode = when (inputMode ?: "numeric") {
            "numeric" -> WalletDemoTransactionCodeInputMode.Numeric
            "text" -> WalletDemoTransactionCodeInputMode.Text
            else -> throw IllegalArgumentException("Unsupported transaction code input mode: $inputMode")
        },
        length = length,
        description = descriptionText,
    )

private fun MobileWalletVerifierMetadata.toDemoMetadata(): WalletDemoVerifierMetadata =
    WalletDemoVerifierMetadata(
        display = display?.toDemoMetadataDisplay(),
        clientUri = clientUri,
        policyUri = policyUri,
        termsOfServiceUri = termsOfServiceUri,
    )
