package id.walt.db.models.todo

import id.walt.db.models.Accounts
import kotlinx.uuid.exposed.KotlinxUUIDTable

object AccountIssuers : KotlinxUUIDTable("account_issuers") {
    val account = reference("account", Accounts)
    val issuer = reference("issuer", Issuers)

    init {
        uniqueIndex(account, issuer)
    }
}
