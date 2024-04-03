package id.walt.webwallet.service.credentials.status

import id.walt.webwallet.usecase.credential.CredentialStatusResult

interface CredentialStatusService {
    suspend fun get(statusEntry: CredentialStatusEntry): CredentialStatusResult
}