package id.walt.verifier.openid.transactiondata

/**
 * Registry of known/supported transaction_data types.
 *
 * Per OID4VP 1.0 §5.1 and §8.5, wallets MUST reject authorization requests containing
 * transaction_data with an unknown type. This registry defines the known types.
 *
 * @param types The set of allowed transaction_data types.
 *              An empty set rejects every transaction-data type.
 */
open class TransactionDataTypeRegistry(
    val types: Set<String>,
) {

    constructor(vararg types: String) : this(types.toSet())

    /**
     * Validates that [type] is a known/supported transaction_data type.
     *
     * @throws IllegalArgumentException if [type] is not explicitly registered
     */
    open fun requireKnown(type: String) {
        require(type in types) { "Unsupported transaction_data type: $type" }
    }
}
