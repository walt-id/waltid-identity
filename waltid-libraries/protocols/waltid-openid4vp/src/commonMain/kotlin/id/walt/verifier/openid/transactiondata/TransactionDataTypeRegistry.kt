package id.walt.verifier.openid.transactiondata

data class TransactionDataTypeRegistry(val types: Set<String>) {

    constructor(vararg types: String) : this(types.toSet())

    fun requireKnown(type: String) = require(type in types) { "Unsupported transaction_data type: $type" }
}