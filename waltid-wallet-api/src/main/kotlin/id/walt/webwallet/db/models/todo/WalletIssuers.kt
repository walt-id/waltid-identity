package id.walt.webwallet.db.models.todo

import id.walt.webwallet.db.models.Wallets
import kotlinx.uuid.exposed.KotlinxUUIDTable

object WalletIssuers : KotlinxUUIDTable("wallet_issuers") {
//    val tenant = varchar("tenant", 128).default("")
//    val accountId = kotlinxUUID("accountId").autoGenerate()
    val wallet = reference("wallet", Wallets)
    val issuer = reference("issuer", Issuers)

    init {
//        foreignKey(wallet, target = Accounts.primaryKey)
        uniqueIndex(wallet, issuer)
    }
}
