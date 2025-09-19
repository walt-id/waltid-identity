package id.walt.webwallet.db.models

import org.jetbrains.exposed.v1.core.Table

object WalletSettings : Table("wallet_settings") {
    val wallet = reference("wallet", Wallets)
    val settings = text("settings").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(wallet)
}
