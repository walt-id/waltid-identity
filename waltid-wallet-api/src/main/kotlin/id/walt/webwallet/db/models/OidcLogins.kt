package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object OidcLogins : Table("oidclogins") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUUID("accountId")

    val oidcId = varchar("oidcId", 256).uniqueIndex()

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey, onDelete = ReferenceOption.CASCADE)
    }

    override val primaryKey = PrimaryKey(tenant, accountId, oidcId)
}
