package id.walt.webwallet.service.notifications

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletNotifications
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object NotificationService {
    fun list(wallet: UUID): List<Notification> = transaction {
        WalletNotifications.selectAll().where {
            WalletNotifications.wallet eq wallet
        }.map {
            Notification(it)
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

    private fun upsert(vararg notifications: Notification): Int = WalletNotifications.batchUpsert(
        notifications.toList(),
        WalletNotifications.id,
    ) {
        this[WalletNotifications.account] = it.account
        this[WalletNotifications.wallet] = it.wallet
        this[WalletNotifications.type] = it.type
        this[WalletNotifications.addedOn] = it.addedOn.toJavaInstant()
        this[WalletNotifications.data] = it.data
    }.size
}