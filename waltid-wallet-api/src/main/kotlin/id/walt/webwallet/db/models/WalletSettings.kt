package id.walt.webwallet.db.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object WalletSettings : Table("wallet_settings") {
    val wallet = reference("wallet", Wallets, onDelete = ReferenceOption.CASCADE)
    val settings = text("settings").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(wallet)
}