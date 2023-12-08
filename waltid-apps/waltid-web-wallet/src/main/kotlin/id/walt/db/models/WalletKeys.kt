package id.walt.db.models

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletKeys : Table("wallet_keys") {
    val wallet = reference("wallet", Wallets)
    val keyId = varchar("kid", 512)
    val document = text("document")

    val createdOn = timestamp("createdOn")

    override val primaryKey = PrimaryKey(wallet, keyId)
}

@Serializable
data class WalletKey(
    val keyId: String,
    val document: String,
    val createdOn: Instant
) {
    constructor(result: ResultRow) : this(
        keyId = result[WalletKeys.keyId],
        document = result[WalletKeys.document],
        createdOn = result[WalletKeys.createdOn].toKotlinInstant()
    )
}
