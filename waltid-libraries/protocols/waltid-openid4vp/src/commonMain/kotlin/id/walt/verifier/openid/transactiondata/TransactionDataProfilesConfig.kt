package id.walt.verifier.openid.transactiondata

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDataProfilesConfig(
    val transactionDataProfiles: List<TransactionDataProfile> = emptyList(),
) {
    fun toTypeRegistry() = TransactionDataTypeRegistry(transactionDataProfiles.map { it.type }.toSet())
}

@Serializable
data class TransactionDataProfile(
    val type: String,
    val displayName: String = type,
    val fields: List<String> = emptyList(),
)
