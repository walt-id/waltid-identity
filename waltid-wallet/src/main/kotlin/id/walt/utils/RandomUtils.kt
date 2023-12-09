package id.walt.utils

import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object RandomUtils {

    val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray {
        val byteArray = ByteArray(size)
        random.nextBytes(byteArray)
        return byteArray
    }

    /**
     * Make sure bits % 8 == 0
     */
    fun randomBase64UrlString(bits: Int) =
        Base64.UrlSafe.encode(randomBytes(bits / 8)).replace("=", "")

}
