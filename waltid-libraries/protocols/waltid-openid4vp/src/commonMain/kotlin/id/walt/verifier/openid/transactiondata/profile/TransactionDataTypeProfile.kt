package id.walt.verifier.openid.transactiondata.profile

import id.walt.verifier.openid.transactiondata.DecodedTransactionData

abstract class TransactionDataTypeProfile(
    val type: String,
    val displayName: String,
    val mdocResponseNamespace: String = type,
) {
    open fun isApplicable(credentialFormat: String, docType: String? = null): Boolean = true

    open fun validate(decoded: DecodedTransactionData) = Unit

    open fun mdocExtraResponseItems(decoded: DecodedTransactionData): Map<String, Any> = emptyMap()
}
