package id.walt.commons.config.list

import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDataProfilesConfig(
    val transactionDataProfiles: List<TransactionDataProfile> = emptyList(),
) {
    fun toTypeRegistry() = TransactionDataTypeRegistry(
        types = transactionDataProfiles.map { it.type }.toSet(),
        fieldsByType = transactionDataProfiles.associate { it.type to it.fields.toSet() },
    )
}

@Serializable
data class TransactionDataProfile(
    val type: String,
    val displayName: String = type,
    val fields: List<String> = emptyList(),
)
