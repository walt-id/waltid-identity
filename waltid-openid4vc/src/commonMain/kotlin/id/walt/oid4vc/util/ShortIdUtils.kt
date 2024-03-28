package id.walt.oid4vc.util

import kotlinx.uuid.SecureRandom

object ShortIdUtils {

    private val squids = Sqids()

    fun randomSessionId(): String {
        return squids.encode(
            listOf(
                (SecureRandom.nextDouble(0.0, Double.MAX_VALUE) * SecureRandom.nextLong(0, Long.MAX_VALUE)).toLong()
            )
        )
    }
}
