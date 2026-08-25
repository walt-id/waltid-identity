package id.walt.openid4vci.requests.notification

import id.walt.openid4vci.errors.NotificationError

sealed class NotificationRequestResult {
    data class Success(val request: NotificationRequest) : NotificationRequestResult()
    data class Failure(val error: NotificationError) : NotificationRequestResult()

    fun isSuccess(): Boolean = this is Success
}
