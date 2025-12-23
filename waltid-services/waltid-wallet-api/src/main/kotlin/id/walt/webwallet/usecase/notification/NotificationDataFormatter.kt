package id.walt.webwallet.usecase.notification

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.Notification.CredentialIssuanceData
import id.walt.webwallet.usecase.entity.EntityNameResolutionUseCase
import kotlinx.serialization.json.JsonObject

class NotificationDataFormatter(
    private val issuerNameResolutionUseCase: EntityNameResolutionUseCase,
) {
    private val message = "%s has issued a new credential to you (%s)"//TODO: make configurable
    private val versionRegex = "(V\\d+)\\)$".toRegex()

    suspend fun format(notification: Notification) = when (notification.data) {
        is CredentialIssuanceData -> NotificationDTO(
            notification,
            JsonObject(
                mapOf(
                    "credentialId" to notification.data.credentialId.toJsonElement(),
                    "detail" to credentialIssuanceDetails(notification.data).toJsonElement(),
                    "logo" to notification.data.logo.toJsonElement(),
                )
            ),
        )

        //else -> NotificationDTO(notification, JsonObject((emptyMap())))
    }

    private suspend fun credentialIssuanceDetails(data: CredentialIssuanceData) =
        issuerNameResolutionUseCase.resolve(data.issuer).let {
            versionRegex.replace(String.format(message, it, data.credentialType), ")")
        }
}
