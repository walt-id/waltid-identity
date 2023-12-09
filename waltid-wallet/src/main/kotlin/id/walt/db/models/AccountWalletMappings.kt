package id.walt.db.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

@Serializable
enum class AccountWalletPermissions(val power: Int) {
    ADMINISTRATE(9999),
    USE(100),
    READ_ONLY(10)
}

object AccountWalletMappings : Table("account_wallet_mapping") {
    val account = reference("account", Accounts)
    val wallet = reference("wallet", Wallets)

    val addedOn = timestamp("added_on")

    val permissions = enumerationByName<AccountWalletPermissions>("permissions", 32)

    override val primaryKey = PrimaryKey(account, wallet)
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

/*
fun main() {
    val uuid1 = UUID.generateUUID()
    val uuid2 = UUID.generateUUID()
    val uuid3 = UUID.generateUUID()
    val uuid4 = UUID.generateUUID()

    println(
        Json.encodeToString(
            AccountWalletListing(
                uuid1,
                listOf(
                    AccountWalletListing.WalletListing(uuid2, AccountWalletPermissions.ADMINISTRATE),
                    AccountWalletListing.WalletListing(uuid3, AccountWalletPermissions.USE),
                    AccountWalletListing.WalletListing(uuid4, AccountWalletPermissions.READ_ONLY)
                )
            )
        )
    )
}*/
