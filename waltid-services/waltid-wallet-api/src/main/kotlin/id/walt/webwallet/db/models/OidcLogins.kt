package id.walt.webwallet.db.models

import id.walt.webwallet.db.kotlinxUuid
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object OidcLogins : Table("oidclogins") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUuid("accountId")

    val oidcId = varchar("oidcId", 256).uniqueIndex()

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
    }

    override val primaryKey = PrimaryKey(tenant, accountId, oidcId)
}
