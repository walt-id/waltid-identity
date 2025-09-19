@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.db.models

import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.ExperimentalTime

//TODO: doesn't have to be part of the relational db
object EntityNameResolutionCache : IntIdTable("entity_name_resolution_cache") {
    val did = varchar("did", 1024).uniqueIndex()
    val name = varchar("name", 512).nullable()
    val timestamp = timestamp("timestamp")
}

data class EntityNameResolutionData(
    val did: String,
    val name: String? = null,
    val timestamp: Instant? = null,
) {
    constructor(row: ResultRow) : this(
        did = row[EntityNameResolutionCache.did],
        name = row[EntityNameResolutionCache.name],
        timestamp = row[EntityNameResolutionCache.timestamp].toKotlinInstant(),
    )
}
