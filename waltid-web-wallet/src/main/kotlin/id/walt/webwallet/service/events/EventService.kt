package id.walt.webwallet.service.events

import id.walt.webwallet.db.models.Events
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object EventService {
    fun get(walletId: UUID, limit: Int, offset: Long, dataFilter: Map<String, String> = emptyMap()): List<Event> =
        addWhereClause(
            Events.select { Events.wallet eq walletId }, dataFilter
        ).orderBy(Events.timestamp).limit(n = limit, offset = offset).map {
                Event(it)
            }

    fun count(walletId: UUID, dataFilter: Map<String, String> = emptyMap()): Long = addWhereClause(
        Events.select { Events.wallet eq walletId }, dataFilter
    ).count()


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

    private fun addWhereClause(query: Query, dataFilter: Map<String, String>) = let {
        dataFilter["event"]?.let {
            query.adjustWhere { Events.event eq it }
        }
        dataFilter["action"]?.let {
            query.adjustWhere { Events.action eq it }
        }
        dataFilter["tenant"]?.let {
            query.adjustWhere { Events.tenant eq it }
        }
        dataFilter["originator"]?.let {
            query.adjustWhere { Events.originator eq it }
        }
        query
    }
}