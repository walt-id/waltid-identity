package id.walt.issuer.revocation.statuslist2021.index

interface IndexingStrategy {
    fun next(bitset: Array<String>): String
}
