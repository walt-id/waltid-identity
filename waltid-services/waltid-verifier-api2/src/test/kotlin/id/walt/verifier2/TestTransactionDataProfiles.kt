package id.walt.verifier2

import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile

const val PAYMENT_TYPE = "org.waltid.transaction-data.payment-authorization"
const val DOC_SIGNING_TYPE = "org.waltid.transaction-data.document-signing"

val PaymentAuthorizationProfile = TransactionDataTypeProfile(
    type = PAYMENT_TYPE,
    displayName = "Payment Authorization",
    requiredFields = listOf("amount", "currency", "payee"),
)

val DocumentSigningProfile = TransactionDataTypeProfile(
    type = DOC_SIGNING_TYPE,
    displayName = "Document Signing",
    applicableFormats = listOf("mso_mdoc"),
    requiredFields = listOf("document_hash", "hash_algorithm_identifier"),
    mdocExtraFields = listOf("document_reference"),
)
