package id.walt.openid4vci.validation

import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.NotificationErrorCodes
import id.walt.openid4vci.requests.notification.DefaultNotificationRequest
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.walt.openid4vci.requests.notification.NotificationRequestValidationResult

/** Validates notification parameters after the provider has verified the access token. */
class DefaultNotificationRequestValidator : NotificationRequestValidator {
    override fun validate(parameters: Map<String, List<String>>): NotificationRequestValidationResult {
        val notificationId = parameters["notification_id"]?.singleOrNull()?.takeIf { it.isNotBlank() }
            ?: return invalidNotificationRequest()
        val event = parameters["event"]?.singleOrNull()?.let(NotificationEvent::fromWireValue)
            ?: return invalidNotificationRequest()
        val description = if ("event_description" in parameters) {
            parameters.getValue("event_description").singleOrNull()?.takeIf { it.isValidEventDescription() }
                ?: return invalidNotificationRequest()
        } else null
        return NotificationRequestValidationResult.Success(DefaultNotificationRequest(notificationId, event, description))
    }

    private fun invalidNotificationRequest() = NotificationRequestValidationResult.Failure(
        NotificationError(NotificationErrorCodes.INVALID_NOTIFICATION_REQUEST),
    )
}

/** OpenID4VCI `event_description`: USASCII `%x20-21 / %x23-5B / %x5D-7E`. */
private fun String.isValidEventDescription(): Boolean =
    all { it in ' '..'!' || it in '#'..'[' || it in ']'..'~' }
