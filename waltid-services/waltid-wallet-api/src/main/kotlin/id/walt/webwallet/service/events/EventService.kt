@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.service.events

import id.walt.webwallet.db.models.Events
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EventService {

    fun get(
        accountId: Uuid,
        walletId: Uuid,
        limit: Int?,
        offset: Long,
        sortOrder: String,
        sortBy: String,
        dataFilter: Map<String, List<String>>,
    ) = transaction {
        let {
            limit?.let {
                getFilterQueryLimited(
                    accountId = accountId,
                    walletId = walletId,
                    sortOrder = sortOrder,
                    sortBy = sortBy,
                    dataFilter = dataFilter,
                    limit = it,
                    offset = offset
                )
            } ?: getFilterQueryUnlimited(
                accountId = accountId,
                walletId = walletId,
                sortOrder = sortOrder,
                sortBy = sortBy,
                dataFilter = dataFilter
            )
        }.map {
            Event(it)
        }
    }

    fun count(walletId: Uuid, dataFilter: Map<String, List<String>>): Long = transaction {
        Events.selectAll().where { Events.wallet eq walletId }.addWhereClause(dataFilter).count()
    }


    fun add(events: List<Event>): Unit = transaction {
        Events.batchInsert(events) {
            this[Events.tenant] = it.tenant
            this[Events.originator] = it.originator ?: "unknown"
            this[Events.account] = it.account
            this[Events.wallet] = it.wallet
            this[Events.credentialId] = it.credentialId
            this[Events.timestamp] = it.timestamp.toJavaInstant()
            this[Events.event] = it.event
            this[Events.action] = it.action
            this[Events.data] = Json.encodeToString(it.data)
            this[Events.note] = it.note
        }
    }

    fun delete(id: Int): Int = transaction {
        Events.deleteWhere { Events.id eq id }
    }

    private fun Query.addWhereClause(dataFilter: Map<String, List<String>>) = let {
        dataFilter.forEach {
            when (it.key.lowercase()) {
                "event" -> this.andWhere { Events.event inList it.value }
                "action" -> this.andWhere { Events.action inList it.value }
                "tenant" -> this.andWhere { Events.tenant inList it.value }
                "originator" -> this.andWhere { Events.originator inList it.value }
                "credentialid" -> this.andWhere { Events.credentialId inList it.value }
                "timestamp", "addedon", "createdon" -> runCatching {
                    it.value.map { LocalDate.parse(it) }
                }.getOrNull()?.let {
                    this.andWhere {
                        Events.timestamp.date() inList it
                    }
                }
            }
        }
        this
    }

    private fun getFilterQueryLimited(
        accountId: Uuid,
        walletId: Uuid,
        sortOrder: String,
        sortBy: String,
        dataFilter: Map<String, List<String>>,
        limit: Int,
        offset: Long,
    ) = getFilterQueryUnlimited(
        accountId = accountId,
        walletId = walletId,
        sortOrder = sortOrder,
        sortBy = sortBy,
        dataFilter = dataFilter
    ).limit(count = limit).offset(start = offset)

    private fun getFilterQueryUnlimited(
        accountId: Uuid,
        walletId: Uuid,
        sortOrder: String,
        sortBy: String,
        dataFilter: Map<String, List<String>>,
    ) = Events.selectAll().where { Events.account eq accountId or (Events.wallet eq walletId) }
        .orderBy(
            getColumn(sortBy) ?: Events.timestamp,
            sortOrder.takeIf { it.uppercase() == "ASC" }?.let { SortOrder.ASC } ?: SortOrder.DESC)
        .addWhereClause(dataFilter)

    private fun getColumn(name: String) = Events.columns.singleOrNull {
        it.name == name.lowercase()
    }
}
