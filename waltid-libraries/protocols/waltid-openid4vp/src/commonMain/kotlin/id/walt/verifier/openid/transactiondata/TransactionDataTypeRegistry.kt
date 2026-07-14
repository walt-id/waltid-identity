package id.walt.verifier.openid.transactiondata

/**
 * Registry of known/supported transaction_data types.
 * 
 * Per OID4VP 1.0 §5.5.1, wallets MUST reject authorization requests containing
 * transaction_data with an unknown type. This registry defines the known types.
 * 
 * @param types The set of allowed transaction_data types.
 *              If empty and [strictMode] is true, ALL types are rejected.
 *              If empty and [strictMode] is false, all types are allowed.
 */
open class TransactionDataTypeRegistry(
    val types: Set<String>,
    private val strictMode: Boolean = true
) {

    constructor(vararg types: String) : this(types.toSet(), strictMode = true)

    /**
     * Validates that [type] is a known/supported transaction_data type.
     * 
     * @throws IllegalArgumentException if [type] is not in [types] and [strictMode] is true
     */
    open fun requireKnown(type: String) {
        if (strictMode || types.isNotEmpty()) {
            require(type in types) { "Unsupported transaction_data type: $type" }
        }
        // In non-strict mode with empty types, all types are allowed
    }
    
    companion object {
        /**
         * Standard transaction_data types defined by OID4VP and HAIP.
         * - payment_confirmation: For payment authorization flows
         * - qes_authorization: For qualified electronic signature authorization
         */
        val STANDARD = TransactionDataTypeRegistry(
            setOf("payment_confirmation", "qes_authorization"),
            strictMode = true
        )
        
        /**
         * Permissive registry that accepts all types (for backwards compatibility).
         * Use this only when strict type validation is not required.
         */
        val PERMISSIVE = TransactionDataTypeRegistry(emptySet(), strictMode = false)
    }
}