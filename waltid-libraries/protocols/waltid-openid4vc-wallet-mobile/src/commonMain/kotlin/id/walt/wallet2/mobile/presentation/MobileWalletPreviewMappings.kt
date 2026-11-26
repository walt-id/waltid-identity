package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.handlers.PresentationTransactionDataItem
import id.walt.wallet2.handlers.PreviewDcApiPresentationResult
import id.walt.wallet2.handlers.PreviewPresentationResult
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletReaderAuthentication

internal fun PreviewPresentationResult.toMobilePreview(
    preferredLocales: List<String>,
    transactionDataProfiles: List<MobileWalletTransactionDataProfile>,
): MobileWalletPresentationPreviewResult = when (this) {
    is PreviewPresentationResult.Invalid ->
        MobileWalletPresentationPreviewResult.Invalid(
            previewHandle = MobileWalletPresentationPreviewHandle(handle.value),
            request = authorizationRequest.toMobileRequestContext(
                preferredLocales = preferredLocales,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
            ),
            errorCode = error.code.toMobileErrorCode(),
            message = error.message,
        )

    is PreviewPresentationResult.Ready -> {
        MobileWalletPresentationPreviewResult.Ready(
            MobileWalletPresentationPreview(
                previewHandle = MobileWalletPresentationPreviewHandle(handle.value),
                request = authorizationRequest.toMobileRequestInfo(
                    preferredLocales = preferredLocales,
                    resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                    responseEncryption = responseEncryption,
                    transactionData = transactionData.toMobileTransactionData(transactionDataProfiles),
                ),
                credentialOptions = credentialOptions.map { it.toMobileCredentialOption() },
                credentialRequirements = credentialRequirements.map { it.toMobileCredentialRequirement() },
            )
        )
    }
}

internal fun PreviewDcApiPresentationResult.toMobilePreview(
    protocol: String,
    preferredLocales: List<String>,
    transactionDataProfiles: List<MobileWalletTransactionDataProfile>,
): MobileWalletDigitalCredentialPreview {
    val authorizationRequest = resolvedRequest.authorizationRequest
    return MobileWalletDigitalCredentialPreview(
        requestId = requestId,
        protocol = protocol,
        verifiedOrigin = resolvedRequest.origin,
        request = authorizationRequest.toMobileDigitalCredentialRequestInfo(
            preferredLocales = preferredLocales,
            transactionData = transactionData.toMobileTransactionData(transactionDataProfiles),
        ),
        credentialOptions = credentialOptions.map { it.toMobileCredentialOption() },
        credentialRequirements = credentialRequirements.map { it.toMobileCredentialRequirement() },
        readerAuthentication = MobileWalletReaderAuthentication.NOT_APPLICABLE,
        readerTrust = null,
    )
}

private fun List<PresentationTransactionDataItem>.toMobileTransactionData(
    profiles: List<MobileWalletTransactionDataProfile>,
): List<MobileWalletTransactionDataItem> {
    val profilesByType = profiles.associateBy { it.type }
    return map { item ->
        val profile = profilesByType[item.type]
        MobileWalletTransactionDataItem(
            type = item.type,
            displayName = profile?.displayName ?: item.type,
            credentialQueryIds = item.credentialQueryIds,
            supportedFields = profile?.fields.orEmpty(),
            rawJson = item.rawJson.encodeJsonObject(),
            detailsJson = item.details.encodeJsonObject(),
        )
    }
}
