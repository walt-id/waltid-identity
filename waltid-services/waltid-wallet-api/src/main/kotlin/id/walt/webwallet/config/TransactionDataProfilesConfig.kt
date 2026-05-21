package id.walt.webwallet.config

import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfileRegistry

data class TransactionDataProfilesConfig(
    val transactionDataProfiles: List<TransactionDataTypeProfile> = emptyList(),
) : WalletConfig() {
    fun toRegistry() = TransactionDataTypeProfileRegistry(transactionDataProfiles)
}
