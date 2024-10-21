@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.db.models

import id.walt.commons.temp.UuidSerializer
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.timestamp
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

object Wallets : UUIDTable("wallets") {
    val name = varchar("name", 128)
    val createdOn = timestamp("createdOn")
}

@Serializable
data class Wallet(
    @Serializable(with = UuidSerializer::class) // required to serialize Uuid, until kotlinx.serialization uses Kotlin 2.1.0
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
