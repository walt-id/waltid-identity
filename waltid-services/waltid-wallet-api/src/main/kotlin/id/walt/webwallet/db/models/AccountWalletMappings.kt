package id.walt.webwallet.db.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

@Serializable
enum class AccountWalletPermissions(val power: Int) {
    ADMINISTRATE(9999),
    USE(100),
    READ_ONLY(10)
}

object AccountWalletMappings : Table("account_wallet_mapping") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUUID("id")
    val wallet = reference("wallet", Wallets)

    val addedOn = timestamp("added_on")

    val permissions = enumerationByName<AccountWalletPermissions>("permissions", 32)

    override val primaryKey = PrimaryKey(tenant, accountId, wallet)

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
    }
}

@Serializable
data class AccountWalletListing(
    val account: UUID,
    val wallets: List<WalletListing>
) {
    @Serializable
    data class WalletListing(
        val id: UUID,
        val name: String,
        val createdOn: Instant,
        val addedOn: Instant,
        val permission: AccountWalletPermissions
    )
}
