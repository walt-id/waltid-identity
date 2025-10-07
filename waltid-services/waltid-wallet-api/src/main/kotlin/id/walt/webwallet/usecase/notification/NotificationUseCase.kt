@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.usecase.notification

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.service.notifications.NotificationService
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class NotificationUseCase(
    private val service: NotificationService,
    private val notificationFormatter: NotificationDataFormatter,
) {
    fun add(vararg notification: Notification) = service.add(notification.toList())
    fun setStatus(vararg id: Uuid, isRead: Boolean) = id.mapNotNull {
        service.get(it).getOrNull()
    }.map {
        service.update(
            Notification(
                id = it.id,
                account = it.account,
                wallet = it.wallet,
                type = it.type,
                status = isRead,
                addedOn = it.addedOn,
                data = it.data
            )
        )
    }.size

    suspend fun findById(id: Uuid) = service.get(id).fold(onSuccess = {
        Result.success(notificationFormatter.format(it))
    }, onFailure = { Result.failure(it) })

    fun deleteById(id: Uuid) = service.delete(id)
    fun deleteAll(wallet: Uuid) = service.list(wallet).mapNotNull { it.id?.let { Uuid.parse(it) } }.let {
        service.delete(*it.toTypedArray())
    }
}
