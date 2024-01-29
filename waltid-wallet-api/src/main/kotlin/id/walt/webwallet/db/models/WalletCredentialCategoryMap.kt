package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.Table

object WalletCredentialCategoryMap : Table("credential_category") {
    //TODO: validate wallet is consistent for both category and credential
    val wallet = kotlinxUUID("wallet")
    //TODO: what if the [id] column in credentials table gets updated?
    val credential = varchar("credential", 256)
    //TODO: what if the [name] column in category table gets updated?
    val category = varchar("category", 128)

    override val primaryKey = PrimaryKey(wallet, credential, category)

    init {
        foreignKey(wallet, credential, target = WalletCredentials.primaryKey)
        foreignKey(wallet, category, target = WalletCategory.primaryKey)
    }
}