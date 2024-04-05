package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.KotlinxUUIDTable

object WalletIssuers : KotlinxUUIDTable("wallet_issuers") {
    val wallet = reference("wallet", Wallets)
    val did = varchar("did", 512)
    val name = varchar("name", 512).nullable()
    val description = text("description").nullable().default("no description")
    val uiEndpoint = varchar("ui", 128)
    val configurationEndpoint = varchar("configuration", 256)
    val authorized = bool("authorized").default(false)//authorized to push credentials silently

    init {
        uniqueIndex(wallet, did)
    }
}
