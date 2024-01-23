package id.walt.webwallet.db.models

import kotlinx.uuid.UUID
import kotlinx.uuid.exposed.KotlinxUUIDTable

object WalletCredentialCategoryMap : KotlinxUUIDTable("credential_category") {
    val credential = reference("credential", WalletCredentials.id)//thankfully it's very unlikely credential-id gets updated
    val category = reference("category", WalletCategory)
    val wallet = reference("wallet", Wallets)
    //TODO: how are we sure wallet is consistent for both category and credential?
    init {
        uniqueIndex(wallet, credential, category)
    }
}

data class WalletCredentialCategory(
    val wallet: UUID,
    val credential: WalletCredential,
    val category: List<WalletCategoryData>
)