package id.walt.verifier.openid.transactiondata

data class TransactionDataTypeRegistry(
    val types: Set<String>,
    val fieldsByType: Map<String, Set<String>> = emptyMap(),
) {

    constructor(vararg types: String) : this(types.toSet())

    fun requireKnown(type: String) = require(type in types) { "Unsupported transaction_data type: $type" }

    fun requireConforming(type: String, fields: Set<String>) {
        requireKnown(type)
        val supportedFields = fieldsByType[type].orEmpty()
        if (supportedFields.isNotEmpty()) {
            require(fields.all { it in supportedFields }) {
                "Unsupported transaction_data fields for type '$type': ${(fields - supportedFields).joinToString()}"
            }
        }
    }
}
