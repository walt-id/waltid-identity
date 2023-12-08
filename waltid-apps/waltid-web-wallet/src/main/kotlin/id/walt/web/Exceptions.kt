package id.walt.web

import id.walt.db.models.AccountWalletPermissions
import io.ktor.http.*
import kotlinx.serialization.SerialName

sealed class WebException(val status: HttpStatusCode, message: String) : Exception(message)

class UnauthorizedException(message: String) : WebException(HttpStatusCode.Unauthorized, message)
class ForbiddenException(message: String) : WebException(HttpStatusCode.Forbidden, message)

@SerialName("InsufficientPermissions")
class InsufficientPermissionsException(
    minimumRequired: AccountWalletPermissions,
    current: AccountWalletPermissions,
) : WebException(HttpStatusCode.Forbidden, "You do not have enough permissions to access this action. Minimum required permissions: $minimumRequired, your current permissions: $current")
