package id.walt.wallet2.mobile.digitalcredentials

import id.walt.wallet2.handlers.PreviewDcApiPresentationResult
import id.walt.wallet2.mobile.presentation.TransactionDataProfile
import id.walt.wallet2.mobile.presentation.toMobileCredentialOption
import id.walt.wallet2.mobile.presentation.toMobileCredentialRequirement
import id.walt.wallet2.mobile.presentation.toMobileTransactionData

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
        request = authorizationRequest.toMobileDigitalCredentialRequestInfo(
            preferredLocales = preferredLocales,
            transactionData = transactionData.toMobileTransactionData(transactionDataProfiles),
        ),
        credentialOptions = credentialOptions.map { it.toMobileCredentialOption() },
        credentialRequirements = credentialRequirements.map { it.toMobileCredentialRequirement() },
        readerAuthentication = DigitalCredentialReaderAuthentication.NOT_APPLICABLE,
        readerTrust = null,
    )
}
