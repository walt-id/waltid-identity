package id.walt.db.models

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletDids : Table("wallet_dids") {
    val wallet = reference("wallet", Wallets)

    val did = varchar("did", 1024)
    val alias = varchar("alias", 1024)

    val document = text("document")
    val keyId = varchar("keyId", 512)

    val default = bool("default").default(false)

    val createdOn = timestamp("createdOn")

    override val primaryKey = PrimaryKey(wallet, did)

    init {
        foreignKey(wallet, keyId, target = WalletKeys.primaryKey)
    }
}

@Serializable
data class WalletDid(
    val did: String,
    val alias: String,
    val document: String,
    val keyId: String,
    val default: Boolean,
    val createdOn: Instant
) {
    constructor(result: ResultRow) : this(
        did = result[WalletDids.did],
        alias = result[WalletDids.alias],
        document = result[WalletDids.document],
        keyId = result[WalletDids.keyId],
        default = result[WalletDids.default],
        createdOn = result[WalletDids.createdOn].toKotlinInstant()
    )
}
