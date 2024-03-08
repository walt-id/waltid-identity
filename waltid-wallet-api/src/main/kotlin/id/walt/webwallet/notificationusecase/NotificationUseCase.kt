package id.walt.webwallet.notificationusecase

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.service.notifications.NotificationService
import kotlinx.uuid.UUID

class NotificationUseCase(
    private val service: NotificationService,
) {

    fun setStatus(vararg id: UUID, isRead: Boolean) = id.mapNotNull {
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

    fun findAll(wallet: UUID) = service.list(wallet)
    fun findById(id: UUID) = service.get(id)
    fun deleteById(id: UUID) = service.delete(id)
    fun deleteAll(wallet: UUID) = service.list(wallet).mapNotNull { it.id }.let {
        service.delete(*it.toTypedArray())
    }
}