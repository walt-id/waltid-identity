package id.walt.verifier.openid.transactiondata

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
data class TransactionDataProfilesConfig(
    val transactionDataProfiles: List<TransactionDataProfile> = emptyList(),
) {
    fun toTypeRegistry() = TransactionDataTypeRegistry(transactionDataProfiles.map { it.type }.toSet())
}

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class TransactionDataProfile(
    val type: String,
    val displayName: String = type,
    val fields: List<String> = emptyList(),
)
