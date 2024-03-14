package id.walt.webwallet.service.notifications

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletNotifications
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.temporal.ChronoUnit

object NotificationService {
    fun list(
        wallet: UUID,
        type: String? = null,
        addedOn: Instant? = null,
        isRead: Boolean? = null,
        sortAscending: Boolean? = null
    ): List<Notification> = transaction {
        filterAll(wallet, type, isRead, sortAscending).mapNotNull { result ->
            if (addedOn != null) {
                addedOn.takeIf {
                    result[WalletNotifications.addedOn].truncatedTo(ChronoUnit.DAYS) == it.toJavaInstant()
                        .truncatedTo(ChronoUnit.DAYS)
                }?.let {
                    Notification(result)
                }
            } else Notification(result)
        }
    }

    fun get(id: UUID): Result<Notification> = transaction {
        WalletNotifications.selectAll().where { WalletNotifications.id eq id }.singleOrNull()?.let {
            Result.success(Notification(it))
        } ?: Result.failure(Throwable("Notification not found for id: $id"))
    }

    fun add(notification: Notification): Int = transaction {
        upsert(notification)
    }

    fun delete(vararg ids: UUID): Int = transaction {
        WalletNotifications.deleteWhere { WalletNotifications.id inList ids.toList() }
    }

    fun update(vararg notification: Notification): Int = transaction {
        upsert(*notification)
    }

    private fun filterAll(
        wallet: UUID, type: String?, isRead: Boolean?, ascending: Boolean?
    ) = WalletNotifications.selectAll().where {
        WalletNotifications.wallet eq wallet
    }.andWhere {
        isRead?.let { WalletNotifications.isRead eq it } ?: Op.TRUE
    }.andWhere {
        type?.let { WalletNotifications.type eq it } ?: Op.TRUE
    }.orderBy(column = WalletNotifications.addedOn,
        order = ascending?.takeIf { it }?.let { SortOrder.ASC } ?: SortOrder.DESC)

    private fun upsert(vararg notifications: Notification): Int = WalletNotifications.batchUpsert(
        notifications.toList(),
        WalletNotifications.id,
    ) {
        this[WalletNotifications.account] = UUID(it.account)
        this[WalletNotifications.wallet] = UUID(it.wallet)
        this[WalletNotifications.type] = it.type
        this[WalletNotifications.addedOn] = it.addedOn.toJavaInstant()
        this[WalletNotifications.data] = it.data
    }.size
}