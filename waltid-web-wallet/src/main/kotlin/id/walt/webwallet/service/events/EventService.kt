package id.walt.webwallet.service.events

import id.walt.webwallet.db.models.Events
import id.walt.webwallet.db.models.WalletOperationHistories
import id.walt.webwallet.db.models.WalletOperationHistory
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
            .limit(n = limit, offset = offset)
            .map { row ->
                Event(row)
            }

    fun add(operation: WalletOperationHistory): Unit = transaction {
        WalletOperationHistories.insert {
            it[tenant] = operation.tenant
            it[accountId] = operation.account
            it[wallet] = operation.wallet
            it[timestamp] = operation.timestamp.toJavaInstant()
            it[this.operation] = operation.operation
            it[data] = Json.encodeToString(operation.data)
        }
    }
}