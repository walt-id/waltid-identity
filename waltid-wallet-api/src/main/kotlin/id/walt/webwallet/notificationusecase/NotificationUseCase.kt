package id.walt.webwallet.notificationusecase

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.service.notifications.NotificationService
import kotlinx.datetime.Instant
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

    fun findAll(wallet: UUID, parameter: NotificationFilterParameter) = service.list(
        wallet = wallet,
        type = parameter.type,
        addedOn = parameter.addedOn?.let { Instant.parse(it) },
        isRead = parameter.isRead,
        sortAscending = parseSortOrder(parameter.sort)
    )

    fun findById(id: UUID) = service.get(id)
    fun deleteById(id: UUID) = service.delete(id)
    fun deleteAll(wallet: UUID) = service.list(wallet).mapNotNull { it.id?.let { UUID(it) } }.let {
        service.delete(*it.toTypedArray())
    }

    private fun parseSortOrder(sort: String) = sort.lowercase().takeIf { it == "asc" }?.let { true } ?: false
}

data class NotificationFilterParameter(
    val type: String?,
    val isRead: Boolean?,
    val sort: String = "desc",
    val addedOn: String? = null,
)