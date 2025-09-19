@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.db.models

import id.walt.webwallet.db.kotlinxUuid
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.ExperimentalTime
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
    @OptIn(ExperimentalUuidApi::class)
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
    @Contextual
    val account: Uuid,
    val wallets: List<WalletListing>,
) {
    @Serializable
    data class WalletListing(
        @Contextual
        val id: Uuid,
        val name: String,
        val createdOn: Instant,
        val addedOn: Instant,
        val permission: AccountWalletPermissions,
    )
}
