package id.walt.webwallet.web

import id.walt.commons.web.WebException
import id.walt.webwallet.db.models.AccountWalletPermissions
import io.ktor.http.*
import kotlinx.serialization.SerialName


@SerialName("InsufficientPermissions")
class InsufficientPermissionsException(
    val minimumRequired: AccountWalletPermissions,
    val current: AccountWalletPermissions,
) : WebException(
    HttpStatusCode.Forbidden.value,
    "You do not have enough permissions to access this action. Minimum required permissions: $minimumRequired, your current permissions: $current"
)
