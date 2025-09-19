@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.db.models

import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

object Wallets : UUIDTable("wallets") {
    val name = varchar("name", 128)
    val createdOn = timestamp("createdOn")
}

@Serializable
data class Wallet(
    @Contextual
    val id: Uuid,
    val name: String,
    val createdOn: Instant,
) {
    constructor(result: ResultRow) : this(
        id = result[Wallets.id].value.toKotlinUuid(),
        name = result[Wallets.name],
        createdOn = result[Wallets.createdOn].toKotlinInstant()
    )
}
