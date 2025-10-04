package id.walt.webwallet.db.models

import id.walt.webwallet.db.kotlinxUuid
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object Events : IntIdTable("events") {
    val tenant = varchar("tenant", 128).default("")
    val originator = varchar("originator", 128).default("unknown")
    val account = kotlinxUuid("account")
    val wallet = kotlinxUuid("wallet").nullable()
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
