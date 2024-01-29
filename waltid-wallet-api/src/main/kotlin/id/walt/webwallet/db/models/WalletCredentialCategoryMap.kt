package id.walt.webwallet.db.models

import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.Table

object WalletCredentialCategoryMap : Table("credential_category") {
    //TODO: how are we sure wallet is consistent for both category and credential?
    val wallet = reference("wallet", Wallets)
    //thankfully it's very unlikely credential-id gets updated
    val credential = reference("credential", WalletCredentials.id)
    val category = reference("category", WalletCategory.name)

    override val primaryKey = PrimaryKey(wallet, credential, category)
}

data class WalletCredentialCategory(
    val wallet: UUID,
    val credential: WalletCredential,
    val category: List<WalletCategoryData>
)