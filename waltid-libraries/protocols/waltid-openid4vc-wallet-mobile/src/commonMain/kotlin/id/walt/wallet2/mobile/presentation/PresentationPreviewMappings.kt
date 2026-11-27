package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.handlers.PresentationTransactionDataItem
import id.walt.wallet2.handlers.PreviewPresentationResult
import id.walt.wallet2.mobile.internal.encodeJsonObject

internal fun PreviewPresentationResult.toPresentationPreviewResult(
    preferredLocales: List<String>,
    transactionDataProfiles: List<TransactionDataProfile>,
): PresentationPreviewResult = when (this) {
    is PreviewPresentationResult.Invalid ->
        PresentationPreviewResult.Invalid(
            previewHandle = PresentationPreviewHandle(handle.value),
            request = authorizationRequest.toPartialPresentationRequest(
                preferredLocales = preferredLocales,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
            ),
            errorCode = error.code.toPresentationErrorCode(),
            message = error.message,
        )

    is PreviewPresentationResult.Ready -> {
        PresentationPreviewResult.Ready(
            PresentationPreview(
                previewHandle = PresentationPreviewHandle(handle.value),
                request = authorizationRequest.toPresentationRequest(
                    preferredLocales = preferredLocales,
                    resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                    responseEncryption = responseEncryption,
                    transactionData = transactionData.toPresentationTransactionData(transactionDataProfiles),
                ),
                credentialOptions = credentialOptions.map { it.toPresentationCredentialOption() },
                credentialRequirements = credentialRequirements.map { it.toPresentationCredentialRequirement() },
            )
        )
    }
}

internal fun List<PresentationTransactionDataItem>.toPresentationTransactionData(
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
