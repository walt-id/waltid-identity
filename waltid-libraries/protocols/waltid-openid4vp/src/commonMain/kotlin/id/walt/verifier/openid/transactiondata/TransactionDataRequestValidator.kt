package id.walt.verifier.openid.transactiondata

import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfileRegistry

private val supportedTransactionDataFormats = setOf(
    CredentialFormat.DC_SD_JWT,
    CredentialFormat.MSO_MDOC,
)

fun validateRequestTransactionData(
    transactionData: List<String>?,
    profileRegistry: TransactionDataTypeProfileRegistry = TransactionDataTypeProfileRegistry(),
    credentialQueriesById: Map<String, CredentialQuery>? = null,
): List<DecodedTransactionData> {
    val decodedItems = decodeList(transactionData.orEmpty())

    decodedItems.forEach { decodedItem ->
        val item = decodedItem.transactionData
        require(item.type.isNotBlank()) { "transaction_data.type must not be blank" }
        profileRegistry.validateType(item.type, decodedItem)
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
                require(credentialQuery.format in supportedTransactionDataFormats) {
                    "transaction_data.credential_ids must reference credential queries with a supported format (${supportedTransactionDataFormats.joinToString()})"
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
