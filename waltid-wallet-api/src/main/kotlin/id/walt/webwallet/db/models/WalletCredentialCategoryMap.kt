package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.Table

object WalletCredentialCategoryMap : Table("credential_category") {
    //TODO: validate wallet is consistent for both category and credential
    val wallet = kotlinxUUID("wallet")
    val credential = varchar("credential", 256)
    val category = reference("category", WalletCategory)

    override val primaryKey = PrimaryKey(wallet, credential, category)

    init {
        foreignKey(wallet, credential, target = WalletCredentials.primaryKey)
    }
}