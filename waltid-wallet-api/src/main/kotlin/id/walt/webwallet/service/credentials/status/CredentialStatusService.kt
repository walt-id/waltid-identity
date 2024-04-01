package id.walt.webwallet.service.credentials.status

interface CredentialStatusService {
    fun get(entry: CredentialStatusEntry): Int
}