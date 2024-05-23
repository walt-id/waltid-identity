package id.walt.oid4vc.util

import kotlinx.uuid.SecureRandom

object ShortIdUtils {

    private val squids = Sqids()

    fun randomSessionId(): String = squids.encode(listOf(SecureRandom.nextLong(0, Long.MAX_VALUE)))
}
