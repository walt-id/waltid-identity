package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.KotlinxUUIDTable
import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.javatime.timestamp

object Events : KotlinxUUIDTable("events") {
    val tenant = varchar("tenant", 128).default("global")
    val originator = varchar("originator", 128).default("unknown")
    val account = kotlinxUUID("accountId")
    val wallet = reference("wallet", Wallets)
    val timestamp = timestamp("timestamp")
    val event = varchar("event", 48)
    val action = varchar("action", 48)
    val data = text("data")

    init {
        foreignKey(tenant, account, target = Accounts.primaryKey)
        index(false, tenant, account, wallet)
    }
}