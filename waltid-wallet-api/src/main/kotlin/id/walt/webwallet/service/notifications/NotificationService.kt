package id.walt.webwallet.service.notifications

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletNotifications
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.dateParam
import org.jetbrains.exposed.sql.transactions.transaction

object NotificationService {
    fun list(
        wallet: UUID,
        type: String? = null,
        addedOn: String? = null,
        isRead: Boolean? = null,
        sortAscending: Boolean? = null,
    ): List<Notification> = transaction {
        filterAll(wallet, type, isRead, addedOn, sortAscending).map {
            Notification(it)
        }
    }

    fun get(id: UUID): Result<Notification> = transaction {
        WalletNotifications.selectAll().where { WalletNotifications.id eq id }.singleOrNull()?.let {
            Result.success(Notification(it))
        } ?: Result.failure(Throwable("Notification not found for id: $id"))
    }

    fun add(notifications: List<Notification>): List<UUID> = transaction {
        insert(*notifications.toTypedArray())
    }

    fun delete(vararg ids: UUID): Int = transaction {
        WalletNotifications.deleteWhere { WalletNotifications.id inList ids.toList() }
    }

    fun update(vararg notification: Notification): Int = transaction {
        notification.fold(0) { acc, notification ->
            acc + update(notification)
        }
    }

    private fun filterAll(
        wallet: UUID, type: String?, isRead: Boolean?, addedOn: String?, ascending: Boolean?
    ) = WalletNotifications.selectAll().where {
        WalletNotifications.wallet eq wallet
    }.andWhere {
        isRead?.let { WalletNotifications.isRead eq it } ?: Op.TRUE
    }.andWhere {
        type?.let { WalletNotifications.type eq it } ?: Op.TRUE
    }.andWhere {
        runCatching { LocalDate.parse(addedOn!!) }.getOrNull()
            ?.let { WalletNotifications.addedOn.date() eq dateParam(it.toJavaLocalDate()) } ?: Op.TRUE
    }.orderBy(column = WalletNotifications.addedOn,
        order = ascending?.takeIf { it }?.let { SortOrder.ASC } ?: SortOrder.DESC)

    private fun insert(vararg notifications: Notification): List<UUID> = WalletNotifications.batchInsert(
        data = notifications.toList()
    ) {
        it.id?.let { UUID(it) }?.let { this[WalletNotifications.id] = it } //TODO: converted back and forth (see silent exchange controller)
        this[WalletNotifications.account] = UUID(it.account)
        this[WalletNotifications.wallet] = UUID(it.wallet)
        this[WalletNotifications.type] = it.type
        this[WalletNotifications.isRead] = it.status
        this[WalletNotifications.addedOn] = it.addedOn.toJavaInstant()
        this[WalletNotifications.data] = it.data
    }.map { it[WalletNotifications.id].value }

    private fun update(notification: Notification) = notification.id?.let {
        UUID(it)
    }?.let {
        WalletNotifications.update({ WalletNotifications.id eq it }) {
            it[this.account] = UUID(notification.account)
            it[this.wallet] = UUID(notification.wallet)
            it[this.type] = notification.type
            it[this.isRead] = notification.status
            it[this.addedOn] = notification.addedOn.toJavaInstant()
            it[this.data] = notification.data
        }
    } ?: 0
}