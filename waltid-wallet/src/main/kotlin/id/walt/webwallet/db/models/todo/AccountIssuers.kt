package id.walt.webwallet.db.models.todo

import id.walt.webwallet.db.models.Accounts
import kotlinx.uuid.exposed.KotlinxUUIDTable
import kotlinx.uuid.exposed.kotlinxUUID

object AccountIssuers : KotlinxUUIDTable("account_issuers") {
    val tenant = Accounts.varchar("tenant", 128).nullable() // null = global
    val accountId = kotlinxUUID("id")
    val issuer = reference("issuer", Issuers)

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
        uniqueIndex(tenant, accountId, issuer)
    }
}
