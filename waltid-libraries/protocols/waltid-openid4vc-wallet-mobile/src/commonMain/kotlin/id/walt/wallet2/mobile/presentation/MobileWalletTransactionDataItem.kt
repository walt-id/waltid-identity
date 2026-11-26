package id.walt.wallet2.mobile.presentation

/**
 * Decoded transaction_data item attached to a presentation request.
 *
 * @property type Transaction data type value.
 * @property displayName Human-readable label from the accepted mobile transaction data profile.
 * @property credentialQueryIds Credential query identifiers to which this transaction data applies.
 * @property supportedFields Profile-declared transaction-data fields this wallet accepts for [type].
 * @property rawJson Full transaction data item encoded as JSON.
 * @property detailsJson Decoded transaction data details encoded as JSON.
 */
public data class MobileWalletTransactionDataItem(
    val type: String,
    val displayName: String,
    val credentialQueryIds: List<String>,
    val supportedFields: List<String>,
    val rawJson: String,
    val detailsJson: String,
) {
    init {
        require(credentialQueryIds.isNotEmpty()) {
            "Transaction data must reference at least one credential query ID."
        }
        require(credentialQueryIds.all(String::isNotBlank)) {
            "Transaction data credential query IDs must not be blank."
        }
    }
}
