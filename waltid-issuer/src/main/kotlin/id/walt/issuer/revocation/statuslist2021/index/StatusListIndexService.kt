package id.walt.issuer.revocation.statuslist2021.index

interface StatusListIndexService {
    fun index(url: String): String
}
