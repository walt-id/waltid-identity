package id.walt.oid4vc.util

import kotlinx.uuid.SecureRandom

object ShortIdUtils {

    private val squids = Sqids()

    fun randomSessionId(): String {
        return squids.encode(
            listOf(
                (SecureRandom.nextDouble() * SecureRandom.nextLong()).toLong()
            )
        )
    }
}
