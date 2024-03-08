package id.walt.webwallet.db.models

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Accounts : Table("accounts") {
    val tenant = varchar("tenant", 128).default("")
    val id = kotlinxUUID("id").uniqueIndex()

    val name = varchar("name", 128).nullable()

    val email = varchar("email", 128).nullable().uniqueIndex()
    val password = varchar("password", 200).nullable()

    // val loginWeb3Wallet = kotlinxUUID("web3wallet").nullable()

    val createdOn = timestamp("createdOn")

    override val primaryKey = PrimaryKey(tenant, id)

    init {
        //foreignKey(id, loginWeb3Wallet, target = Web3Wallets.primaryKey)

        /*check {
            (email.isNotNull() and password.isNotNull()) or loginWeb3Wallet.isNotNull()
        }*/
    }
}

@Serializable
data class Account(
    val tenant: String,
    val id: UUID,
    val name: String? = null,
    val email: String? = null,
    val updatePasswordTo: String? = null,
    //val loginWeb3Wallet: UUID? = null,
    val createdOn: Instant
) {
    constructor(result: ResultRow) : this(
        tenant = result[Accounts.tenant],
        id = result[Accounts.id],
        name = result[Accounts.name],
        email = result[Accounts.email],
        //loginWeb3Wallet = result[Accounts.loginWeb3Wallet]?,
        createdOn = result[Accounts.createdOn].toKotlinInstant()
    )
}
