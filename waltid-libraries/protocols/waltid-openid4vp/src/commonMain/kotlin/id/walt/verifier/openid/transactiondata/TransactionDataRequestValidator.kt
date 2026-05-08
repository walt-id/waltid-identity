package id.walt.verifier.openid.transactiondata

import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery

private val supportedTransactionDataFormats = setOf(
    CredentialFormat.DC_SD_JWT,
    CredentialFormat.MSO_MDOC,
)

fun validateRequestTransactionData(
    transactionData: List<String>?,
    supportedTypes: Set<String>? = null,
    credentialQueriesById: Map<String, CredentialQuery>? = null,
): List<DecodedTransactionData> {
    val decodedItems = decodeList(transactionData.orEmpty())

    decodedItems.forEach { decodedItem ->
        val item = decodedItem.transactionData
        require(item.type.isNotBlank()) { "transaction_data.type must not be blank" }
        if (supportedTypes != null) {
            require(item.type in supportedTypes) { "Unsupported transaction_data type: ${item.type}" }
        }
        require(item.credentialIds.isNotEmpty()) { "transaction_data.credential_ids must not be empty" }
        require(item.requireCryptographicHolderBinding != false) {
            "transaction_data type ${item.type} requires cryptographic holder binding"
        }

        if (credentialQueriesById != null) {
            require(item.credentialIds.all { it in credentialQueriesById }) {
                "transaction_data.credential_ids must refer to credential ids from the authorization request"
            }
            item.credentialIds.forEach { credentialId ->
                val credentialQuery = credentialQueriesById.getValue(credentialId)
                require(isTransactionDataSupportedFormat(credentialQuery.format)) {
                    "transaction_data.credential_ids must reference credential queries with a supported transaction_data profile"
                }
                require(credentialQuery.requireCryptographicHolderBinding) {
                    "transaction_data.credential_ids must require cryptographic holder binding"
                }
            }
        }

        item.transactionDataHashesAlg?.let(::requireSupportedHashAlgorithms)
    }

    return decodedItems
}

private fun isTransactionDataSupportedFormat(format: CredentialFormat): Boolean =
    format in supportedTransactionDataFormats
