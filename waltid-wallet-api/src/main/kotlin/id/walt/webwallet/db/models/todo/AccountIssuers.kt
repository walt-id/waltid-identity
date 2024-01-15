package id.walt.webwallet.db.models.todo

import id.walt.webwallet.db.models.Accounts
import kotlinx.uuid.exposed.KotlinxUUIDTable
import kotlinx.uuid.exposed.autoGenerate
import kotlinx.uuid.exposed.kotlinxUUID

object AccountIssuers : KotlinxUUIDTable("account_issuers") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUUID("accountId").autoGenerate()
    val issuer = reference("issuer", Issuers)

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
        uniqueIndex(tenant, accountId, issuer)
    }
}
