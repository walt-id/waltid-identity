package id.walt.wallet2.mobile.digitalcredentials

import id.walt.wallet2.handlers.PreviewDcApiPresentationResult
import id.walt.wallet2.mobile.presentation.TransactionDataProfile
import id.walt.wallet2.mobile.presentation.toPresentationCredentialOption
import id.walt.wallet2.mobile.presentation.toPresentationCredentialRequirement
import id.walt.wallet2.mobile.presentation.toPresentationTransactionData

internal fun PreviewDcApiPresentationResult.toDigitalCredentialPreview(
    protocol: String,
    preferredLocales: List<String>,
    transactionDataProfiles: List<TransactionDataProfile>,
): DigitalCredentialPreview {
    val authorizationRequest = resolvedRequest.authorizationRequest
    return DigitalCredentialPreview(
        requestId = requestId,
        protocol = protocol,
        verifiedOrigin = resolvedRequest.origin,
        request = authorizationRequest.toDigitalCredentialRequestInfo(
            preferredLocales = preferredLocales,
            transactionData = transactionData.toPresentationTransactionData(transactionDataProfiles),
        ),
        credentialOptions = credentialOptions.map { it.toPresentationCredentialOption() },
        credentialRequirements = credentialRequirements.map { it.toPresentationCredentialRequirement() },
        readerAuthentication = DigitalCredentialReaderAuthentication.NOT_APPLICABLE,
        readerTrust = null,
    )
}
