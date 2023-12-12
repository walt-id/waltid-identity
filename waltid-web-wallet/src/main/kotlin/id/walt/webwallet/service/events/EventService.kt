package id.walt.webwallet.service.events

import id.walt.webwallet.db.models.Events
import id.walt.webwallet.db.models.WalletOperationHistories
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object EventService {
    fun get(walletId: UUID, limit: Int, offset: Long): List<Event> =
        Events.select { WalletOperationHistories.wallet eq walletId }
            .orderBy(WalletOperationHistories.timestamp)
            .limit(n = limit, offset = offset).map {
                Event(it)
            }

    fun add(event: Event): Unit = transaction {
        Events.insert {
            it[tenant] = event.tenant ?: "global"
            it[originator] = event.originator ?: "unknown"
            it[account] = event.account
            it[wallet] = event.wallet
            it[timestamp] = event.timestamp.toJavaInstant()
            it[this.event] = event.event
            it[action] = event.action
            it[data] = Json.encodeToString(event.data)
        }
    }
}