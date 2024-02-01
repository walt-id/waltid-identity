package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import kotlinx.uuid.exposed.KotlinxUUIDTable
import org.jetbrains.exposed.sql.ResultRow

object WalletCategory : KotlinxUUIDTable("category") {
    val wallet = reference("wallet", Wallets)
    val name = varchar("name", 128)

    init {
        uniqueIndex(wallet, name)
    }
}

@Serializable
data class WalletCategoryData(
    val name: String,
) {
    constructor(row: ResultRow) : this(
        name = row[WalletCategory.name]
    )
}