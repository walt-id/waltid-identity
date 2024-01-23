package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import kotlinx.uuid.exposed.KotlinxUUIDTable
import org.jetbrains.exposed.sql.ResultRow

object Category : KotlinxUUIDTable("category") {
    val name = text("name").uniqueIndex()
}

@Serializable
data class CategoryData(
    val name: String,
) {
    constructor(row: ResultRow) : this(
        name = row[Category.name]
    )
}