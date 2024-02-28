package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Events : IntIdTable("events") {
    val tenant = varchar("tenant", 128).default("")
    val originator = varchar("originator", 128).default("unknown")
    val account = kotlinxUUID("account")
    val wallet = kotlinxUUID("wallet").nullable()
    val credentialId = varchar("credential", 256).nullable()
    val timestamp = timestamp("timestamp")
    val event = varchar("event", 48)
    val action = varchar("action", 48)
    val data = text("data")
    val note = text("note").nullable()

    init {
        foreignKey(tenant, account, target = Accounts.primaryKey)
        index(false, tenant, account, wallet)
    }
}
