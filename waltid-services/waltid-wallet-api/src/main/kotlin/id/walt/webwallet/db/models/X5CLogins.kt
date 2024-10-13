package id.walt.webwallet.db.models

import id.walt.webwallet.db.kotlinxUuid
import org.jetbrains.exposed.sql.Table
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object X5CLogins : Table("x5clogins") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUuid("accountId")

    val x5cId = varchar("x5cId", 256).uniqueIndex()

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
    }

    override val primaryKey = PrimaryKey(tenant, accountId, x5cId)
}
