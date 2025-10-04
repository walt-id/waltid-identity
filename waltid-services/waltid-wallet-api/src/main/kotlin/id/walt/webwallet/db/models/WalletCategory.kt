package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object WalletCategory : IntIdTable("category") {
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
