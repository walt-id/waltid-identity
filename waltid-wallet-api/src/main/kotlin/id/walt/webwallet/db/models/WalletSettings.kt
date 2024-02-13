package id.walt.webwallet.db.models

import org.jetbrains.exposed.sql.Table

object WalletSettings : Table("wallet_settings") {
    val wallet = reference("wallet", Wallets)
    val showNoteOnPresentation = bool("note").default(true)

    override val primaryKey: PrimaryKey = PrimaryKey(wallet)
}