package id.walt.issuer.revocation.statuslist2021.index

import kotlin.random.Random

class RandomIndexingStrategy : IndexingStrategy() {
    private val trialAttempts = 100
    override fun next(bitset: Array<String>): String =
        generate(bitset) ?: throw Exception("Couldn't find an empty bit, exhausted the given attempts limit: $trialAttempts")

    private fun generate(bitset: Array<String>) = let {
        var idx: String? = null
        var attempts = 1
        do {
            idx = Random.nextLong(from = 0, until = Long.MAX_VALUE).toString()
        } while (bitset.contains(idx) && attempts++ <= trialAttempts)
        idx
    }
}
