package id.walt.issuer.issuance.openapi.issuerapi

import io.github.smiley4.ktoropenapi.config.RequestConfig

object IssuanceRequestErrors {
    const val MISSING_ISSUER_KEY = "Missing issuerKey in the request body."
    const val INVALID_ISSUER_KEY_FORMAT = "Invalid issuerKey format."
    const val MISSING_SUBJECT_DID = "Missing subjectDid in the request body."
    const val MISSING_ISSUER_DID = "Missing issuerDid in the request body."
    const val MISSING_CREDENTIAL_CONFIGURATION_ID = "Missing credentialConfigurationId in the request body."
    const val MISSING_CREDENTIAL_DATA = "Missing credentialData in the request body."
    const val INVALID_CREDENTIAL_DATA_FORMAT = "Invalid credentialData format."
}

fun RequestConfig.statusCallbackUriHeader() = headerParameter<String>("statusCallbackUri") {
    description = "Callback to push state changes of the issuance process to"
    required = false
}

fun RequestConfig.sessionTtlHeader() = headerParameter<Long>("sessionTtl") {
    description = "Custom session time-to-live in seconds"
    required = false
}

