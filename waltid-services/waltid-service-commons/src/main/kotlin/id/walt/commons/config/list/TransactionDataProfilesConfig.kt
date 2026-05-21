package id.walt.commons.config.list

import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfileRegistry
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDataProfilesConfig(
    val transactionDataProfiles: List<TransactionDataTypeProfile> = emptyList(),
) {
    fun toRegistry() = TransactionDataTypeProfileRegistry(transactionDataProfiles)
}
