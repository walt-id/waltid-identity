package id.walt.webwallet.service.events

import id.walt.webwallet.db.models.Events
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class EventService {
    fun get(
        accountId: UUID,
        walletId: UUID,
        limit: Int,
        offset: Long,
        sortOrder: String,
        sortBy: String,
        dataFilter: Map<String, String>
    ) = transaction {
        Events.selectAll().where { Events.account eq accountId or (Events.wallet eq walletId) }
            .orderBy(getColumn(sortBy) ?: Events.timestamp,
                sortOrder.takeIf { it.uppercase() == "ASC" }?.let { SortOrder.ASC } ?: SortOrder.DESC)
            .limit(n = limit, offset = offset).addWhereClause(dataFilter).map {
                Event(it)
            }
    }

    fun count(walletId: UUID, dataFilter: Map<String, String>): Long =
        Events.selectAll().where { Events.wallet eq walletId }.addWhereClause(dataFilter).count()


    fun add(event: Event): Unit = transaction {
        Events.insert {
            it[tenant] = event.tenant
            it[originator] = event.originator ?: "unknown"
            it[account] = event.account
            it[wallet] = event.wallet
            it[timestamp] = event.timestamp.toJavaInstant()
            it[this.event] = event.event
            it[action] = event.action
            it[data] = Json.encodeToString(event.data)
        }
    }

    private fun Query.addWhereClause(dataFilter: Map<String, String>) = let {
        dataFilter.forEach {
            when (it.key.lowercase()) {
                "event" -> this.andWhere { Events.event eq it.value }
                "action" -> this.andWhere { Events.action eq it.value }
                "tenant" -> this.andWhere { Events.tenant eq it.value }
                "originator" -> this.andWhere { Events.originator eq it.value }
            }
        }
        this
    }

    private fun getColumn(name: String) = Events.columns.singleOrNull {
        it.name == name.lowercase()
    }
}
