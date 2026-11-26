package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.mobile.internal.encodeJsonObject

import id.walt.wallet2.handlers.PresentationTransactionDataItem
import id.walt.wallet2.handlers.PreviewDcApiPresentationResult
import id.walt.wallet2.handlers.PreviewPresentationResult
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialPreview
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialReaderAuthentication

internal fun PreviewPresentationResult.toMobilePreview(
    preferredLocales: List<String>,
    transactionDataProfiles: List<TransactionDataProfile>,
): PresentationPreviewResult = when (this) {
    is PreviewPresentationResult.Invalid ->
        PresentationPreviewResult.Invalid(
            previewHandle = PresentationPreviewHandle(handle.value),
            request = authorizationRequest.toMobileRequestContext(
                preferredLocales = preferredLocales,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
            ),
            errorCode = error.code.toMobileErrorCode(),
            message = error.message,
        )

    is PreviewPresentationResult.Ready -> {
        PresentationPreviewResult.Ready(
            PresentationPreview(
                previewHandle = PresentationPreviewHandle(handle.value),
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

internal fun List<PresentationTransactionDataItem>.toMobileTransactionData(
    profiles: List<TransactionDataProfile>,
): List<PresentationTransactionData> {
    val profilesByType = profiles.associateBy { it.type }
    return map { item ->
        val profile = profilesByType[item.type]
        PresentationTransactionData(
            type = item.type,
            displayName = profile?.displayName ?: item.type,
            credentialQueryIds = item.credentialQueryIds,
            supportedFields = profile?.fields.orEmpty(),
            rawJson = item.rawJson.encodeJsonObject(),
            detailsJson = item.details.encodeJsonObject(),
        )
    }
}
