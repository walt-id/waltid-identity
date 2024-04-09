package id.walt.webwallet.db.models

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.timestamp

object EntityNameResolutionCache : IntIdTable("entity_name_resolution_cache") {
    val did = varchar("did", 1024).uniqueIndex()
    val name = varchar("name", 512).nullable()
    val age = timestamp("age")
}

data class EntityNameResolutionData(
    val did: String,
    val name: String?,
    val age: Instant,
) {
    constructor(row: ResultRow) : this(
        did = row[EntityNameResolutionCache.did],
        name = row[EntityNameResolutionCache.name],
        age = row[EntityNameResolutionCache.age].toKotlinInstant(),
    )
}