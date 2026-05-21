package id.walt.verifier.openid.transactiondata.profile

import id.walt.verifier.openid.transactiondata.DecodedTransactionData

class TransactionDataTypeProfileRegistry(vararg profiles: TransactionDataTypeProfile) {
    private val profilesByType: Map<String, TransactionDataTypeProfile>

    init {
        val byType = profiles.associateBy { it.type }
        require(byType.size == profiles.size) { "Duplicate transaction_data type identifiers" }

        val namespaces = profiles.map { it.mdocResponseNamespace }
        require(namespaces.size == namespaces.toSet().size) { "Duplicate mdocResponseNamespace values" }

        profilesByType = byType
    }

    val isEmpty: Boolean get() = profilesByType.isEmpty()

    fun requireProfile(type: String): TransactionDataTypeProfile =
        requireNotNull(profilesByType[type]) { "Unsupported transaction_data type: $type" }

    internal fun validateType(type: String, decodedItem: DecodedTransactionData) {
        if (profilesByType.isEmpty()) return
        requireProfile(type).validate(decodedItem)
    }
}
