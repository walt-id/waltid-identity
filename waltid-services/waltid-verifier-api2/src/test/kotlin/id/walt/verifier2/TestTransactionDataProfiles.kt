package id.walt.verifier2

import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile
import kotlinx.serialization.json.jsonPrimitive

const val PAYMENT_TYPE = "org.waltid.transaction-data.payment-authorization"
const val DOC_SIGNING_TYPE = "org.waltid.transaction-data.document-signing"

object PaymentAuthorizationProfile : TransactionDataTypeProfile(
    type = PAYMENT_TYPE,
    displayName = "Payment Authorization",
) {
    override fun validate(decoded: DecodedTransactionData) {
        val details = decoded.details
        require("amount" in details) { "payment transaction_data requires 'amount'" }
        require("currency" in details) { "payment transaction_data requires 'currency'" }
        require("payee" in details) { "payment transaction_data requires 'payee'" }
    }
}

object DocumentSigningProfile : TransactionDataTypeProfile(
    type = DOC_SIGNING_TYPE,
    displayName = "Document Signing",
) {
    override fun isApplicable(credentialFormat: String, docType: String?): Boolean =
        credentialFormat == "mso_mdoc"

    override fun validate(decoded: DecodedTransactionData) {
        val details = decoded.details
        require("document_hash" in details) { "document-signing transaction_data requires 'document_hash'" }
        require("hash_algorithm_identifier" in details) { "document-signing transaction_data requires 'hash_algorithm_identifier'" }
    }

    override fun mdocExtraResponseItems(decoded: DecodedTransactionData): Map<String, Any> = buildMap {
        decoded.details["document_reference"]?.jsonPrimitive?.content?.let {
            put("document_reference", it)
        }
    }
}
