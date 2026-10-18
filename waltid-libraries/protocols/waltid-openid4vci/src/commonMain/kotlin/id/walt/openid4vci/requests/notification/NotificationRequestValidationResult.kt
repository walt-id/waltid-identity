package id.walt.openid4vci.requests.notification

import id.walt.openid4vci.errors.NotificationError

/** Parameter validation only; the provider verifies and attaches access-token claims. */
sealed class NotificationRequestValidationResult {
    data class Success(val request: NotificationRequest) : NotificationRequestValidationResult()
    data class Failure(val error: NotificationError) : NotificationRequestValidationResult()
}
