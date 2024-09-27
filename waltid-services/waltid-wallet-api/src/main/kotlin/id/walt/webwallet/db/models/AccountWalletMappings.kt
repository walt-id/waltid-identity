@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.db.models

import id.walt.commons.temp.UuidSerializer
import id.walt.webwallet.db.kotlinxUuid
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class AccountWalletPermissions(val power: Int) {
    ADMINISTRATE(9999),
    USE(100),
    READ_ONLY(10)
}

object AccountWalletMappings : Table("account_wallet_mapping") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUuid("id")
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
    @Serializable(with = UuidSerializer::class) // required to serialize Uuid, until kotlinx.serialization uses Kotlin 2.1.0
    val account: Uuid,
    val wallets: List<WalletListing>,
) {
    @Serializable
    data class WalletListing(
        @Serializable(with = UuidSerializer::class) // required to serialize Uuid, until kotlinx.serialization uses Kotlin 2.1.0
        val id: Uuid,
        val name: String,
        val createdOn: Instant,
        val addedOn: Instant,
        val permission: AccountWalletPermissions,
    )
}
