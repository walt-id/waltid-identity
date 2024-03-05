package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.KotlinxUUIDTable

object WalletIssuers : KotlinxUUIDTable("wallet_issuers") {
    val wallet = reference("wallet", Wallets)
    val name = varchar("name", 512)
    val description = text("description").nullable().default("no description")
    val uiEndpoint = varchar("ui", 128)
    val configurationEndpoint = varchar("configuration", 256)
    val authorized = bool("authorized").default(false)//authorized to push credentials silently

    init {
        uniqueIndex(wallet, name)
    }
}
