package id.walt.webwallet.service.credentials

import id.walt.webwallet.service.credentials.status.StatusListCredentialStatusService

class CredentialStatusServiceFactory(
    private val statusListService: StatusListCredentialStatusService,
) {
    fun new() = statusListService
}