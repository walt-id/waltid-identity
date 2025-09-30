@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.service.notifications

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletNotifications
import id.walt.webwallet.db.models.serialize
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.dateParam
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

object NotificationService {
    fun list(
        wallet: Uuid,
        type: String? = null,
        addedOn: String? = null,
        isRead: Boolean? = null,
        sortAscending: Boolean? = null,
    ): List<Notification> = transaction {
        filterAll(wallet, type, isRead, addedOn, sortAscending).map {
            Notification(it)
        }
    }

    fun get(id: Uuid): Result<Notification> = transaction {
        WalletNotifications.selectAll().where { WalletNotifications.id eq id.toJavaUuid() }.singleOrNull()?.let {
            Result.success(Notification(it))
        } ?: Result.failure(Throwable("Notification not found for id: $id"))
    }

    fun add(notifications: List<Notification>): List<Uuid> = transaction {
        insert(*notifications.toTypedArray())
    }

    fun delete(vararg ids: Uuid): Int = transaction {
        WalletNotifications.deleteWhere { id inList ids.map { it.toJavaUuid() } }
    }

    fun update(vararg notification: Notification): Int = transaction {
        notification.fold(0) { acc, notification ->
            acc + update(notification)
        }
    }

    private fun filterAll(
        wallet: Uuid, type: String?, isRead: Boolean?, addedOn: String?, ascending: Boolean?,
    ) = WalletNotifications.selectAll().where {
        WalletNotifications.wallet eq wallet.toJavaUuid()
    }.andWhere {
        isRead?.let { WalletNotifications.isRead eq it } ?: Op.TRUE
    }.andWhere {
        type?.let { WalletNotifications.type eq it } ?: Op.TRUE
    }.andWhere {
        runCatching { LocalDate.parse(addedOn!!) }.getOrNull()
            ?.let { WalletNotifications.addedOn.date() eq dateParam(it.toJavaLocalDate()) } ?: Op.TRUE
    }.orderBy(
        column = WalletNotifications.addedOn,
        order = ascending?.takeIf { it }?.let { SortOrder.ASC } ?: SortOrder.DESC)

    private fun insert(vararg notifications: Notification): List<Uuid> = WalletNotifications.batchInsert(
        data = notifications.toList()
    ) {
        it.id?.let { Uuid.parse(it) }?.let {
            this[WalletNotifications.id] = it.toJavaUuid()
        } //TODO: converted back and forth (see silent exchange controller)
        this[WalletNotifications.account] = Uuid.parse(it.account)
        this[WalletNotifications.wallet] = Uuid.parse(it.wallet).toJavaUuid()
        this[WalletNotifications.type] = it.type
        this[WalletNotifications.isRead] = it.status
        this[WalletNotifications.addedOn] = it.addedOn.toJavaInstant()
        this[WalletNotifications.data] = it.data.serialize()
    }.map { it[WalletNotifications.id].value.toKotlinUuid() }

    private fun update(notification: Notification) = notification.id?.let {
        Uuid.parse(it)
    }?.let {
        WalletNotifications.update({ WalletNotifications.id eq it.toJavaUuid() }) {
            it[this.account] = Uuid.parse(notification.account)
            it[this.wallet] = Uuid.parse(notification.wallet).toJavaUuid()
            it[this.type] = notification.type
            it[this.isRead] = notification.status
            it[this.addedOn] = notification.addedOn.toJavaInstant()
            it[this.data] = notification.data.serialize()
        }
    } ?: 0
}
