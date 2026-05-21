package id.walt.webwallet.service.exchange.transactiondata

import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile
import kotlinx.serialization.json.jsonPrimitive

object DocumentSigningTransactionDataProfile : TransactionDataTypeProfile(
    type = "org.waltid.transaction-data.document-signing",
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
