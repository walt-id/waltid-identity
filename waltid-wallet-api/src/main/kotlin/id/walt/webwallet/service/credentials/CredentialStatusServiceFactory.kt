package id.walt.webwallet.service.credentials

import id.walt.webwallet.service.credentials.status.CredentialStatusTypes
import id.walt.webwallet.service.credentials.status.StatusListCredentialStatusService

class CredentialStatusServiceFactory(
    private val statusListService: StatusListCredentialStatusService,
) {
    fun new(type: String) = when (type) {
        in CredentialStatusTypes.StatusList.type -> statusListService
        else -> error("Not supported credential status type: $type")
    }
}