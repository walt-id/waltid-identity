package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

object WalletCategory : Table("category") {
    val wallet = reference("wallet", Wallets)
    val name = varchar("name", 128)

    override val primaryKey = PrimaryKey(wallet, name)
}

@Serializable
data class WalletCategoryData(
    val name: String,
) {
    constructor(row: ResultRow) : this(
        name = row[WalletCategory.name]
    )
}