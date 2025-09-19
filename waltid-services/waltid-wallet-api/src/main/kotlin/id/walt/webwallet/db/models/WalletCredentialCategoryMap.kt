package id.walt.webwallet.db.models

import id.walt.webwallet.db.kotlinxUuid
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object WalletCredentialCategoryMap : Table("credential_category") {
    //TODO: validate wallet is consistent for both category and credential
    val wallet = kotlinxUuid("wallet")
    val credential = varchar("credential", 256)
    val category = reference("category", WalletCategory)

    override val primaryKey = PrimaryKey(wallet, credential, category)

    init {
        foreignKey(wallet, credential, target = WalletCredentials.primaryKey)
    }
}
