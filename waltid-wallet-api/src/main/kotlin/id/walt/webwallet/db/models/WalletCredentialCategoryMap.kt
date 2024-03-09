package id.walt.webwallet.db.models

import org.jetbrains.exposed.sql.Table

object WalletCredentialCategoryMap : Table("credential_category") {
    //TODO: validate wallet is consistent for both category and credential
    val wallet = reference("wallet", Wallets)
    val credential = reference("credential", WalletCredentials)
    val category = reference("category", WalletCategory)

    override val primaryKey = PrimaryKey(wallet, credential, category)
}