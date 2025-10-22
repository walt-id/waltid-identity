@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.notification

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.notifications.NotificationService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class NotificationFilterUseCase(
    private val notificationService: NotificationService,
    private val credentialService: CredentialsService,
    private val notificationFormatter: NotificationDataFormatter,
) {
    suspend fun filter(wallet: Uuid, parameter: NotificationFilterParameter): List<NotificationDTO> =
        notificationService.list(
            wallet = wallet,
            type = parameter.type,
            addedOn = parameter.addedOn,
            isRead = parameter.isRead,
            sortAscending = parseSortOrder(parameter.sort)
        ).let {
            filterPending(wallet, it, parameter.showPending)
        }.map {
            notificationFormatter.format(it)
        }

    private fun parseSortOrder(sort: String) = sort.lowercase().takeIf { it == "asc" }?.let { true } == true

    private fun filterPending(wallet: Uuid, notifications: List<Notification>, showPending: Boolean?) =
        showPending?.let { pending ->
            credentialService.get(
                wallet,
                notifications.mapNotNull { (it.data as? Notification.CredentialIssuanceData)?.credentialId })
                .filter {
                    it.pending == pending
                }.let {
                    intersect(notifications, it.map { it.id })
                }
        } ?: notifications

    private fun intersect(notifications: List<Notification>, credentials: List<String>) = let {
        notifications.filter {
            credentials.contains(
                (it.data as? Notification.CredentialIssuanceData)?.credentialId
            )
        }
    }
}

data class NotificationFilterParameter(
    val type: String?,
    val isRead: Boolean?,
    val sort: String = "desc",
    val addedOn: String? = null,
    val showPending: Boolean?,
)
