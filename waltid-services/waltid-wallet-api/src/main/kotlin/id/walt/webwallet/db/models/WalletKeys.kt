@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

object WalletKeys : Table("wallet_keys") {
    val wallet = reference("wallet", Wallets)
    val keyId = varchar("kid", 512)
    val document = text("document")
    val name = varchar("name", 255).nullable()
    val createdOn = timestamp("createdOn")

    override val primaryKey = PrimaryKey(wallet, keyId)
}

@Serializable
data class WalletKey(
    val keyId: String,
    val document: String,
    val name: String?,
    val createdOn: Instant,
) {
    constructor(result: ResultRow) : this(
        keyId = result[WalletKeys.keyId],
        document = result[WalletKeys.document],
        name = result[WalletKeys.name],
        createdOn = result[WalletKeys.createdOn].toKotlinInstant()
    )
}
