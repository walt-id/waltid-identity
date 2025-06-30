package id.walt.oid4vc.util

import kotlin.random.Random


object ShortIdUtils {

    private val squids = Sqids()

    // todo: SecureRandom()
    fun randomSessionId(): String = squids.encode(listOf(Random.nextLong(0, Long.MAX_VALUE)))
}
