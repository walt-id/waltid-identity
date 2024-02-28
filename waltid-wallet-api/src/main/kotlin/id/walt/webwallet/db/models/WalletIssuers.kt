package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.KotlinxUUIDTable

object WalletIssuers : KotlinxUUIDTable("wallet_issuers") {
//    val tenant = varchar("tenant", 128).default("")
//    val accountId = kotlinxUUID("accountId").autoGenerate()
    val wallet = reference("wallet", Wallets)
    val issuer = reference("issuer", Issuers)
    val authorized = bool("authorized").default(false)//authorized to push credentials silently

    init {
//        foreignKey(wallet, target = Accounts.primaryKey)
        uniqueIndex(wallet, issuer)
    }
}
