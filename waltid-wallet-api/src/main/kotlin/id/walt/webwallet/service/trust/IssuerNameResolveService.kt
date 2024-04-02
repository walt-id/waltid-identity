package id.walt.webwallet.service.trust

interface IssuerNameResolveService {
    suspend fun resolve(did: String): String
}