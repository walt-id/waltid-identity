package id.walt.openid4vci.errors

import id.walt.errors.ClientRequestException

/** Like `require`, for a check on what the client sent: throws [ClientRequestException], whose message is returned. */
internal inline fun ensureClientInput(condition: Boolean, message: () -> String) {
    if (!condition) throw ClientRequestException(message())
}
