package id.walt.verifier.openid.transactiondata.profile

import id.walt.verifier.openid.transactiondata.DecodedTransactionData

class TransactionDataTypeProfileRegistry(profiles: List<TransactionDataTypeProfile> = emptyList()) {

    constructor(vararg profiles: TransactionDataTypeProfile) : this(profiles.toList())

    private val profilesByType: Map<String, TransactionDataTypeProfile> = profiles.associateBy { it.type }

    val isEmpty: Boolean get() = profilesByType.isEmpty()

    val all: List<TransactionDataTypeProfile> get() = profilesByType.values.toList()

    fun requireProfile(type: String): TransactionDataTypeProfile =
        requireNotNull(profilesByType[type]) { "Unsupported transaction_data type: $type" }

    internal fun validateType(type: String, decodedItem: DecodedTransactionData) {
        if (profilesByType.isEmpty()) return
        requireProfile(type).validate(decodedItem)
    }
}
