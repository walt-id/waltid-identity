package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.KotlinxUUIDTable

object WalletCredentialCategoryMap : KotlinxUUIDTable("wallet_credential_category_map") {
    val wallet = reference("wallet", Wallets)
    val category = reference("category", WalletCategory)
}