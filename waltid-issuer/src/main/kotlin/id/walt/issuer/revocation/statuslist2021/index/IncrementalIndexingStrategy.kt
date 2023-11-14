package id.walt.issuer.revocation.statuslist2021.index

class IncrementalIndexingStrategy : IndexingStrategy() {

    override fun next(bitset: Array<String>): String = let {
        bitset.maxOrNull()?.toLongOrNull()?.let {
            it + 1
        } ?: 0
    }.toString()
}
