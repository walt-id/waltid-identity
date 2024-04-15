package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow

object WalletCategory : IntIdTable("category") {
    val wallet = reference("wallet", Wallets, onDelete = ReferenceOption.CASCADE)
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