package id.walt.webwallet.utils

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import java.security.SecureRandom

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
        randomBytes(bits / 8).encodeToBase64Url()

}
