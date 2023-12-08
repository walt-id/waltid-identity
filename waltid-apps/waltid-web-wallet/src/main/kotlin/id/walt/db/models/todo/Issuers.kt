package id.walt.db.models.todo

import kotlinx.uuid.UUID
import kotlinx.uuid.exposed.KotlinxUUIDTable
import org.jetbrains.exposed.sql.ResultRow

object Issuers : KotlinxUUIDTable("issuers") {
    val name = varchar("name", 128).uniqueIndex()
    val description = text("description").nullable().default("no description")
    val uiEndpoint = varchar("ui", 128)
    val configurationEndpoint = varchar("configuration", 256)
}

data class Issuer(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val uiEndpoint: String,
    val configurationEndpoint: String,
) {
    constructor(result: ResultRow) : this(
        id = result[Issuers.id].value,
        name = result[Issuers.name],
        description = result[Issuers.description],
        uiEndpoint = result[Issuers.uiEndpoint],
        configurationEndpoint = result[Issuers.configurationEndpoint]
    )
}
