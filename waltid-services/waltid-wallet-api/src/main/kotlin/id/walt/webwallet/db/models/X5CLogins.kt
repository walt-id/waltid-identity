package id.walt.webwallet.db.models

import org.jetbrains.exposed.sql.Table

object X5CLogins : Table("x5clogins") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = uuid("accountId")

    val x5cId = varchar("x5cId", 256).uniqueIndex()

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
    }

    override val primaryKey = PrimaryKey(tenant, accountId, x5cId)
}
