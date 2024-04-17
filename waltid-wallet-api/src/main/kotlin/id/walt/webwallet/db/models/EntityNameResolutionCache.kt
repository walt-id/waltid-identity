package id.walt.webwallet.db.models

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.timestamp

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